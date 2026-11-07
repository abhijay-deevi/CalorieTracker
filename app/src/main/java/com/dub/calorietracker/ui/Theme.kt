package com.dub.calorietracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dub.calorietracker.data.Accent
import com.dub.calorietracker.data.ThemeMode
import com.dub.calorietracker.data.ThemeSettings

/** Bar colors that follow the chosen accent: calories (gauge, day bars) and macros. */
data class AppColors(val calories: Color, val macros: Color)

val LocalAppColors = staticCompositionLocalOf { AppColors(Teal, SkyBlue) }

private fun hsl(h: Float, s: Float, l: Float) =
    Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

/** Text color that reads on [bg]. */
private fun onColor(bg: Color, darkText: Color = Color(0xFF1A1C1E)) = if (bg.luminance() > 0.45f) darkText else Color.White

/** The swatch shown in Settings. */
fun swatchColor(a: Accent): Color = if (a == Accent.BLUE) AccentBlue else hsl(a.hue, a.sat, 0.5f)

/**
 * Color scheme for [accent] in light or dark mode. Neutrals (backgrounds, cards, text) stay the same; the accent
 * colors buttons, tabs, highlights and macro bars, and a neighboring hue colors the calorie bars.
 * Over-target red and high-day amber never change, so they keep their meaning.
 */
fun colorsFor(accent: Accent, dark: Boolean): Pair<ColorScheme, AppColors> {
    val h = accent.hue; val s = accent.sat; val c = accent.calorieHue
    val blue = accent == Accent.BLUE
    return if (!dark) {
        val primary = if (blue) AccentBlue else hsl(h, s, 0.42f)
        val calories = if (blue) Teal else hsl(c, s * 0.75f, 0.6f)
        lightColorScheme(
            primary = primary, onPrimary = onColor(primary),
            primaryContainer = if (blue) Color(0xFFE3F1FC) else hsl(h, s, 0.93f),
            onPrimaryContainer = if (blue) Color(0xFF0E4C7A) else hsl(h, s, 0.25f),
            secondary = calories, onSecondary = onColor(calories),
            secondaryContainer = if (blue) Color(0xFFE2F6F1) else hsl(c, s * 0.6f, 0.92f),
            onSecondaryContainer = if (blue) Color(0xFF175C4E) else hsl(c, s, 0.25f),
            tertiary = Amber, tertiaryContainer = Color(0xFFFFF4DE), onTertiaryContainer = Color(0xFF6B4A00),
            background = Color(0xFFF5F7FA), onBackground = Color(0xFF3A4351),
            surface = Color.White, onSurface = Color(0xFF3A4351),
            surfaceVariant = Color(0xFFECEFF3), onSurfaceVariant = Color(0xFF7D8695),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
            surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFF1F3F6),
            outline = Color(0xFFD5DAE1), outlineVariant = Color(0xFFEAEDF1),
            error = OverColor, errorContainer = Color(0xFFFDECEC), onErrorContainer = Color(0xFF8A1F1F),
        ) to AppColors(calories, if (blue) SkyBlue else hsl(h, s, 0.6f))
    } else {
        val primary = if (blue) Color(0xFF5DB2F0) else hsl(h, s, 0.68f)
        val calories = if (blue) Teal else hsl(c, s * 0.75f, 0.62f)
        darkColorScheme(
            primary = primary, onPrimary = if (blue) Color(0xFF00243F) else hsl(h, s, 0.14f),
            primaryContainer = if (blue) Color(0xFF173A55) else hsl(h, s * 0.45f, 0.22f),
            onPrimaryContainer = if (blue) Color(0xFFD3E9FA) else hsl(h, s, 0.9f),
            secondary = calories, onSecondary = hsl(c, s, 0.14f),
            secondaryContainer = if (blue) Color(0xFF1C3D36) else hsl(c, s * 0.4f, 0.2f),
            onSecondaryContainer = if (blue) Color(0xFFC9F1E7) else hsl(c, s, 0.88f),
            tertiary = Amber, tertiaryContainer = Color(0xFF3F3218), onTertiaryContainer = Color(0xFFFFE3AE),
            background = Color(0xFF111418), onBackground = Color(0xFFE4E7EC),
            surface = Color(0xFF1B1F25), onSurface = Color(0xFFE4E7EC),
            surfaceVariant = Color(0xFF2B3139), onSurfaceVariant = Color(0xFF9AA3B1),
            surfaceContainerLowest = Color(0xFF1B1F25), surfaceContainerLow = Color(0xFF1B1F25),
            surfaceContainer = Color(0xFF1B1F25), surfaceContainerHigh = Color(0xFF22272E), surfaceContainerHighest = Color(0xFF2A3038),
            outline = Color(0xFF3A414B), outlineVariant = Color(0xFF2A3038),
            error = OverColor, errorContainer = Color(0xFF4A1E1E), onErrorContainer = Color(0xFFFFD9D9),
        ) to AppColors(calories, if (blue) SkyBlue else hsl(h, s, 0.62f))
    }
}

@Composable
fun AppTheme(accent: Accent, dark: Boolean, content: @Composable () -> Unit) {
    val (scheme, bars) = colorsFor(accent, dark)
    CompositionLocalProvider(LocalAppColors provides bars) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}

/** Settings → Appearance: light/dark mode and the accent color. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceCard(theme: ThemeSettings, onMode: (ThemeMode) -> Unit, onAccent: (Accent) -> Unit) {
    SectionCard {
        Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { i, m ->
                SegmentedButton(selected = theme.mode == m, onClick = { onMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) { Text(m.label) }
            }
        }
        if (theme.mode == ThemeMode.SYSTEM) Text("Follows your phone's dark mode setting.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("Color · ${theme.accent.label}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Accent.entries.forEach { a ->
                val selected = theme.accent == a
                val color = swatchColor(a)
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(color)
                        .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { onAccent(a) }
                        .semantics { contentDescription = a.label + if (selected) " (selected)" else "" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Filled.Check, null, Modifier.size(20.dp), tint = onColor(color))
                }
            }
        }
    }
}
