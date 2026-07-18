package com.radion.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radion.app.data.Channel
import com.radion.app.data.ChannelType
import com.radion.app.ui.theme.PlexMono
import com.radion.app.ui.theme.Pretendard
import com.radion.app.ui.theme.RadionColors

/** 하단 고정 미니 플레이어. 목업의 .mini 재현. */
@Composable
fun MiniPlayer(
    channel: Channel?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(RadionColors.Surface2, RadionColors.Surface)))
            .border(1.dp, RadionColors.Line, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // 주파수 배지
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RadionColors.Bg)
                .background(
                    Brush.radialGradient(
                        listOf(RadionColors.Amber.copy(alpha = 0.28f), RadionColors.Amber.copy(alpha = 0f)),
                        center = Offset(15f, 13f),
                        radius = 40f,
                    ),
                )
                .border(1.dp, RadionColors.Line, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = buildString {
                    append(if (channel?.type == ChannelType.VIDEO) "📺" else "FM")
                    append("\n")
                    append(channel?.let { String.format("%.1f", it.freq) } ?: "--.-")
                },
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 10.5.sp,
                    color = RadionColors.Amber,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp,
                ),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel?.let { it.name + if (it.type == ChannelType.VIDEO) " 보이는 라디오" else "" }
                    ?: "채널을 선택하세요",
                style = TextStyle(
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = RadionColors.Text,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                LiveDot(active = isPlaying)
                val status = when {
                    isBuffering -> "연결 중"
                    isPlaying -> "재생 중"
                    else -> "대기 중"
                }
                Text(
                    text = channel?.let { String.format("%.1f MHz · %s", it.freq, status) } ?: status,
                    style = TextStyle(fontFamily = PlexMono, fontSize = 11.5.sp, color = RadionColors.Muted),
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
        }

        // 재생/일시정지 버튼
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(RadionColors.Amber)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            if (isPlaying) {
                PauseIcon(color = RadionColors.PlayIconDark, modifier = Modifier.size(19.dp))
            } else {
                PlayIcon(color = RadionColors.PlayIconDark, modifier = Modifier.size(19.dp))
            }
        }
    }
}

/** LIVE 점멸 점. */
@Composable
fun LiveDot(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "liveDotAlpha",
    )
    Box(
        modifier = modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(
                if (active) RadionColors.Needle.copy(alpha = alpha) else RadionColors.Muted,
            ),
    )
}
