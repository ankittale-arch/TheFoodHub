package com.ankitt.thefoodhub.data

import androidx.room3.Room
import app.cash.turbine.test
import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.domain.model.Cookbook
import com.ankitt.thefoodhub.domain.model.CookbookNameError
import com.ankitt.thefoodhub.domain.model.CookbookNameResult
import com.ankitt.thefoodhub.domain.model.RecipeNotes
import com.ankitt.thefoodhub.data.local.FavoriteEntity
import com.ankitt.thefoodhub.data.local.FoodHubDatabase
import com.ankitt.thefoodhub.data.local.IngredientDensityEntity
import com.ankitt.thefoodhub.data.local.IngredientSubstitutesEntity
import com.ankitt.thefoodhub.data.local.RecipeEntity
import com.ankitt.thefoodhub.data.local.StoredIngredient
import com.ankitt.thefoodhub.data.local.StoredStep
import com.ankitt.thefoodhub.data.local.StoredWineBottle
import com.ankitt.thefoodhub.data.local.WinePairingEntity
import com.ankitt.thefoodhub.data.local.buildFoodHubDatabase
import com.ankitt.thefoodhub.data.repository.CookbookRepositoryImpl
import com.ankitt.thefoodhub.data.repository.FavoritesRepositoryImpl
import com.ankitt.thefoodhub.data.repository.RecipeNotesRepositoryImpl
import com.ankitt.thefoodhub.data.repository.RecipeRepositoryImpl
import com.ankitt.thefoodhub.testutil.FixedClock
import com.ankitt.thefoodhub.testutil.MockSpoonacular
import com.ankitt.thefoodhub.testutil.RECIPE_INFORMATION_JSON
import com.ankitt.thefoodhub.testutil.recipeSummary
import com.ankitt.thefoodhub.testutil.respondJson
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the real Room 3 schema, generated DAOs, and type converters against bundled SQLite.
 * The JVM tests use fake DAOs; this is where the actual SQL is checked.
 */
class FoodHubDatabaseTest {

    private val db: FoodHubDatabase = Room.inMemoryDatabaseBuilder<FoodHubDatabase>().buildFoodHubDatabase()

    @AfterTest
    fun closeDb() = db.close()

    @Test
    fun winePairingsRoundTripWithAndWithoutABottle() = runTest {
        val dao = db.winePairingDao()
        val withBottle = WinePairingEntity(
            food = "pork",
            wines = listOf("pinot noir", "malbec"),
            description = "Pork works well with red wine.",
            suggestedBottle = StoredWineBottle("Vinum Pinot Noir", "$18.99", null, 4.6, 23, null),
            cachedAtEpochMillis = 1,
        )
        val noPairing = WinePairingEntity("tofu", emptyList(), "", suggestedBottle = null, cachedAtEpochMillis = 2)

        dao.upsert(withBottle)
        dao.upsert(noPairing)

        assertEquals(withBottle, dao.get("pork"))
        assertEquals(noPairing, dao.get("tofu"))
        assertNull(dao.get("beef"))
    }

    @Test
    fun kitchenLookupsRoundTripAndOnlyUsefulOnesAreListed() = runTest {
        val dao = db.kitchenDao()
        dao.upsertDensity(IngredientDensityEntity("flour", 125.0, cachedAtEpochMillis = 1))
        dao.upsertDensity(IngredientDensityEntity("sugar", 200.0, cachedAtEpochMillis = 3))
        dao.upsertDensity(IngredientDensityEntity("air", null, cachedAtEpochMillis = 2))
        dao.upsertSubstitutes(IngredientSubstitutesEntity("butter", listOf("1 cup = 1 cup margarine"), cachedAtEpochMillis = 1))
        dao.upsertSubstitutes(IngredientSubstitutesEntity("saffron", emptyList(), cachedAtEpochMillis = 2))

        assertEquals(125.0, dao.getDensity("flour")?.gramsPerCup)
        assertNull(assertNotNull(dao.getDensity("air")).gramsPerCup)
        assertNull(dao.getDensity("rice"))
        assertEquals(emptyList(), dao.getSubstitutes("saffron")?.substitutes)

        // Unknown answers are saved but not offered as suggestions; newest first.
        assertEquals(listOf("sugar", "flour"), dao.observeDensityIngredients().first())
        assertEquals(listOf("butter"), dao.observeSubstituteIngredients().first())
    }

    private fun recipeEntity(id: Int) = RecipeEntity(
        id = id,
        title = "Soup $id",
        imageUrl = null,
        readyInMinutes = 20,
        servings = null,
        healthScore = 7,
        sourceName = null,
        sourceUrl = "https://example.com/$id",
        summaryHtml = "<b>hot</b>",
        diets = listOf("vegan", "gluten free"),
        cuisines = emptyList(),
        dishTypes = listOf("soup"),
        ingredients = listOf(StoredIngredient("salt", "1 tsp \"sea\" salt", null)),
        steps = listOf(StoredStep(1, "Boil."), StoredStep(2, "Serve.")),
        cachedAtEpochMillis = 42,
    )

    @Test
    fun recipeRoundTripsThroughConverters() = runTest {
        val dao = db.recipeDao()
        assertFalse(dao.exists(1))

        dao.upsert(recipeEntity(1))

        assertTrue(dao.exists(1))
        assertEquals(recipeEntity(1), dao.observe(1).first())
        assertNull(dao.observe(2).first())
    }

    @Test
    fun upsertReplacesTheCachedRecipe() = runTest {
        val dao = db.recipeDao()
        dao.upsert(recipeEntity(1))

        dao.upsert(recipeEntity(1).copy(title = "Better soup"))

        assertEquals("Better soup", dao.observe(1).first()?.title)
    }

    @Test
    fun favoritesAreOrderedNewestFirstAndObservable() = runTest {
        val dao = db.favoriteDao()

        // Room re-runs the query after every write to the table, so the raw flow can repeat a value
        // (writing recipe 1 re-emits "false" for recipe 2). FavoritesRepositoryImpl drops repeats the same way.
        dao.observeIsFavorite(2).distinctUntilChanged().test {
            assertFalse(awaitItem())

            dao.upsert(FavoriteEntity(recipeId = 1, title = "A", imageUrl = null, savedAtEpochMillis = 10))
            dao.upsert(FavoriteEntity(recipeId = 2, title = "B", imageUrl = null, savedAtEpochMillis = 20))
            assertTrue(awaitItem())

            dao.delete(2)
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        dao.upsert(FavoriteEntity(recipeId = 3, title = "C", imageUrl = null, savedAtEpochMillis = 30))

        assertEquals(listOf(3, 1), dao.observeAll().first().map { it.recipeId })
        assertEquals(setOf(1, 3), dao.observeIds().first().toSet())
    }

    @Test
    fun repositoriesWorkEndToEndAgainstTheRealDatabase() = runTest {
        val mock = MockSpoonacular { respondJson(RECIPE_INFORMATION_JSON) }
        val recipes = RecipeRepositoryImpl(mock.api, db.recipeDao(), FixedClock())
        val favorites = FavoritesRepositoryImpl(db.favoriteDao(), FixedClock())

        assertEquals(Outcome.Success(Unit), recipes.refreshRecipe(716429))
        assertEquals(Outcome.Success(Unit), recipes.refreshRecipe(716429))
        assertEquals(1, mock.requests.size, "second refresh must be served from the cache")

        val recipe = assertNotNull(recipes.observeRecipe(716429).first())
        assertEquals(3, recipe.steps.size)

        assertTrue(favorites.toggleFavorite(recipeSummary(716429)))
        assertEquals(setOf(716429), favorites.observeFavoriteIds().first())
    }

    private val favoritesRepo = FavoritesRepositoryImpl(db.favoriteDao(), FixedClock())
    private val clock = FixedClock()
    private val cookbooks = CookbookRepositoryImpl(db.cookbookDao(), clock)

    private suspend fun create(name: String): Long = assertIs<CookbookNameResult.Saved>(cookbooks.createCookbook(name)).cookbookId

    @Test
    fun cookbooksAreSortedByNameWithCountsAndNamesAreUniqueIgnoringCase() = runTest {
        favoritesRepo.setFavorite(recipeSummary(1), favorite = true)
        val weeknight = create("quick Weeknight")
        val desserts = create("Desserts")
        cookbooks.addRecipe(1, setOf(desserts))

        assertEquals(
            listOf(Cookbook(desserts, "Desserts", 1), Cookbook(weeknight, "quick Weeknight", 0)),
            cookbooks.observeCookbooks().first(),
        )
        assertEquals(CookbookNameResult.Rejected(CookbookNameError.Taken), cookbooks.createCookbook(" DESSERTS "))
        assertEquals(CookbookNameResult.Rejected(CookbookNameError.Taken), cookbooks.renameCookbook(weeknight, "desserts"))
        assertEquals(CookbookNameResult.Rejected(CookbookNameError.Blank), cookbooks.createCookbook("  "))
        assertEquals(
            CookbookNameResult.Rejected(CookbookNameError.TooLong),
            cookbooks.createCookbook("x".repeat(Cookbook.MAX_NAME_LENGTH + 1)),
        )
        // Changing only the case of its own name is fine.
        assertEquals(CookbookNameResult.Saved(weeknight), cookbooks.renameCookbook(weeknight, "Quick Weeknight"))
        assertEquals(listOf("Desserts", "Quick Weeknight"), cookbooks.observeCookbooks().first().map { it.name })
    }

    @Test
    fun aRecipeCanBeInManyCookbooksNewestAddedFirst() = runTest {
        (1..3).forEach { favoritesRepo.setFavorite(recipeSummary(it), favorite = true) }
        val desserts = create("Desserts")
        val mealPrep = create("Meal Prep")

        clock.nowMillis = 1
        cookbooks.addRecipe(1, setOf(desserts, mealPrep))
        clock.nowMillis = 2
        cookbooks.addRecipe(3, setOf(desserts))
        // Adding again is ignored and keeps the original position.
        clock.nowMillis = 3
        cookbooks.addRecipe(1, setOf(desserts))

        assertEquals(listOf(3, 1), cookbooks.observeRecipes(desserts).first().map { it.id })
        assertEquals(listOf(1), cookbooks.observeRecipes(mealPrep).first().map { it.id })
        assertEquals(setOf(desserts, mealPrep), cookbooks.cookbookIds(1))

        cookbooks.removeRecipe(1, desserts)
        assertEquals(setOf(mealPrep), cookbooks.observeCookbookIds(1).first())
    }

    @Test
    fun onlyFavoritesInExistingCookbooksCanBeFiled() = runTest {
        favoritesRepo.setFavorite(recipeSummary(1), favorite = true)
        val desserts = create("Desserts")

        // Neither would satisfy the foreign keys; both are skipped instead of throwing.
        cookbooks.addRecipe(99, setOf(desserts))
        cookbooks.addRecipe(1, setOf(desserts, 12345L))

        assertEquals(setOf(desserts), cookbooks.cookbookIds(1))
        assertEquals(emptySet(), cookbooks.cookbookIds(99))
    }

    @Test
    fun deletesCascadeThroughTheJunctionTable() = runTest {
        favoritesRepo.setFavorite(recipeSummary(1), favorite = true)
        favoritesRepo.setFavorite(recipeSummary(2), favorite = true)
        val desserts = create("Desserts")
        val mealPrep = create("Meal Prep")
        cookbooks.addRecipe(1, setOf(desserts, mealPrep))
        cookbooks.addRecipe(2, setOf(desserts))

        // Unfavoriting takes the recipe out of every cookbook.
        favoritesRepo.setFavorite(recipeSummary(1), favorite = false)
        assertEquals(emptySet(), cookbooks.cookbookIds(1))
        assertEquals(listOf(1, 0), cookbooks.observeCookbooks().first().map { it.recipeCount })

        // Deleting a cookbook keeps its recipes in favorites.
        cookbooks.deleteCookbook(desserts)
        assertEquals(listOf("Meal Prep"), cookbooks.observeCookbooks().first().map { it.name })
        assertEquals(emptySet(), cookbooks.cookbookIds(2))
        assertEquals(setOf(2), favoritesRepo.observeFavoriteIds().first())
    }

    @Test
    fun resavingAFavoriteKeepsItsCookbooks() = runTest {
        favoritesRepo.setFavorite(recipeSummary(1), favorite = true)
        val desserts = create("Desserts")
        cookbooks.addRecipe(1, setOf(desserts))

        // @Upsert updates the existing row instead of replacing it, so nothing cascades.
        favoritesRepo.setFavorite(recipeSummary(1).copy(title = "Renamed"), favorite = true)

        assertEquals(setOf(desserts), cookbooks.cookbookIds(1))
        assertEquals(listOf("Renamed"), cookbooks.observeRecipes(desserts).first().map { it.title })
    }

    private val notesRepo = RecipeNotesRepositoryImpl(db.recipeNotesDao(), FixedClock())

    @Test
    fun notesAndTweaksRoundTripPerRecipeAndBlankDeletes() = runTest {
        notesRepo.saveNote(1, "  Used half the sugar, baked 5 mins less\n")
        notesRepo.saveTweak(1, "1 cup sugar", "Used half")
        notesRepo.saveTweak(1, "1 tsp salt", "Pinch")
        notesRepo.saveTweak(2, "1 cup sugar", "Honey instead")

        assertEquals(
            RecipeNotes("Used half the sugar, baked 5 mins less", mapOf("1 cup sugar" to "Used half", "1 tsp salt" to "Pinch")),
            notesRepo.observeNotes(1).first(),
        )
        assertEquals(mapOf("1 cup sugar" to "Honey instead"), notesRepo.observeNotes(2).first().tweaks)

        notesRepo.saveTweak(1, "1 cup sugar", "A third")
        notesRepo.saveTweak(1, "1 tsp salt", " ")
        notesRepo.saveNote(1, "")

        assertEquals(RecipeNotes(tweaks = mapOf("1 cup sugar" to "A third")), notesRepo.observeNotes(1).first())
        assertEquals(RecipeNotes.EMPTY, notesRepo.observeNotes(3).first())
    }

    @Test
    fun notesSurviveRemovingTheFavorite() = runTest {
        favoritesRepo.setFavorite(recipeSummary(1), favorite = true)
        notesRepo.saveNote(1, "Baked 5 mins less")
        notesRepo.saveTweak(1, "2 eggs", "Flax eggs")

        favoritesRepo.setFavorite(recipeSummary(1), favorite = false)

        assertEquals(RecipeNotes("Baked 5 mins less", mapOf("2 eggs" to "Flax eggs")), notesRepo.observeNotes(1).first())
    }

    @Test
    fun overlongTextIsCappedAtTheLimit() = runTest {
        notesRepo.saveNote(1, "a".repeat(RecipeNotes.MAX_NOTE_LENGTH + 10))
        notesRepo.saveTweak(1, "salt", "b".repeat(RecipeNotes.MAX_TWEAK_LENGTH + 10))

        with(notesRepo.observeNotes(1).first()) {
            assertEquals(RecipeNotes.MAX_NOTE_LENGTH, note.length)
            assertEquals(RecipeNotes.MAX_TWEAK_LENGTH, tweakFor("salt")?.length)
        }
    }
}
