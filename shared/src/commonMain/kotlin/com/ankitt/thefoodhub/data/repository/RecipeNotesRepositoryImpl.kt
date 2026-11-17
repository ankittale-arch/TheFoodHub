package com.ankitt.thefoodhub.data.repository

import com.ankitt.thefoodhub.data.local.IngredientTweakEntity
import com.ankitt.thefoodhub.data.local.RecipeNoteEntity
import com.ankitt.thefoodhub.data.local.RecipeNotesDao
import com.ankitt.thefoodhub.domain.model.RecipeNotes
import com.ankitt.thefoodhub.domain.repository.RecipeNotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Clock

internal class RecipeNotesRepositoryImpl(
    private val recipeNotesDao: RecipeNotesDao,
    private val clock: Clock,
) : RecipeNotesRepository {

    override fun observeNotes(recipeId: Int): Flow<RecipeNotes> =
        combine(recipeNotesDao.observeNote(recipeId), recipeNotesDao.observeTweaks(recipeId)) { note, tweaks ->
            RecipeNotes(
                note = note?.note.orEmpty(),
                tweaks = tweaks.associate { it.ingredientLine to it.tweak },
            )
        }.distinctUntilChanged()

    override suspend fun saveNote(recipeId: Int, note: String) {
        // The UI caps input at the limit; the cap here only guards other callers.
        val cleaned = note.trim().take(RecipeNotes.MAX_NOTE_LENGTH)
        if (cleaned.isEmpty()) {
            recipeNotesDao.deleteNote(recipeId)
        } else {
            recipeNotesDao.upsertNote(RecipeNoteEntity(recipeId, cleaned, updatedAtEpochMillis = now()))
        }
    }

    override suspend fun saveTweak(recipeId: Int, ingredientLine: String, tweak: String) {
        val cleaned = tweak.trim().take(RecipeNotes.MAX_TWEAK_LENGTH)
        if (cleaned.isEmpty()) {
            recipeNotesDao.deleteTweak(recipeId, ingredientLine)
        } else {
            recipeNotesDao.upsertTweak(IngredientTweakEntity(recipeId, ingredientLine, cleaned, updatedAtEpochMillis = now()))
        }
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()
}
