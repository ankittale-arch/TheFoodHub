package com.ankitt.thefoodhub.domain.repository

import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.domain.model.IngredientSubstitutes
import kotlinx.coroutines.flow.Flow

/**
 * Data for the kitchen calculator: ingredient densities (for converting between mass and volume)
 * and ingredient substitutes.
 *
 * Every Spoonacular answer, including "unknown ingredient", is saved on the device and never
 * expires, so each ingredient costs quota once and then works offline. Ingredient names are
 * matched case- and whitespace-insensitively.
 */
interface KitchenRepository {

    /**
     * The saved density of [ingredient] in grams per US cup, without touching the network.
     * Null when it was never looked up; a saved "unknown" answer is also returned as null.
     */
    suspend fun savedGramsPerCup(ingredient: String): Double?

    /**
     * The density of [ingredient] in grams per US cup: saved, or else looked up on Spoonacular
     * (1 point). Succeeds with null when Spoonacular can't convert the ingredient.
     */
    suspend fun getGramsPerCup(ingredient: String): Outcome<Double?>

    /**
     * Substitutes for [ingredient]: saved, or else looked up on Spoonacular (1 point). Succeeds
     * with null when Spoonacular has none.
     */
    suspend fun getSubstitutes(ingredient: String): Outcome<IngredientSubstitutes?>

    /** Ingredients with a known density, most recently looked up first. */
    fun observeSavedDensityIngredients(): Flow<List<String>>

    /** Ingredients with saved substitutes, most recently looked up first. */
    fun observeSavedSubstituteIngredients(): Flow<List<String>>
}
