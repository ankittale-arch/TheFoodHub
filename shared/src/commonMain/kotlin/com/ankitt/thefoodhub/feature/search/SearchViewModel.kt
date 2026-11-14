package com.ankitt.thefoodhub.feature.search

import androidx.lifecycle.viewModelScope
import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.core.mvi.MviViewModel
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

    init {
        favoritesRepository.observeFavoriteIds()
            .onEach { ids -> updateState { copy(favoriteIds = ids) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> onQueryChanged(intent.query)
            SearchIntent.LoadNextPage -> loadNextPage()
            SearchIntent.Retry -> search(currentState.query, debounce = false)
            is SearchIntent.ToggleFavorite -> viewModelScope.launch {
                favoritesRepository.toggleFavorite(intent.recipe)
            }
        }
    }

    private fun onQueryChanged(query: String) {
        if (query == currentState.query) return
        val searchTermChanged = query.trim() != currentState.query.trim()
        updateState { copy(query = query) }
        // Typing a trailing space should not cost another API call.
        if (searchTermChanged) search(query, debounce = true)
    }

    /** Cancels any in-flight request, so only the latest query's results reach the state. */
    private fun search(query: String, debounce: Boolean) {
        searchJob?.cancel()
        loadMoreJob?.cancel()

        val term = query.trim()
        if (term.isEmpty()) {
            updateState {
                copy(
                    recipes = emptyList(), totalResults = 0, nextOffset = 0, endReached = false,
                    isLoading = false, isLoadingMore = false, error = null, hasSearched = false,
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MILLIS)
            updateState { copy(isLoading = true, isLoadingMore = false, error = null) }

            when (val outcome = recipeRepository.searchRecipes(term, offset = 0, pageSize = PAGE_SIZE)) {
                is Outcome.Success -> {
                    val page = outcome.value
                    updateState {
                        copy(
                            recipes = page.recipes,
                            totalResults = page.totalResults,
                            nextOffset = page.nextOffset,
                            endReached = page.endReached,
                            isLoading = false,
                            hasSearched = true,
                        )
                    }
                }
                is Outcome.Failure -> updateState {
                    copy(
                        recipes = emptyList(), totalResults = 0, nextOffset = 0, endReached = false,
                        isLoading = false, error = outcome.error, hasSearched = true,
                    )
                }
            }
        }
    }

    private fun loadNextPage() {
        val state = currentState
        val canLoadMore = !state.isLoading && !state.isLoadingMore && !state.endReached &&
            state.recipes.isNotEmpty() && loadMoreJob?.isActive != true
        if (!canLoadMore) return

        val term = state.query.trim()
        loadMoreJob = viewModelScope.launch {
            updateState { copy(isLoadingMore = true) }

            when (val outcome = recipeRepository.searchRecipes(term, offset = state.nextOffset, pageSize = PAGE_SIZE)) {
                is Outcome.Success -> {
                    val page = outcome.value
                    updateState {
                        copy(
                            // Spoonacular can repeat items across pages; list keys must stay unique.
                            recipes = (recipes + page.recipes).distinctBy { it.id },
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
        const val SEARCH_DEBOUNCE_MILLIS = 500L
    }
}
