package com.ankitt.thefoodhub.data

/**
 * Chooses the food to look up a recipe's wine pairing for.
 *
 * Spoonacular's pairing endpoint only understands simple terms: "pork" returns a pairing, but a
 * full title such as "Cider-Braised Pork Shoulder With Apple Confit" does not. This reduces a
 * recipe to the term most likely to decide the wine: its main ingredient, then the dish, then
 * the cuisine.
 */
internal object WinePairingFoods {

    /** Checked in the title, then in the ingredient list. Most specific first: "steak" before "beef". */
    private val mainIngredients = listOf(
        "steak", "beef", "lamb", "veal", "venison", "pork", "ham", "bacon", "sausage",
        "duck", "turkey", "chicken",
        "salmon", "tuna", "trout", "cod", "halibut", "shrimp", "lobster", "crab", "scallop", "mussel", "oyster", "fish",
        "mushroom", "pasta", "chocolate", "cheese",
    )

    /** Checked in the title only: as ingredients ("curry powder") they don't define the dish. */
    private val dishes = listOf("pizza", "risotto", "burger", "curry", "chili", "taco", "lasagna", "paella")

    private val patterns: Map<String, Regex> = (mainIngredients + dishes).associateWith { term ->
        // Whole words with optional plural, so "ham" does not match "graham" but "scallops" matches "scallop".
        Regex("""\b${Regex.escape(term)}(s|es)?\b""", RegexOption.IGNORE_CASE)
    }

    fun forRecipe(
        title: String,
        ingredientNames: List<String>,
        cuisines: List<String>,
        dishTypes: List<String>,
    ): String? =
        mainIngredients.firstOrNull { patterns.getValue(it).containsMatchIn(title) }
            ?: dishes.firstOrNull { patterns.getValue(it).containsMatchIn(title) }
            ?: mainIngredients.firstOrNull { term -> ingredientNames.any { patterns.getValue(term).containsMatchIn(it) } }
            ?: cuisines.firstOrNull { it.isNotBlank() }?.trim()?.lowercase()
            ?: "dessert".takeIf { dishTypes.any { it.equals("dessert", ignoreCase = true) } }
}
