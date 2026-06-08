// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import nz.ursa.onymdroid.core.OnymAntonym
import nz.ursa.onymdroid.core.OnymTreeNode
import nz.ursa.onymdroid.core.OnymWord

/** A heading followed by a wrapping row of word chips, each tappable when it is a headword. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordChipSection(
    title: String,
    words: List<OnymWord>,
    navigable: Set<String>,
    tone: ChipTone,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = title, icon = iconForSection(title))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 2.dp),
        ) {
            words.forEach { word ->
                WordChip(
                    label = word.term,
                    navigable = word.term in navigable,
                    tone = tone,
                    onClick = { onNavigate(word.term) },
                )
            }
        }
    }
}

/**
 * The antonyms section. Each opposite is a reddish chip; an indirect antonym (one reached through a
 * similar sense) is labelled as such, and any implication terms it carries follow as smaller chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AntonymSection(
    title: String,
    antonyms: List<OnymAntonym>,
    navigable: Set<String>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = ChipTones.antonym
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = title, icon = iconForSection(title))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            antonyms.forEach { antonym ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WordChip(
                            label = antonym.term,
                            navigable = antonym.term in navigable,
                            tone = tone,
                            onClick = { onNavigate(antonym.term) },
                        )
                        if (!antonym.direct) {
                            Text(
                                text = "indirect",
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (antonym.implications.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            antonym.implications.forEach { implication ->
                                WordChip(
                                    label = implication.term,
                                    navigable = implication.term in navigable,
                                    tone = ChipTones.related,
                                    onClick = { onNavigate(implication.term) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A lexical-relation tree (is-a, kinds, part-of …). Each node shows its terms — tappable when a
 * headword — and, if it has children, a chevron that expands or collapses the branch. Whether
 * branches start open follows the user's preference ([expandedByDefault]).
 */
@Composable
fun TreeSection(
    title: String,
    nodes: List<OnymTreeNode>,
    navigable: Set<String>,
    expandedByDefault: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = title, icon = iconForSection(title))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                nodes.forEach { node ->
                    TreeNodeRow(
                        node = node,
                        depth = 0,
                        navigable = navigable,
                        expandedByDefault = expandedByDefault,
                        onNavigate = onNavigate,
                    )
                }
            }
        }
    }
}

@Composable
private fun TreeNodeRow(
    node: OnymTreeNode,
    depth: Int,
    navigable: Set<String>,
    expandedByDefault: Boolean,
    onNavigate: (String) -> Unit,
) {
    val hasChildren = node.children.isNotEmpty()
    var expanded by rememberSaveable(node.label, depth) { mutableStateOf(expandedByDefault) }
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "treeChevron")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (hasChildren) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(start = (16 + depth * 18).dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        if (hasChildren) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(rotation),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .padding(start = 7.dp, end = 7.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        TreeTerms(terms = node.terms, navigable = navigable, onNavigate = onNavigate)
    }
    if (hasChildren && expanded) {
        node.children.forEach { child ->
            TreeNodeRow(
                node = child,
                depth = depth + 1,
                navigable = navigable,
                expandedByDefault = expandedByDefault,
                onNavigate = onNavigate,
            )
        }
    }
}

/**
 * The terms of one tree node rendered inline: each headword term is tappable and underlined-feeling
 * via the primary colour, each non-headword term greyed, joined by commas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TreeTerms(
    terms: List<String>,
    navigable: Set<String>,
    onNavigate: (String) -> Unit,
) {
    FlowRow(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        terms.forEachIndexed { index, term ->
            val isNavigable = term in navigable
            Text(
                text = if (index < terms.lastIndex) "$term," else term,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (isNavigable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier =
                    Modifier
                        .then(if (isNavigable) Modifier.clickable { onNavigate(term) } else Modifier)
                        .padding(end = 6.dp),
            )
        }
    }
}
