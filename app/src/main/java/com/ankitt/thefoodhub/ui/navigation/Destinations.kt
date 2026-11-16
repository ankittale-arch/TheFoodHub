package com.ankitt.thefoodhub.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeDestination

@Serializable
data object SearchDestination

@Serializable
data object FavoritesDestination

@Serializable
data object CalculatorDestination

/** [title] is shown in the app bar while the details load. */
@Serializable
data class RecipeDetailDestination(val recipeId: Int, val title: String? = null)
