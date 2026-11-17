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
import com.ankitt.thefoodhub.domain.model.Cookbook
import com.ankitt.thefoodhub.domain.model.CookbookNameError
import com.ankitt.thefoodhub.domain.model.CookbookNameResult
import com.ankitt.thefoodhub.domain.model.IngredientSubstitutes
import com.ankitt.thefoodhub.domain.model.PopularRecipe
import com.ankitt.thefoodhub.domain.model.RecipeDetail
import com.ankitt.thefoodhub.domain.model.RecipeNotes
import com.ankitt.thefoodhub.domain.model.RecipePage
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import com.ankitt.thefoodhub.domain.model.SearchCriteria
import com.ankitt.thefoodhub.domain.model.WinePairing
import com.ankitt.thefoodhub.domain.repository.CookbookRepository
import com.ankitt.thefoodhub.domain.repository.FavoritesRepository
import com.ankitt.thefoodhub.domain.repository.KitchenRepository
import com.ankitt.thefoodhub.domain.repository.RecipeNotesRepository
import com.ankitt.thefoodhub.domain.repository.RecipeRepository
import com.ankitt.thefoodhub.domain.repository.WinePairingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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

/**
 * In-memory cookbooks over [favorites], with the same rules as the Room schema: only favorites
 * can be filed, names are unique ignoring case, and removing a favorite drops its links.
 */
class FakeCookbookRepository(private val favorites: FakeFavoritesRepository) : CookbookRepository {
    /** Cookbook id to name, in creation order. */
    val names = MutableStateFlow<Map<Long, String>>(emptyMap())

    /** Cookbook id to recipe ids, most recently added first. */
    private val links = MutableStateFlow<Map<Long, List<Int>>>(emptyMap())
    private var nextId = 1L

    /** Links whose recipe is no longer a favorite are dropped, like the foreign key cascade. */
    private val liveLinks = combine(links, favorites.favorites) { links, favs ->
        val ids = favs.map { it.id }.toSet()
        links.mapValues { (_, recipes) -> recipes.filter { it in ids } }
    }

    override fun observeCookbooks(): Flow<List<Cookbook>> = combine(names, liveLinks) { names, links ->
        names.map { (id, name) -> Cookbook(id, name, links[id].orEmpty().size) }.sortedBy { it.name.lowercase() }
    }

    override fun observeRecipes(cookbookId: Long): Flow<List<RecipeSummary>> =
        combine(liveLinks, favorites.favorites) { links, favs ->
            links[cookbookId].orEmpty().mapNotNull { id -> favs.firstOrNull { it.id == id } }
        }

    override fun observeCookbookIds(recipeId: Int): Flow<Set<Long>> =
        liveLinks.map { links -> links.filterValues { recipeId in it }.keys }.distinctUntilChanged()

    override suspend fun cookbookIds(recipeId: Int): Set<Long> = observeCookbookIds(recipeId).first()

    override suspend fun createCookbook(name: String): CookbookNameResult {
        val cleaned = Cookbook.normalizeName(name)
        validate(cleaned, exceptId = 0)?.let { return CookbookNameResult.Rejected(it) }
        val id = nextId++
        names.update { it + (id to cleaned) }
        return CookbookNameResult.Saved(id)
    }

    override suspend fun renameCookbook(cookbookId: Long, name: String): CookbookNameResult {
        val cleaned = Cookbook.normalizeName(name)
        validate(cleaned, exceptId = cookbookId)?.let { return CookbookNameResult.Rejected(it) }
        names.update { it + (cookbookId to cleaned) }
        return CookbookNameResult.Saved(cookbookId)
    }

    override suspend fun deleteCookbook(cookbookId: Long) {
        names.update { it - cookbookId }
        links.update { it - cookbookId }
    }

    override suspend fun addRecipe(recipeId: Int, cookbookIds: Set<Long>) {
        if (favorites.favorites.value.none { it.id == recipeId }) return
        links.update { current ->
            current + cookbookIds.filter { it in names.value }.associateWith { id ->
                val recipes = current[id].orEmpty()
                if (recipeId in recipes) recipes else listOf(recipeId) + recipes
            }
        }
    }

    override suspend fun removeRecipe(recipeId: Int, cookbookId: Long) {
        links.update { current -> current + (cookbookId to current[cookbookId].orEmpty().filterNot { it == recipeId }) }
    }

    private fun validate(name: String, exceptId: Long): CookbookNameError? = when {
        name.isEmpty() -> CookbookNameError.Blank
        name.length > Cookbook.MAX_NAME_LENGTH -> CookbookNameError.TooLong
        names.value.any { (id, existing) -> id != exceptId && existing.equals(name, ignoreCase = true) } -> CookbookNameError.Taken
        else -> null
    }
}

/** Notes per recipe id, trimmed like the real repository; blank text removes. */
class FakeRecipeNotesRepository : RecipeNotesRepository {
    val notes = MutableStateFlow<Map<Int, RecipeNotes>>(emptyMap())

    override fun observeNotes(recipeId: Int): Flow<RecipeNotes> =
        notes.map { it[recipeId] ?: RecipeNotes.EMPTY }.distinctUntilChanged()

    override suspend fun saveNote(recipeId: Int, note: String) = edit(recipeId) { copy(note = note.trim()) }

    override suspend fun saveTweak(recipeId: Int, ingredientLine: String, tweak: String) = edit(recipeId) {
        val cleaned = tweak.trim()
        copy(tweaks = if (cleaned.isEmpty()) tweaks - ingredientLine else tweaks + (ingredientLine to cleaned))
    }

    private fun edit(recipeId: Int, change: RecipeNotes.() -> RecipeNotes) =
        notes.update { it + (recipeId to (it[recipeId] ?: RecipeNotes.EMPTY).change()) }
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
