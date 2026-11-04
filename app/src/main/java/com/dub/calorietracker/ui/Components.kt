package com.dub.calorietracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------- palette ----------
val Teal = Color(0xFF72D3BF)        // calories
val SkyBlue = Color(0xFF52B4F2)     // macros
val AccentBlue = Color(0xFF1B8BDB)  // buttons
val Amber = Color(0xFFF2B33D)       // zigzag high days
val OverColor = Color(0xFFEF5B5B)

// Kept so existing screens read naturally; macros share one calm color like the reference design.
val CaloriesColor = Teal
val ProteinColor = SkyBlue
val CarbsColor = SkyBlue
val FatColor = SkyBlue

fun num(v: Double): String = NumberFormat.getIntegerInstance().format(v.roundToInt())
fun num(v: Int): String = NumberFormat.getIntegerInstance().format(v)

/** Big left-aligned page title with optional subtitle and prev/next chevrons. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onPrev: (() -> Unit)? = null, onNext: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onPrev != null) IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onNext != null) IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Thin rounded progress bar: 50 of 100 fills half. Turns red when over. */
@Composable
fun ProgressBar(fraction: Float, color: Color, height: Dp = 8.dp, modifier: Modifier = Modifier) {
    val over = fraction > 1f
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(if (over) OverColor else color)
        )
    }
}

/** Full-width row: "Protein ........ 35 / 103 g", bar, "68 g left". */
@Composable
fun MacroBar(label: String, eaten: Double, target: Double, unit: String, color: Color, big: Boolean = false) {
    val fraction = if (target > 0) (eaten / target).toFloat() else 0f
    val left = target - eaten
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = if (big) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (big) FontWeight.SemiBold else FontWeight.Normal)
            Spacer(Modifier.weight(1f))
            Text("${num(eaten)} / ${num(target)} $unit", fontWeight = FontWeight.SemiBold,
                style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(fraction, color, if (big) 10.dp else 8.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            if (left >= 0) "${num(left)} $unit left" else "${num(abs(left))} $unit over",
            style = MaterialTheme.typography.bodySmall,
            color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else OverColor,
        )
    }
}

/** Compact centered macro: label, bar, "206 / 258 g". */
@Composable
fun MacroColumn(label: String, eaten: Double, target: Double, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        ProgressBar(if (target > 0) (eaten / target).toFloat() else 0f, SkyBlue, 8.dp)
        Spacer(Modifier.height(8.dp))
        Text("${num(eaten)} / ${num(target)} g", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

/** Open arc gauge (240°) used for the day's calories. */
@Composable
fun CalorieGauge(eaten: Double, target: Double, size: Dp = 168.dp, stroke: Dp = 14.dp) {
    val remaining = target - eaten
    val fraction = if (target > 0) (eaten / target).toFloat() else 0f
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "gauge")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = if (fraction > 1f) OverColor else Teal
    // The arc is open at the bottom, so the box is cut to ~80% height to avoid dead space below it.
    Box(Modifier.width(size).height(size * 0.8f)) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val d = this.size.width - w
            val arcSize = androidx.compose.ui.geometry.Size(d, d)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 150f, 240f, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (animated > 0f) drawArc(color, 150f, 240f * animated, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top = size / 2 - 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(num(abs(remaining)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = if (remaining < 0) OverColor else MaterialTheme.colorScheme.onSurface)
            Text(if (remaining < 0) "Over" else "Remaining", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Small stat: big number over a muted label. */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** White rounded card with no border — the basic building block of every screen. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.Top) { content() }
    }
}
