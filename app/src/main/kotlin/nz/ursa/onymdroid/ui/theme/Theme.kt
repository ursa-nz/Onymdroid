// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The app theme. It follows the system light/dark setting — there is no in-app toggle — and prefers
 * Material You: on Android 12 and newer the colour scheme is derived from the user's wallpaper, and
 * on older releases it falls back to the brand purple palette ([LightColours] / [DarkColours]).
 */
@Composable
fun OnymTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colourScheme =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                DarkColours
            }

            else -> {
                LightColours
            }
        }
    MaterialTheme(
        colorScheme = colourScheme,
        typography = OnymTypography,
        shapes = OnymShapes,
        content = content,
    )
}
