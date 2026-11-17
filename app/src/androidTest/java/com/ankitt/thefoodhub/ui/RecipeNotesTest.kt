package com.ankitt.thefoodhub.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ankitt.thefoodhub.domain.model.Ingredient
import com.ankitt.thefoodhub.domain.model.InstructionStep
import com.ankitt.thefoodhub.domain.model.RecipeDetail
import com.ankitt.thefoodhub.domain.model.RecipeNotes
import com.ankitt.thefoodhub.feature.detail.NoteEditor
import com.ankitt.thefoodhub.feature.detail.RecipeDetailIntent
import com.ankitt.thefoodhub.feature.detail.RecipeDetailState
import com.ankitt.thefoodhub.ui.detail.RecipeDetailScreen
import com.ankitt.thefoodhub.ui.theme.TheFoodHubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeNotesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<RecipeDetailIntent>()

    private val recipe = RecipeDetail(
        id = 1,
        title = "Fudgy brownies",
        imageUrl = null,
        readyInMinutes = 40,
        servings = 8,
        healthScore = null,
        sourceName = null,
        sourceUrl = null,
        summaryHtml = "",
        diets = emptyList(),
        cuisines = emptyList(),
        dishTypes = emptyList(),
        ingredients = listOf(Ingredient("sugar", "1 cup sugar", null), Ingredient("eggs", "2 eggs", null)),
        steps = listOf(InstructionStep(1, "Bake.")),
    )

    private val saved = RecipeDetailState(recipeId = 1, recipe = recipe, isFavorite = true)

    private fun setContent(state: RecipeDetailState) {
        composeRule.setContent {
            TheFoodHubTheme {
                RecipeDetailScreen(state = state, onIntent = { intents += it }, onBack = {})
            }
        }
    }

    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun anUnsavedRecipeExplainsHowToTakeNotes() {
        setContent(saved.copy(isFavorite = false, notes = RecipeNotes("Hidden while unsaved", mapOf("1 cup sugar" to "Used half"))))

        composeRule.onNodeWithText("Save this recipe to add your own notes and ingredient tweaks.").assertIsDisplayed()
        assertTrue(composeRule.onAllNodes(hasText("Hidden while unsaved")).fetchSemanticsNodes().isEmpty())

        scrollTo("1 cup sugar")
        composeRule.onNodeWithText("1 cup sugar").performClick()
        assertTrue(composeRule.onAllNodes(hasText("Used half")).fetchSemanticsNodes().isEmpty())
        assertEquals(emptyList<RecipeDetailIntent>(), intents)
    }

    @Test
    fun aSavedRecipeShowsItsNoteAndTweaksAndOpensEditors() {
        setContent(saved.copy(notes = RecipeNotes("Baked 5 mins less", mapOf("1 cup sugar" to "Used half"))))

        composeRule.onNodeWithText("Baked 5 mins less").assertIsDisplayed()
        composeRule.onNodeWithText("Edit note").performClick()

        scrollTo("Used half")
        composeRule.onNodeWithText("Used half").assertIsDisplayed()
        composeRule.onNodeWithText("2 eggs").performClick()

        assertEquals(listOf(RecipeDetailIntent.EditNote, RecipeDetailIntent.EditIngredientTweak("2 eggs")), intents)
    }

    @Test
    fun anEmptyNoteInvitesOne() {
        setContent(saved)

        composeRule.onNodeWithText("Add a note").assertIsDisplayed()
        composeRule.onNodeWithText("Tap an ingredient to note a tweak.").assertIsDisplayed()
    }

    @Test
    fun theTweakEditorSavesTypedTextAndCanRemove() {
        setContent(saved.copy(noteEditor = NoteEditor(ingredientLine = "1 cup sugar", initialText = "Used half")))

        composeRule.onNodeWithText("Ingredient tweak").assertIsDisplayed()
        composeRule.onNodeWithText("9 / 200").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("A third")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Remove").performClick()

        assertEquals(listOf(RecipeDetailIntent.SaveNoteEdit("A third"), RecipeDetailIntent.SaveNoteEdit("")), intents)
    }
}
