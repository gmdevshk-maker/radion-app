package com.radion.app.ui

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.radion.app.data.Channel
import com.radion.app.data.ChannelRepository
import com.radion.app.data.ChannelType
import com.radion.app.data.PreferencesRepository
import com.radion.app.playback.RadioPlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val channelRepo = ChannelRepository(application)
    private val prefsRepo = PreferencesRepository(application)

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels

    val favorites: StateFlow<Set<String>> =
        prefsRepo.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel: StateFlow<Channel?> = _currentChannel

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    private val _sleepMinutes = MutableStateFlow<Int?>(null)
    val sleepMinutes: StateFlow<Int?> = _sleepMinutes

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen

    /** 영상 채널인데 보이는 라디오가 미송출이라 오디오로만 재생 중 */
    private val _videoUnavailable = MutableStateFlow(false)
    val videoUnavailable: StateFlow<Boolean> = _videoUnavailable

    private val _toast = MutableSharedFlow<String>()
    val toast: SharedFlow<String> = _toast

    /** 화면이 보이는 상태인지 (보이는 라디오 영상 트랙 on/off 판단용) */
    private var inForeground = true

    private var retryCount = 0
    private var sleepResetJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            _channels.value = channelRepo.loadChannels()
            val lastId = prefsRepo.lastChannelId.first()
            if (_currentChannel.value == null) {
                _currentChannel.value = _channels.value.firstOrNull { it.id == lastId }
                    ?: _channels.value.firstOrNull()
            }
        }

        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, RadioPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val controller = future.get()
            _controller.value = controller
            _isPlaying.value = controller.isPlaying
            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) retryCount = 0
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _isBuffering.value = playbackState == Player.STATE_BUFFERING
                }

                override fun onPlayerError(error: PlaybackException) {
                    handlePlayerError()
                }
            })
        }, MoreExecutors.directExecutor())
    }

    fun selectChannel(channel: Channel, play: Boolean = true) {
        val previous = _currentChannel.value
        _currentChannel.value = channel
        _videoUnavailable.value = false
        if (channel.type == ChannelType.AUDIO) _isFullscreen.value = false
        viewModelScope.launch { prefsRepo.setLastChannel(channel.id) }
        if (play) {
            retryCount = 0
            startPlayback(channel)
        } else if (previous?.id != channel.id) {
            _isPlaying.value = false
        }
    }

    fun togglePlay() {
        val channel = _currentChannel.value ?: return
        val controller = _controller.value
        when {
            // 서비스 연결 전이면 startPlayback이 연결을 기다렸다가 재생한다
            controller == null -> selectChannel(channel)
            controller.isPlaying -> controller.pause()
            controller.currentMediaItem?.mediaId == channel.id &&
                controller.playbackState != Player.STATE_IDLE -> {
                // 라이브 스트림: 일시정지 지점이 아니라 라이브 에지로 복귀
                controller.seekToDefaultPosition()
                controller.play()
            }
            else -> selectChannel(channel)
        }
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch { prefsRepo.toggleFavorite(channelId) }
    }

    /** 취침 타이머 칩: 15 → 30 → 60 → 해제 순환 */
    fun cycleSleepTimer() {
        val next = when (_sleepMinutes.value) {
            null -> 15
            15 -> 30
            30 -> 60
            else -> null
        }
        _sleepMinutes.value = next
        sendSleepCommand(next ?: 0)

        sleepResetJob?.cancel()
        if (next != null) {
            sleepResetJob = viewModelScope.launch {
                delay(next * 60_000L)
                _sleepMinutes.value = null
            }
            showToast("${next}분 후 재생이 종료됩니다")
        } else {
            showToast("취침 타이머 해제")
        }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _isFullscreen.value = fullscreen
    }

    /** Activity onStart/onStop에서 호출 — 백그라운드에서는 영상 트랙을 끊어 소리만 유지 */
    fun setForeground(foreground: Boolean) {
        inForeground = foreground
        if (!foreground) _isFullscreen.value = false
        applyVideoTrackPolicy()
    }

    private fun startPlayback(channel: Channel, audioFallback: Boolean = false) {
        viewModelScope.launch {
            _isBuffering.value = true

            // 앱 시작 직후엔 MediaController 연결이 끝나지 않았을 수 있다.
            // 여기서 기다리지 않으면 그 사이의 재생 요청이 조용히 사라진다.
            val controller = withTimeoutOrNull(CONTROLLER_WAIT_MS) { _controller.filterNotNull().first() }
            if (controller == null) {
                _isBuffering.value = false
                showToast("재생 서비스에 연결하지 못했습니다")
                return@launch
            }
            if (_currentChannel.value?.id != channel.id) return@launch

            val url = try {
                if (audioFallback) {
                    channelRepo.resolveAudioFallbackUrl(channel)
                } else {
                    channelRepo.resolveStreamUrl(channel)
                }
            } catch (e: Exception) {
                // 보이는 라디오 미편성이면 URL 발급 단계에서 실패한다(MBC) → 오디오로 전환
                if (!audioFallback && canFallBackToAudio(channel)) {
                    _videoUnavailable.value = true
                    showToast("보이는 라디오 송출이 없어 소리만 재생합니다")
                    startPlayback(channel, audioFallback = true)
                    return@launch
                }
                _isBuffering.value = false
                showToast("${channel.name} 스트림 주소를 가져오지 못했습니다")
                return@launch
            }
            if (_currentChannel.value?.id != channel.id) return@launch

            val mediaItem = MediaItem.Builder()
                .setMediaId(channel.id)
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(channel.name)
                        .setArtist(String.format("%.1f MHz", channel.freq))
                        .build(),
                )
                .build()

            applyVideoTrackPolicy()
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }

    private fun applyVideoTrackPolicy() {
        val controller = _controller.value ?: return
        val channel = _currentChannel.value
        // 영상 채널이 포그라운드일 때만 비디오 트랙 활성화.
        // (EBS처럼 오디오 채널인데 영상 트랙이 섞인 스트림도 있어 오디오 채널은 항상 비활성화)
        val enableVideo = channel?.type == ChannelType.VIDEO && inForeground
        controller.trackSelectionParameters = controller.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enableVideo)
            .build()
    }

    private fun canFallBackToAudio(channel: Channel): Boolean =
        channel.type == ChannelType.VIDEO && !_videoUnavailable.value && channel.audioApi != null

    private fun handlePlayerError() {
        val channel = _currentChannel.value ?: return

        // 보이는 라디오 미송출(영상 스트림 오류) → 오디오 스트림으로 폴백
        if (canFallBackToAudio(channel)) {
            _videoUnavailable.value = true
            showToast("보이는 라디오 송출이 없어 소리만 재생합니다")
            startPlayback(channel, audioFallback = true)
            return
        }

        if (retryCount >= MAX_RETRY) {
            showToast("${channel.name} 재생에 실패했습니다")
            return
        }
        retryCount++
        val audioFallback = _videoUnavailable.value
        viewModelScope.launch {
            delay(RETRY_DELAY_MS * retryCount)
            if (_currentChannel.value?.id == channel.id) {
                startPlayback(channel, audioFallback)
            }
        }
    }

    private fun sendSleepCommand(minutes: Int) {
        val controller = _controller.value ?: return
        controller.sendCustomCommand(
            SessionCommand(RadioPlaybackService.ACTION_SLEEP_TIMER, Bundle.EMPTY),
            bundleOf(RadioPlaybackService.EXTRA_MINUTES to minutes),
        )
    }

    private fun showToast(message: String) {
        viewModelScope.launch { _toast.emit(message) }
    }

    override fun onCleared() {
        _controller.value?.release()
        super.onCleared()
    }

    companion object {
        private const val MAX_RETRY = 3
        private const val RETRY_DELAY_MS = 2_000L
        private const val CONTROLLER_WAIT_MS = 10_000L
    }
}
