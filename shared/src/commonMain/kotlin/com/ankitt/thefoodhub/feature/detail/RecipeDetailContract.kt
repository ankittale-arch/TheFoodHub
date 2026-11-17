package com.ankitt.thefoodhub.feature.detail

import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.domain.model.RecipeDetail
import com.ankitt.thefoodhub.domain.model.RecipeNotes

data class RecipeDetailState(
    val recipeId: Int,
    val recipe: RecipeDetail? = null,
    val isFavorite: Boolean = false,
    val isLoading: Boolean = false,
    /** Only meaningful when [recipe] is null; cached data is always preferred to an error screen. */
    val error: DataError? = null,
    /** The user's note and ingredient tweaks; shown only while the recipe is saved. */
    val notes: RecipeNotes = RecipeNotes.EMPTY,
    /** Open while the user writes the note or an ingredient tweak. */
    val noteEditor: NoteEditor? = null,
) {
    /** Notes belong to saved recipes. They are kept, but hidden, while the recipe is not saved. */
    val canTakeNotes: Boolean get() = recipe != null && isFavorite
}

/** The note or tweak prompt. The text field owns the text; this only seeds it. */
data class NoteEditor(
    /** The ingredient line being tweaked; null edits the recipe's note. */
    val ingredientLine: String? = null,
    val initialText: String = "",
) {
    val isTweak: Boolean get() = ingredientLine != null

    /** Whether there is something saved that "Remove" would delete. */
    val canRemove: Boolean get() = initialText.isNotEmpty()

    val maxLength: Int get() = if (isTweak) RecipeNotes.MAX_TWEAK_LENGTH else RecipeNotes.MAX_NOTE_LENGTH
}

sealed interface RecipeDetailIntent {
    data object Retry : RecipeDetailIntent
    data object ToggleFavorite : RecipeDetailIntent

    data object EditNote : RecipeDetailIntent
    data class EditIngredientTweak(val ingredientLine: String) : RecipeDetailIntent

    /** Saves the open note or tweak; blank text removes it. */
    data class SaveNoteEdit(val text: String) : RecipeDetailIntent
    data object CancelNoteEdit : RecipeDetailIntent
}

sealed interface RecipeDetailEffect {
    data class FavoriteChanged(val isFavorite: Boolean) : RecipeDetailEffect
}
