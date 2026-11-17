package com.ankitt.thefoodhub.feature.favorites

import com.ankitt.thefoodhub.domain.model.Cookbook
import com.ankitt.thefoodhub.domain.model.CookbookNameError
import com.ankitt.thefoodhub.domain.model.RecipeSummary

data class FavoritesState(
    val favorites: List<RecipeSummary> = emptyList(),
    val isLoading: Boolean = true,
    val cookbooks: List<Cookbook> = emptyList(),
    /** The cookbook being shown; null shows every favorite. */
    val selectedCookbookId: Long? = null,
    /** Recipes in the selected cookbook, most recently added first. */
    val cookbookRecipes: List<RecipeSummary> = emptyList(),
    /** Open while the user files a recipe into cookbooks. */
    val cookbookPicker: CookbookPicker? = null,
    /** Open while the user names a new cookbook or renames one. */
    val nameEditor: CookbookNameEditor? = null,
) {
    val selectedCookbook: Cookbook?
        get() = selectedCookbookId?.let { id -> cookbooks.firstOrNull { it.id == id } }

    /** What the grid shows: every favorite, or the selected cookbook's recipes. */
    val visibleRecipes: List<RecipeSummary>
        get() = if (selectedCookbookId == null) favorites else cookbookRecipes
}

/** The "Add to cookbook" sheet for [recipe]; [cookbookIds] are the cookbooks it is already in. */
data class CookbookPicker(
    val recipe: RecipeSummary,
    val cookbookIds: Set<Long>,
) {
    fun contains(cookbookId: Long): Boolean = cookbookId in cookbookIds
}

/** The name prompt. The text field owns the text; this only seeds it and reports errors. */
data class CookbookNameEditor(
    /** The cookbook being renamed; null names a new one. */
    val cookbookId: Long? = null,
    val initialName: String = "",
    /** A new cookbook is filed with this recipe as soon as it is created (when started from the picker). */
    val recipeToAdd: RecipeSummary? = null,
    val error: CookbookNameError? = null,
) {
    val isRename: Boolean get() = cookbookId != null
}

sealed interface FavoritesIntent {
    data class Remove(val recipe: RecipeSummary) : FavoritesIntent
    data class UndoRemove(val recipe: RecipeSummary) : FavoritesIntent

    /** Null shows every favorite. */
    data class SelectCookbook(val cookbookId: Long?) : FavoritesIntent

    data class OpenCookbookPicker(val recipe: RecipeSummary) : FavoritesIntent
    data object CloseCookbookPicker : FavoritesIntent

    /** Adds the picker's recipe to the cookbook, or takes it out if it is already there. */
    data class ToggleCookbook(val cookbookId: Long) : FavoritesIntent

    /** Opens the name prompt for a new cookbook, filed with [recipeToAdd] once created. */
    data class StartNewCookbook(val recipeToAdd: RecipeSummary? = null) : FavoritesIntent
    data class StartRenameCookbook(val cookbookId: Long) : FavoritesIntent
    data class SaveCookbookName(val name: String) : FavoritesIntent
    data object CancelCookbookName : FavoritesIntent

    /** Deletes the cookbook; its recipes stay in favorites. */
    data class DeleteCookbook(val cookbookId: Long) : FavoritesIntent
}

sealed interface FavoritesEffect {
    /** The UI can offer an undo that sends [FavoritesIntent.UndoRemove]. */
    data class Removed(val recipe: RecipeSummary) : FavoritesEffect
}
