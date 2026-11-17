package com.ankitt.thefoodhub.feature

import app.cash.turbine.test
import com.ankitt.thefoodhub.domain.model.CookbookNameError
import com.ankitt.thefoodhub.domain.model.CookbookNameResult
import com.ankitt.thefoodhub.feature.favorites.CookbookNameEditor
import com.ankitt.thefoodhub.feature.favorites.FavoritesEffect
import com.ankitt.thefoodhub.feature.favorites.FavoritesIntent
import com.ankitt.thefoodhub.feature.favorites.FavoritesViewModel
import com.ankitt.thefoodhub.testutil.FakeCookbookRepository
import com.ankitt.thefoodhub.testutil.FakeFavoritesRepository
import com.ankitt.thefoodhub.testutil.MainDispatcherTest
import com.ankitt.thefoodhub.testutil.recipeSummary
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FavoritesViewModelTest : MainDispatcherTest() {

    private val favorites = FakeFavoritesRepository(initial = listOf(recipeSummary(1), recipeSummary(2)))
    private val cookbooks = FakeCookbookRepository(favorites)

    private fun TestScope.viewModel(): FavoritesViewModel =
        FavoritesViewModel(favorites, cookbooks).also { advanceUntilIdle() }

    private fun TestScope.send(viewModel: FavoritesViewModel, intent: FavoritesIntent) {
        viewModel.onIntent(intent)
        advanceUntilIdle()
    }

    /** Creates a cookbook and returns its id. */
    private suspend fun cookbook(name: String): Long =
        (cookbooks.createCookbook(name) as CookbookNameResult.Saved).cookbookId

    @Test
    fun startsLoadingThenShowsFavorites() = runTest {
        val viewModel = FavoritesViewModel(favorites, cookbooks)
        assertTrue(viewModel.state.value.isLoading)

        advanceUntilIdle()

        with(viewModel.state.value) {
            assertFalse(isLoading)
            assertEquals(listOf(1, 2), this.favorites.map { it.id })
            assertEquals(listOf(1, 2), visibleRecipes.map { it.id })
        }
    }

    @Test
    fun removeEmitsUndoableEffectAndUndoRestores() = runTest {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onIntent(FavoritesIntent.Remove(recipeSummary(1)))
            assertEquals(FavoritesEffect.Removed(recipeSummary(1)), awaitItem())
        }
        assertEquals(listOf(2), viewModel.state.value.favorites.map { it.id })

        send(viewModel, FavoritesIntent.UndoRemove(recipeSummary(1)))

        assertEquals(setOf(1, 2), viewModel.state.value.favorites.map { it.id }.toSet())
    }

    @Test
    fun creatingFromTheCookbookRowOpensTheNewEmptyCookbook() = runTest {
        val viewModel = viewModel()

        send(viewModel, FavoritesIntent.StartNewCookbook())
        assertEquals(CookbookNameEditor(), viewModel.state.value.nameEditor)

        send(viewModel, FavoritesIntent.SaveCookbookName("  Meal   Prep "))

        with(viewModel.state.value) {
            assertNull(nameEditor)
            assertEquals(listOf("Meal Prep"), cookbooks.map { it.name })
            assertEquals("Meal Prep", selectedCookbook?.name)
            assertEquals(emptyList(), visibleRecipes)
        }
    }

    @Test
    fun rejectedNamesKeepTheEditorOpenWithTheReason() = runTest {
        cookbook("Desserts")
        val viewModel = viewModel()
        send(viewModel, FavoritesIntent.StartNewCookbook())

        send(viewModel, FavoritesIntent.SaveCookbookName("   "))
        assertEquals(CookbookNameError.Blank, viewModel.state.value.nameEditor?.error)

        send(viewModel, FavoritesIntent.SaveCookbookName("DESSERTS"))
        assertEquals(CookbookNameError.Taken, viewModel.state.value.nameEditor?.error)

        send(viewModel, FavoritesIntent.SaveCookbookName("x".repeat(41)))
        assertEquals(CookbookNameError.TooLong, viewModel.state.value.nameEditor?.error)

        send(viewModel, FavoritesIntent.CancelCookbookName)
        assertNull(viewModel.state.value.nameEditor)
        assertEquals(1, viewModel.state.value.cookbooks.size)
    }

    @Test
    fun pickerFilesAndUnfilesARecipe() = runTest {
        val desserts = cookbook("Desserts")
        val weeknight = cookbook("Quick Weeknight")
        val viewModel = viewModel()

        send(viewModel, FavoritesIntent.OpenCookbookPicker(recipeSummary(2)))
        assertEquals(emptySet(), assertNotNull(viewModel.state.value.cookbookPicker).cookbookIds)

        send(viewModel, FavoritesIntent.ToggleCookbook(desserts))
        send(viewModel, FavoritesIntent.ToggleCookbook(weeknight))
        assertEquals(setOf(desserts, weeknight), viewModel.state.value.cookbookPicker?.cookbookIds)
        assertEquals(listOf(1, 1), viewModel.state.value.cookbooks.map { it.recipeCount })

        send(viewModel, FavoritesIntent.ToggleCookbook(desserts))
        assertEquals(setOf(weeknight), viewModel.state.value.cookbookPicker?.cookbookIds)

        send(viewModel, FavoritesIntent.CloseCookbookPicker)
        assertNull(viewModel.state.value.cookbookPicker)
    }

    @Test
    fun aCookbookCreatedFromThePickerIsFiledWithTheRecipe() = runTest {
        val viewModel = viewModel()
        send(viewModel, FavoritesIntent.OpenCookbookPicker(recipeSummary(1)))

        send(viewModel, FavoritesIntent.StartNewCookbook(recipeToAdd = recipeSummary(1)))
        send(viewModel, FavoritesIntent.SaveCookbookName("Desserts"))

        with(viewModel.state.value) {
            assertNull(nameEditor)
            val desserts = cookbooks.single()
            assertTrue(assertNotNull(cookbookPicker).contains(desserts.id), "the picker shows the new cookbook ticked")
            assertNull(selectedCookbookId, "creating from the picker does not switch the grid")
        }
    }

    @Test
    fun selectingACookbookShowsOnlyItsRecipes() = runTest {
        val desserts = cookbook("Desserts")
        cookbooks.addRecipe(2, setOf(desserts))
        val viewModel = viewModel()

        send(viewModel, FavoritesIntent.SelectCookbook(desserts))
        assertEquals(listOf(2), viewModel.state.value.visibleRecipes.map { it.id })
        assertEquals("Desserts", viewModel.state.value.selectedCookbook?.name)

        send(viewModel, FavoritesIntent.SelectCookbook(null))
        assertEquals(listOf(1, 2), viewModel.state.value.visibleRecipes.map { it.id })
    }

    @Test
    fun renameKeepsTheSelection() = runTest {
        val desserts = cookbook("Desserts")
        val viewModel = viewModel()
        send(viewModel, FavoritesIntent.SelectCookbook(desserts))

        send(viewModel, FavoritesIntent.StartRenameCookbook(desserts))
        assertEquals(CookbookNameEditor(cookbookId = desserts, initialName = "Desserts"), viewModel.state.value.nameEditor)
        send(viewModel, FavoritesIntent.SaveCookbookName("Sweet Things"))

        with(viewModel.state.value) {
            assertNull(nameEditor)
            assertEquals("Sweet Things", selectedCookbook?.name)
        }
    }

    @Test
    fun renamingToTheSameNameInAnotherCaseIsAllowed() = runTest {
        val desserts = cookbook("desserts")
        val viewModel = viewModel()

        send(viewModel, FavoritesIntent.StartRenameCookbook(desserts))
        send(viewModel, FavoritesIntent.SaveCookbookName("Desserts"))

        assertNull(viewModel.state.value.nameEditor)
        assertEquals(listOf("Desserts"), viewModel.state.value.cookbooks.map { it.name })
    }

    @Test
    fun deletingTheSelectedCookbookFallsBackToAllFavoritesAndKeepsTheRecipes() = runTest {
        val desserts = cookbook("Desserts")
        cookbooks.addRecipe(1, setOf(desserts))
        val viewModel = viewModel()
        send(viewModel, FavoritesIntent.SelectCookbook(desserts))

        send(viewModel, FavoritesIntent.DeleteCookbook(desserts))

        with(viewModel.state.value) {
            assertEquals(emptyList(), cookbooks)
            assertNull(selectedCookbookId)
            assertEquals(listOf(1, 2), visibleRecipes.map { it.id })
        }
    }

    @Test
    fun undoingARemovalPutsTheRecipeBackInItsCookbooks() = runTest {
        val desserts = cookbook("Desserts")
        val weeknight = cookbook("Quick Weeknight")
        cookbooks.addRecipe(1, setOf(desserts, weeknight))
        val viewModel = viewModel()

        send(viewModel, FavoritesIntent.Remove(recipeSummary(1)))
        assertEquals(emptySet(), cookbooks.cookbookIds(1), "removing a favorite unfiles it")

        send(viewModel, FavoritesIntent.UndoRemove(recipeSummary(1)))

        assertEquals(setOf(desserts, weeknight), cookbooks.cookbookIds(1))
        assertEquals(listOf(1, 1), viewModel.state.value.cookbooks.map { it.recipeCount })
    }
}
