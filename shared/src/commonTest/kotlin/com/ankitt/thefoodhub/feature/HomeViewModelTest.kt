package com.ankitt.thefoodhub.feature

import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.feature.home.Diet
import com.ankitt.thefoodhub.feature.home.HomeCategory
import com.ankitt.thefoodhub.feature.home.HomeIntent
import com.ankitt.thefoodhub.feature.home.HomeViewModel
import com.ankitt.thefoodhub.feature.home.HomeViewModel.Companion.PAGE_COUNT
import com.ankitt.thefoodhub.feature.home.HomeViewModel.Companion.QUICK_FETCH_COUNT
import com.ankitt.thefoodhub.testutil.FakeRecipeRepository
import com.ankitt.thefoodhub.testutil.MainDispatcherTest
import com.ankitt.thefoodhub.testutil.RandomCall
import com.ankitt.thefoodhub.testutil.popularRecipe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeViewModelTest : MainDispatcherTest() {

    private val recipes = FakeRecipeRepository()

    private val untagged = listOf(
        popularRecipe(1, likes = 10),
        popularRecipe(2, likes = null),
        popularRecipe(3, likes = 300),
        popularRecipe(4, likes = 50),
    )

    private fun ids(viewModel: HomeViewModel) = viewModel.state.value.recipes.map { it.id }

    @Test
    fun loadsRandomRecipesForAllOnStart() = runTest {
        recipes.onRandom = { Outcome.Success(untagged) }
        val viewModel = HomeViewModel(recipes)
        assertTrue(viewModel.state.value.isLoading)

        advanceUntilIdle()

        assertEquals(listOf(RandomCall(PAGE_COUNT, emptyList(), emptyList())), recipes.randomCalls)
        with(viewModel.state.value) {
            assertEquals(HomeCategory.All, category)
            assertEquals(listOf(1, 2, 3, 4), recipes.map { it.id })
            assertFalse(isLoading)
            assertNull(error)
        }
    }

    @Test
    fun popularSortsTheSameRecipesByLikesWithoutAnotherCall() = runTest {
        recipes.onRandom = { Outcome.Success(untagged) }
        val viewModel = HomeViewModel(recipes)
        advanceUntilIdle()

        viewModel.onIntent(HomeIntent.SelectCategory(HomeCategory.Popular))
        advanceUntilIdle()

        assertEquals(listOf(3, 4, 1, 2), ids(viewModel), "most liked first, missing likes last")
        assertEquals(1, recipes.randomCalls.size)

        viewModel.onIntent(HomeIntent.SelectCategory(HomeCategory.All))
        assertEquals(listOf(1, 2, 3, 4), ids(viewModel), "All keeps the API order")
    }

    @Test
    fun quickAndEasyFetchesExtraAndKeepsRecipesReadyInThirtyMinutes() = runTest {
        recipes.onRandom = { call ->
            if (call.count == QUICK_FETCH_COUNT) {
                Outcome.Success(
                    listOf(
                        popularRecipe(10, readyInMinutes = 15),
                        popularRecipe(11, readyInMinutes = 45),
                        popularRecipe(12, readyInMinutes = 30),
                        popularRecipe(13, readyInMinutes = null),
                    ),
                )
            } else {
                Outcome.Success(untagged)
            }
        }
        val viewModel = HomeViewModel(recipes)
        advanceUntilIdle()

        viewModel.onIntent(HomeIntent.SelectCategory(HomeCategory.QuickAndEasy))
        advanceUntilIdle()

        assertEquals(RandomCall(QUICK_FETCH_COUNT, emptyList(), emptyList()), recipes.randomCalls.last())
        assertEquals(listOf(10, 12), ids(viewModel))
    }

    @Test
    fun selectingADietRequestsItsTagAndIsCached() = runTest {
        recipes.onRandom = { call ->
            Outcome.Success(if (call.includeTags == listOf("vegan")) listOf(popularRecipe(20)) else untagged)
        }
        val viewModel = HomeViewModel(recipes)
        advanceUntilIdle()

        viewModel.onIntent(HomeIntent.SelectDiet(Diet.Vegan))
        advanceUntilIdle()

        with(viewModel.state.value) {
            assertEquals(HomeCategory.Diets, category)
            assertEquals(Diet.Vegan, diet)
            assertEquals(listOf(20), recipes.map { it.id })
        }
        assertEquals(RandomCall(PAGE_COUNT, listOf("vegan"), emptyList()), recipes.randomCalls.last())

        viewModel.onIntent(HomeIntent.SelectCategory(HomeCategory.All))
        viewModel.onIntent(HomeIntent.SelectDiet(Diet.Vegan))
        advanceUntilIdle()

        assertEquals(2, recipes.randomCalls.size, "switching back is served from memory")
        assertEquals(listOf(20), ids(viewModel))
    }

    @Test
    fun failureShowsErrorAndRetryFetchesAgain() = runTest {
        recipes.onRandom = { Outcome.Failure(DataError.QuotaExceeded) }
        val viewModel = HomeViewModel(recipes)
        advanceUntilIdle()

        with(viewModel.state.value) {
            assertEquals(DataError.QuotaExceeded, error)
            assertFalse(isLoading)
            assertTrue(recipes.isEmpty())
        }

        recipes.onRandom = { Outcome.Success(untagged) }
        viewModel.onIntent(HomeIntent.Retry)
        advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertEquals(listOf(1, 2, 3, 4), ids(viewModel))
        assertEquals(2, recipes.randomCalls.size)
    }

    @Test
    fun switchingWhileLoadingDoesNotDuplicateTheRequest() = runTest {
        val response = CompletableDeferred<Outcome<List<com.ankitt.thefoodhub.domain.model.PopularRecipe>>>()
        recipes.onRandom = { response.await() }
        val viewModel = HomeViewModel(recipes)
        advanceUntilIdle()

        viewModel.onIntent(HomeIntent.SelectCategory(HomeCategory.Popular))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isLoading)

        response.complete(Outcome.Success(untagged))
        advanceUntilIdle()

        assertEquals(1, recipes.randomCalls.size)
        assertEquals(listOf(3, 4, 1, 2), ids(viewModel), "the shared result is rendered for Popular")
    }

    @Test
    fun aLateResultForAnotherChipDoesNotReplaceTheVisibleOne() = runTest {
        val quickResponse = CompletableDeferred<Outcome<List<com.ankitt.thefoodhub.domain.model.PopularRecipe>>>()
        recipes.onRandom = { call ->
            if (call.count == QUICK_FETCH_COUNT) quickResponse.await() else Outcome.Success(untagged)
        }
        val viewModel = HomeViewModel(recipes)
        advanceUntilIdle()

        viewModel.onIntent(HomeIntent.SelectCategory(HomeCategory.QuickAndEasy))
        advanceUntilIdle()
        viewModel.onIntent(HomeIntent.SelectCategory(HomeCategory.All))
        quickResponse.complete(Outcome.Success(listOf(popularRecipe(99, readyInMinutes = 10))))
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3, 4), ids(viewModel))

        viewModel.onIntent(HomeIntent.SelectCategory(HomeCategory.QuickAndEasy))
        assertEquals(listOf(99), ids(viewModel), "the late result was still cached")
        assertEquals(2, recipes.randomCalls.size)
    }
}
