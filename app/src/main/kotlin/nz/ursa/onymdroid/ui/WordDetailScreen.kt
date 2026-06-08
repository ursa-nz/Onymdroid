// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nz.ursa.onymdroid.core.OnymSection
import nz.ursa.onymdroid.ui.components.AntonymSection
import nz.ursa.onymdroid.ui.components.ChipTones
import nz.ursa.onymdroid.ui.components.DefinitionList
import nz.ursa.onymdroid.ui.components.SectionHeader
import nz.ursa.onymdroid.ui.components.TreeSection
import nz.ursa.onymdroid.ui.components.WordChip
import nz.ursa.onymdroid.ui.components.WordChipSection
import nz.ursa.onymdroid.ui.components.WordHero
import nz.ursa.onymdroid.ui.components.iconForSection

/**
 * The word screen: one scrolling column rendering the looked-up entry — the headword hero, the
 * numbered definitions, and every relation section in the order the engine returns them, each chip
 * or tree term routing back through [onNavigate]. It also renders the loading and "did you mean"
 * states so the same composable covers every detail outcome.
 */
@Composable
fun WordDetailScreen(
    state: DetailState,
    treeExpandedByDefault: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    when (state) {
        // Nothing looked up yet; the search pane is the entry point.
        is DetailState.Empty -> {}

        is DetailState.Loading -> {
            LoadingState(modifier = modifier, contentPadding = contentPadding)
        }

        is DetailState.Missing -> {
            MissingState(
                state = state,
                onNavigate = onNavigate,
                modifier = modifier,
                contentPadding = contentPadding,
            )
        }

        is DetailState.Loaded -> {
            LoadedState(
                rendered = state.word,
                treeExpandedByDefault = treeExpandedByDefault,
                onNavigate = onNavigate,
                modifier = modifier,
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun LoadedState(
    rendered: RenderedWord,
    treeExpandedByDefault: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    val result = rendered.result
    val navigable = rendered.navigable
    val partsOfSpeech =
        result.sections
            .filterIsInstance<OnymSection.Definitions>()
            .flatMap { section -> section.items.mapNotNull { it.pos } }
            .distinct()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "hero") {
            WordHero(term = result.term, partsOfSpeech = partsOfSpeech)
        }
        result.sections.forEach { section ->
            renderSection(
                section = section,
                navigable = navigable,
                treeExpandedByDefault = treeExpandedByDefault,
                onNavigate = onNavigate,
            )
        }
    }
}

private fun LazyListScope.renderSection(
    section: OnymSection,
    navigable: Set<String>,
    treeExpandedByDefault: Boolean,
    onNavigate: (String) -> Unit,
) {
    when (section) {
        is OnymSection.Definitions -> {
            item(key = section.title) {
                Column {
                    SectionHeader(title = section.title, icon = iconForSection(section.title))
                    DefinitionList(section.items)
                }
            }
        }

        is OnymSection.Words -> {
            item(key = section.title) {
                WordChipSection(
                    title = section.title,
                    words = section.items,
                    navigable = navigable,
                    tone = ChipTones.related,
                    onNavigate = onNavigate,
                )
            }
        }

        is OnymSection.Antonyms -> {
            item(key = section.title) {
                AntonymSection(
                    title = section.title,
                    antonyms = section.items,
                    navigable = navigable,
                    onNavigate = onNavigate,
                )
            }
        }

        is OnymSection.Tree -> {
            item(key = section.title) {
                TreeSection(
                    title = section.title,
                    nodes = section.items,
                    navigable = navigable,
                    expandedByDefault = treeExpandedByDefault,
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

@Composable
private fun LoadingState(
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissingState(
    state: DetailState.Missing,
    onNavigate: (String) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(contentPadding).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = "No entry for “${state.term}”.",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            if (state.suggestions.isNotEmpty()) {
                Text(
                    text = "Did you mean",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.suggestions.forEach { suggestion ->
                        WordChip(
                            label = suggestion,
                            navigable = true,
                            tone = ChipTones.related,
                            onClick = { onNavigate(suggestion) },
                        )
                    }
                }
            }
        }
    }
}
