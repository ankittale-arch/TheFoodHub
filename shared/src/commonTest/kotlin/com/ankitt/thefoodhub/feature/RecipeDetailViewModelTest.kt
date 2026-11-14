package com.ankitt.thefoodhub.feature

import app.cash.turbine.test
import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.feature.detail.RecipeDetailEffect
import com.ankitt.thefoodhub.feature.detail.RecipeDetailIntent
import com.ankitt.thefoodhub.feature.detail.RecipeDetailViewModel
import com.ankitt.thefoodhub.testutil.FakeFavoritesRepository
import com.ankitt.thefoodhub.testutil.FakeRecipeRepository
import com.ankitt.thefoodhub.testutil.MainDispatcherTest
import com.ankitt.thefoodhub.testutil.recipeDetail
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeDetailViewModelTest : MainDispatcherTest() {

    private val recipes = FakeRecipeRepository()
    private val favorites = FakeFavoritesRepository()

    private fun createViewModel(id: Int = 42) = RecipeDetailViewModel(id, recipes, favorites)

    @Test
    fun refreshesOnStartAndShowsTheStoredRecipe() = runTest {
        recipes.onRefresh = { id ->
            recipes.cached.value = mapOf(id to recipeDetail(id))
            Outcome.Success(Unit)
        }
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf(42), recipes.refreshCalls)
        with(viewModel.state.value) {
            assertEquals(42, recipe?.id)
            assertFalse(isLoading)
            assertNull(error)
        }
    }

    @Test
    fun failureWithoutCacheShowsErrorAndRetryRecovers() = runTest {
        recipes.onRefresh = { Outcome.Failure(DataError.Unauthorized) }
        val viewModel = createViewModel()
        advanceUntilIdle()

        with(viewModel.state.value) {
            assertNull(recipe)
            assertEquals(DataError.Unauthorized, error)
        }

        recipes.onRefresh = { id ->
            recipes.cached.value = mapOf(id to recipeDetail(id))
            Outcome.Success(Unit)
        }
        viewModel.onIntent(RecipeDetailIntent.Retry)
        advanceUntilIdle()

        with(viewModel.state.value) {
            assertEquals(42, recipe?.id)
            assertNull(error)
        }
    }

    @Test
    fun toggleFavoriteUpdatesStateAndEmitsEffect() = runTest {
        recipes.cached.value = mapOf(42 to recipeDetail(42))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isFavorite)

        viewModel.effects.test {
            viewModel.onIntent(RecipeDetailIntent.ToggleFavorite)
            assertEquals(RecipeDetailEffect.FavoriteChanged(isFavorite = true), awaitItem())

            viewModel.onIntent(RecipeDetailIntent.ToggleFavorite)
            assertEquals(RecipeDetailEffect.FavoriteChanged(isFavorite = false), awaitItem())
        }
        assertTrue(favorites.favorites.value.isEmpty())
    }

    @Test
    fun toggleFavoriteIsIgnoredUntilTheRecipeLoads() = runTest {
        recipes.onRefresh = { Outcome.Failure(DataError.Network) }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(RecipeDetailIntent.ToggleFavorite)
        advanceUntilIdle()

        assertTrue(favorites.favorites.value.isEmpty())
    }
}
