package com.ankitt.thefoodhub.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class SearchResponseDto(
    val results: List<RecipeSummaryDto> = emptyList(),
    val offset: Int = 0,
    val number: Int = 0,
    val totalResults: Int = 0,
)

/** `GET recipes/random`: each recipe has the same shape as `GET recipes/{id}/information`. */
@Serializable
internal data class RandomRecipesResponseDto(
    val recipes: List<RecipeInformationDto> = emptyList(),
)

@Serializable
internal data class RecipeSummaryDto(
    val id: Int,
    val title: String,
    val image: String? = null,
    /** Only with `fillIngredients=true`. */
    val usedIngredientCount: Int? = null,
    val missedIngredientCount: Int? = null,
    /** Only when the search had nutrient filters; lists the filtered nutrients. */
    val nutrition: SearchNutritionDto? = null,
)

@Serializable
internal data class SearchNutritionDto(
    val nutrients: List<NutrientDto> = emptyList(),
)

@Serializable
internal data class NutrientDto(
    /** e.g. "Calories", "Protein", "Carbohydrates", "Fat". */
    val name: String,
    val amount: Double,
    val unit: String? = null,
)

@Serializable
internal data class RecipeInformationDto(
    val id: Int,
    val title: String,
    val image: String? = null,
    val servings: Int? = null,
    val readyInMinutes: Int? = null,
    val healthScore: Double? = null,
    /** 0–100. */
    val spoonacularScore: Double? = null,
    /** Not listed in the Recipe Information docs, so treated as optional. */
    val aggregateLikes: Int? = null,
    val sourceName: String? = null,
    val sourceUrl: String? = null,
    val summary: String? = null,
    /** Free-form HTML; used only when [analyzedInstructions] is empty. */
    val instructions: String? = null,
    val diets: List<String> = emptyList(),
    val cuisines: List<String> = emptyList(),
    val dishTypes: List<String> = emptyList(),
    val extendedIngredients: List<IngredientDto> = emptyList(),
    val analyzedInstructions: List<AnalyzedInstructionDto> = emptyList(),
)

@Serializable
internal data class IngredientDto(
    val id: Int? = null,
    val name: String? = null,
    val original: String? = null,
    /** File name only, e.g. "flour.png". See [INGREDIENT_IMAGE_BASE_URL]. */
    val image: String? = null,
)

@Serializable
internal data class AnalyzedInstructionDto(
    val name: String? = null,
    val steps: List<StepDto> = emptyList(),
)

@Serializable
internal data class StepDto(
    val number: Int,
    val step: String,
)

internal const val INGREDIENT_IMAGE_BASE_URL = "https://img.spoonacular.com/ingredients_100x100/"
