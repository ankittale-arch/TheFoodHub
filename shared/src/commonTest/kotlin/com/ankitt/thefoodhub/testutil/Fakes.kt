package com.ankitt.thefoodhub.testutil

import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.data.local.FavoriteDao
import com.ankitt.thefoodhub.data.local.FavoriteEntity
import com.ankitt.thefoodhub.data.local.RecipeDao
import com.ankitt.thefoodhub.data.local.RecipeEntity
import com.ankitt.thefoodhub.domain.model.PopularRecipe
import com.ankitt.thefoodhub.domain.model.RecipeDetail
import com.ankitt.thefoodhub.domain.model.RecipePage
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import com.ankitt.thefoodhub.domain.repository.FavoritesRepository
import com.ankitt.thefoodhub.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

data class RandomCall(val count: Int, val includeTags: List<String>, val excludeTags: List<String>)

data class SearchCall(val query: String, val offset: Int, val pageSize: Int)

class FakeRecipeRepository : RecipeRepository {
    val searchCalls = mutableListOf<SearchCall>()
    var onSearch: suspend (SearchCall) -> Outcome<RecipePage> = { Outcome.Success(recipePage(1..0)) }

    val cached = MutableStateFlow<Map<Int, RecipeDetail>>(emptyMap())
    val refreshCalls = mutableListOf<Int>()
    var onRefresh: suspend (Int) -> Outcome<Unit> = { Outcome.Success(Unit) }

    override suspend fun searchRecipes(query: String, offset: Int, pageSize: Int): Outcome<RecipePage> {
        val call = SearchCall(query, offset, pageSize)
        searchCalls += call
        return onSearch(call)
    }

    val randomCalls = mutableListOf<RandomCall>()
    var onRandom: suspend (RandomCall) -> Outcome<List<PopularRecipe>> = { Outcome.Success(emptyList()) }

    override suspend fun getRandomRecipes(
        count: Int,
        includeTags: List<String>,
        excludeTags: List<String>,
    ): Outcome<List<PopularRecipe>> {
        val call = RandomCall(count, includeTags, excludeTags)
        randomCalls += call
        return onRandom(call)
    }

    override fun observeRecipe(id: Int): Flow<RecipeDetail?> = cached.map { it[id] }.distinctUntilChanged()

    override suspend fun refreshRecipe(id: Int, force: Boolean): Outcome<Unit> {
        refreshCalls += id
        return onRefresh(id)
    }
}

class FakeFavoritesRepository(initial: List<RecipeSummary> = emptyList()) : FavoritesRepository {
    val favorites = MutableStateFlow(initial)

    override fun observeFavorites(): Flow<List<RecipeSummary>> = favorites

    override fun observeFavoriteIds(): Flow<Set<Int>> = favorites.map { list -> list.map { it.id }.toSet() }

    override fun observeIsFavorite(recipeId: Int): Flow<Boolean> =
        favorites.map { list -> list.any { it.id == recipeId } }.distinctUntilChanged()

    override suspend fun setFavorite(recipe: RecipeSummary, favorite: Boolean) {
        favorites.update { list ->
            val without = list.filterNot { it.id == recipe.id }
            if (favorite) listOf(recipe) + without else without
        }
    }

    override suspend fun toggleFavorite(recipe: RecipeSummary): Boolean {
        val newValue = favorites.value.none { it.id == recipe.id }
        setFavorite(recipe, newValue)
        return newValue
    }
}

class FakeRecipeDao : RecipeDao {
    val recipes = MutableStateFlow<Map<Int, RecipeEntity>>(emptyMap())

    override fun observe(id: Int): Flow<RecipeEntity?> = recipes.map { it[id] }

    override suspend fun exists(id: Int): Boolean = id in recipes.value

    override suspend fun upsert(recipe: RecipeEntity) = recipes.update { it + (recipe.id to recipe) }
}

class FakeFavoriteDao : FavoriteDao {
    val favorites = MutableStateFlow<Map<Int, FavoriteEntity>>(emptyMap())

    override fun observeAll(): Flow<List<FavoriteEntity>> =
        favorites.map { map -> map.values.sortedByDescending { it.savedAtEpochMillis } }

    override fun observeIds(): Flow<List<Int>> = favorites.map { it.keys.toList() }

    override fun observeIsFavorite(recipeId: Int): Flow<Boolean> = favorites.map { recipeId in it }

    override suspend fun isFavorite(recipeId: Int): Boolean = recipeId in favorites.value

    override suspend fun upsert(favorite: FavoriteEntity) = favorites.update { it + (favorite.recipeId to favorite) }

    override suspend fun delete(recipeId: Int) = favorites.update { it - recipeId }
}
