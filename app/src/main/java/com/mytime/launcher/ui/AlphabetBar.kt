package com.mytime.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow

/**
 * Niagara-style wave alphabet. Letters near the finger bulge out to the left:
 * offset = maxOffset * 2^(-4·d²), where d is the distance from the finger in units of
 * [spreadLetters] letters. Dragging selects a section; a double tap calls [onDoubleTap].
 */
@Composable
fun AlphabetBar(
    sections: List<Char>,
    color: Color,
    modifier: Modifier = Modifier,
    onSection: (Char) -> Unit,
    onTouch: (active: Boolean) -> Unit = {},
    onDoubleTap: () -> Unit = {},
) {
    if (sections.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    var fingerY by remember { mutableFloatStateOf(-1f) }
    var active by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<Char?>(null) }
    val strength by animateFloatAsState(if (active) 1f else 0f, tween(180), label = "wave")

    fun select(y: Float, height: Float) {
        val index = (y / (height / sections.size)).toInt().coerceIn(0, sections.lastIndex)
        val c = sections[index]
        if (c != current) {
            current = c
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSection(c)
        }
    }

    Canvas(
        modifier
            .width(40.dp)
            .fillMaxHeight()
            .pointerInput(sections) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onTap = { select(it.y, size.height.toFloat()) },
                )
            }
            .pointerInput(sections) {
                detectDragGestures(
                    onDragStart = {
                        active = true; fingerY = it.y; current = null
                        onTouch(true)
                        select(it.y, size.height.toFloat())
                    },
                    onDragEnd = { active = false; onTouch(false) },
                    onDragCancel = { active = false; onTouch(false) },
                    onDrag = { change, _ ->
                        fingerY = change.position.y
                        select(change.position.y, size.height.toFloat())
                    },
                )
            },
    ) {
        val slot = size.height / sections.size
        val maxOffset = 56.dp.toPx()
        val spread = slot * SPREAD_LETTERS
        sections.forEachIndexed { i, c ->
            val cy = slot * i + slot / 2
            val d = if (fingerY < 0) 10f else (cy - fingerY) / spread
            val wave = 2f.pow(-4f * d * d) * strength
            val selected = active && c == current
            val style = TextStyle(
                color = if (selected) color else color.copy(alpha = 0.55f + 0.45f * wave),
                fontSize = (11 + 9 * wave).sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            val layout = measurer.measure(c.toString(), style)
            drawText(
                layout,
                topLeft = Offset(
                    size.width / 2 - layout.size.width / 2 - maxOffset * wave,
                    cy - layout.size.height / 2,
                ),
            )
        }
    }
}

private const val SPREAD_LETTERS = 3.5f
