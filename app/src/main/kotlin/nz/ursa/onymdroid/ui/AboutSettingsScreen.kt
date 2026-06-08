// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * The About/Settings destination: a top app bar with a back button over the [AboutSettingsScreen]
 * content. Hardware/predictive back returns to the thesaurus too. The bar fills the status bar with
 * the surface colour, and the content is inset for the system bars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsRoute(
    onBack: () -> Unit,
    treeExpandedByDefault: Boolean,
    onTreeExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("About & settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        AboutSettingsScreen(
            treeExpandedByDefault = treeExpandedByDefault,
            onTreeExpandedChange = onTreeExpandedChange,
            contentPadding = innerPadding,
        )
    }
}

/**
 * The combined About and Settings screen. It carries the project's attributions — WordNet from
 * Princeton, the Artha lineage the engine descends from, and the Kaurna acknowledgement — and the
 * app's single preference: whether relation trees open expanded by default. The preference is
 * persisted in DataStore by the ViewModel.
 */
@Composable
fun AboutSettingsScreen(
    treeExpandedByDefault: Boolean,
    onTreeExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Onymdroid", style = MaterialTheme.typography.displaySmall)
            Text(
                "An offline WordNet thesaurus and dictionary.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard {
            Text("Settings", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Expand relation trees", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Open is-a, kinds, and part-of trees expanded by default.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = treeExpandedByDefault, onCheckedChange = onTreeExpandedChange)
            }
        }

        SettingsCard {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Attribution(
                heading = "Built on WordNet",
                body =
                    "Word data comes from WordNet, the lexical database from Princeton University. " +
                        "The bundled database is the patched build maintained by the Debian project.",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Attribution(
                heading = "Engine derived from Artha",
                body =
                    "The lookup logic is derived from Artha, an earlier WordNet thesaurus by " +
                        "Sundaram Ramaswamy, under the GPL.",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Attribution(
                heading = "Acknowledgement of Country",
                body =
                    "Built in Narrm on Woiwurrung, Boonwurrung Country, with respect to the Wurundjeri " +
                        "and Bunurong peoples, their languages, and their continuing connection to this Country.",
            )
        }

        SettingsCard {
            Text("Links", style = MaterialTheme.typography.titleMedium)
            LinkRow(
                label = "ursa.nz software",
                url = "https://software.ursa.nz",
                icon = Icons.Rounded.Public,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LinkRow(
                label = "Support on Ko-fi",
                url = "https://ko-fi.com/arlewatyerre",
                icon = Icons.Rounded.Favorite,
            )
        }

        Text(
            "Free software, licensed under the GPL, version 3 or later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun Attribution(
    heading: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            heading,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A tappable row that opens [url] in the browser, for the project's website and sponsor links. */
@Composable
private fun LinkRow(
    label: String,
    url: String,
    icon: ImageVector,
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .padding(vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
