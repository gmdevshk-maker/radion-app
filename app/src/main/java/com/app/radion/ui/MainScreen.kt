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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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
    val controller by viewModel.controller.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    var toastMessage by remember { mutableStateOf<String?>(null) }
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

        // 토스트
        toastMessage?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 88.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(RadionColors.Surface2)
                    .border(1.dp, RadionColors.Line, RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Text(text = message, style = RadionType.Body)
            }
        }

        UpdateDialog(
            state = updateState,
            currentVersion = viewModel.appVersion,
            onUpdate = viewModel::startUpdate,
            onDismiss = viewModel::dismissUpdate,
        )
    }
}

@Composable
private fun Header(
    version: String,
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
            Text(text = "라디온", style = RadionType.AppTitle)
            Text(
                text = "(v$version)",
                style = RadionType.Overline,
                modifier = Modifier.padding(start = 3.dp, bottom = 2.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeaderClock()

            val active = sleepMinutes != null
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
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
