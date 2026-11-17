package com.ankitt.thefoodhub.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ankitt.thefoodhub.domain.model.Cookbook
import com.ankitt.thefoodhub.domain.model.CookbookNameError
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import com.ankitt.thefoodhub.feature.favorites.CookbookNameEditor
import com.ankitt.thefoodhub.feature.favorites.CookbookPicker
import com.ankitt.thefoodhub.feature.favorites.FavoritesIntent
import com.ankitt.thefoodhub.feature.favorites.FavoritesState
import com.ankitt.thefoodhub.ui.favorites.FavoritesScreen
import com.ankitt.thefoodhub.ui.theme.TheFoodHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<FavoritesIntent>()

    private val pasta = RecipeSummary(1, "Weeknight pasta", null)
    private val brownies = RecipeSummary(2, "Fudgy brownies", null)
    private val desserts = Cookbook(id = 10, name = "Desserts", recipeCount = 1)
    private val mealPrep = Cookbook(id = 11, name = "Meal Prep", recipeCount = 0)

    private val loaded = FavoritesState(
        favorites = listOf(pasta, brownies),
        isLoading = false,
        cookbooks = listOf(desserts, mealPrep),
    )

    private fun setContent(state: FavoritesState) {
        composeRule.setContent {
            TheFoodHubTheme {
                FavoritesScreen(state = state, onIntent = { intents += it }, onRecipeClick = {})
            }
        }
    }

    @Test
    fun cookbookChipsShowCountsAndSelect() {
        setContent(loaded)

        composeRule.onNodeWithText("All · 2").assertIsDisplayed()
        composeRule.onNodeWithText("Meal Prep · 0").assertIsDisplayed()
        composeRule.onNodeWithText("Desserts · 1").performClick()
        composeRule.onNodeWithText("New cookbook").performClick()

        assertEquals(listOf(FavoritesIntent.SelectCookbook(10), FavoritesIntent.StartNewCookbook()), intents)
    }

    @Test
    fun anEmptyCookbookExplainsHowToFillIt() {
        setContent(loaded.copy(selectedCookbookId = mealPrep.id, cookbookRecipes = emptyList()))

        composeRule.onNodeWithText("Nothing in Meal Prep yet").assertIsDisplayed()
        composeRule.onNodeWithText("Tap the book icon on any favorite to add it here.").assertIsDisplayed()
    }

    @Test
    fun theCardBookButtonOpensThePicker() {
        setContent(loaded)

        composeRule.onAllNodesWithContentDescription("Add to cookbook").onFirst().performClick()

        assertEquals(listOf(FavoritesIntent.OpenCookbookPicker(pasta)), intents)
    }

    @Test
    fun pickerTicksTheRecipesCookbooksAndToggles() {
        setContent(loaded.copy(cookbookPicker = CookbookPicker(brownies, cookbookIds = setOf(desserts.id))))

        composeRule.onNodeWithText("Save to cookbook").assertIsDisplayed()
        composeRule.onNodeWithText("Desserts").assertIsOn()
        composeRule.onNodeWithText("Meal Prep").assertIsOff()
        composeRule.onNodeWithText("0 recipes").assertIsDisplayed()

        composeRule.onNodeWithText("Meal Prep").performClick()
        // The chip row behind the sheet has a "New cookbook" chip too; the sheet is the later root.
        composeRule.onAllNodesWithText("New cookbook").onLast().performClick()

        assertEquals(
            listOf(FavoritesIntent.ToggleCookbook(mealPrep.id), FavoritesIntent.StartNewCookbook(recipeToAdd = brownies)),
            intents,
        )
    }

    @Test
    fun namePromptShowsTheErrorAndSavesTheTypedName() {
        setContent(loaded.copy(nameEditor = CookbookNameEditor(error = CookbookNameError.Taken)))

        composeRule.onNodeWithText("You already have a cookbook with this name.").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Quick Weeknight")
        composeRule.onNodeWithText("Create").performClick()

        assertEquals(listOf(FavoritesIntent.SaveCookbookName("Quick Weeknight")), intents)
    }

    @Test
    fun deletingTheSelectedCookbookAsksFirst() {
        setContent(loaded.copy(selectedCookbookId = desserts.id, cookbookRecipes = listOf(brownies)))

        composeRule.onNodeWithContentDescription("Cookbook options").performClick()
        composeRule.onNodeWithText("Delete cookbook").performClick()
        composeRule.onNodeWithText("Delete “Desserts”?").assertIsDisplayed()
        composeRule.onNodeWithText("Its recipes stay in your favorites.").assertIsDisplayed()
        assertEquals(emptyList<FavoritesIntent>(), intents)

        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(listOf(FavoritesIntent.DeleteCookbook(desserts.id)), intents)
    }
}
