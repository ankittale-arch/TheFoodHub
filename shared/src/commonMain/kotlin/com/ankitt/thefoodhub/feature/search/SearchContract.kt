package com.ankitt.thefoodhub.feature.search

import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.domain.model.Nutrient
import com.ankitt.thefoodhub.domain.model.NutrientFilter
import com.ankitt.thefoodhub.domain.model.RecipeMatch
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import com.ankitt.thefoodhub.domain.model.SearchCriteria

data class SearchState(
    /** The text field's value as typed; [criteria] trims it. */
    val query: String = "",
    val ingredients: List<String> = emptyList(),
    val nutrients: NutrientFilter = NutrientFilter(),
    val recipes: List<RecipeSummary> = emptyList(),
    /** Why each recipe matched the filters, keyed by recipe id. See [matchFor]. */
    val matches: Map<Int, RecipeMatch> = emptyMap(),
    val favoriteIds: Set<Int> = emptySet(),
    val totalResults: Int = 0,
    val nextOffset: Int = 0,
    val endReached: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** Set when the first page fails. Later-page failures are sent as [SearchEffect.ShowError]. */
    val error: DataError? = null,
    /** True once a search for the current criteria has finished, so "no results" can be told apart from "not searched yet". */
    val hasSearched: Boolean = false,
) {
    val criteria: SearchCriteria get() = SearchCriteria(query = query.trim(), ingredients = ingredients, nutrients = nutrients)

    /** True when ingredient or nutrient filters are set, regardless of the text query. */
    val hasFilters: Boolean get() = ingredients.isNotEmpty() || !nutrients.isEmpty

    /** Swift sees `favoriteIds` as `Set<KotlinInt>`; this keeps call sites simple on both platforms. */
    fun isFavorite(recipeId: Int): Boolean = recipeId in favoriteIds

    /** Same reason as [isFavorite]: Swift would otherwise see `[KotlinInt: RecipeMatch]`. */
    fun matchFor(recipeId: Int): RecipeMatch? = matches[recipeId]
}

sealed interface SearchIntent {
    /** Searches after a short debounce, since it fires on every keystroke. */
    data class QueryChanged(val query: String) : SearchIntent

    /** Replaces the ingredient filter, e.g. when the ingredients sheet is applied. Searches right away. */
    data class ApplyIngredients(val ingredients: List<String>) : SearchIntent
    data class RemoveIngredient(val ingredient: String) : SearchIntent

    /** Replaces the nutrient filter, e.g. when the nutrition sheet is applied. Searches right away. */
    data class ApplyNutrients(val filter: NutrientFilter) : SearchIntent
    data class RemoveNutrient(val nutrient: Nutrient) : SearchIntent

    /** Removes ingredient and nutrient filters and keeps the text query. */
    data object ClearFilters : SearchIntent

    data object LoadNextPage : SearchIntent
    data object Retry : SearchIntent
    data class ToggleFavorite(val recipe: RecipeSummary) : SearchIntent
}

sealed interface SearchEffect {
    data class ShowError(val error: DataError) : SearchEffect
}
