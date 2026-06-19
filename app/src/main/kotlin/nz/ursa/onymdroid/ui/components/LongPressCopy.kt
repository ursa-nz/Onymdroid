// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.ui.components

import android.content.ClipData
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

/** The label every copy action carries, so assistive technology names the gesture one way everywhere. */
const val COPY_ACTION_LABEL = "Copy"

/**
 * Returns an action that puts [term] on the clipboard as plain text. This is the touch counterpart
 * of the desktop's right-click copy menu and copies the same thing: the bare term. The system's
 * clipboard overlay acknowledges the copy, so callers add no confirmation of their own.
 */
@Composable
fun rememberCopyTerm(term: String): () -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, term) {
        {
            scope.launch { clipboard.setClipEntry(ClipData.newPlainText(term, term).toClipEntry()) }
        }
    }
}

/**
 * Long-press copy for a surface with no tap action of its own: the headword and the non-navigable
 * chips and tree terms. A long press puts [term] on the clipboard with the standard long-press
 * haptic, and the same action is published to accessibility services under [COPY_ACTION_LABEL].
 * A surface that already navigates on tap reaches the action through combinedClickable instead,
 * which spares it a second, competing gesture detector.
 */
@Composable
fun Modifier.longPressCopy(term: String): Modifier {
    val copyTerm = rememberCopyTerm(term)
    val haptics = LocalHapticFeedback.current
    return this
        .semantics(mergeDescendants = true) {
            onLongClick(label = COPY_ACTION_LABEL) {
                copyTerm()
                true
            }
        }.pointerInput(term) {
            // Hand-rolled rather than detectTapGestures, which would consume ordinary taps: a
            // tree term sits inside a row whose own tap expands and collapses the branch, and
            // that must keep working. Nothing is consumed until the long press actually fires.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                // true: released in time (a tap); false: cancelled, e.g. taken over by a
                // scroll; null: still held when the window closed — the long press.
                val released =
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation() != null
                    }
                if (released == null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    copyTerm()
                    // Swallow the rest of the gesture, or an enclosing tap target would also
                    // treat the release as a tap and collapse the branch just copied from.
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.all { it.changedToUp() }) break
                    }
                }
            }
        }
}
