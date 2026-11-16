package com.ankitt.thefoodhub.feature.search

import androidx.lifecycle.viewModelScope
import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.core.mvi.MviViewModel
import com.ankitt.thefoodhub.domain.model.IngredientCatalog
import com.ankitt.thefoodhub.domain.model.NutrientFilter
import com.ankitt.thefoodhub.domain.model.NutrientRange
import com.ankitt.thefoodhub.domain.model.SearchCriteria
import com.ankitt.thefoodhub.domain.repository.FavoritesRepository
import com.ankitt.thefoodhub.domain.repository.RecipeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SearchViewModel(
    private val recipeRepository: RecipeRepository,
    private val favoritesRepository: FavoritesRepository,
) : MviViewModel<SearchState, SearchIntent, SearchEffect>(SearchState()) {

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    /** What [searchJob] has sent a request for; null while it is still waiting for typing to pause. */
    private var fetchingCriteria: SearchCriteria? = null

    /** What the results on screen were found for. */
    private var shownCriteria: SearchCriteria? = null

    init {
        favoritesRepository.observeFavoriteIds()
            .onEach { ids -> updateState { copy(favoriteIds = ids) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> onQueryChanged(intent.query)
            SearchIntent.Submit -> search(debounce = false)
            is SearchIntent.ApplyIngredients -> setIngredients(
                intent.ingredients.fold(emptyList(), IngredientCatalog::add),
            )
            is SearchIntent.RemoveIngredient -> setIngredients(currentState.ingredients - intent.ingredient)
            is SearchIntent.ApplyNutrients -> setNutrients(intent.filter)
            is SearchIntent.RemoveNutrient -> setNutrients(currentState.nutrients.with(intent.nutrient, NutrientRange()))
            SearchIntent.ClearFilters -> setFilters(emptyList(), NutrientFilter())
            SearchIntent.LoadNextPage -> loadNextPage()
            SearchIntent.Retry -> search(debounce = false, force = true)
            is SearchIntent.ToggleFavorite -> viewModelScope.launch {
                favoritesRepository.toggleFavorite(intent.recipe)
            }
        }
    }

    private fun onQueryChanged(query: String) {
        if (query == currentState.query) return
        // Changing only case or spacing ("Pasta " → "pasta") is the same search, so it costs nothing.
        val searchTermChanged = SearchCriteria.normalizeQuery(query) != currentState.criteria.query
        updateState { copy(query = query) }
        if (searchTermChanged) search(debounce = true)
    }

    private fun setIngredients(ingredients: List<String>) = setFilters(ingredients, currentState.nutrients)

    private fun setNutrients(nutrients: NutrientFilter) = setFilters(currentState.ingredients, nutrients)

    /** Filter changes come from a deliberate tap, so they search without the typing debounce. */
    private fun setFilters(ingredients: List<String>, nutrients: NutrientFilter) {
        if (ingredients == currentState.ingredients && nutrients == currentState.nutrients) return
        updateState { copy(ingredients = ingredients, nutrients = nutrients) }
        search(debounce = false)
    }

    /**
     * Searches for the current criteria, cancelling a search for older criteria so only the latest
     * results reach the state. Skips the request when it would repeat one: the criteria already on
     * screen, or already being fetched. [force] (Retry) searches regardless.
     */
    private fun search(debounce: Boolean, force: Boolean = false) {
        val criteria = currentState.criteria
        if (!force && searchJob?.isActive == true && criteria == fetchingCriteria) return
        searchJob?.cancel()
        fetchingCriteria = null
        // E.g. "pasta" → "pastas" → back to "pasta" before the pause: the results are still right.
        if (!force && criteria == shownCriteria && currentState.error == null) return
        loadMoreJob?.cancel()

        if (criteria.isEmpty || currentState.isQueryTooShort) {
            shownCriteria = null
            updateState {
                copy(
                    recipes = emptyList(), matches = emptyMap(), totalResults = 0, nextOffset = 0, endReached = false,
                    isLoading = false, isLoadingMore = false, error = null, hasSearched = false,
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MILLIS)
            fetchingCriteria = criteria
            updateState { copy(isLoading = true, isLoadingMore = false, error = null) }

            val outcome = recipeRepository.searchRecipes(criteria, offset = 0, pageSize = PAGE_SIZE)
            fetchingCriteria = null
            when (outcome) {
                is Outcome.Success -> {
                    val page = outcome.value
                    shownCriteria = criteria
                    updateState {
                        copy(
                            recipes = page.recipes,
                            matches = page.matches,
                            totalResults = page.totalResults,
                            nextOffset = page.nextOffset,
                            endReached = page.endReached,
                            isLoading = false,
                            hasSearched = true,
                        )
                    }
                }
                is Outcome.Failure -> {
                    shownCriteria = null
                    updateState {
                        copy(
                            recipes = emptyList(), matches = emptyMap(), totalResults = 0, nextOffset = 0,
                            endReached = false, isLoading = false, error = outcome.error, hasSearched = true,
                        )
                    }
                }
            }
        }
    }

    private fun loadNextPage() {
        val state = currentState
        val canLoadMore = !state.isLoading && !state.isLoadingMore && !state.endReached &&
            state.recipes.isNotEmpty() && loadMoreJob?.isActive != true
        if (!canLoadMore) return

        val criteria: SearchCriteria = state.criteria
        loadMoreJob = viewModelScope.launch {
            updateState { copy(isLoadingMore = true) }

            when (val outcome = recipeRepository.searchRecipes(criteria, offset = state.nextOffset, pageSize = PAGE_SIZE)) {
                is Outcome.Success -> {
                    val page = outcome.value
                    updateState {
                        copy(
                            // Spoonacular can repeat items across pages; list keys must stay unique.
                            recipes = (recipes + page.recipes).distinctBy { it.id },
                            matches = matches + page.matches,
                            totalResults = page.totalResults,
                            nextOffset = page.nextOffset,
                            endReached = page.endReached,
                            isLoadingMore = false,
                        )
                    }
                }
                is Outcome.Failure -> {
                    updateState { copy(isLoadingMore = false) }
                    emitEffect(SearchEffect.ShowError(outcome.error))
                }
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 20
        /**
         * How long typing must pause before a search. Long enough not to search between the words of
         * "chicken curry"; the keyboard's search key ([SearchIntent.Submit]) skips the wait.
         */
        const val SEARCH_DEBOUNCE_MILLIS = 800L
    }
}
