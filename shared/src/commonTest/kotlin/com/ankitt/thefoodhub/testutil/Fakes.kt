package com.ankitt.thefoodhub.testutil

import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.data.local.FavoriteDao
import com.ankitt.thefoodhub.data.local.FavoriteEntity
import com.ankitt.thefoodhub.data.local.IngredientDensityEntity
import com.ankitt.thefoodhub.data.local.IngredientSubstitutesEntity
import com.ankitt.thefoodhub.data.local.KitchenDao
import com.ankitt.thefoodhub.data.local.RecipeDao
import com.ankitt.thefoodhub.data.local.RecipeEntity
import com.ankitt.thefoodhub.data.local.WinePairingDao
import com.ankitt.thefoodhub.data.local.WinePairingEntity
import com.ankitt.thefoodhub.domain.model.IngredientSubstitutes
import com.ankitt.thefoodhub.domain.model.PopularRecipe
import com.ankitt.thefoodhub.domain.model.RecipeDetail
import com.ankitt.thefoodhub.domain.model.RecipePage
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import com.ankitt.thefoodhub.domain.model.SearchCriteria
import com.ankitt.thefoodhub.domain.model.WinePairing
import com.ankitt.thefoodhub.domain.repository.FavoritesRepository
import com.ankitt.thefoodhub.domain.repository.KitchenRepository
import com.ankitt.thefoodhub.domain.repository.RecipeRepository
import com.ankitt.thefoodhub.domain.repository.WinePairingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

data class RandomCall(val count: Int, val includeTags: List<String>, val excludeTags: List<String>)

data class SearchCall(val criteria: SearchCriteria, val offset: Int, val pageSize: Int) {
    val query: String get() = criteria.query
}

class FakeRecipeRepository : RecipeRepository {
    val searchCalls = mutableListOf<SearchCall>()
    var onSearch: suspend (SearchCall) -> Outcome<RecipePage> = { Outcome.Success(recipePage(1..0)) }

    val cached = MutableStateFlow<Map<Int, RecipeDetail>>(emptyMap())
    val refreshCalls = mutableListOf<Int>()
    var onRefresh: suspend (Int) -> Outcome<Unit> = { Outcome.Success(Unit) }

    override suspend fun searchRecipes(criteria: SearchCriteria, offset: Int, pageSize: Int): Outcome<RecipePage> {
        val call = SearchCall(criteria, offset, pageSize)
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

class FakeWinePairingRepository : WinePairingRepository {
    val calls = mutableListOf<String>()
    var onGetPairing: suspend (String) -> Outcome<WinePairing?> = { food -> Outcome.Success(winePairing(food)) }

    override suspend fun getPairing(food: String): Outcome<WinePairing?> {
        calls += food
        return onGetPairing(food)
    }
}

class FakeWinePairingDao : WinePairingDao {
    val rows = mutableMapOf<String, WinePairingEntity>()

    override suspend fun get(food: String): WinePairingEntity? = rows[food]

    override suspend fun upsert(pairing: WinePairingEntity) {
        rows[pairing.food] = pairing
    }
}

class FakeKitchenRepository : KitchenRepository {
    /** Densities saved on the "device"; a null value is a saved "can't convert". */
    val saved = mutableMapOf<String, Double?>()
    val savedDensityIngredients = MutableStateFlow<List<String>>(emptyList())
    val savedSubstituteIngredients = MutableStateFlow<List<String>>(emptyList())

    val densityCalls = mutableListOf<String>()
    var onGetGramsPerCup: suspend (String) -> Outcome<Double?> = { Outcome.Success(125.0) }

    val substituteCalls = mutableListOf<String>()
    var onGetSubstitutes: suspend (String) -> Outcome<IngredientSubstitutes?> = { ingredient ->
        Outcome.Success(IngredientSubstitutes(ingredient, listOf("1 cup = 1 cup margarine")))
    }

    override suspend fun savedGramsPerCup(ingredient: String): Double? = saved[ingredient]

    override suspend fun getGramsPerCup(ingredient: String): Outcome<Double?> {
        if (ingredient in saved) return Outcome.Success(saved[ingredient])
        densityCalls += ingredient
        return onGetGramsPerCup(ingredient)
    }

    override suspend fun getSubstitutes(ingredient: String): Outcome<IngredientSubstitutes?> {
        substituteCalls += ingredient
        return onGetSubstitutes(ingredient)
    }

    override fun observeSavedDensityIngredients(): Flow<List<String>> = savedDensityIngredients

    override fun observeSavedSubstituteIngredients(): Flow<List<String>> = savedSubstituteIngredients
}

class FakeKitchenDao : KitchenDao {
    val densities = MutableStateFlow<Map<String, IngredientDensityEntity>>(emptyMap())
    val substitutes = MutableStateFlow<Map<String, IngredientSubstitutesEntity>>(emptyMap())

    override suspend fun getDensity(ingredient: String): IngredientDensityEntity? = densities.value[ingredient]

    override suspend fun upsertDensity(density: IngredientDensityEntity) =
        densities.update { it + (density.ingredient to density) }

    override fun observeDensityIngredients(): Flow<List<String>> = densities.map { map ->
        map.values.filter { it.gramsPerCup != null }.sortedByDescending { it.cachedAtEpochMillis }.map { it.ingredient }
    }

    override suspend fun getSubstitutes(ingredient: String): IngredientSubstitutesEntity? = substitutes.value[ingredient]

    override suspend fun upsertSubstitutes(substitutes: IngredientSubstitutesEntity) =
        this.substitutes.update { it + (substitutes.ingredient to substitutes) }

    override fun observeSubstituteIngredients(): Flow<List<String>> = substitutes.map { map ->
        map.values.filter { it.substitutes.isNotEmpty() }.sortedByDescending { it.cachedAtEpochMillis }.map { it.ingredient }
    }
}
