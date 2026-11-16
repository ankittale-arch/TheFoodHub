package com.ankitt.thefoodhub.data

import com.ankitt.thefoodhub.data.local.FavoriteEntity
import com.ankitt.thefoodhub.data.local.RecipeEntity
import com.ankitt.thefoodhub.data.local.StoredIngredient
import com.ankitt.thefoodhub.data.local.StoredStep
import com.ankitt.thefoodhub.data.local.StoredWineBottle
import com.ankitt.thefoodhub.data.local.WinePairingEntity
import com.ankitt.thefoodhub.data.remote.dto.INGREDIENT_IMAGE_BASE_URL
import com.ankitt.thefoodhub.data.remote.dto.RecipeInformationDto
import com.ankitt.thefoodhub.data.remote.dto.RecipeSummaryDto
import com.ankitt.thefoodhub.data.remote.dto.SearchResponseDto
import com.ankitt.thefoodhub.data.remote.dto.WinePairingDto
import com.ankitt.thefoodhub.domain.model.Ingredient
import com.ankitt.thefoodhub.domain.model.InstructionStep
import com.ankitt.thefoodhub.domain.model.Nutrient
import com.ankitt.thefoodhub.domain.model.NutrientAmount
import com.ankitt.thefoodhub.domain.model.PopularRecipe
import com.ankitt.thefoodhub.domain.model.RecipeDetail
import com.ankitt.thefoodhub.domain.model.RecipeMatch
import com.ankitt.thefoodhub.domain.model.RecipePage
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import com.ankitt.thefoodhub.domain.model.WineBottle
import com.ankitt.thefoodhub.domain.model.WinePairing
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
    winePairingFood = WinePairingFoods.forRecipe(
        title = title,
        ingredientNames = extendedIngredients.mapNotNull { it.name },
        cuisines = cuisines,
        dishTypes = dishTypes,
    ),
)

/** 0–100 → 0.0–5.0 with one decimal, e.g. 96.0 → 4.8. */
internal fun Double.toFiveStarRating(): Double =
    (coerceIn(0.0, 100.0) / 20.0 * 10).roundToInt() / 10.0

// Wine pairing: remote → local → domain

internal fun WinePairingDto.toEntity(food: String, cachedAtEpochMillis: Long): WinePairingEntity = WinePairingEntity(
    food = food,
    wines = pairedWines.map { it.trim() }.filter { it.isNotEmpty() },
    description = pairingText.trim(),
    suggestedBottle = productMatches.firstOrNull()?.let { product ->
        StoredWineBottle(
            title = product.title,
            price = product.price?.takeIf { it.isNotBlank() },
            imageUrl = product.imageUrl?.takeIf { it.isNotBlank() },
            rating = product.averageRating?.let { (it.coerceIn(0.0, 1.0) * 5 * 10).roundToInt() / 10.0 },
            ratingCount = product.ratingCount?.roundToInt(),
            link = product.link?.takeIf { it.isNotBlank() },
        )
    },
    cachedAtEpochMillis = cachedAtEpochMillis,
)

/** Null when the row records that Spoonacular has no pairing for the food. */
internal fun WinePairingEntity.toDomain(): WinePairing? {
    if (wines.isEmpty() && description.isBlank()) return null
    return WinePairing(
        food = food,
        wines = wines.toDisplayedWines(),
        explanation = description.withoutCategoryLabelSentence(),
        suggestedBottle = suggestedBottle?.let {
            WineBottle(
                title = it.title,
                // Normalised on read, so rows cached before this formatting existed display the same.
                price = it.price?.toTwoDecimalPrice(),
                imageUrl = it.imageUrl,
                rating = it.rating,
                ratingCount = it.ratingCount,
                link = it.link,
            )
        },
    )
}

private val priceRegex = Regex("""^(\D*)(\d+)(?:\.(\d{1,2}))?$""")

/** Spoonacular sends prices such as "$89.0"; shows them as "$89.00". Anything else is kept as sent. */
internal fun String.toTwoDecimalPrice(): String {
    val match = priceRegex.matchEntire(trim()) ?: return this
    val (currency, whole, cents) = match.destructured
    return "$currency$whole.${cents.padEnd(2, '0')}"
}

/**
 * Labels Spoonacular sometimes lists among the wines, e.g. for "chocolate":
 * "wine, alcoholic drink, ingredient, food product category, madeira, …".
 */
private val wineCategoryLabels = setOf(
    "wine", "alcoholic drink", "drink", "beverage", "ingredient", "food product category", "menu item type",
)

/** Broad styles that say less than a named wine, so they are listed after named ones. */
private val genericWineStyles = setOf("red wine", "white wine", "rose wine", "rosé wine", "dessert wine", "sparkling wine")

internal const val MAX_DISPLAYED_WINES = 3

/** Labels that only appear in the explanation when Spoonacular wrote it from [wineCategoryLabels]. */
private val categoryLabelRegex = Regex("""\b(alcoholic drink|food product category|menu item type|ingredient)\b""", RegexOption.IGNORE_CASE)

/**
 * Spoonacular builds its first sentence from the wine list, so category labels leak into it:
 * "White Wine, Alcoholic Drink, and Ingredient are great choices for Chicken. White meat is…".
 * Drops that sentence when it has such labels and keeps the rest.
 */
internal fun String.withoutCategoryLabelSentence(): String {
    val text = trim()
    val firstSentenceEnd = text.indexOf(". ").takeIf { it >= 0 } ?: return text
    val firstSentence = text.substring(0, firstSentenceEnd)
    return if (categoryLabelRegex.containsMatchIn(firstSentence)) text.substring(firstSentenceEnd + 2).trim() else text
}

/** Cleans Spoonacular's wine list for display: no category labels, named wines first, at most [MAX_DISPLAYED_WINES]. */
internal fun List<String>.toDisplayedWines(): List<String> =
    map { it.trim().lowercase() }
        .filter { it.isNotEmpty() && it !in wineCategoryLabels }
        .distinct()
        .sortedBy { it in genericWineStyles }
        .take(MAX_DISPLAYED_WINES)
        .map { it.toTitleCase() }

/** "pinot noir" → "Pinot Noir". */
private fun String.toTitleCase(): String =
    split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }

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
