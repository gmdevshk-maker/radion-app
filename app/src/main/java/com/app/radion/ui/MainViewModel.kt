package com.app.radion.ui

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
import com.app.radion.data.ApkInstaller
import com.app.radion.data.Channel
import com.app.radion.data.ChannelRepository
import com.app.radion.data.ChannelType
import com.app.radion.data.NowPlayingRepository
import com.app.radion.data.PreferencesRepository
import com.app.radion.data.ScheduleItem
import com.app.radion.data.ScheduleRepository
import com.app.radion.data.UpdateInfo
import com.app.radion.data.UpdateRepository
import com.app.radion.data.freqText
import com.app.radion.data.hasSchedule
import com.app.radion.playback.RadioPlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val channelRepo = ChannelRepository(application)
    private val prefsRepo = PreferencesRepository(application)
    private val updateRepo = UpdateRepository(application)
    private val nowPlayingRepo = NowPlayingRepository()
    private val scheduleRepo = ScheduleRepository()

    /** 현재 설치된 앱 버전명 (업데이트 다이얼로그 표시용). */
    val appVersion: String = updateRepo.currentVersionName()

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

    /** 취침 타이머 종료 예정 시각. 설정 시점에 고정한다(매초 재계산하면 시각이 밀림). */
    private val _sleepEndsAt = MutableStateFlow<LocalTime?>(null)
    val sleepEndsAt: StateFlow<LocalTime?> = _sleepEndsAt

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen

    /** 영상 채널인데 보이는 라디오가 미송출이라 오디오로만 재생 중 */
    private val _videoUnavailable = MutableStateFlow(false)
    val videoUnavailable: StateFlow<Boolean> = _videoUnavailable

    /** 스테이지 아래 한 줄로 보여줄 현재 방송 정보. 정보가 없는 채널이면 null */
    private val _nowPlaying = MutableStateFlow<String?>(null)
    val nowPlaying: StateFlow<String?> = _nowPlaying

    /**
     * 방송 정보를 받아오는 중인지.
     *
     * 첫 조회가 끝나기 전에 "수신된 방송 정보가 없습니다"라고 단정하지 않으려고 구분한다
     * (MBC는 280KB 편성표를 받는 날이면 첫 조회가 몇 초 걸린다).
     */
    private val _nowPlayingLoading = MutableStateFlow(false)
    val nowPlayingLoading: StateFlow<Boolean> = _nowPlayingLoading

    /** 수동 갱신 버튼을 누를 수 있는 상태인지. 누른 뒤 [MANUAL_REFRESH_INTERVAL_MS] 동안 false */
    private val _nowPlayingRefreshEnabled = MutableStateFlow(true)
    val nowPlayingRefreshEnabled: StateFlow<Boolean> = _nowPlayingRefreshEnabled

    /** 편성표 시트에 뿌릴 오늘 편성. 시트가 닫혀 있으면 null */
    private val _schedule = MutableStateFlow<ScheduleSheetState?>(null)
    val schedule: StateFlow<ScheduleSheetState?> = _schedule

    /** 값이 바뀌면 갱신 루프가 다시 시작된다(수동 새로고침 트리거) */
    private val nowPlayingRefreshTrigger = MutableStateFlow(0)

    private val _toast = MutableSharedFlow<ToastMessage>()
    val toast: SharedFlow<ToastMessage> = _toast

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    /** 화면이 보이는 상태인지 (영상 트랙 on/off, 방송 정보 갱신 중단 판단용) */
    private val inForeground = MutableStateFlow(true)

    private var retryCount = 0
    private var sleepResetJob: Job? = null

    /** 방송 정보가 어느 채널 것인지 — 채널이 실제로 바뀔 때만 화면을 비우려고 들고 있다 */
    private var nowPlayingChannelId: String? = null

    init {
        viewModelScope.launch {
            _channels.value = channelRepo.loadChannels()
            val lastId = prefsRepo.lastChannelId.first()
            if (_currentChannel.value == null) {
                _currentChannel.value = _channels.value.firstOrNull { it.id == lastId }
                    ?: _channels.value.firstOrNull()
            }
        }

        // 채널이 바뀌거나 앱이 다시 보이면 방송 정보를 새로 받는다.
        // collectLatest가 이전 갱신 루프를 취소하므로 루프는 항상 하나만 돈다.
        viewModelScope.launch {
            combine(
                _currentChannel,
                inForeground,
                nowPlayingRefreshTrigger,
            ) { channel, foreground, _ -> channel to foreground }
                .collectLatest { (channel, foreground) ->
                    // 백그라운드에 다녀온 것뿐이면 받아 둔 정보를 그대로 두고 이어서 갱신한다.
                    // 여기서 비우면 앱으로 돌아올 때마다 한 줄이 사라졌다 다시 뜬다.
                    if (channel?.id != nowPlayingChannelId) {
                        nowPlayingChannelId = channel?.id
                        _nowPlaying.value = null
                    }
                    if (foreground) trackNowPlaying(channel)
                }
        }

        // 앱 시작 시 업데이트 확인 (실패해도 조용히 무시)
        viewModelScope.launch {
            val info = updateRepo.checkForUpdate() ?: return@launch
            if (_updateState.value is UpdateState.Idle) {
                _updateState.value = UpdateState.Available(info)
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

    fun selectChannel(channel: Channel) {
        _currentChannel.value = channel
        _videoUnavailable.value = false
        if (channel.type == ChannelType.AUDIO) _isFullscreen.value = false
        viewModelScope.launch { prefsRepo.setLastChannel(channel.id) }
        retryCount = 0
        startPlayback(channel)
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

    /** 취침 타이머 칩: 30 → 60 → 90 → 120 → 해제 순환 (30분 간격, 최대 120분) */
    fun cycleSleepTimer() {
        val next = when (_sleepMinutes.value) {
            null -> 30
            30 -> 60
            60 -> 90
            90 -> 120
            else -> null
        }
        _sleepMinutes.value = next
        _sleepEndsAt.value = next?.let { LocalTime.now().plusMinutes(it.toLong()) }
        sendSleepCommand(next ?: 0)

        sleepResetJob?.cancel()
        if (next != null) {
            sleepResetJob = viewModelScope.launch {
                delay(next * 60_000L)
                _sleepMinutes.value = null
                _sleepEndsAt.value = null
            }
            showToast("${next}분 후 재생이 종료됩니다", ToastAnchor.SLEEP_CHIP)
        } else {
            showToast("취침 타이머 해제", ToastAnchor.SLEEP_CHIP)
        }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _isFullscreen.value = fullscreen
    }

    /** Activity onStart/onStop에서 호출 — 백그라운드에서는 영상 트랙을 끊어 소리만 유지 */
    fun setForeground(foreground: Boolean) {
        inForeground.value = foreground
        if (!foreground) _isFullscreen.value = false
        applyVideoTrackPolicy()
    }

    /**
     * 방송 정보 줄의 새로고침 버튼.
     *
     * 연타로 방송사 API를 두드리지 않도록 누른 뒤 [MANUAL_REFRESH_INTERVAL_MS] 동안은 무시한다.
     * 그동안 버튼이 흐려지므로 눌리지 않는 이유가 화면에 드러난다.
     */
    fun refreshNowPlaying() {
        if (!_nowPlayingRefreshEnabled.value) return
        _nowPlayingRefreshEnabled.value = false
        // 트리거가 바뀌면 collectLatest가 갱신 루프를 다시 시작해 곧바로 다시 받아 온다
        nowPlayingRefreshTrigger.value++
        viewModelScope.launch {
            delay(MANUAL_REFRESH_INTERVAL_MS)
            _nowPlayingRefreshEnabled.value = true
        }
    }

    /**
     * 방송 정보를 받아 두고, 방송사가 알려준 다음 시각까지 기다렸다가 다시 받는다.
     *
     * 프로그램은 종료 시각에, 곡이 붙는 채널(MBC)은 그보다 짧은 주기로 갱신된다.
     * 시각을 못 받았거나 호출이 실패하면 [NOW_PLAYING_RETRY_MS] 뒤에 다시 시도한다.
     */
    private suspend fun trackNowPlaying(channel: Channel?) {
        if (channel?.infoProvider == null) {
            _nowPlayingLoading.value = false
            return
        }
        try {
            while (true) {
                _nowPlayingLoading.value = true
                val info = nowPlayingRepo.fetch(channel)
                _nowPlayingLoading.value = false
                _nowPlaying.value = info?.text
                val waitMs = info?.refreshAt
                    ?.let { Duration.between(LocalDateTime.now(), it).toMillis() }
                    ?: NOW_PLAYING_RETRY_MS
                // 시계가 어긋나거나 편성 시각이 이상해도 과하게 자거나 깨지 않도록 가둔다
                delay(waitMs.coerceIn(NOW_PLAYING_MIN_WAIT_MS, NOW_PLAYING_MAX_WAIT_MS))
            }
        } finally {
            // 채널 변경·백그라운드 진입으로 취소되면 조회 중 상태가 그대로 남는다
            _nowPlayingLoading.value = false
        }
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
            } catch (_: Exception) {
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
                        .setArtist("${channel.freqText} MHz")
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
        val enableVideo = channel?.type == ChannelType.VIDEO && inForeground.value
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

    /** 업데이트 다이얼로그의 '업데이트' 버튼: APK 다운로드 후 시스템 설치 화면을 띄운다. */
    fun startUpdate() {
        val info = (_updateState.value as? UpdateState.Available)?.info ?: return
        val context = getApplication<Application>()

        // '알 수 없는 앱 설치' 권한이 없으면 설정 화면으로 보내고 중단(허용 후 다시 시도)
        if (!ApkInstaller.canInstall(context)) {
            showToast("설정에서 '알 수 없는 앱 설치'를 허용한 뒤 다시 눌러주세요")
            ApkInstaller.openInstallPermissionSettings(context)
            return
        }

        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(0f)
            try {
                val apk = updateRepo.downloadApk(info) { progress ->
                    _updateState.value = UpdateState.Downloading(progress.coerceAtLeast(0f))
                }
                ApkInstaller.install(context, apk)
                _updateState.value = UpdateState.Idle
            } catch (_: Exception) {
                showToast("업데이트 다운로드에 실패했습니다")
                _updateState.value = UpdateState.Available(info)
            }
        }
    }

    /**
     * 방송 정보 줄을 눌렀을 때: 오늘 편성표 시트를 연다.
     *
     * 표를 받는 동안에도 시트는 먼저 띄운다 — MBC는 280KB짜리 주간 편성표를 받는 날이 있어
     * 다 받고 열면 누르고 한참 아무 반응이 없다.
     */
    fun openSchedule() {
        val channel = _currentChannel.value ?: return
        if (!channel.hasSchedule) return

        _schedule.value = ScheduleSheetState(channel, items = emptyList(), loading = true)
        viewModelScope.launch {
            val items = scheduleRepo.fetchToday(channel)
            // 받는 사이에 시트를 닫았거나 채널을 바꿨으면 늦게 온 표는 버린다
            if (_schedule.value?.channel?.id != channel.id) return@launch
            _schedule.value = ScheduleSheetState(channel, items, loading = false)
        }
    }

    fun closeSchedule() {
        _schedule.value = null
    }

    /** '나중에' 버튼: 이번 실행 동안 다이얼로그를 닫는다. */
    fun dismissUpdate() {
        if (_updateState.value is UpdateState.Available) {
            _updateState.value = UpdateState.Idle
        }
    }

    private fun showToast(message: String, anchor: ToastAnchor = ToastAnchor.BOTTOM) {
        viewModelScope.launch { _toast.emit(ToastMessage(message, anchor)) }
    }

    override fun onCleared() {
        _controller.value?.release()
        super.onCleared()
    }

    companion object {
        private const val MAX_RETRY = 3
        private const val RETRY_DELAY_MS = 2_000L
        private const val CONTROLLER_WAIT_MS = 10_000L
        private const val NOW_PLAYING_RETRY_MS = 120_000L
        private const val NOW_PLAYING_MIN_WAIT_MS = 30_000L
        private const val NOW_PLAYING_MAX_WAIT_MS = 900_000L
        private const val MANUAL_REFRESH_INTERVAL_MS = 10_000L
    }
}

/**
 * 편성표 시트 상태. 시트가 열려 있는 동안만 존재한다.
 *
 * 어느 채널 표인지 들고 있어야 늦게 도착한 응답을 버릴 수 있고,
 * 시트를 먼저 띄우고 표를 채우므로 [loading]과 빈 목록을 구분해야 한다.
 */
data class ScheduleSheetState(
    val channel: Channel,
    val items: List<ScheduleItem>,
    val loading: Boolean,
)

/** 토스트가 뜰 자리. 취침 타이머 안내는 눌린 칩 바로 아래, 나머지는 화면 하단. */
enum class ToastAnchor { BOTTOM, SLEEP_CHIP }

data class ToastMessage(val text: String, val anchor: ToastAnchor = ToastAnchor.BOTTOM)

/** 인앱 업데이트 상태. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
}
