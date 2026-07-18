package com.radion.app.ui

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.radion.app.data.ChannelType
import com.radion.app.ui.theme.PlexMono
import com.radion.app.ui.theme.Pretendard
import com.radion.app.ui.theme.RadionColors
import kotlinx.coroutines.delay
import java.time.LocalTime

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val channels by viewModel.channels.collectAsState()
    val currentChannel by viewModel.currentChannel.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val sleepMinutes by viewModel.sleepMinutes.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()
    val videoUnavailable by viewModel.videoUnavailable.collectAsState()
    val controller by viewModel.controller.collectAsState()

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
                    sleepMinutes = sleepMinutes,
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
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
                Text(
                    text = message,
                    style = TextStyle(fontFamily = Pretendard, fontSize = 12.5.sp, color = RadionColors.Text),
                )
            }
        }
    }
}

@Composable
private fun Header(
    sleepMinutes: Int?,
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
            Text(
                text = "라디온",
                style = TextStyle(
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.02).em,
                    color = RadionColors.Text,
                ),
            )
            Text(
                text = "RADION",
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.08.em,
                    color = RadionColors.Amber,
                ),
                modifier = Modifier.padding(start = 6.dp, bottom = 1.dp),
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
                    text = sleepMinutes?.let { "${it}분 후 종료" } ?: "취침 타이머",
                    style = TextStyle(
                        fontFamily = Pretendard,
                        fontSize = 12.sp,
                        color = if (active) RadionColors.Amber else RadionColors.Muted,
                    ),
                )
            }
        }
    }
}

/** 현재 시각 디지털 시계. 콜론은 1초 주기로 점멸한다. */
@Composable
private fun HeaderClock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            // 초가 바뀌는 시점에 맞춰 갱신
            delay(1000 - System.currentTimeMillis() % 1000)
            now = LocalTime.now()
        }
    }

    val hour12 = if (now.hour % 12 == 0) 12 else now.hour % 12
    val numberStyle = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        color = RadionColors.Text,
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = if (now.hour < 12) "AM" else "PM",
            style = TextStyle(
                fontFamily = PlexMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
                letterSpacing = 0.06.em,
                color = RadionColors.Muted,
            ),
            modifier = Modifier.padding(end = 5.dp),
        )
        Text(text = String.format("%02d", hour12), style = numberStyle)
        Text(
            text = ":",
            style = numberStyle,
            modifier = Modifier.alpha(if (now.second % 2 == 0) 1f else 0f),
        )
        Text(text = String.format("%02d", now.minute), style = numberStyle)
    }
}
