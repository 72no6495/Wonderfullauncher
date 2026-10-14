package com.wonderful.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.wonderful.launcher.data.FontStyle
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.data.PalettePreset

/** ヴェイパーウェイヴ配色一式 */
@Immutable
data class RetroPalette(
    val pink: Color,
    val cyan: Color,
    val purple: Color,
    val yellow: Color,
    val mint: Color,
    val skyTop: Color,
    val skyMid: Color,
    val skyBottom: Color,
    val sunTop: Color,
    val sunBottom: Color,
    val grid: Color,
    val ground: Color,
    val mountain: Color,
    val panel: Color,
    val text: Color,
    val titleStart: Color,
    val titleEnd: Color,
) {
    /** フォルダなどの色選択に使う色リスト */
    val swatches: List<Color> get() = listOf(pink, cyan, purple, yellow, mint, sunBottom)
}

fun paletteOf(preset: PalettePreset): RetroPalette = when (preset) {
    PalettePreset.VAPORWAVE -> RetroPalette(
        pink = Color(0xFFFF71CE), cyan = Color(0xFF01CDFE), purple = Color(0xFFB967FF),
        yellow = Color(0xFFFFFB96), mint = Color(0xFF05FFA1),
        skyTop = Color(0xFF12002B), skyMid = Color(0xFF4B0A6B), skyBottom = Color(0xFFFF6EC7),
        sunTop = Color(0xFFFFF000), sunBottom = Color(0xFFFF2E97),
        grid = Color(0xFFFF71CE), ground = Color(0xFF14002E), mountain = Color(0xFF2A0B4A),
        panel = Color(0xFF1B0B33), text = Color(0xFFF5E9FF),
        titleStart = Color(0xFFFF71CE), titleEnd = Color(0xFF01CDFE),
    )
    PalettePreset.SYNTHWAVE -> RetroPalette(
        pink = Color(0xFFF72585), cyan = Color(0xFF4CC9F0), purple = Color(0xFF7209B7),
        yellow = Color(0xFFFEE440), mint = Color(0xFF3AF5C0),
        skyTop = Color(0xFF0D0221), skyMid = Color(0xFF241734), skyBottom = Color(0xFF9D1F8C),
        sunTop = Color(0xFFFFD319), sunBottom = Color(0xFFFF2975),
        grid = Color(0xFFF222FF), ground = Color(0xFF0D0221), mountain = Color(0xFF1B0B3A),
        panel = Color(0xFF120624), text = Color(0xFFF2E6FF),
        titleStart = Color(0xFF7209B7), titleEnd = Color(0xFFF72585),
    )
    PalettePreset.OUTRUN -> RetroPalette(
        pink = Color(0xFFFF00A0), cyan = Color(0xFF00F0FF), purple = Color(0xFF8A00FF),
        yellow = Color(0xFFFF8A00), mint = Color(0xFF00FF9C),
        skyTop = Color(0xFF000000), skyMid = Color(0xFF1A0A2E), skyBottom = Color(0xFFFF3864),
        sunTop = Color(0xFFFFE600), sunBottom = Color(0xFFFF0066),
        grid = Color(0xFF00F0FF), ground = Color(0xFF050010), mountain = Color(0xFF12002A),
        panel = Color(0xFF0A0418), text = Color(0xFFEFF9FF),
        titleStart = Color(0xFFFF8A00), titleEnd = Color(0xFFFF00A0),
    )
    PalettePreset.MIAMI -> RetroPalette(
        pink = Color(0xFFFF6AD5), cyan = Color(0xFF26F7FD), purple = Color(0xFFC774E8),
        yellow = Color(0xFFFFCF66), mint = Color(0xFF8FFFE0),
        skyTop = Color(0xFF2B1055), skyMid = Color(0xFF7D2AE8), skyBottom = Color(0xFFFF9ECF),
        sunTop = Color(0xFFFFCF66), sunBottom = Color(0xFFFF6AD5),
        grid = Color(0xFF26F7FD), ground = Color(0xFF1F0B40), mountain = Color(0xFF3D1A78),
        panel = Color(0xFF241046), text = Color(0xFFFFF4FB),
        titleStart = Color(0xFF26F7FD), titleEnd = Color(0xFFFF6AD5),
    )
    PalettePreset.SEAPUNK -> RetroPalette(
        pink = Color(0xFFFF99FF), cyan = Color(0xFF00FFD1), purple = Color(0xFF8A2BE2),
        yellow = Color(0xFFE0FF4F), mint = Color(0xFF7CFFCB),
        skyTop = Color(0xFF001F3F), skyMid = Color(0xFF006D77), skyBottom = Color(0xFF83C5BE),
        sunTop = Color(0xFFE0FF4F), sunBottom = Color(0xFFFF99FF),
        grid = Color(0xFF00FFD1), ground = Color(0xFF00141F), mountain = Color(0xFF003845),
        panel = Color(0xFF00202B), text = Color(0xFFE8FFFB),
        titleStart = Color(0xFF00FFD1), titleEnd = Color(0xFF8A2BE2),
    )
    PalettePreset.MACPLUS -> RetroPalette(
        pink = Color(0xFFFF8BD1), cyan = Color(0xFF9FE8FF), purple = Color(0xFFB8A9FF),
        yellow = Color(0xFFFFF5B8), mint = Color(0xFFBFFFE4),
        skyTop = Color(0xFF2F2F3A), skyMid = Color(0xFF6D6A7C), skyBottom = Color(0xFFD9C8E0),
        sunTop = Color(0xFFFFFFFF), sunBottom = Color(0xFFFF8BD1),
        grid = Color(0xFFE6E6F0), ground = Color(0xFF1E1E26), mountain = Color(0xFF3B3B48),
        panel = Color(0xFF26262F), text = Color(0xFFF6F6F6),
        titleStart = Color(0xFF8E8E9E), titleEnd = Color(0xFFFF8BD1),
    )
}

val LocalPalette = staticCompositionLocalOf { paletteOf(PalettePreset.VAPORWAVE) }
val LocalSettings = staticCompositionLocalOf { LauncherSettings() }
val LocalRetroFont = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

fun fontOf(style: FontStyle): FontFamily = when (style) {
    FontStyle.MONO -> FontFamily.Monospace
    FontStyle.SANS -> FontFamily.SansSerif
    FontStyle.SERIF -> FontFamily.Serif
}

/** アニメーション速度設定を反映した時間(ms) */
fun LauncherSettings.ms(base: Int): Int = (base / animSpeed.coerceAtLeast(0.1f)).toInt().coerceAtLeast(1)

@Composable
fun RetroTheme(settings: LauncherSettings, content: @Composable () -> Unit) {
    val p = paletteOf(settings.palette)
    val scheme = darkColorScheme(
        primary = p.pink,
        onPrimary = Color.Black,
        secondary = p.cyan,
        onSecondary = Color.Black,
        tertiary = p.purple,
        background = p.panel,
        surface = p.panel,
        surfaceVariant = p.panel.copy(alpha = 0.8f),
        onSurface = p.text,
        onBackground = p.text,
        outline = p.cyan,
    )
    CompositionLocalProvider(
        LocalPalette provides p,
        LocalSettings provides settings,
        LocalRetroFont provides fontOf(settings.fontStyle),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
