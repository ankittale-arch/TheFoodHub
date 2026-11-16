package com.ankitt.thefoodhub.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How far each kind of surface is raised, named for the surface rather than a number, so a chip
 * or card looks the same wherever it appears.
 *
 * The values follow Material 3's elevation scale. iOS mirrors these tokens in `Elevation.swift`;
 * change both together so the platforms keep matching.
 */
object FoodHubElevation {
    /** Filter, category, ingredient and suggestion chips. */
    val Chip: Dp = 1.dp

    /** Pill-shaped fields, such as the home search bar. */
    val Field: Dp = 1.dp

    /** Recipe cards in grids, and panels laid over an image. */
    val Card: Dp = 1.dp

    /** The featured recipe cards in the home carousel, which sit above everything around them. */
    val FeaturedCard: Dp = 3.dp
}
