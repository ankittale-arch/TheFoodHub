package com.ankitt.thefoodhub.feature

import app.cash.turbine.test
import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.domain.model.Nutrient
import com.ankitt.thefoodhub.domain.model.NutrientFilter
import com.ankitt.thefoodhub.domain.model.NutrientPreset
import com.ankitt.thefoodhub.domain.model.NutrientRange
import com.ankitt.thefoodhub.domain.model.RecipeMatch
import com.ankitt.thefoodhub.domain.model.SearchCriteria
import com.ankitt.thefoodhub.feature.search.SearchEffect
import com.ankitt.thefoodhub.feature.search.SearchIntent
import com.ankitt.thefoodhub.feature.search.SearchViewModel
import com.ankitt.thefoodhub.feature.search.SearchViewModel.Companion.PAGE_SIZE
import com.ankitt.thefoodhub.feature.search.SearchViewModel.Companion.SEARCH_DEBOUNCE_MILLIS
import com.ankitt.thefoodhub.testutil.FakeFavoritesRepository
import com.ankitt.thefoodhub.testutil.FakeRecipeRepository
import com.ankitt.thefoodhub.testutil.MainDispatcherTest
import com.ankitt.thefoodhub.testutil.SearchCall
import com.ankitt.thefoodhub.testutil.recipePage
import com.ankitt.thefoodhub.testutil.recipeSummary
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchViewModelTest : MainDispatcherTest() {

    private val recipes = FakeRecipeRepository()
    private val favorites = FakeFavoritesRepository()

    private fun createViewModel() = SearchViewModel(recipes, favorites)

    @Test
    fun typingQuicklySendsOneRequestForTheLatestQuery() = runTest {
        recipes.onSearch = { Outcome.Success(recipePage(1..3, totalResults = 3)) }
        val viewModel = createViewModel()

        viewModel.onIntent(SearchIntent.QueryChanged("p"))
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS / 2)
        viewModel.onIntent(SearchIntent.QueryChanged("pa"))
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS / 2)
        viewModel.onIntent(SearchIntent.QueryChanged("pasta"))
        advanceUntilIdle()

        assertEquals(listOf(SearchCall(SearchCriteria("pasta"), offset = 0, pageSize = PAGE_SIZE)), recipes.searchCalls)
        with(viewModel.state.value) {
            assertEquals(listOf(1, 2, 3), this.recipes.map { it.id })
            assertTrue(endReached)
            assertTrue(hasSearched)
            assertFalse(isLoading)
        }
    }

    @Test
    fun showsLoadingOnlyAfterTheDebounce() = runTest {
        val viewModel = createViewModel()

        viewModel.onIntent(SearchIntent.QueryChanged("soup"))
        runCurrent()
        assertFalse(viewModel.state.value.isLoading)

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        assertEquals(1, recipes.searchCalls.size)
    }

    @Test
    fun whitespaceOnlyChangesDoNotSearchAgain() = runTest {
        val viewModel = createViewModel()
        viewModel.onIntent(SearchIntent.QueryChanged("pasta"))
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.QueryChanged("pasta "))
        advanceUntilIdle()

        assertEquals(1, recipes.searchCalls.size)
        assertEquals("pasta ", viewModel.state.value.query)
    }

    @Test
    fun clearingTheQueryResetsResultsWithoutCallingTheApi() = runTest {
        recipes.onSearch = { Outcome.Success(recipePage(1..5)) }
        val viewModel = createViewModel()
        viewModel.onIntent(SearchIntent.QueryChanged("pasta"))
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.QueryChanged("  "))
        advanceUntilIdle()

        assertEquals(1, recipes.searchCalls.size)
        with(viewModel.state.value) {
            assertTrue(this.recipes.isEmpty())
            assertFalse(hasSearched)
            assertNull(error)
        }
    }

    @Test
    fun firstPageFailureShowsErrorAndRetryRecovers() = runTest {
        recipes.onSearch = { Outcome.Failure(DataError.Network) }
        val viewModel = createViewModel()
        viewModel.onIntent(SearchIntent.QueryChanged("pasta"))
        advanceUntilIdle()

        assertEquals(DataError.Network, viewModel.state.value.error)

        recipes.onSearch = { Outcome.Success(recipePage(1..2)) }
        viewModel.onIntent(SearchIntent.Retry)
        advanceUntilIdle()

        with(viewModel.state.value) {
            assertNull(error)
            assertEquals(listOf(1, 2), this.recipes.map { it.id })
        }
    }

    @Test
    fun loadNextPageAppendsWithoutDuplicatesAndStopsAtTheEnd() = runTest {
        recipes.onSearch = { call ->
            when (call.offset) {
                0 -> Outcome.Success(recipePage(1..20, offset = 0, totalResults = 25))
                // Overlaps the first page by one item, as Spoonacular sometimes does.
                20 -> Outcome.Success(recipePage(20..25, offset = 20, totalResults = 25))
                else -> error("unexpected offset ${call.offset}")
            }
        }
        val viewModel = createViewModel()
        viewModel.onIntent(SearchIntent.QueryChanged("pasta"))
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.LoadNextPage)
        advanceUntilIdle()
        viewModel.onIntent(SearchIntent.LoadNextPage)
        advanceUntilIdle()

        assertEquals(listOf(0, 20), recipes.searchCalls.map { it.offset })
        with(viewModel.state.value) {
            assertEquals((1..25).toList(), this.recipes.map { it.id })
            assertTrue(endReached)
            assertFalse(isLoadingMore)
        }
    }

    @Test
    fun loadNextPageFailureKeepsResultsAndEmitsAnEffect() = runTest {
        recipes.onSearch = { call ->
            if (call.offset == 0) Outcome.Success(recipePage(1..20)) else Outcome.Failure(DataError.QuotaExceeded)
        }
        val viewModel = createViewModel()
        viewModel.onIntent(SearchIntent.QueryChanged("pasta"))
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(SearchIntent.LoadNextPage)
            assertEquals(SearchEffect.ShowError(DataError.QuotaExceeded), awaitItem())
        }
        with(viewModel.state.value) {
            assertEquals(20, this.recipes.size)
            assertNull(error)
            assertFalse(isLoadingMore)
        }
    }

    @Test
    fun newQueryCancelsAnInFlightSearch() = runTest {
        recipes.onSearch = { call ->
            if (call.query == "slow") {
                kotlinx.coroutines.delay(10_000)
                Outcome.Success(recipePage(100..101))
            } else {
                Outcome.Success(recipePage(1..2))
            }
        }
        val viewModel = createViewModel()
        viewModel.onIntent(SearchIntent.QueryChanged("slow"))
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)

        viewModel.onIntent(SearchIntent.QueryChanged("fast"))
        advanceUntilIdle()

        assertEquals(listOf(1, 2), viewModel.state.value.recipes.map { it.id })
    }

    @Test
    fun applyingIngredientsSearchesWithoutTheDebounceAndNormalizes() = runTest {
        val viewModel = createViewModel()

        viewModel.onIntent(SearchIntent.ApplyIngredients(listOf(" Chicken ", "chicken", "", "Bell  Pepper")))
        runCurrent()

        assertEquals(
            listOf(SearchCall(SearchCriteria(ingredients = listOf("chicken", "bell pepper")), 0, PAGE_SIZE)),
            recipes.searchCalls,
        )
        assertEquals(listOf("chicken", "bell pepper"), viewModel.state.value.ingredients)
    }

    @Test
    fun filtersCombineWithTheTypedQuery() = runTest {
        val viewModel = createViewModel()
        viewModel.onIntent(SearchIntent.QueryChanged("pasta "))
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.ApplyIngredients(listOf("chicken")))
        viewModel.onIntent(SearchIntent.ApplyNutrients(NutrientPreset.UNDER_500_KCAL.filter))
        advanceUntilIdle()

        assertEquals(
            SearchCriteria(
                query = "pasta",
                ingredients = listOf("chicken"),
                nutrients = NutrientFilter(calories = NutrientRange(max = 500)),
            ),
            recipes.searchCalls.last().criteria,
        )
        assertTrue(viewModel.state.value.hasFilters)
    }

    @Test
    fun applyingAFilterDuringTheDebounceSendsOneRequestWithBoth() = runTest {
        val viewModel = createViewModel()

        viewModel.onIntent(SearchIntent.QueryChanged("soup"))
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS / 2)
        viewModel.onIntent(SearchIntent.ApplyIngredients(listOf("tomato")))
        advanceUntilIdle()

        assertEquals(
            listOf(SearchCriteria(query = "soup", ingredients = listOf("tomato"))),
            recipes.searchCalls.map { it.criteria },
        )
    }

    @Test
    fun reapplyingTheSameFiltersDoesNotSearchAgain() = runTest {
        val viewModel = createViewModel()
        viewModel.onIntent(SearchIntent.ApplyIngredients(listOf("egg")))
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.ApplyIngredients(listOf("Egg")))
        viewModel.onIntent(SearchIntent.ApplyNutrients(NutrientFilter()))
        advanceUntilIdle()

        assertEquals(1, recipes.searchCalls.size)
    }

    @Test
    fun removingOneNutrientKeepsTheOthers() = runTest {
        val viewModel = createViewModel()
        val filter = NutrientFilter(protein = NutrientRange(min = 30), carbs = NutrientRange(max = 20))
        viewModel.onIntent(SearchIntent.ApplyNutrients(filter))
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.RemoveNutrient(Nutrient.PROTEIN))
        advanceUntilIdle()

        assertEquals(NutrientFilter(carbs = NutrientRange(max = 20)), viewModel.state.value.nutrients)
        assertEquals(2, recipes.searchCalls.size)
    }

    @Test
    fun clearingTheLastFilterWithoutAQueryResetsWithoutCallingTheApi() = runTest {
        recipes.onSearch = { Outcome.Success(recipePage(1..5)) }
        val viewModel = createViewModel()
        viewModel.onIntent(SearchIntent.ApplyIngredients(listOf("rice", "egg")))
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.RemoveIngredient("rice"))
        advanceUntilIdle()
        assertEquals(listOf("egg"), recipes.searchCalls.last().criteria.ingredients)

        viewModel.onIntent(SearchIntent.ClearFilters)
        advanceUntilIdle()

        assertEquals(2, recipes.searchCalls.size)
        with(viewModel.state.value) {
            assertTrue(this.recipes.isEmpty())
            assertFalse(hasSearched)
            assertFalse(hasFilters)
        }
    }

    @Test
    fun matchesAreKeptAndMergedAcrossPages() = runTest {
        recipes.onSearch = { call ->
            val ids = if (call.offset == 0) 1..20 else 21..22
            Outcome.Success(
                recipePage(ids, offset = call.offset, totalResults = 22).copy(
                    matches = ids.associateWith { RecipeMatch(usedIngredientCount = 1, missedIngredientCount = it) },
                ),
            )
        }
        val viewModel = createViewModel()
        viewModel.onIntent(SearchIntent.ApplyIngredients(listOf("egg")))
        advanceUntilIdle()
        viewModel.onIntent(SearchIntent.LoadNextPage)
        advanceUntilIdle()

        assertEquals(listOf("egg"), recipes.searchCalls.last().criteria.ingredients)
        with(viewModel.state.value) {
            assertEquals(22, matches.size)
            assertEquals(21, matchFor(21)?.missedIngredientCount)
        }
    }

    @Test
    fun favoriteIdsAreObservedAndToggleUpdatesThem() = runTest {
        favorites.favorites.value = listOf(recipeSummary(7))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(setOf(7), viewModel.state.value.favoriteIds)

        viewModel.onIntent(SearchIntent.ToggleFavorite(recipeSummary(8)))
        advanceUntilIdle()

        assertEquals(setOf(7, 8), viewModel.state.value.favoriteIds)
    }
}
