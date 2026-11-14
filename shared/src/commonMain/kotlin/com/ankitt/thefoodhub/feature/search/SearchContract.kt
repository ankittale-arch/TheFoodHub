package com.ankitt.thefoodhub.feature.search

import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.domain.model.RecipeSummary

data class SearchState(
    val query: String = "",
    val recipes: List<RecipeSummary> = emptyList(),
    val favoriteIds: Set<Int> = emptySet(),
    val totalResults: Int = 0,
    val nextOffset: Int = 0,
    val endReached: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** Set when the first page fails. Later-page failures are sent as [SearchEffect.ShowError]. */
    val error: DataError? = null,
    /** True once a search for the current query has finished, so "no results" can be told apart from "not searched yet". */
    val hasSearched: Boolean = false,
) {
    /** Swift sees `favoriteIds` as `Set<KotlinInt>`; this keeps call sites simple on both platforms. */
    fun isFavorite(recipeId: Int): Boolean = recipeId in favoriteIds
}

sealed interface SearchIntent {
    data class QueryChanged(val query: String) : SearchIntent
    data object LoadNextPage : SearchIntent
    data object Retry : SearchIntent
    data class ToggleFavorite(val recipe: RecipeSummary) : SearchIntent
}

sealed interface SearchEffect {
    data class ShowError(val error: DataError) : SearchEffect
}
