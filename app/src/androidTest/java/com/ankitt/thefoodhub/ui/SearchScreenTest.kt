package com.ankitt.thefoodhub.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ankitt.thefoodhub.core.DataError
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
    fun emptyResultsMessageIncludesTheQuery() {
        setContent(SearchState(query = "zzz", hasSearched = true))

        composeRule.onNodeWithText("No recipes found for “zzz”.").assertIsDisplayed()
    }
}
