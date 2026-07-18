package com.app.radion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.app.radion.R

/** 목업(radio-app-mockup.html)에서 추출한 디자인 토큰. */
object RadionColors {
    val Bg = Color(0xFF131417)
    val Surface = Color(0xFF1C1E23)
    val Surface2 = Color(0xFF24262D)
    val Line = Color(0xFF2E3138)
    val Amber = Color(0xFFFFB454)
    val AmberDim = Color(0xFF8A6430)
    val Needle = Color(0xFFE8503A)
    val Text = Color(0xFFECE9E2)
    val Muted = Color(0xFF8D9099)
    val TickMinor = Color(0xFF3A3D45)
    val TickMajor = Color(0xFF565A64)
    val StarOff = Color(0xFF3F434C)
    val PlayIconDark = Color(0xFF1A130A)
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
)

val PlexMono = FontFamily(
    Font(R.font.ibmplexmono_regular, FontWeight.Normal),
    Font(R.font.ibmplexmono_medium, FontWeight.Medium),
    Font(R.font.ibmplexmono_semibold, FontWeight.SemiBold),
)

private val DarkScheme = darkColorScheme(
    primary = RadionColors.Amber,
    background = RadionColors.Bg,
    surface = RadionColors.Surface,
    onPrimary = RadionColors.PlayIconDark,
    onBackground = RadionColors.Text,
    onSurface = RadionColors.Text,
    outline = RadionColors.Line,
)

@Composable
fun RadionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
