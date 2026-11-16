package com.ankitt.thefoodhub.data.remote

import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.data.remote.dto.RandomRecipesResponseDto
import com.ankitt.thefoodhub.data.remote.dto.RecipeInformationDto
import com.ankitt.thefoodhub.data.remote.dto.SearchResponseDto
import com.ankitt.thefoodhub.domain.model.Nutrient
import com.ankitt.thefoodhub.domain.model.SearchCriteria
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Spoonacular endpoints used by the app. Point costs (free plan: 150/day):
 * complexSearch costs 1 + 0.01 per result, information costs 1, random costs 1 + 0.01 per recipe.
 */
internal class SpoonacularApi(private val client: HttpClient) {

    /**
     * Name, ingredient and nutrient search all go through complexSearch, which combines them and
     * pages with `offset`. Parameters for unused filters are left out.
     */
    suspend fun searchRecipes(criteria: SearchCriteria, offset: Int, number: Int): Outcome<SearchResponseDto> =
        safeApiCall {
            client.get("recipes/complexSearch") {
                criteria.query.trim().takeIf { it.isNotEmpty() }?.let { parameter("query", it) }
                if (criteria.ingredients.isNotEmpty()) {
                    parameter("includeIngredients", criteria.ingredients.joinToString(","))
                    parameter("sort", "max-used-ingredients")
                    // Adds used/missed ingredient counts to each result, for a small extra cost per result.
                    parameter("fillIngredients", true)
                    // Salt, water, flour and the like should not count as missing.
                    parameter("ignorePantry", true)
                }
                criteria.nutrients.activeNutrients.forEach { nutrient ->
                    val range = criteria.nutrients.range(nutrient)
                    range.min?.let { parameter("min${nutrient.apiSuffix}", it) }
                    range.max?.let { parameter("max${nutrient.apiSuffix}", it) }
                }
                parameter("offset", offset)
                parameter("number", number)
            }
        }

    /**
     * "Find random (popular) recipes." Tags can be diets, meal types, cuisines, or intolerances,
     * e.g. `vegetarian`, `dessert`. Nutrition is never requested, since it would cost 0.5 points per recipe.
     */
    suspend fun getRandomRecipes(
        number: Int,
        includeTags: List<String> = emptyList(),
        excludeTags: List<String> = emptyList(),
    ): Outcome<RandomRecipesResponseDto> =
        safeApiCall {
            client.get("recipes/random") {
                parameter("number", number)
                // Spoonacular's names for these two are hyphenated, unlike its other parameters.
                if (includeTags.isNotEmpty()) parameter("include-tags", includeTags.joinToString(","))
                if (excludeTags.isNotEmpty()) parameter("exclude-tags", excludeTags.joinToString(","))
                parameter("includeNutrition", false)
            }
        }

    suspend fun getRecipeInformation(id: Int): Outcome<RecipeInformationDto> =
        safeApiCall {
            client.get("recipes/$id/information") {
                parameter("includeNutrition", false)
            }
        }
}

/** The name complexSearch uses in its `min…` / `max…` parameters. */
private val Nutrient.apiSuffix: String
    get() = when (this) {
        Nutrient.CALORIES -> "Calories"
        Nutrient.PROTEIN -> "Protein"
        Nutrient.CARBS -> "Carbs"
        Nutrient.FAT -> "Fat"
    }
