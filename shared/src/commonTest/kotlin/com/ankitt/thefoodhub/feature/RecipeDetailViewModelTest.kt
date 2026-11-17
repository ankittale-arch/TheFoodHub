package com.ankitt.thefoodhub.feature

import app.cash.turbine.test
import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.domain.model.RecipeNotes
import com.ankitt.thefoodhub.domain.model.toSummary
import com.ankitt.thefoodhub.feature.detail.NoteEditor
import com.ankitt.thefoodhub.feature.detail.RecipeDetailEffect
import com.ankitt.thefoodhub.feature.detail.RecipeDetailIntent
import com.ankitt.thefoodhub.feature.detail.RecipeDetailViewModel
import com.ankitt.thefoodhub.testutil.FakeFavoritesRepository
import com.ankitt.thefoodhub.testutil.FakeRecipeNotesRepository
import com.ankitt.thefoodhub.testutil.FakeRecipeRepository
import com.ankitt.thefoodhub.testutil.MainDispatcherTest
import com.ankitt.thefoodhub.testutil.recipeDetail
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeDetailViewModelTest : MainDispatcherTest() {

    private val recipes = FakeRecipeRepository()
    private val favorites = FakeFavoritesRepository()
    private val notes = FakeRecipeNotesRepository()

    private fun createViewModel(id: Int = 42) = RecipeDetailViewModel(id, recipes, favorites, notes)

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

    /** A cached recipe, saved to favorites, with the ViewModel settled. */
    private suspend fun TestScope.savedRecipeViewModel(): RecipeDetailViewModel {
        recipes.cached.value = mapOf(42 to recipeDetail(42))
        favorites.setFavorite(recipeDetail(42).toSummary(), favorite = true)
        return createViewModel().also { advanceUntilIdle() }
    }

    @Test
    fun writingANoteSavesItAndShowsIt() = runTest {
        val viewModel = savedRecipeViewModel()
        assertTrue(viewModel.state.value.canTakeNotes)

        viewModel.onIntent(RecipeDetailIntent.EditNote)
        assertEquals(NoteEditor(), viewModel.state.value.noteEditor)

        viewModel.onIntent(RecipeDetailIntent.SaveNoteEdit("  Used half the sugar, baked 5 mins less "))
        advanceUntilIdle()

        with(viewModel.state.value) {
            assertNull(noteEditor)
            assertEquals("Used half the sugar, baked 5 mins less", this.notes.note)
        }
    }

    @Test
    fun editingStartsFromTheSavedTextAndBlankRemoves() = runTest {
        notes.saveNote(42, "Too salty")
        notes.saveTweak(42, "1 tsp salt", "Half a tsp")
        val viewModel = savedRecipeViewModel()

        viewModel.onIntent(RecipeDetailIntent.EditNote)
        assertEquals(NoteEditor(initialText = "Too salty"), viewModel.state.value.noteEditor)
        viewModel.onIntent(RecipeDetailIntent.SaveNoteEdit("   "))
        advanceUntilIdle()
        assertEquals("", viewModel.state.value.notes.note)

        viewModel.onIntent(RecipeDetailIntent.EditIngredientTweak("1 tsp salt"))
        with(assertNotNull(viewModel.state.value.noteEditor)) {
            assertEquals("Half a tsp", initialText)
            assertTrue(isTweak)
            assertTrue(canRemove)
            assertEquals(RecipeNotes.MAX_TWEAK_LENGTH, maxLength)
        }
        viewModel.onIntent(RecipeDetailIntent.SaveNoteEdit(""))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.notes.isEmpty)
    }

    @Test
    fun tweaksAreKeptPerIngredientLine() = runTest {
        val viewModel = savedRecipeViewModel()

        viewModel.onIntent(RecipeDetailIntent.EditIngredientTweak("1 cup sugar"))
        viewModel.onIntent(RecipeDetailIntent.SaveNoteEdit("Used half"))
        viewModel.onIntent(RecipeDetailIntent.EditIngredientTweak("2 eggs"))
        viewModel.onIntent(RecipeDetailIntent.SaveNoteEdit("Flax eggs"))
        advanceUntilIdle()

        with(viewModel.state.value.notes) {
            assertEquals("Used half", tweakFor("1 cup sugar"))
            assertEquals("Flax eggs", tweakFor("2 eggs"))
            assertNull(tweakFor("1 tsp salt"))
        }
    }

    @Test
    fun cancellingKeepsTheSavedNote() = runTest {
        notes.saveNote(42, "Keep me")
        val viewModel = savedRecipeViewModel()

        viewModel.onIntent(RecipeDetailIntent.EditNote)
        viewModel.onIntent(RecipeDetailIntent.CancelNoteEdit)
        advanceUntilIdle()

        assertNull(viewModel.state.value.noteEditor)
        assertEquals("Keep me", viewModel.state.value.notes.note)
    }

    @Test
    fun notesNeedASavedRecipeAndSurviveUnsaving() = runTest {
        notes.saveNote(42, "Baked 5 mins less")
        recipes.cached.value = mapOf(42 to recipeDetail(42))
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canTakeNotes)
        viewModel.onIntent(RecipeDetailIntent.EditNote)
        assertNull(viewModel.state.value.noteEditor, "an unsaved recipe can't be noted")

        viewModel.onIntent(RecipeDetailIntent.ToggleFavorite)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canTakeNotes)
        assertEquals("Baked 5 mins less", viewModel.state.value.notes.note, "saving again brings the note back")
    }
}
