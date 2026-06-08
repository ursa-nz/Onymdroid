// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import nz.ursa.onymdroid.ui.OnymApp
import nz.ursa.onymdroid.ui.ThesaurusViewModel
import nz.ursa.onymdroid.ui.theme.OnymTheme
import nz.ursa.onymdroid.ui.thesaurusViewModelInitializer

/**
 * The single activity that hosts the Compose UI. It draws edge to edge — the system bars carry the
 * app colour while content stays clear of them — and themes the whole app with Material You (system
 * light/dark, dynamic colour where the platform supports it). The [ThesaurusViewModel] survives
 * configuration changes, so the looked-up word and search state outlast a rotation.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: ThesaurusViewModel by viewModels {
        ViewModelProvider.Factory.from(thesaurusViewModelInitializer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OnymTheme {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    OnymApp(viewModel = viewModel)
                }
            }
        }
    }
}
