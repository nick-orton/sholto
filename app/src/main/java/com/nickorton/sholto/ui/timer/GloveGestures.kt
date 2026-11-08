package com.nickorton.sholto.ui.timer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope

/** How long a press must last to end a session (DESIGN.md §2.2). */
const val LONG_PRESS_MILLIS = 1_000L

/**
 * Tap and long-press detection that works with a boxing glove or a palm.
 *
 * Unlike the standard tap detector it ignores movement and extra fingers: a
 * glove smears across the glass and a palm lands as several touches. A touch
 * counts as a tap if every pointer lifts within [longPressMillis], and as a
 * long press if anything is still down after that.
 *
 * A tap that a child (such as a button) already handled is not reported, so
 * a button on top of this area doesn't also trigger [onTap].
 */
suspend fun PointerInputScope.detectGloveGestures(
    longPressMillis: Long = LONG_PRESS_MILLIS,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var handledByChild = down.isConsumed
        val released =
            withTimeoutOrNull(longPressMillis) {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.any { it.isConsumed }) handledByChild = true
                    if (event.changes.none { it.pressed }) break
                }
                true
            }
        if (released == true) {
            if (!handledByChild) onTap()
        } else {
            onLongPress()
            // Swallow the rest of this touch so lifting the glove isn't a tap.
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
        }
    }
}
