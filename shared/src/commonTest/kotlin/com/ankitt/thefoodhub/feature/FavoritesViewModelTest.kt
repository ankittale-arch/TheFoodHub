package com.ankitt.thefoodhub.feature

import app.cash.turbine.test
import com.ankitt.thefoodhub.feature.favorites.FavoritesEffect
import com.ankitt.thefoodhub.feature.favorites.FavoritesIntent
import com.ankitt.thefoodhub.feature.favorites.FavoritesViewModel
import com.ankitt.thefoodhub.testutil.FakeFavoritesRepository
import com.ankitt.thefoodhub.testutil.MainDispatcherTest
import com.ankitt.thefoodhub.testutil.recipeSummary
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavoritesViewModelTest : MainDispatcherTest() {

    private val favorites = FakeFavoritesRepository(initial = listOf(recipeSummary(1), recipeSummary(2)))

    @Test
    fun startsLoadingThenShowsFavorites() = runTest {
        val viewModel = FavoritesViewModel(favorites)
        assertTrue(viewModel.state.value.isLoading)

        advanceUntilIdle()

        with(viewModel.state.value) {
            assertFalse(isLoading)
            assertEquals(listOf(1, 2), this.favorites.map { it.id })
        }
    }

    @Test
    fun removeEmitsUndoableEffectAndUndoRestores() = runTest {
        val viewModel = FavoritesViewModel(favorites)
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(FavoritesIntent.Remove(recipeSummary(1)))
            assertEquals(FavoritesEffect.Removed(recipeSummary(1)), awaitItem())
        }
        assertEquals(listOf(2), viewModel.state.value.favorites.map { it.id })

        viewModel.onIntent(FavoritesIntent.UndoRemove(recipeSummary(1)))
        advanceUntilIdle()

        assertEquals(setOf(1, 2), viewModel.state.value.favorites.map { it.id }.toSet())
    }
}
