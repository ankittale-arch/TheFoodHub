package com.ankitt.thefoodhub.feature.favorites

import androidx.lifecycle.viewModelScope
import com.ankitt.thefoodhub.core.mvi.MviViewModel
import com.ankitt.thefoodhub.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class FavoritesViewModel(
    private val favoritesRepository: FavoritesRepository,
) : MviViewModel<FavoritesState, FavoritesIntent, FavoritesEffect>(FavoritesState()) {

    init {
        favoritesRepository.observeFavorites()
            .onEach { favorites -> updateState { copy(favorites = favorites, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: FavoritesIntent) {
        when (intent) {
            is FavoritesIntent.Remove -> viewModelScope.launch {
                favoritesRepository.setFavorite(intent.recipe, favorite = false)
                emitEffect(FavoritesEffect.Removed(intent.recipe))
            }
            is FavoritesIntent.UndoRemove -> viewModelScope.launch {
                favoritesRepository.setFavorite(intent.recipe, favorite = true)
            }
        }
    }
}
