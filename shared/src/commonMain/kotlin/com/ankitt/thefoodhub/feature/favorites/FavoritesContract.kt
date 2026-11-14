package com.ankitt.thefoodhub.feature.favorites

import com.ankitt.thefoodhub.domain.model.RecipeSummary

data class FavoritesState(
    val favorites: List<RecipeSummary> = emptyList(),
    val isLoading: Boolean = true,
)

sealed interface FavoritesIntent {
    data class Remove(val recipe: RecipeSummary) : FavoritesIntent
    data class UndoRemove(val recipe: RecipeSummary) : FavoritesIntent
}

sealed interface FavoritesEffect {
    /** The UI can offer an undo that sends [FavoritesIntent.UndoRemove]. */
    data class Removed(val recipe: RecipeSummary) : FavoritesEffect
}
