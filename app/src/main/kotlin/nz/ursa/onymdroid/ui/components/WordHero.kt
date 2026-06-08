// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The headword card: a primary-container surface with a soft diagonal gradient and two decorative
 * tonal circles bleeding off the edges, over which sit the part-of-speech pills and the large serif
 * headword. There is deliberately no pronunciation line and no audio control — WordNet carries no
 * phonetics and the app has no text-to-speech (see the README). The card's surface clips the circles
 * to its rounded shape.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordHero(
    term: String,
    partsOfSpeech: List<String>,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            // A gentle diagonal wash gives the flat container the depth the mockup shows.
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0f),
                                ),
                            ),
                        ),
            )
            // Decorative tonal circles, bleeding off the corners and clipped by the card.
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 38.dp, y = (-52).dp)
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-30).dp, y = 56.dp)
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f)),
            )
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (partsOfSpeech.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        partsOfSpeech.forEach { PosPill(it) }
                    }
                }
                // The headword shrinks to fit the card width on a single line, so a long term like
                // "unrelentingly" stays whole rather than wrapping its last letters; it only steps down
                // from the display size as needed, and reaches the floor size for very long words.
                BasicText(
                    text = term,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style =
                        MaterialTheme.typography.displayLarge.copy(
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    autoSize =
                        TextAutoSize.StepBased(
                            minFontSize = 24.sp,
                            maxFontSize = MaterialTheme.typography.displayLarge.fontSize,
                            stepSize = 1.sp,
                        ),
                )
            }
        }
    }
}
