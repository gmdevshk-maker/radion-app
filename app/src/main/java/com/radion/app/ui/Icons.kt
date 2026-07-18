package com.radion.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

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
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * s),
            style = stroke,
        )
        val lens = Path().apply {
            polyline(s, 15f to 10.5f, 21f to 7f, 21f to 17f, 15f to 13.5f)
            close()
        }
        drawPath(lens, color, style = stroke)
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
