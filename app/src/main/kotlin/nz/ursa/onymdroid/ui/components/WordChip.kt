// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The container/label colour pair a chip is tinted with, chosen per section by the caller. */
data class ChipTone(
    val container: Color,
    val onContainer: Color,
)

/** The chip tints used across the app: a soft tonal one for related words, a reddish one for opposites. */
object ChipTones {
    /** Synonyms, similar terms, derived forms, and every other relation — the soft lavender of the mockups. */
    val related: ChipTone
        @Composable get() =
            ChipTone(
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
            )

    /** Antonyms — tinted reddish so opposites read as opposites. */
    val antonym: ChipTone
        @Composable get() =
            ChipTone(
                container = MaterialTheme.colorScheme.errorContainer,
                onContainer = MaterialTheme.colorScheme.onErrorContainer,
            )
}

/**
 * A word chip. A navigable term — one that is itself a headword — is tinted with [tone], carries a
 * ↗ arrow, and is tappable, springing its corners a touch when pressed (the Expressive "squish").
 * A non-navigable term is drawn greyed and outlined with no arrow and does not respond to touch.
 */
@Composable
fun WordChip(
    label: String,
    navigable: Boolean,
    tone: ChipTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!navigable) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = modifier,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 18.dp else 12.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "chipCorner",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = tone.container,
        contentColor = tone.onContainer,
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.CallMade,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
