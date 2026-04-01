package com.monosync.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize

fun Modifier.shimmerLoadingAnimation(
    isLoading: Boolean = true,
    shimmerColor: Color = Color.White
): Modifier = composed {
    if (!isLoading) return@composed this

    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer_transition")

    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                shimmerColor.copy(alpha = 0.1f),
                shimmerColor.copy(alpha = 0.3f),
                shimmerColor.copy(alpha = 0.1f),
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).onGloballyPositioned {
        size = it.size
    }
}

/**
 * Modifier to handle horizontal swipes on the 'Now Playing' album art.
 * Swipe Left → Next Track, Swipe Right → Previous Track.
 * Provides haptic feedback when threshold is met.
 */
fun Modifier.swipeToSkipTrack(
    onSeekNext: () -> Unit,
    onSeekPrevious: () -> Unit,
    swipeThreshold: Float = 150f
): Modifier = composed {
    val haptic = LocalHapticFeedback.current

    this.pointerInput(Unit) {
        var accumulatedDrag = 0f

        detectHorizontalDragGestures(
            onDragStart = {
                accumulatedDrag = 0f
            },
            onHorizontalDrag = { change, dragAmount ->
                accumulatedDrag += dragAmount
                change.consume()
            },
            onDragEnd = {
                if (accumulatedDrag > swipeThreshold) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSeekPrevious()
                } else if (accumulatedDrag < -swipeThreshold) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSeekNext()
                }
            }
        )
    }
}

/**
 * Formats milliseconds to m:ss display format.
 */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
