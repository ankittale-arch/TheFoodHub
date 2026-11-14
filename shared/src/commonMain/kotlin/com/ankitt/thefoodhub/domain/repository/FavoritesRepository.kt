package com.ankitt.thefoodhub.domain.repository

import com.ankitt.thefoodhub.domain.model.RecipeSummary
import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {

    /** Most recently saved first. */
    fun observeFavorites(): Flow<List<RecipeSummary>>

    fun observeFavoriteIds(): Flow<Set<Int>>

    fun observeIsFavorite(recipeId: Int): Flow<Boolean>

    suspend fun setFavorite(recipe: RecipeSummary, favorite: Boolean)

    /** Returns whether the recipe is a favorite after toggling. */
    suspend fun toggleFavorite(recipe: RecipeSummary): Boolean
}
