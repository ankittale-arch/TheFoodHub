package com.ankitt.thefoodhub.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.domain.model.PopularRecipe
import com.ankitt.thefoodhub.domain.model.WineBottle
import com.ankitt.thefoodhub.domain.model.WinePairing
import com.ankitt.thefoodhub.feature.home.Diet
import com.ankitt.thefoodhub.feature.home.HomeCategory
import com.ankitt.thefoodhub.feature.home.HomeIntent
import com.ankitt.thefoodhub.feature.home.HomeState
import com.ankitt.thefoodhub.feature.home.WinePairingState
import com.ankitt.thefoodhub.ui.home.HomeScreen
import com.ankitt.thefoodhub.ui.theme.TheFoodHubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<HomeIntent>()

    /** Intents other than the carousel reporting its centred card, which it does as soon as it appears. */
    private val userIntents get() = intents.filterNot { it is HomeIntent.FocusRecipe }
    private var searchClicks = 0
    private val opened = mutableListOf<PopularRecipe>()

    private val pasta = PopularRecipe(1, "Leek pasta with miso sauce", null, readyInMinutes = 25, likes = 385, rating = 4.8, winePairingFood = "pasta")

    private fun setContent(state: HomeState) {
        composeRule.setContent {
            TheFoodHubTheme {
                HomeScreen(
                    state = state,
                    onIntent = { intents += it },
                    onSearchClick = { searchClicks++ },
                    onRecipeClick = { opened += it },
                )
            }
        }
    }

    @Test
    fun showsTheRecipeCardWithItsStats() {
        setContent(HomeState(isLoading = false, recipes = listOf(pasta)))

        composeRule.onNodeWithText("Most popular recipes").assertIsDisplayed()
        composeRule.onNodeWithText("Leek pasta with miso sauce").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("25 min").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("385 likes").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rated 4.8 out of 5").assertIsDisplayed()

        composeRule.onNodeWithText("Leek pasta with miso sauce").performClick()
        assertEquals(listOf(pasta), opened)
    }

    @Test
    fun chipsSendCategoryIntents() {
        setContent(HomeState(isLoading = false, recipes = listOf(pasta)))

        composeRule.onNodeWithText("All").assertIsSelected()
        composeRule.onNodeWithText("Popular").performClick()
        composeRule.onNodeWithText("Quick & Easy").performClick()

        assertEquals(
            listOf(
                HomeIntent.SelectCategory(HomeCategory.Popular),
                HomeIntent.SelectCategory(HomeCategory.QuickAndEasy),
            ),
            userIntents,
        )
    }

    @Test
    fun dietsChipOpensAMenuAndSelectsADiet() {
        setContent(HomeState(isLoading = false, recipes = listOf(pasta)))

        composeRule.onNodeWithText("Diets").performClick()
        composeRule.onNodeWithText("Vegan").performClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.SelectDiet(Diet.Vegan)), userIntents)
    }

    @Test
    fun selectedDietReplacesTheChipLabelAndSectionTitle() {
        setContent(HomeState(category = HomeCategory.Diets, diet = Diet.GlutenFree, isLoading = false, recipes = listOf(pasta)))

        composeRule.onNodeWithText("Gluten free").assertIsSelected()
        composeRule.onNodeWithText("Gluten free recipes").assertIsDisplayed()
    }

    @Test
    fun searchBarOpensSearch() {
        setContent(HomeState(isLoading = false, recipes = listOf(pasta)))

        composeRule.onNodeWithContentDescription("Let’s cook something…").performClick()

        assertEquals(1, searchClicks)
    }

    @Test
    fun errorOffersRetry() {
        setContent(HomeState(isLoading = false, error = DataError.QuotaExceeded))

        composeRule.onNodeWithText("The daily Spoonacular quota is used up. Try again tomorrow.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.Retry), intents)
    }

    @Test
    fun theCentredRecipeIsReportedSoItsPairingCanLoad() {
        setContent(HomeState(isLoading = false, recipes = listOf(pasta)))

        composeRule.waitForIdle()
        assertTrue(HomeIntent.FocusRecipe(pasta.id) in intents)
    }

    @Test
    fun showsTheWinePairingAndSuggestedBottle() {
        val pairing = WinePairing(
            food = "pasta",
            wines = listOf("Chianti", "Pinot Grigio"),
            explanation = "Match the wine to the sauce.",
            suggestedBottle = WineBottle("Castello Chianti", "$24.99", null, 4.6, 31, link = null),
        )
        setContent(HomeState(isLoading = false, recipes = listOf(pasta), winePairing = WinePairingState.Ready(pairing)))

        composeRule.onNodeWithText("Pairs well with").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Chianti · Pinot Grigio").assertExists()
        composeRule.onNodeWithText("Match the wine to the sauce.").assertExists()
        composeRule.onNodeWithContentDescription("Castello Chianti, $24.99, Rated 4.6 out of 5").assertExists()
    }

    @Test
    fun noPairingSaysSo() {
        setContent(HomeState(isLoading = false, recipes = listOf(pasta), winePairing = WinePairingState.NoPairing))

        composeRule.onNodeWithText("No wine pairing for this one").assertExists()
        composeRule.onNodeWithText("It shines on its own. Try sparkling water with a squeeze of lemon, or swipe for another recipe.")
            .assertExists()
    }

    @Test
    fun aFailedPairingOffersRetry() {
        setContent(
            HomeState(isLoading = false, recipes = listOf(pasta), winePairing = WinePairingState.Failed(DataError.Network)),
        )

        composeRule.onNodeWithText("Can’t reach Spoonacular. Check your connection.").performScrollTo()
        composeRule.onAllNodesWithText("Retry").onLast().performScrollTo().performClick()

        assertEquals(HomeIntent.RetryWinePairing, intents.last())
    }

    @Test
    fun loadingRecipesShowsTheFoodLoader() {
        setContent(HomeState(isLoading = true))

        composeRule.onNodeWithContentDescription("Finding recipes…").assertIsDisplayed()
    }

    @Test
    fun loadingAPairingShowsTheFoodLoaderInTheCard() {
        setContent(HomeState(isLoading = false, recipes = listOf(pasta), winePairing = WinePairingState.Loading))

        composeRule.onNodeWithText("Finding a wine…").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theSectionIsHiddenWithoutRecipes() {
        setContent(HomeState(isLoading = true))

        composeRule.onNodeWithText("Pairs well with").assertDoesNotExist()
    }
}
