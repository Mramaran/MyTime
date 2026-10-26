package com.mytime.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Home-screen clock styles, after Niagara's at-a-glance styles. */
enum class ClockStyle(val title: String) {
    Basic("Basic"),
    Bold("Bold"),
    Analog("Analog"),
    Flip("Flip"),
    Vertical("Vertical"),
    Words("Words"),
    DayOfWeek("Day of week"),
    None("No clock");

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: Basic
    }
}


/** Draws [now] in [style]. [scale] shrinks it for previews. */
@Composable
fun ClockView(style: ClockStyle, now: Long, is24h: Boolean, scale: Float = 1f) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h24 = cal.get(Calendar.HOUR_OF_DAY)
    val hour = if (is24h) h24 else (h24 % 12).let { if (it == 0) 12 else it }
    val minute = cal.get(Calendar.MINUTE)
    val hh = if (is24h) "%02d".format(hour) else hour.toString()
    val mm = "%02d".format(minute)
    val s = { v: Int -> (v * scale).sp }
    val accent = MaterialTheme.colorScheme.primary

    when (style) {
        ClockStyle.Basic -> Text("$hh:$mm", style = OnWallpaper.copy(fontSize = s(64), fontWeight = FontWeight.Light))
        ClockStyle.Bold -> Text("$hh:$mm", style = OnWallpaper.copy(fontSize = s(76), fontWeight = FontWeight.Black, letterSpacing = (-2).sp))
        ClockStyle.Analog -> AnalogClock(cal, (150 * scale).dp, Wall, accent)
        ClockStyle.Flip -> Row(horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
            FlipCard(hh, scale)
            FlipCard(mm, scale)
        }
        ClockStyle.Vertical -> Column {
            Text(hh, style = OnWallpaper.copy(fontSize = s(84), fontWeight = FontWeight.Bold, lineHeight = s(80)))
            Text(mm, style = OnWallpaper.copy(fontSize = s(84), fontWeight = FontWeight.Light, color = accent, lineHeight = s(80)))
        }
        ClockStyle.Words -> Text(
            timeInWords(h24, minute),
            style = OnWallpaper.copy(fontSize = s(40), fontWeight = FontWeight.Light, lineHeight = s(46)),
        )
        ClockStyle.DayOfWeek -> Column {
            Text(
                cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: "",
                style = OnWallpaper.copy(fontSize = s(52), fontWeight = FontWeight.Bold),
            )
            Text("$hh:$mm", style = OnWallpaper.copy(fontSize = s(28), color = accent))
        }
        ClockStyle.None -> Unit
    }
}

@Composable
private fun FlipCard(text: String, scale: Float) {
    Box(
        Modifier
            .clip(RoundedCornerShape((14 * scale).dp))
            .background(Color(0xFF1B1E24))
            .padding(horizontal = (14 * scale).dp, vertical = (6 * scale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = OnWallpaper.copy(color = Color.White, fontSize = (60 * scale).sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
        // The fold line across the middle of a flip card.
        Box(Modifier.matchParentSize().padding(vertical = 0.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height((2 * scale).dp).background(Color.Black.copy(alpha = 0.6f)))
        }
    }
}

@Composable
private fun AnalogClock(cal: Calendar, size: Dp, face: Color, accent: Color) {
    val minute = cal.get(Calendar.MINUTE)
    val hour = cal.get(Calendar.HOUR) + minute / 60f
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = Offset(r, r)
        drawCircle(Color.Black.copy(alpha = 0.25f), r)
        drawCircle(face, r, style = Stroke(r * 0.04f))
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90)
            val outer = Offset(c.x + (r * 0.9f) * cos(a).toFloat(), c.y + (r * 0.9f) * sin(a).toFloat())
            val inner = Offset(c.x + (r * 0.78f) * cos(a).toFloat(), c.y + (r * 0.78f) * sin(a).toFloat())
            drawLine(face, inner, outer, strokeWidth = if (i % 3 == 0) r * 0.05f else r * 0.025f, cap = StrokeCap.Round)
        }
        fun hand(angleDeg: Double, len: Float, width: Float, color: Color) {
            val a = Math.toRadians(angleDeg - 90)
            drawLine(color, c, Offset(c.x + len * cos(a).toFloat(), c.y + len * sin(a).toFloat()), strokeWidth = width, cap = StrokeCap.Round)
        }
        hand(hour * 30.0, r * 0.5f, r * 0.08f, face)
        hand(minute * 6.0, r * 0.75f, r * 0.05f, accent)
        drawCircle(accent, r * 0.06f)
    }
}

/** "twenty past three", "half past ten", "quarter to nine"; rounded to 5 minutes. */
fun timeInWords(hour24: Int, minute: Int): String {
    val names = listOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven")
    val m = ((minute + 2) / 5) * 5
    val base = if (m > 30) hour24 + 1 else hour24
    val h = names[base % 12]
    return when (m) {
        0, 60 -> "$h o'clock"
        15 -> "quarter past $h"
        30 -> "half past $h"
        45 -> "quarter to $h"
        in 5..25 -> "${minutesWord(m)} past $h"
        else -> "${minutesWord(60 - m)} to $h"
    }
}

private fun minutesWord(m: Int) = when (m) {
    5 -> "five"; 10 -> "ten"; 20 -> "twenty"; 25 -> "twenty-five"; else -> m.toString()
}

/** Picker: every style rendered live; tap to choose. */
@Composable
fun ClockStylesScreen(vm: LauncherViewModel, state: LauncherState, now: Long, is24h: Boolean) {
    val current = ClockStyle.of(state.settings.clockStyle)
    val accent = MaterialTheme.colorScheme.primary
    EditorScaffold("Clock style", { vm.screen.value = Screen.Settings }) {
        items(ClockStyle.entries.toList(), key = { it.name }) { style ->
            val selected = style == current
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(2.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(20.dp))
                    .clickable { vm.updateSettings { it.copy(clockStyle = style.name) } }
                    .padding(20.dp),
            ) {
                Text(style.title + if (selected) "  ✓" else "", style = MaterialTheme.typography.labelLarge, color = accent)
                Spacer(Modifier.height(8.dp))
                if (style == ClockStyle.None) Text("Only the date and info line", color = LocalContentColor.current.copy(alpha = 0.7f))
                else CompositionLocalProvider(LocalOnWallpaper provides MaterialTheme.colorScheme.onSurface) { ClockView(style, now, is24h, scale = 0.7f) }
            }
        }
        item { Spacer(Modifier.width(1.dp).height(32.dp)) }
    }
}
