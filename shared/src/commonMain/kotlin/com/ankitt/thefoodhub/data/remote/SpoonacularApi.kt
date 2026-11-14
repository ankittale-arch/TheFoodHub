package com.ankitt.thefoodhub.data.remote

import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.data.remote.dto.RandomRecipesResponseDto
import com.ankitt.thefoodhub.data.remote.dto.RecipeInformationDto
import com.ankitt.thefoodhub.data.remote.dto.SearchResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Spoonacular endpoints used by the app. Point costs (free plan: 150/day):
 * complexSearch costs 1 + 0.01 per result, information costs 1, random costs 1 + 0.01 per recipe.
 */
internal class SpoonacularApi(private val client: HttpClient) {

    suspend fun searchRecipes(query: String, offset: Int, number: Int): Outcome<SearchResponseDto> =
        safeApiCall {
            client.get("recipes/complexSearch") {
                parameter("query", query)
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
