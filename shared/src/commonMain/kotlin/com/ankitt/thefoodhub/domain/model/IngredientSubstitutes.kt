package com.ankitt.thefoodhub.domain.model

/** Ways to replace an ingredient, e.g. butter → "1 cup = 1 cup margarine". */
data class IngredientSubstitutes(
    /** The ingredient as it was looked up, lowercase, e.g. "butter". */
    val ingredient: String,
    /** Spoonacular's suggestions, usually "amount = replacement", e.g. "1 cup = 7/8 cup shortening and 1/2 tsp salt". */
    val substitutes: List<String>,
)
