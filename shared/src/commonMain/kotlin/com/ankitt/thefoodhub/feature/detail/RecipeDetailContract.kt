package com.ankitt.thefoodhub.feature.detail

import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.domain.model.RecipeDetail

data class RecipeDetailState(
    val recipeId: Int,
    val recipe: RecipeDetail? = null,
    val isFavorite: Boolean = false,
    val isLoading: Boolean = false,
    /** Only meaningful when [recipe] is null; cached data is always preferred to an error screen. */
    val error: DataError? = null,
)

sealed interface RecipeDetailIntent {
    data object Retry : RecipeDetailIntent
    data object ToggleFavorite : RecipeDetailIntent
}

sealed interface RecipeDetailEffect {
    data class FavoriteChanged(val isFavorite: Boolean) : RecipeDetailEffect
}
