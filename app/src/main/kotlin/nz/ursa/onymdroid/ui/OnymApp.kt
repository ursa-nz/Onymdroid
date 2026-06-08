// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** Which top-level screen the app is showing. */
private enum class Screen { Thesaurus, About }

/**
 * The app shell. It hosts the adaptive list-detail layout — a search/word-list pane beside the word
 * detail on wide screens, a single pane on a phone. The serif top bar (history arrows, the docked
 * search field, the combined About/Settings button) sits over the detail pane; the list pane is the
 * full search experience with its own field. The dice FAB floats over the lot, and About/Settings is
 * a separate top-level destination. Everything draws edge to edge and insets for the system bars.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnymApp(
    viewModel: ThesaurusViewModel,
    modifier: Modifier = Modifier,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.Thesaurus) }

    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val canBack by viewModel.canBack.collectAsStateWithLifecycle()
    val canForward by viewModel.canForward.collectAsStateWithLifecycle()
    val currentTerm by viewModel.currentTerm.collectAsStateWithLifecycle()

    // Start on the detail pane so a phone lands on the word, not the search list; on a wide screen
    // both panes show regardless. The search list is one tap (or a back gesture) away.
    val navigator =
        rememberListDetailPaneScaffoldNavigator<Unit>(
            initialDestinationHistory =
                listOf(ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.Detail)),
        )
    val scope = rememberCoroutineScope()

    when (screen) {
        Screen.About -> {
            AboutSettingsRoute(
                onBack = { screen = Screen.Thesaurus },
                treeExpandedByDefault = settings.treeExpandedByDefault,
                onTreeExpandedChange = viewModel::setTreeExpanded,
                modifier = modifier,
            )
        }

        Screen.Thesaurus -> {
            Scaffold(
                modifier = modifier,
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = viewModel::shuffle,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.Casino, contentDescription = "Random word")
                    }
                },
                // The panes manage their own status-bar inset; let the FAB clear the navigation bar.
                contentWindowInsets = WindowInsets.statusBars,
            ) { innerPadding ->
                Box(Modifier.fillMaxSize()) {
                    NavigableListDetailPaneScaffold(
                        navigator = navigator,
                        listPane = {
                            AnimatedPane {
                                WordListPane(
                                    viewModel = viewModel,
                                    onPick = { term ->
                                        viewModel.navigate(term)
                                        scope.launch {
                                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                                        }
                                    },
                                )
                            }
                        },
                        detailPane = {
                            AnimatedPane {
                                Column(Modifier.fillMaxSize()) {
                                    OnymTopBar(
                                        searchText = currentTerm.orEmpty(),
                                        canBack = canBack,
                                        canForward = canForward,
                                        onBack = viewModel::back,
                                        onForward = viewModel::forward,
                                        onOpenSearch = {
                                            scope.launch {
                                                navigator.navigateTo(ListDetailPaneScaffoldRole.List)
                                            }
                                        },
                                        onOpenAbout = { screen = Screen.About },
                                    )
                                    WordDetailScreen(
                                        state = detail,
                                        treeExpandedByDefault = settings.treeExpandedByDefault,
                                        onNavigate = viewModel::navigate,
                                        contentPadding =
                                            PaddingValues(
                                                start = 16.dp,
                                                end = 16.dp,
                                                top = 8.dp,
                                                bottom = innerPadding.calculateBottomPadding() + 112.dp,
                                            ),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
