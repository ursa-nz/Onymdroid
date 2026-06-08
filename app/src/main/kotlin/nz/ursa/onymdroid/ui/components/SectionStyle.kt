// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.JoinInner
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The small accent icon shown beside each section heading, keyed by the section title the engine
 * produces. The titles are fixed strings from the core ([nz.ursa.onymdroid.core.OnymSection]); any
 * unmapped one falls back to a neutral icon rather than crashing.
 */
fun iconForSection(title: String): ImageVector = when (title) {
    "Definitions" -> Icons.Rounded.FormatListNumbered
    "Synonyms" -> Icons.Rounded.JoinInner
    "Antonyms" -> Icons.Rounded.SwapHoriz
    "Derived forms" -> Icons.Rounded.AccountTree
    "Similar to" -> Icons.Rounded.Hub
    "Attributes" -> Icons.AutoMirrored.Rounded.Label
    "Causes" -> Icons.Rounded.Bolt
    "Entails" -> Icons.AutoMirrored.Rounded.ArrowForward
    "Pertains to" -> Icons.Rounded.Link
    "Is a kind of" -> Icons.Rounded.NorthEast
    "Kinds" -> Icons.Rounded.SouthWest
    "Part of" -> Icons.Rounded.Extension
    "Parts" -> Icons.Rounded.Widgets
    "Domains" -> Icons.Rounded.Category
    else -> Icons.Rounded.Hub
}
