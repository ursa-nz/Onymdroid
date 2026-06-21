// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nz.ursa.onymdroid.core.OnymAntonym
import nz.ursa.onymdroid.core.OnymSenseTranslations
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
 * The etymology section: prose paragraphs to read, not terms to navigate. Drawn as the same tonal
 * card as the definitions, each origin a numbered row (a word with several etymologies shows one
 * paragraph each), inside a SelectionContainer so the prose can be copied.
 */
@Composable
fun EtymologySection(
    title: String,
    paragraphs: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = title, icon = iconForSection(title))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Column {
                    paragraphs.forEachIndexed { index, paragraph ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = (index + 1).toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Text(
                                text = paragraph,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The translations section: one block per looked-up sense, the words other languages use for it
 * grouped by language. Drawn as the same tonal card as the definitions and etymology, each sense
 * naming its meaning with a dimmed part of speech and gloss, then a line per language. The words are
 * plain selectable text, not chips, because a foreign word is not an English headword to navigate to.
 */
@Composable
fun TranslationsSection(
    title: String,
    items: List<OnymSenseTranslations>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = title, icon = iconForSection(title))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Column {
                    items.forEachIndexed { index, sense ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                sense.pos?.let { pos ->
                                    Text(
                                        text = pos,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = sense.gloss,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            sense.languages.forEach { language ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(start = 12.dp),
                                ) {
                                    Text(
                                        text = language.language,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = language.words.joinToString(", "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
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
                nodes.forEachIndexed { index, node ->
                    val path = "$title/$index"
                    key(path) {
                        TreeNodeRow(
                            node = node,
                            depth = 0,
                            statePath = path,
                            navigable = navigable,
                            expandedByDefault = expandedByDefault,
                            onNavigate = onNavigate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TreeNodeRow(
    node: OnymTreeNode,
    depth: Int,
    statePath: String,
    navigable: Set<String>,
    expandedByDefault: Boolean,
    onNavigate: (String) -> Unit,
) {
    val hasChildren = node.children.isNotEmpty()
    // The open/closed state is saved under the node's path in its tree ([statePath]), never under
    // the composition position: these rows all recurse from one call site, where positional
    // saveable keys collide and saved state becomes order-dependent. The label input still resets
    // the state when a different tree comes to occupy the same path, so a newly loaded word always
    // starts from the user's preference.
    var expanded by rememberSaveable(node.label, key = statePath) { mutableStateOf(expandedByDefault) }
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
        node.children.forEachIndexed { index, child ->
            val childPath = "$statePath/$index"
            key(childPath) {
                TreeNodeRow(
                    node = child,
                    depth = depth + 1,
                    statePath = childPath,
                    navigable = navigable,
                    expandedByDefault = expandedByDefault,
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

/**
 * The terms of one tree node rendered inline: each headword term is tappable and underlined-feeling
 * via the primary colour, each non-headword term greyed, joined by commas. A long press copies any
 * term — the bare term, never the joining comma its label may carry — matching the desktop, where
 * tree terms are chips with the same copy menu.
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
            val gestures =
                if (isNavigable) {
                    Modifier.combinedClickable(
                        onLongClickLabel = COPY_ACTION_LABEL,
                        onLongClick = rememberCopyTerm(term),
                        hapticFeedbackEnabled = true,
                        onClick = { onNavigate(term) },
                    )
                } else {
                    Modifier.longPressCopy(term)
                }
            Text(
                text = if (index < terms.lastIndex) "$term," else term,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (isNavigable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = gestures.padding(end = 6.dp),
            )
        }
    }
}
