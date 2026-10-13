package dev.kidyoh.liquid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.kidyoh.liquid.R
import dev.kidyoh.liquid.data.ThemeMode

@OptIn(ExperimentalTextApi::class)
private fun inter(w: Int) = Font(
    R.font.inter,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

/** Inter is the closest freely-licensed match to SF Pro. */
val Inter = FontFamily(inter(300), inter(400), inter(500), inter(600), inter(700), inter(800))

/** Apple system colours. */
object IOS {
    val Blue = Color(0xFF007AFF)
    val BlueDark = Color(0xFF0A84FF)
    val Red = Color(0xFFFF3B30)
    val Green = Color(0xFF34C759)
    val Orange = Color(0xFFFF9500)
    val Yellow = Color(0xFFFFCC00)
    val Purple = Color(0xFFAF52DE)
    val Pink = Color(0xFFFF2D55)
    val Teal = Color(0xFF30B0C7)
    val Indigo = Color(0xFF5856D6)
    val Cyan = Color(0xFF32ADE6)
    val Gray = Color(0xFF8E8E93)
    val Mint = Color(0xFF00C7BE)
    val Brown = Color(0xFFA2845E)
}

@Immutable
data class Palette(
    val dark: Boolean,
    val groupedBackground: Color,
    val card: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,
    val accent: Color,
)

fun palette(dark: Boolean) = if (dark) Palette(
    dark = true,
    groupedBackground = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    label = Color.White,
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0xFF38383A),
    fill = Color(0x5C787880),
    accent = IOS.BlueDark,
) else Palette(
    dark = false,
    groupedBackground = Color(0xFFF2F2F7),
    card = Color.White,
    label = Color.Black,
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0xFFC6C6C8),
    fill = Color(0x33787880),
    accent = IOS.Blue,
)

val LocalPalette = staticCompositionLocalOf { palette(false) }

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.Auto -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun LiquidTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = palette(dark)
    val base = TextStyle(fontFamily = Inter, color = p.label, letterSpacing = (-0.2).sp)
    val typography = Typography(
        displayLarge = base.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        headlineMedium = base.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
        titleLarge = base.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        titleMedium = base.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.copy(fontSize = 17.sp),
        bodyMedium = base.copy(fontSize = 15.sp),
        bodySmall = base.copy(fontSize = 13.sp),
        labelLarge = base.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        labelSmall = base.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
    val scheme = if (dark) darkColorScheme(primary = p.accent, background = p.groupedBackground, surface = p.card)
    else lightColorScheme(primary = p.accent, background = p.groupedBackground, surface = p.card)
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalPalette provides p, LocalTextStyle provides typography.bodyLarge) {
            content()
        }
    }
}
