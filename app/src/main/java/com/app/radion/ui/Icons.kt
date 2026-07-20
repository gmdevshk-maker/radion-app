package com.app.radion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.app.radion.ui.theme.RadionColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 목업 SVG 아이콘들을 Canvas로 재현 (24x24 뷰포트 기준). */

private fun DrawScope.strokePath(path: Path, color: Color, strokeWidthRatio: Float = 2f / 24f) {
    drawPath(
        path,
        color = color,
        style = Stroke(
            width = size.minDimension * strokeWidthRatio,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private fun Path.polyline(scale: Float, vararg points: Pair<Float, Float>) {
    points.forEachIndexed { i, (x, y) ->
        if (i == 0) moveTo(x * scale, y * scale) else lineTo(x * scale, y * scale)
    }
}

@Composable
fun PlayIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val path = Path().apply {
            moveTo(8f * s, 5f * s); lineTo(19f * s, 12f * s); lineTo(8f * s, 19f * s); close()
        }
        drawPath(path, color)
    }
}

@Composable
fun PauseIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        drawRect(color, topLeft = Offset(6f * s, 5f * s), size = Size(4f * s, 14f * s))
        drawRect(color, topLeft = Offset(14f * s, 5f * s), size = Size(4f * s, 14f * s))
    }
}

@Composable
fun StarIcon(filled: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val path = Path().apply {
            polyline(
                s,
                12f to 17.3f, 6.6f to 20.5f, 8f to 14.4f, 3.3f to 10.3f, 9.5f to 9.8f,
                12f to 4f, 14.5f to 9.8f, 20.7f to 10.3f, 16f to 14.4f, 17.4f to 20.5f,
            )
            close()
        }
        if (filled) drawPath(path, color) else strokePath(path, color)
    }
}

@Composable
fun ClockIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        drawCircle(
            color,
            radius = 9f * s,
            center = Offset(12f * s, 12f * s),
            style = Stroke(width = size.minDimension * 2f / 24f, cap = StrokeCap.Round),
        )
        val hands = Path().apply { polyline(s, 12f to 7f, 12f to 12f, 15f to 14f) }
        strokePath(hands, color)
    }
}

/** 편성표 버튼 — 시각 눈금(점)과 프로그램 줄이 짝지어 내려가는 모양. */
@Composable
fun ScheduleIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val stroke = Stroke(width = size.minDimension * 2f / 24f, cap = StrokeCap.Round)
        listOf(6f, 12f, 18f).forEach { y ->
            drawCircle(color, radius = 1.6f * s, center = Offset(4.5f * s, y * s))
            drawLine(
                color,
                start = Offset(10f * s, y * s),
                end = Offset(20f * s, y * s),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun CameraIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val stroke = Stroke(
            width = size.minDimension * 1.6f / 24f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        drawRoundRect(
            color,
            topLeft = Offset(2f * s, 6f * s),
            size = Size(13f * s, 12f * s),
            cornerRadius = CornerRadius(2.5f * s),
            style = stroke,
        )
        val lens = Path().apply {
            polyline(s, 15f to 10.5f, 21f to 7f, 21f to 17f, 15f to 13.5f)
            close()
        }
        drawPath(lens, color, style = stroke)
    }
}

/**
 * 앱 아이콘의 라디오 그래픽 (design/radion-icon.svg의 전경과 동일).
 * SVG(512 뷰포트) 좌표를 그대로 쓰되, 그래픽 bbox(x[122.5,389.5] y[60,398])를 캔버스에 맞춰 스케일한다.
 */
@Composable
fun RadionLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 338f          // bbox 높이(338) 기준
        val offX = size.width / 2f - 256f * s     // bbox 가로 중심 256
        val offY = size.height / 2f - 229f * s    // bbox 세로 중심 229
        fun p(x: Float, y: Float) = Offset(offX + x * s, offY + y * s)

        // 안테나 + 팁
        drawLine(
            RadionColors.Amber,
            start = p(256f, 154f),
            end = p(330f, 74f),
            strokeWidth = 11f * s,
            cap = StrokeCap.Round,
        )
        drawCircle(RadionColors.Needle, radius = 14f * s, center = p(330f, 74f))

        // 본체
        drawRoundRect(
            RadionColors.Amber,
            topLeft = p(128f, 154f),
            size = Size(256f * s, 190f * s),
            cornerRadius = CornerRadius(36f * s),
            style = Stroke(width = 11f * s),
        )

        // 다이얼
        drawCircle(
            RadionColors.Amber,
            radius = 33f * s,
            center = p(200f, 248f),
            style = Stroke(width = 11f * s),
        )
        drawCircle(RadionColors.Amber, radius = 11f * s, center = p(200f, 248f))

        // 스피커 그릴
        val grill = RadionColors.Amber.copy(alpha = 0.65f)
        drawRoundRect(
            grill,
            topLeft = p(272f, 222f),
            size = Size(66f * s, 15f * s),
            cornerRadius = CornerRadius(7f * s),
        )
        drawRoundRect(
            grill,
            topLeft = p(272f, 256f),
            size = Size(66f * s, 15f * s),
            cornerRadius = CornerRadius(7f * s),
        )

        // 받침대
        drawRoundRect(
            RadionColors.TickMajor,
            topLeft = p(164f, 344f),
            size = Size(184f * s, 50f * s),
            cornerRadius = CornerRadius(14f * s),
            style = Stroke(width = 8f * s),
        )
    }
}

@Composable
fun FullscreenIcon(exit: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val path = Path().apply {
            if (exit) {
                polyline(s, 8f to 3f, 8f to 8f, 3f to 8f)
                polyline(s, 21f to 8f, 16f to 8f, 16f to 3f)
                polyline(s, 3f to 16f, 8f to 16f, 8f to 21f)
                polyline(s, 16f to 21f, 16f to 16f, 21f to 16f)
            } else {
                polyline(s, 8f to 3f, 3f to 3f, 3f to 8f)
                polyline(s, 16f to 3f, 21f to 3f, 21f to 8f)
                polyline(s, 8f to 21f, 3f to 21f, 3f to 16f)
                polyline(s, 16f to 21f, 21f to 21f, 21f to 16f)
            }
        }
        strokePath(path, color)
    }
}

/** 새로고침(원형 화살표). 오른쪽 위가 트인 호 + 그 끝에 진행 방향으로 놓인 화살촉. */
@Composable
fun RefreshIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val center = Offset(12f * s, 12f * s)
        val radius = 8f * s

        // 오른쪽 위 70도를 비워 둔다 — 그 자리에 화살촉이 들어간다
        val startDeg = 10f
        val sweepDeg = 290f
        drawArc(
            color = color,
            startAngle = startDeg,
            sweepAngle = sweepDeg,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = size.minDimension * 2f / 24f, cap = StrokeCap.Round),
        )

        // 호가 끝나는 점의 접선(시계 방향)을 구해 그 방향으로 삼각형을 세운다.
        // 좌표를 손으로 박아 넣으면 반지름이나 각도를 조금만 바꿔도 화살촉이 호에서 떨어진다.
        val endRad = ((startDeg + sweepDeg) * PI / 180f).toFloat()
        val tip = Offset(center.x + radius * cos(endRad), center.y + radius * sin(endRad))
        val dir = Offset(-sin(endRad), cos(endRad))
        val perp = Offset(-dir.y, dir.x)
        val head = 3.4f * s
        val path = Path().apply {
            moveTo(tip.x + dir.x * head, tip.y + dir.y * head)
            lineTo(tip.x + perp.x * head * 0.8f, tip.y + perp.y * head * 0.8f)
            lineTo(tip.x - perp.x * head * 0.8f, tip.y - perp.y * head * 0.8f)
            close()
        }
        drawPath(path, color)
    }
}
