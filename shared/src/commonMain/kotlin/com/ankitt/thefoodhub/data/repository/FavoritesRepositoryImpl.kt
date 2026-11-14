package com.ankitt.thefoodhub.data.repository

import com.ankitt.thefoodhub.data.local.FavoriteDao
import com.ankitt.thefoodhub.data.toDomain
import com.ankitt.thefoodhub.data.toFavoriteEntity
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import com.ankitt.thefoodhub.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

internal class FavoritesRepositoryImpl(
    private val favoriteDao: FavoriteDao,
    private val clock: Clock,
) : FavoritesRepository {

    override fun observeFavorites(): Flow<List<RecipeSummary>> =
        favoriteDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeFavoriteIds(): Flow<Set<Int>> =
        favoriteDao.observeIds().map { it.toSet() }.distinctUntilChanged()

    override fun observeIsFavorite(recipeId: Int): Flow<Boolean> =
        favoriteDao.observeIsFavorite(recipeId).distinctUntilChanged()

    override suspend fun setFavorite(recipe: RecipeSummary, favorite: Boolean) {
        if (favorite) {
            favoriteDao.upsert(recipe.toFavoriteEntity(savedAtEpochMillis = clock.now().toEpochMilliseconds()))
        } else {
            favoriteDao.delete(recipe.id)
        }
    }

    override suspend fun toggleFavorite(recipe: RecipeSummary): Boolean {
        val newValue = !favoriteDao.isFavorite(recipe.id)
        setFavorite(recipe, newValue)
        return newValue
    }
}
