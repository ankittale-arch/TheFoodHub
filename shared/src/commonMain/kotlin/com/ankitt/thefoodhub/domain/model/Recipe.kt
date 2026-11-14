package com.ankitt.thefoodhub.domain.model

/** Minimal recipe info shown in lists (search results, favorites). */
data class RecipeSummary(
    val id: Int,
    val title: String,
    val imageUrl: String?,
)

/** One page of search results. */
data class RecipePage(
    val recipes: List<RecipeSummary>,
    val offset: Int,
    val totalResults: Int,
) {
    val nextOffset: Int get() = offset + recipes.size

    /** Spoonacular rejects offsets above [MAX_OFFSET], so stop paging there too. */
    val endReached: Boolean
        get() = recipes.isEmpty() || nextOffset >= totalResults || nextOffset > MAX_OFFSET

    companion object {
        const val MAX_OFFSET = 900
    }
}

/** A recipe card for the home screen: list data plus the stats the card shows. */
data class PopularRecipe(
    val id: Int,
    val title: String,
    val imageUrl: String?,
    val readyInMinutes: Int?,
    val likes: Int?,
    /** 0.0–5.0, converted from Spoonacular's 0–100 score. */
    val rating: Double?,
)

data class RecipeDetail(
    val id: Int,
    val title: String,
    val imageUrl: String?,
    val readyInMinutes: Int?,
    val servings: Int?,
    val healthScore: Int?,
    val sourceName: String?,
    val sourceUrl: String?,
    /** Spoonacular returns the summary as HTML (`<b>`, `<a>`). */
    val summaryHtml: String,
    val diets: List<String>,
    val cuisines: List<String>,
    val dishTypes: List<String>,
    val ingredients: List<Ingredient>,
    val steps: List<InstructionStep>,
)

data class Ingredient(
    val name: String,
    /** The ingredient line as written in the recipe, e.g. "2 cups of flour". */
    val original: String,
    val imageUrl: String?,
)

data class InstructionStep(
    val number: Int,
    val text: String,
)

fun PopularRecipe.toSummary(): RecipeSummary = RecipeSummary(id = id, title = title, imageUrl = imageUrl)

fun RecipeDetail.toSummary(): RecipeSummary = RecipeSummary(id = id, title = title, imageUrl = imageUrl)
