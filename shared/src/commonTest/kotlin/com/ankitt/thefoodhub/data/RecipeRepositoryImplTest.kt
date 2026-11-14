package com.ankitt.thefoodhub.data

import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.data.repository.RecipeRepositoryImpl
import com.ankitt.thefoodhub.domain.model.InstructionStep
import com.ankitt.thefoodhub.testutil.FakeRecipeDao
import com.ankitt.thefoodhub.testutil.FixedClock
import com.ankitt.thefoodhub.domain.model.PopularRecipe
import com.ankitt.thefoodhub.testutil.MockSpoonacular
import com.ankitt.thefoodhub.testutil.RANDOM_RECIPES_JSON
import com.ankitt.thefoodhub.testutil.RECIPE_INFORMATION_JSON
import com.ankitt.thefoodhub.testutil.SEARCH_RESPONSE_JSON
import com.ankitt.thefoodhub.testutil.respondJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeRepositoryImplTest {

    private val dao = FakeRecipeDao()
    private val clock = FixedClock(nowMillis = 1_700_000_000_000)

    private fun repository(mock: MockSpoonacular) = RecipeRepositoryImpl(mock.api, dao, clock)

    @Test
    fun searchMapsToDomainPage() = runTest {
        val repo = repository(MockSpoonacular { respondJson(SEARCH_RESPONSE_JSON) })

        val page = assertIs<Outcome.Success<com.ankitt.thefoodhub.domain.model.RecipePage>>(
            repo.searchRecipes("pasta", offset = 0, pageSize = 2),
        ).value

        assertEquals(2, page.nextOffset)
        assertEquals(86, page.totalResults)
        assertFalse(page.endReached)
        assertNull(page.recipes[1].imageUrl)
    }

    @Test
    fun randomRecipesMapToCardsWithoutDuplicates() = runTest {
        val repo = repository(MockSpoonacular { respondJson(RANDOM_RECIPES_JSON) })

        val recipes = assertIs<Outcome.Success<List<PopularRecipe>>>(repo.getRandomRecipes(count = 3)).value

        assertEquals(
            listOf(
                PopularRecipe(
                    id = 1001,
                    title = "Leek Pasta with Miso Sauce",
                    imageUrl = "https://img.spoonacular.com/recipes/1001-556x370.jpg",
                    readyInMinutes = 25,
                    likes = 385,
                    rating = 4.8,
                ),
                // Missing stats stay null instead of becoming 0, and a blank image becomes null.
                PopularRecipe(id = 1002, title = "Plain Toast", imageUrl = null, readyInMinutes = null, likes = null, rating = null),
            ),
            recipes,
        )
    }

    @Test
    fun randomRecipesAreCachedSoOpeningOneCostsNoExtraCall() = runTest {
        val mock = MockSpoonacular { respondJson(RANDOM_RECIPES_JSON) }
        val repo = repository(mock)
        repo.getRandomRecipes(count = 3)

        assertEquals(Outcome.Success(Unit), repo.refreshRecipe(1001))

        assertEquals(1, mock.requests.size, "the detail screen must be served from the cache")
        val cached = assertNotNull(repo.observeRecipe(1001).first())
        assertEquals("Leek Pasta with Miso Sauce", cached.title)
        assertEquals(listOf("1 leek"), cached.ingredients.map { it.original })
        assertEquals(clock.nowMillis, dao.recipes.value.getValue(1001).cachedAtEpochMillis)
    }

    @Test
    fun randomRecipesClampCountToSpoonacularLimits() = runTest {
        val mock = MockSpoonacular { respondJson(RANDOM_RECIPES_JSON) }
        val repo = repository(mock)

        repo.getRandomRecipes(count = 500)
        repo.getRandomRecipes(count = 0)

        assertEquals(listOf("100", "1"), mock.requests.map { it.url.parameters["number"] })
    }

    @Test
    fun randomRecipesFailureCachesNothing() = runTest {
        val repo = repository(MockSpoonacular { respondJson("{}", HttpStatusCode.PaymentRequired) })

        assertEquals(Outcome.Failure(DataError.QuotaExceeded), repo.getRandomRecipes(count = 10))
        assertTrue(dao.recipes.value.isEmpty())
    }

    @Test
    fun refreshFetchesMapsAndCachesTheRecipe() = runTest {
        val mock = MockSpoonacular { respondJson(RECIPE_INFORMATION_JSON) }
        val repo = repository(mock)

        assertEquals(Outcome.Success(Unit), repo.refreshRecipe(716429))

        assertTrue(mock.requests.single().url.encodedPath.endsWith("/recipes/716429/information"))
        assertEquals("false", mock.requests.single().url.parameters["includeNutrition"])
        assertEquals(clock.nowMillis, dao.recipes.value.getValue(716429).cachedAtEpochMillis)

        val recipe = assertNotNull(repo.observeRecipe(716429).first())
        assertEquals("Pasta with Garlic", recipe.title)
        assertEquals(19, recipe.healthScore)
        // Blank "original" falls back to the name, and entries with neither are dropped.
        assertEquals(listOf("1 tbsp butter", "salt"), recipe.ingredients.map { it.original })
        assertEquals("https://img.spoonacular.com/ingredients_100x100/butter-sliced.jpg", recipe.ingredients[0].imageUrl)
        assertNull(recipe.ingredients[1].imageUrl)
        // Steps from multiple instruction groups are renumbered and trimmed.
        assertEquals(
            listOf(InstructionStep(1, "Boil the pasta."), InstructionStep(2, "Drain."), InstructionStep(3, "Melt the butter.")),
            recipe.steps,
        )
    }

    @Test
    fun refreshSkipsTheNetworkWhenCached() = runTest {
        val mock = MockSpoonacular { respondJson(RECIPE_INFORMATION_JSON) }
        val repo = repository(mock)
        repo.refreshRecipe(716429)

        assertEquals(Outcome.Success(Unit), repo.refreshRecipe(716429))

        assertEquals(1, mock.requests.size)
    }

    @Test
    fun forcedRefreshCallsTheNetworkEvenWhenCached() = runTest {
        val mock = MockSpoonacular { respondJson(RECIPE_INFORMATION_JSON) }
        val repo = repository(mock)
        repo.refreshRecipe(716429)

        repo.refreshRecipe(716429, force = true)

        assertEquals(2, mock.requests.size)
    }

    @Test
    fun refreshFailureStoresNothing() = runTest {
        val repo = repository(MockSpoonacular { respondJson("{}", HttpStatusCode.PaymentRequired) })

        assertEquals(Outcome.Failure(DataError.QuotaExceeded), repo.refreshRecipe(716429))
        assertTrue(dao.recipes.value.isEmpty())
        assertNull(repo.observeRecipe(716429).first())
    }
}
