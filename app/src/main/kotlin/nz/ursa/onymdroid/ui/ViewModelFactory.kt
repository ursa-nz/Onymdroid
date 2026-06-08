// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.ViewModelInitializer
import nz.ursa.onymdroid.data.OnymRepository
import nz.ursa.onymdroid.data.SettingsRepository

/**
 * Builds the [ThesaurusViewModel] with the two repositories it needs. The app is small enough not to
 * warrant a dependency-injection framework, so this hand-written initializer wires them — both
 * repositories are cheap to construct and hold only the application context (the heavy engine is
 * opened lazily inside the repository).
 */
val thesaurusViewModelInitializer =
    ViewModelInitializer(ThesaurusViewModel::class.java) {
        val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        ThesaurusViewModel(
            application = application,
            repository = OnymRepository(application),
            settingsRepository = SettingsRepository(application),
        )
    }
