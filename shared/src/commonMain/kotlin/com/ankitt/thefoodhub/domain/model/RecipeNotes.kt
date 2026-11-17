package com.ankitt.thefoodhub.domain.model

/**
 * The user's own notes on a saved recipe: a free-text [note] ("Baked 5 mins less") and per-ingredient
 * [tweaks] ("Used half"). Tweaks are keyed by the ingredient line as the recipe writes it
 * ([Ingredient.original]), so two lines for the same ingredient keep separate tweaks.
 */
data class RecipeNotes(
    val note: String = "",
    val tweaks: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = note.isEmpty() && tweaks.isEmpty()

    fun tweakFor(ingredientLine: String): String? = tweaks[ingredientLine]

    companion object {
        val EMPTY = RecipeNotes()
        const val MAX_NOTE_LENGTH = 2000
        const val MAX_TWEAK_LENGTH = 200
    }
}
