package com.app.radion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.app.radion.data.Channel
import com.app.radion.data.formatFreq
import com.app.radion.ui.theme.RadionColors
import com.app.radion.ui.theme.RadionType
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 눈금 격자(1/10 MHz 단위). 한국 FM 대역(88~108)에 여유를 둔 홀수 소수점 범위. */
private const val FREQ_MIN_TENTHS = 871
private const val FREQ_MAX_TENTHS = 1089
private const val TICK_STEP = 0.2f
private val TICK_W = 14.dp

/** 숫자 라벨을 붙일 정수 MHz 범위 */
private const val LABEL_MIN = 88
private const val LABEL_MAX = 108
private const val LABEL_STEP = 2

/**
 * 아날로그 튜너 다이얼 + 주파수 표시. 목업의 .tuner / .freq-readout 재현.
 *
 * 좌우로 드래그하면 실제 라디오처럼 눈금이 손가락을 따라 움직이고,
 * 손을 떼면 가장 가까운 방송국으로 붙으면서 그 채널이 선택된다.
 */
@Composable
fun TunerStage(
    currentChannel: Channel?,
    channels: List<Channel>,
    onSelectChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetFreq = currentChannel?.freq?.toFloat() ?: 93.1f
    // 드래그와 애니메이션이 같은 값을 다투므로 Animatable로 직접 제어한다
    val freqAnim = remember { Animatable(targetFreq) }
    val scope = rememberCoroutineScope()
    val tickPx = with(LocalDensity.current) { TICK_W.toPx() }

    // 채널이 바뀌면(리스트 탭 등) 해당 주파수로 애니메이션. 드래그 중엔 targetFreq가 안 변해 간섭 없음
    LaunchedEffect(targetFreq) {
        freqAnim.animateTo(
            targetValue = targetFreq,
            animationSpec = tween(durationMillis = 550, easing = CubicBezierEasing(0.22f, 0.9f, 0.3f, 1f)),
        )
    }
    val animFreq = freqAnim.value

    val stationTicks = remember(channels) { channels.map { (it.freq * 10).roundToInt() }.toSet() }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = RadionType.MonoXs

    val dragModifier = Modifier.pointerInput(channels, currentChannel?.id) {
        detectHorizontalDragGestures(
            onDragEnd = {
                val nearest = channels.minByOrNull { abs(it.freq.toFloat() - freqAnim.value) }
                    ?: return@detectHorizontalDragGestures
                if (nearest.id != currentChannel?.id) {
                    // 채널이 바뀌면 위 LaunchedEffect가 눈금을 그 주파수로 붙여준다
                    onSelectChannel(nearest)
                } else {
                    scope.launch { freqAnim.animateTo(nearest.freq.toFloat(), tween(280)) }
                }
            },
        ) { _, dragAmount ->
            // 눈금 띠를 오른쪽으로 밀면 바늘은 낮은 주파수를 가리킨다
            scope.launch {
                val next = (freqAnim.value - dragAmount / tickPx * TICK_STEP)
                    .coerceIn(FREQ_MIN_TENTHS / 10f, FREQ_MAX_TENTHS / 10f)
                freqAnim.snapTo(next)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().then(dragModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
        ) {
            val tickW = TICK_W.toPx()
            val centerX = size.width / 2f

            val top = 34.dp.toPx()
            fun xOf(freq: Float) = centerX + (freq - animFreq) / TICK_STEP * tickW
            fun visible(x: Float) = x > -tickW && x < size.width + tickW

            // 눈금: 0.2MHz 간격. 한국 FM 주파수는 모두 홀수 소수점(93.1, 89.1 …)이므로
            // 격자도 홀수 소수점에서 시작해야 방송국이 눈금 위에 정확히 놓인다.
            var tenths = FREQ_MIN_TENTHS
            while (tenths <= FREQ_MAX_TENTHS) {
                val x = xOf(tenths / 10f)
                if (visible(x)) {
                    if (tenths in stationTicks) {
                        drawLine(
                            RadionColors.AmberDim,
                            Offset(x, top),
                            Offset(x, top + 22.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                        )
                        // 앰버 점 + 글로우
                        drawCircle(RadionColors.Amber.copy(alpha = 0.35f), 5.dp.toPx(), Offset(x, 26.5.dp.toPx()))
                        drawCircle(RadionColors.Amber, 2.5.dp.toPx(), Offset(x, 26.5.dp.toPx()))
                    } else {
                        drawLine(
                            RadionColors.TickMinor,
                            Offset(x, top),
                            Offset(x, top + 12.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
                tenths += 2
            }

            // 주눈금 + 라벨: 정수 MHz(88, 90 …)는 홀수 소수점 격자와 겹치지 않으므로 따로 그린다
            var mhz = LABEL_MIN
            while (mhz <= LABEL_MAX) {
                val x = xOf(mhz.toFloat())
                if (visible(x)) {
                    drawLine(
                        RadionColors.TickMajor,
                        Offset(x, top),
                        Offset(x, top + 22.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                    val measured = textMeasurer.measure(mhz.toString(), labelStyle)
                    drawText(
                        measured,
                        topLeft = Offset(x - measured.size.width / 2f, 62.dp.toPx()),
                    )
                }
                mhz += LABEL_STEP
            }

            // 좌우 페이드 (마스크 대신 배경색 그라데이션 오버레이)
            val fadeW = size.width * 0.18f
            drawRect(
                Brush.horizontalGradient(
                    listOf(RadionColors.Bg, RadionColors.Bg.copy(alpha = 0f)),
                    startX = 0f,
                    endX = fadeW,
                ),
                size = Size(fadeW, size.height),
            )
            drawRect(
                Brush.horizontalGradient(
                    listOf(RadionColors.Bg.copy(alpha = 0f), RadionColors.Bg),
                    startX = size.width - fadeW,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - fadeW, 0f),
                size = Size(fadeW, size.height),
            )

            // 바늘 (중앙 고정)
            val needleTop = 14.dp.toPx()
            val needleBottom = needleTop + 52.dp.toPx()
            drawLine(
                RadionColors.Needle.copy(alpha = 0.4f),
                Offset(centerX, needleTop),
                Offset(centerX, needleBottom),
                strokeWidth = 6.dp.toPx(),
            )
            drawLine(
                RadionColors.Needle,
                Offset(centerX, needleTop),
                Offset(centerX, needleBottom),
                strokeWidth = 2.dp.toPx(),
            )
            val tri = Path().apply {
                moveTo(centerX - 5.dp.toPx(), needleTop - 5.dp.toPx())
                lineTo(centerX + 5.dp.toPx(), needleTop - 5.dp.toPx())
                lineTo(centerX, needleTop + 5.dp.toPx())
                close()
            }
            drawPath(tri, RadionColors.Needle)
        }

        // 주파수 표시
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatFreq(animFreq.toDouble()),
                style = RadionType.FreqDisplay.copy(
                    shadow = Shadow(
                        color = RadionColors.Amber.copy(alpha = 0.35f),
                        blurRadius = 18f,
                    ),
                ),
            )
            Text(
                text = "MHz",
                style = RadionType.MonoMd,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        // 드래그 중에도 바늘이 가리키는 방송국을 바로 보여준다
        val pointedName = if (currentChannel == null) {
            "채널을 선택하세요"
        } else {
            channels.minByOrNull { abs(it.freq.toFloat() - animFreq) }?.name ?: currentChannel.name
        }
        Text(
            text = pointedName,
            style = RadionType.BodyMuted,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
