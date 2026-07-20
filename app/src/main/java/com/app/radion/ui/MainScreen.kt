package com.app.radion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.app.radion.data.ChannelType
import com.app.radion.ui.theme.RadionColors
import com.app.radion.ui.theme.RadionType
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.util.Locale

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val channels by viewModel.channels.collectAsState()
    val currentChannel by viewModel.currentChannel.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val sleepMinutes by viewModel.sleepMinutes.collectAsState()
    val sleepEndsAt by viewModel.sleepEndsAt.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()
    val videoUnavailable by viewModel.videoUnavailable.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val nowPlayingLoading by viewModel.nowPlayingLoading.collectAsState()
    val nowPlayingRefreshEnabled by viewModel.nowPlayingRefreshEnabled.collectAsState()
    val controller by viewModel.controller.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    var toastMessage by remember { mutableStateOf<ToastMessage?>(null) }
    LaunchedEffect(Unit) {
        viewModel.toast.collect { message ->
            toastMessage = message
        }
    }
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(1800)
            toastMessage = null
        }
    }

    BackHandler(enabled = isFullscreen) {
        viewModel.setFullscreen(false)
    }

    val showVideo = currentChannel?.type == ChannelType.VIDEO

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RadionColors.Bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isFullscreen) Modifier else Modifier.statusBarsPadding()),
        ) {
            if (!isFullscreen) {
                Header(
                    version = viewModel.appVersion,
                    sleepToast = toastMessage?.takeIf { it.anchor == ToastAnchor.SLEEP_CHIP }?.text,
                    sleepMinutes = sleepMinutes,
                    sleepEndsAt = sleepEndsAt,
                    onSleepClick = viewModel::cycleSleepTimer,
                )
            }

            // 미디어 스테이지: 오디오 → 튜너, 영상 → 플레이어
            if (showVideo) {
                VideoStage(
                    player = controller,
                    channelName = currentChannel?.name,
                    freq = currentChannel?.freq,
                    isPlaying = isPlaying,
                    videoUnavailable = videoUnavailable,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = { viewModel.setFullscreen(!isFullscreen) },
                    modifier = if (isFullscreen) {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .aspectRatio(16f / 9f)
                    },
                )
            } else {
                TunerStage(
                    currentChannel = currentChannel,
                    channels = channels,
                    onSelectChannel = { viewModel.selectChannel(it) },
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                )
            }

            if (!isFullscreen) {
                // 방송 정보는 튜너·영상 어느 쪽이든 스테이지 바로 아래에 같은 모양으로 붙는다
                NowPlayingLine(
                    text = nowPlaying,
                    channelId = currentChannel?.id,
                    hasProvider = currentChannel?.infoProvider != null,
                    loading = nowPlayingLoading,
                    refreshEnabled = nowPlayingRefreshEnabled,
                    onRefresh = viewModel::refreshNowPlaying,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                // 스테이지와 리스트의 경계. 리스트를 스크롤하면 채널 행이 정보 줄 바로 밑까지
                // 올라와 위아래가 한 덩어리로 읽히므로 선을 그어 영역을 갈라 준다
                HorizontalDivider(
                    thickness = 1.dp,
                    color = RadionColors.Line,
                    modifier = Modifier.padding(top = 8.dp),
                )

                ChannelList(
                    channels = channels,
                    currentChannelId = currentChannel?.id,
                    isPlaying = isPlaying,
                    favorites = favorites,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 10.dp,
                        bottom = 120.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    onSelect = { viewModel.selectChannel(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }

        if (!isFullscreen) {
            MiniPlayer(
                channel = currentChannel,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                onTogglePlay = viewModel::togglePlay,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // 토스트. 취침 타이머 안내는 Header가 칩 아래에 직접 띄운다
        toastMessage?.takeIf { it.anchor == ToastAnchor.BOTTOM }?.let { message ->
            ToastBubble(
                message = message.text,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 88.dp),
            )
        }

        UpdateDialog(
            state = updateState,
            currentVersion = viewModel.appVersion,
            onUpdate = viewModel::startUpdate,
            onDismiss = viewModel::dismissUpdate,
        )
    }
}

/** 화면 아무 데나 띄우는 알약 모양 알림. 위치는 호출부가 modifier로 정한다. */
@Composable
private fun ToastBubble(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(RadionColors.Surface2)
            .border(1.dp, RadionColors.Line, RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text = message, style = RadionType.Body)
    }
}

@Composable
private fun Header(
    version: String,
    sleepToast: String?,
    sleepMinutes: Int?,
    sleepEndsAt: LocalTime?,
    onSleepClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            RadionLogoIcon(
                modifier = Modifier
                    .padding(end = 7.dp, bottom = 1.dp)
                    .size(22.dp),
            )
            Text(text = "RadiOn", style = RadionType.AppTitle)

            // 버전은 평소엔 감춰 두고 ⓘ를 누를 때만 바로 아래 말풍선으로 알린다.
            // 헤더 Row 안에서 그리면 레이아웃을 밀어내므로 Popup으로 띄운다
            var showVersion by remember { mutableStateOf(false) }
            var iconHeight by remember { mutableIntStateOf(0) }
            LaunchedEffect(showVersion) {
                if (showVersion) {
                    delay(1800)
                    showVersion = false
                }
            }
            Box {
                Text(
                    text = "ⓘ",
                    style = RadionType.Overline.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .padding(start = 1.dp, bottom = 1.dp)
                        .onSizeChanged { iconHeight = it.height }
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { showVersion = !showVersion }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
                if (showVersion) {
                    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
                    Popup(
                        alignment = Alignment.TopCenter,
                        offset = IntOffset(0, iconHeight + gap),
                        onDismissRequest = { showVersion = false },
                    ) {
                        ToastBubble(message = "버전 v$version")
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeaderClock()

            val active = sleepMinutes != null
            var chipHeight by remember { mutableIntStateOf(0) }
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .onSizeChanged { chipHeight = it.height }
                        .clip(RoundedCornerShape(999.dp))
                        .background(RadionColors.Surface)
                        .border(
                            1.dp,
                            if (active) RadionColors.AmberDim else RadionColors.Line,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable(onClick = onSleepClick)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    ClockIcon(
                        color = if (active) RadionColors.Amber else RadionColors.Muted,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = sleepEndsAt?.let { "${formatEndTime(it)}에 종료" } ?: "취침 타이머",
                        style = RadionType.Caption.copy(
                            color = if (active) RadionColors.Amber else RadionColors.Muted,
                        ),
                    )
                }

                // 말풍선이 칩보다 넓어 가운데 정렬하면 화면 밖으로 나간다 → 오른쪽 끝을 맞춘다
                if (sleepToast != null) {
                    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = IntOffset(0, chipHeight + gap),
                    ) {
                        ToastBubble(message = sleepToast)
                    }
                }
            }
        }
    }
}

/** 취침 종료 시각을 "PM 01:40" 형태로 포맷. */
private fun formatEndTime(t: LocalTime): String {
    val ampm = if (t.hour < 12) "AM" else "PM"
    val hour12 = if (t.hour % 12 == 0) 12 else t.hour % 12
    return "$ampm ${String.format(Locale.US, "%02d:%02d", hour12, t.minute)}"
}

/** 현재 시각 디지털 시계. 초는 표시하지 않으므로 분이 바뀔 때만 갱신한다. */
@Composable
private fun HeaderClock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            // 다음 분으로 넘어가는 시점에 맞춰 갱신
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = LocalTime.now()
        }
    }

    val hour12 = if (now.hour % 12 == 0) 12 else now.hour % 12
    val numberStyle = RadionType.ClockTime

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = if (now.hour < 12) "AM" else "PM",
            style = RadionType.Overline,
            modifier = Modifier.padding(end = 5.dp),
        )
        Text(
            text = String.format(Locale.US, "%02d:%02d", hour12, now.minute),
            style = numberStyle,
        )
    }
}
