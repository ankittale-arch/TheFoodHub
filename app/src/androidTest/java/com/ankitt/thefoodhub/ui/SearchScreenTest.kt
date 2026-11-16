package com.ankitt.thefoodhub.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.domain.model.Nutrient
import com.ankitt.thefoodhub.domain.model.NutrientAmount
import com.ankitt.thefoodhub.domain.model.NutrientFilter
import com.ankitt.thefoodhub.domain.model.NutrientRange
import com.ankitt.thefoodhub.domain.model.RecipeMatch
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import com.ankitt.thefoodhub.feature.search.SearchIntent
import com.ankitt.thefoodhub.feature.search.SearchState
import com.ankitt.thefoodhub.ui.search.SearchScreen
import com.ankitt.thefoodhub.ui.theme.TheFoodHubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SearchIntent>()

    private fun setContent(state: SearchState) {
        composeRule.setContent {
            TheFoodHubTheme {
                SearchScreen(state = state, onIntent = { intents += it }, onRecipeClick = {})
            }
        }
    }

    @Test
    fun typingSendsQueryChangedIntents() {
        setContent(SearchState())

        composeRule.onNodeWithText("Search recipes, e.g. pasta").performTextInput("soup")

        assertEquals(SearchIntent.QueryChanged("soup"), intents.last())
    }

    @Test
    fun fieldKeepsEveryKeystrokeWhileTheStateLagsBehind() {
        // The state never updates here, like a ViewModel that has not delivered the new query yet.
        setContent(SearchState())
        // Matched by role, not by placeholder: the placeholder disappears once there is text.
        val field = composeRule.onNode(hasSetTextAction())

        field.performTextInput("so")
        field.performTextInput("up")

        assertEquals(SearchIntent.QueryChanged("soup"), intents.last())
        composeRule.onNodeWithText("soup").assertIsDisplayed()
    }

    @Test
    fun showsResultsAndTogglesFavorite() {
        val recipe = RecipeSummary(id = 1, title = "Tomato Soup", imageUrl = null)
        setContent(SearchState(query = "soup", recipes = listOf(recipe), hasSearched = true))

        composeRule.onNodeWithText("Tomato Soup").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Add to favorites").onFirst().performClick()

        assertTrue(SearchIntent.ToggleFavorite(recipe) in intents)
    }

    @Test
    fun errorStateOffersRetry() {
        setContent(SearchState(query = "soup", error = DataError.QuotaExceeded, hasSearched = true))

        composeRule.onNodeWithText("The daily Spoonacular quota is used up. Try again tomorrow.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(SearchIntent.Retry, intents.last())
    }

    @Test
    fun activeFiltersShowAsRemovableChips() {
        setContent(
            SearchState(
                ingredients = listOf("chicken"),
                nutrients = NutrientFilter(calories = NutrientRange(max = 500)),
            ),
        )

        composeRule.onNodeWithText("Ingredients · 1").assertIsDisplayed()
        composeRule.onNodeWithText("Nutrition · 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove chicken").performClick()
        assertEquals(SearchIntent.RemoveIngredient("chicken"), intents.last())

        composeRule.onNodeWithContentDescription("Remove Calories ≤ 500 kcal").performClick()
        assertEquals(SearchIntent.RemoveNutrient(Nutrient.CALORIES), intents.last())

        composeRule.onNodeWithText("Clear all").performClick()
        assertEquals(SearchIntent.ClearFilters, intents.last())
    }

    @Test
    fun ingredientsSheetAppliesSuggestionsAndTypedText() {
        setContent(SearchState())

        composeRule.onNodeWithText("Ingredients").performClick()
        composeRule.onNodeWithText("chicken").performClick()
        composeRule.onNodeWithText("Add an ingredient, e.g. chicken").performTextInput("Leek")
        composeRule.onNodeWithText("Show recipes").performClick()
        composeRule.waitForIdle()

        assertEquals(SearchIntent.ApplyIngredients(listOf("chicken", "leek")), intents.last())
    }

    @Test
    fun resultsShowWhyTheyMatched() {
        val recipe = RecipeSummary(id = 1, title = "Chicken Fajitas", imageUrl = null)
        setContent(
            SearchState(
                ingredients = listOf("chicken"),
                recipes = listOf(recipe),
                matches = mapOf(
                    1 to RecipeMatch(
                        usedIngredientCount = 1,
                        missedIngredientCount = 3,
                        nutrients = listOf(NutrientAmount(Nutrient.CALORIES, 512.3)),
                    ),
                ),
                hasSearched = true,
            ),
        )

        composeRule.onNodeWithText("1 used · 3 missing · 512 kcal").assertIsDisplayed()
    }

    @Test
    fun noResultsWithFiltersSuggestsLoosening() {
        setContent(SearchState(ingredients = listOf("durian"), hasSearched = true))

        composeRule.onNodeWithText("No recipes match these filters.").assertIsDisplayed()
    }

    @Test
    fun emptyResultsMessageIncludesTheQuery() {
        setContent(SearchState(query = "zzz", hasSearched = true))

        composeRule.onNodeWithText("No recipes found for “zzz”.").assertIsDisplayed()
    }
}
