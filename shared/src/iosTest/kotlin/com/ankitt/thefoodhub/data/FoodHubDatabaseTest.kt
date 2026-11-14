package com.ankitt.thefoodhub.data

import androidx.room3.Room
import app.cash.turbine.test
import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.data.local.FavoriteEntity
import com.ankitt.thefoodhub.data.local.FoodHubDatabase
import com.ankitt.thefoodhub.data.local.RecipeEntity
import com.ankitt.thefoodhub.data.local.StoredIngredient
import com.ankitt.thefoodhub.data.local.StoredStep
import com.ankitt.thefoodhub.data.local.buildFoodHubDatabase
import com.ankitt.thefoodhub.data.repository.FavoritesRepositoryImpl
import com.ankitt.thefoodhub.data.repository.RecipeRepositoryImpl
import com.ankitt.thefoodhub.testutil.FixedClock
import com.ankitt.thefoodhub.testutil.MockSpoonacular
import com.ankitt.thefoodhub.testutil.RECIPE_INFORMATION_JSON
import com.ankitt.thefoodhub.testutil.recipeSummary
import com.ankitt.thefoodhub.testutil.respondJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

        dao.observeIsFavorite(2).test {
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
}
