// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import nz.ursa.onymdroid.R

/*
 * Two bundled variable fonts wired into a complete M3 type scale. Lora (a serif) carries the
 * headword and every heading; Roboto Flex carries body and label text. Both ship as .ttf in res/font
 * and are loaded with the weight variation axis, so the app needs no Google Play Services and no
 * network — the type renders identically offline and on any device. A variable axis also means the
 * weights stay crisp at any size the user's font-scale setting asks for.
 */

@OptIn(ExperimentalTextApi::class)
private fun loraFont(weight: FontWeight) = Font(
    resId = R.font.lora_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun robotoFlexFont(weight: FontWeight) = Font(
    resId = R.font.roboto_flex_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Lora — the serif used for the headword and the section headings. */
internal val LoraFamily =
    FontFamily(
        loraFont(FontWeight.Normal),
        loraFont(FontWeight.Medium),
        loraFont(FontWeight.SemiBold),
        loraFont(FontWeight.Bold),
    )

/** Roboto Flex — the body and label face. */
internal val RobotoFlexFamily =
    FontFamily(
        robotoFlexFont(FontWeight.Normal),
        robotoFlexFont(FontWeight.Medium),
        robotoFlexFont(FontWeight.SemiBold),
        robotoFlexFont(FontWeight.Bold),
    )

private val Base = Typography()

/**
 * The app's type scale: Roboto Flex everywhere by default, with the display, headline, and title
 * roles re-cut in Lora — those are the headword card and the serif section headings of the mockups.
 */
internal val OnymTypography =
    Base.copy(
        displayLarge =
            Base.displayLarge.copy(
                fontFamily = LoraFamily,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp,
            ),
        displayMedium = Base.displayMedium.copy(fontFamily = LoraFamily, fontWeight = FontWeight.SemiBold),
        displaySmall = Base.displaySmall.copy(fontFamily = LoraFamily, fontWeight = FontWeight.SemiBold),
        headlineLarge = Base.headlineLarge.copy(fontFamily = LoraFamily, fontWeight = FontWeight.SemiBold),
        headlineMedium = Base.headlineMedium.copy(fontFamily = LoraFamily, fontWeight = FontWeight.SemiBold),
        headlineSmall = Base.headlineSmall.copy(fontFamily = LoraFamily, fontWeight = FontWeight.SemiBold),
        titleLarge = Base.titleLarge.copy(fontFamily = LoraFamily, fontWeight = FontWeight.SemiBold),
        titleMedium = Base.titleMedium.copy(fontFamily = RobotoFlexFamily),
        titleSmall = Base.titleSmall.copy(fontFamily = RobotoFlexFamily),
        bodyLarge = Base.bodyLarge.copy(fontFamily = RobotoFlexFamily),
        bodyMedium = Base.bodyMedium.copy(fontFamily = RobotoFlexFamily),
        bodySmall = Base.bodySmall.copy(fontFamily = RobotoFlexFamily),
        labelLarge = Base.labelLarge.copy(fontFamily = RobotoFlexFamily),
        labelMedium = Base.labelMedium.copy(fontFamily = RobotoFlexFamily),
        labelSmall = Base.labelSmall.copy(fontFamily = RobotoFlexFamily),
    )
