package com.ankitt.thefoodhub.domain.repository

import com.ankitt.thefoodhub.domain.model.RecipeNotes
import kotlinx.coroutines.flow.Flow

/**
 * Private notes and ingredient tweaks, stored only on the device. They are kept when a recipe is
 * removed from favorites, so saving it again brings them back.
 */
interface RecipeNotesRepository {

    /** Emits [RecipeNotes.EMPTY] when the recipe has none. */
    fun observeNotes(recipeId: Int): Flow<RecipeNotes>

    /** Trims [note]; a blank note deletes it. */
    suspend fun saveNote(recipeId: Int, note: String)

    /** Trims [tweak]; a blank tweak deletes it. */
    suspend fun saveTweak(recipeId: Int, ingredientLine: String, tweak: String)
}
