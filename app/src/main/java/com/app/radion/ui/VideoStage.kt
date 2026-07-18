package com.app.radion.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.app.radion.data.formatFreq
import com.app.radion.ui.theme.RadionColors
import com.app.radion.ui.theme.RadionType

/** 보이는 라디오 영상 스테이지. 전체화면 버튼 포함. */
@OptIn(UnstableApi::class)
@Composable
fun VideoStage(
    player: Player?,
    channelName: String?,
    freq: Double?,
    isPlaying: Boolean,
    videoUnavailable: Boolean,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black)
            .then(if (isFullscreen) Modifier else Modifier.border(1.dp, RadionColors.Line, shape)),
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view ->
                view.player = player
                view.keepScreenOn = isPlaying
            },
            onRelease = { view -> view.player = null },
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(),
        )

        // 재생 전/일시정지/영상 미송출(오디오 폴백) 상태: 스튜디오 플레이스홀더 (목업 .video-fake)
        if (!isPlaying || videoUnavailable) {
            StudioPlaceholder(
                channelName = channelName,
                freq = freq,
                audioOnly = videoUnavailable && isPlaying,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 전체화면 버튼 (우하단)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(if (isFullscreen) 14.dp else 10.dp)
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onToggleFullscreen),
            contentAlignment = Alignment.Center,
        ) {
            FullscreenIcon(
                exit = isFullscreen,
                color = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 재생 전/일시정지 상태의 스튜디오 플레이스홀더. 목업 .video-fake 재현. */
@Composable
private fun StudioPlaceholder(
    channelName: String?,
    freq: Double?,
    audioOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF17181D), Color(0xFF101116), Color(0xFF0B0C10)),
                ),
            )
            .drawBehind {
                // 하단 중앙에서 올라오는 앰버 스튜디오 조명
                drawRect(
                    Brush.radialGradient(
                        listOf(RadionColors.Amber.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 1.1f),
                        radius = size.width * 0.65f,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CameraIcon(
                color = RadionColors.Amber,
                modifier = Modifier
                    .size(34.dp)
                    .alpha(0.9f),
            )
            Text(
                text = channelName?.let { "$it 보이는 라디오" } ?: "보이는 라디오",
                style = RadionType.StageTitle,
            )
            val subLabel = if (audioOnly) "AUDIO ONLY" else "STUDIO LIVE"
            Text(
                text = freq?.let { "${formatFreq(it)} MHz · $subLabel" } ?: subLabel,
                style = RadionType.MonoXs,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.height(22.dp),
            ) {
                listOf(8, 16, 11, 19, 7).forEach { barHeight ->
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(barHeight.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(RadionColors.Amber.copy(alpha = 0.55f)),
                    )
                }
            }
        }
    }
}
