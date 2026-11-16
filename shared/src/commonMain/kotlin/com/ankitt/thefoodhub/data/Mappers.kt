package com.ankitt.thefoodhub.data

import com.ankitt.thefoodhub.data.local.FavoriteEntity
import com.ankitt.thefoodhub.data.local.RecipeEntity
import com.ankitt.thefoodhub.data.local.StoredIngredient
import com.ankitt.thefoodhub.data.local.StoredStep
import com.ankitt.thefoodhub.data.remote.dto.INGREDIENT_IMAGE_BASE_URL
import com.ankitt.thefoodhub.data.remote.dto.RecipeInformationDto
import com.ankitt.thefoodhub.data.remote.dto.RecipeSummaryDto
import com.ankitt.thefoodhub.data.remote.dto.SearchResponseDto
import com.ankitt.thefoodhub.domain.model.Ingredient
import com.ankitt.thefoodhub.domain.model.InstructionStep
import com.ankitt.thefoodhub.domain.model.Nutrient
import com.ankitt.thefoodhub.domain.model.NutrientAmount
import com.ankitt.thefoodhub.domain.model.PopularRecipe
import com.ankitt.thefoodhub.domain.model.RecipeDetail
import com.ankitt.thefoodhub.domain.model.RecipeMatch
import com.ankitt.thefoodhub.domain.model.RecipePage
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import kotlin.math.roundToInt

// Remote → domain

internal fun SearchResponseDto.toDomain(): RecipePage = RecipePage(
    recipes = results.map { it.toDomain() },
    offset = offset,
    totalResults = totalResults,
    matches = results.mapNotNull { dto -> dto.toMatch()?.let { dto.id to it } }.toMap(),
)

/** Null when the response has no match details, as for a name-only search. */
internal fun RecipeSummaryDto.toMatch(): RecipeMatch? {
    val nutrients = nutrition?.nutrients.orEmpty().mapNotNull { dto ->
        nutrientForApiName(dto.name)?.let { NutrientAmount(it, dto.amount) }
    }.sortedBy { it.nutrient.ordinal }
    if (usedIngredientCount == null && missedIngredientCount == null && nutrients.isEmpty()) return null
    return RecipeMatch(
        usedIngredientCount = usedIngredientCount,
        missedIngredientCount = missedIngredientCount,
        nutrients = nutrients,
    )
}

private fun nutrientForApiName(name: String): Nutrient? = when (name.lowercase()) {
    "calories" -> Nutrient.CALORIES
    "protein" -> Nutrient.PROTEIN
    "carbohydrates", "carbs" -> Nutrient.CARBS
    "fat" -> Nutrient.FAT
    else -> null
}

internal fun RecipeSummaryDto.toDomain(): RecipeSummary =
    RecipeSummary(id = id, title = title, imageUrl = image?.takeIf { it.isNotBlank() })

internal fun RecipeInformationDto.toPopularRecipe(): PopularRecipe = PopularRecipe(
    id = id,
    title = title,
    imageUrl = image?.takeIf { it.isNotBlank() },
    readyInMinutes = readyInMinutes,
    likes = aggregateLikes,
    rating = spoonacularScore?.toFiveStarRating(),
)

/** 0–100 → 0.0–5.0 with one decimal, e.g. 96.0 → 4.8. */
internal fun Double.toFiveStarRating(): Double =
    (coerceIn(0.0, 100.0) / 20.0 * 10).roundToInt() / 10.0

// Remote → local

internal fun RecipeInformationDto.toEntity(cachedAtEpochMillis: Long): RecipeEntity = RecipeEntity(
    id = id,
    title = title,
    imageUrl = image?.takeIf { it.isNotBlank() },
    readyInMinutes = readyInMinutes,
    servings = servings,
    healthScore = healthScore?.roundToInt(),
    sourceName = sourceName,
    sourceUrl = sourceUrl,
    summaryHtml = summary.orEmpty(),
    diets = diets,
    cuisines = cuisines,
    dishTypes = dishTypes,
    ingredients = extendedIngredients.mapNotNull { dto ->
        val original = dto.original?.takeIf { it.isNotBlank() } ?: dto.name ?: return@mapNotNull null
        StoredIngredient(
            name = dto.name ?: original,
            original = original,
            imageUrl = dto.image?.takeIf { it.isNotBlank() }?.let { INGREDIENT_IMAGE_BASE_URL + it },
        )
    },
    steps = toSteps(),
    cachedAtEpochMillis = cachedAtEpochMillis,
)

/** Prefers structured steps; otherwise falls back to the free-text HTML instructions. */
private fun RecipeInformationDto.toSteps(): List<StoredStep> {
    val structured = analyzedInstructions.flatMap { it.steps }
    if (structured.isNotEmpty()) {
        // Multi-part recipes restart numbering per part, so renumber sequentially.
        return structured.mapIndexed { index, step -> StoredStep(number = index + 1, text = step.step.trim()) }
    }
    return instructions.orEmpty()
        .stripHtml()
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapIndexed { index, text -> StoredStep(number = index + 1, text = text) }
}

private val blockTagRegex = Regex("(?i)</?(li|p|br|ol|ul|div)[^>]*>")
private val anyTagRegex = Regex("<[^>]+>")

internal fun String.stripHtml(): String =
    replace(blockTagRegex, "\n").replace(anyTagRegex, "")

// Local → domain

internal fun RecipeEntity.toDomain(): RecipeDetail = RecipeDetail(
    id = id,
    title = title,
    imageUrl = imageUrl,
    readyInMinutes = readyInMinutes,
    servings = servings,
    healthScore = healthScore,
    sourceName = sourceName,
    sourceUrl = sourceUrl,
    summaryHtml = summaryHtml,
    diets = diets,
    cuisines = cuisines,
    dishTypes = dishTypes,
    ingredients = ingredients.map { Ingredient(name = it.name, original = it.original, imageUrl = it.imageUrl) },
    steps = steps.map { InstructionStep(number = it.number, text = it.text) },
)

internal fun FavoriteEntity.toDomain(): RecipeSummary =
    RecipeSummary(id = recipeId, title = title, imageUrl = imageUrl)

internal fun RecipeSummary.toFavoriteEntity(savedAtEpochMillis: Long): FavoriteEntity =
    FavoriteEntity(recipeId = id, title = title, imageUrl = imageUrl, savedAtEpochMillis = savedAtEpochMillis)
