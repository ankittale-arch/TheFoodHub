package com.ankitt.thefoodhub.feature.detail

import androidx.lifecycle.viewModelScope
import com.ankitt.thefoodhub.core.errorOrNull
import com.ankitt.thefoodhub.core.mvi.MviViewModel
import com.ankitt.thefoodhub.domain.model.toSummary
import com.ankitt.thefoodhub.domain.repository.FavoritesRepository
import com.ankitt.thefoodhub.domain.repository.RecipeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Cache-first: renders from Room and only calls the network when the recipe is not cached yet. */
class RecipeDetailViewModel(
    private val recipeId: Int,
    private val recipeRepository: RecipeRepository,
    private val favoritesRepository: FavoritesRepository,
) : MviViewModel<RecipeDetailState, RecipeDetailIntent, RecipeDetailEffect>(RecipeDetailState(recipeId)) {

    private var refreshJob: Job? = null

    init {
        recipeRepository.observeRecipe(recipeId)
            .onEach { recipe -> updateState { copy(recipe = recipe) } }
            .launchIn(viewModelScope)
        favoritesRepository.observeIsFavorite(recipeId)
            .onEach { isFavorite -> updateState { copy(isFavorite = isFavorite) } }
            .launchIn(viewModelScope)
        refresh()
    }

    override fun onIntent(intent: RecipeDetailIntent) {
        when (intent) {
            RecipeDetailIntent.Retry -> refresh()
            RecipeDetailIntent.ToggleFavorite -> toggleFavorite()
        }
    }

    private fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            val outcome = recipeRepository.refreshRecipe(recipeId)
            updateState { copy(isLoading = false, error = outcome.errorOrNull) }
        }
    }

    private fun toggleFavorite() {
        val recipe = currentState.recipe ?: return
        viewModelScope.launch {
            val isFavorite = favoritesRepository.toggleFavorite(recipe.toSummary())
            emitEffect(RecipeDetailEffect.FavoriteChanged(isFavorite))
        }
    }
}
