package com.ankitt.thefoodhub.domain.model

/**
 * Everything a recipe search filters on. Any combination works, e.g. a name only,
 * ingredients only, or "pasta" with chicken and at most 600 kcal.
 */
data class SearchCriteria(
    val query: String = "",
    /** Normalized with [IngredientCatalog.normalize]: lowercase, trimmed, no duplicates. */
    val ingredients: List<String> = emptyList(),
    val nutrients: NutrientFilter = NutrientFilter(),
) {
    val isEmpty: Boolean get() = query.isBlank() && ingredients.isEmpty() && nutrients.isEmpty

    companion object {
        /**
         * The query as Spoonacular sees it: trimmed, lowercase, single spaces. Spoonacular ignores
         * case and spacing, so "Pasta " and "pasta" are one search and should cost one request.
         */
        fun normalizeQuery(text: String): String =
            text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }
}

/**
 * The nutrients the search can filter on.
 *
 * Filter controls offer bounds from 0 to [filterCeiling] in steps of [filterStep]. A minimum of 0
 * or a maximum at the ceiling means "no limit" for that side of the range.
 */
enum class Nutrient(val unit: String, val filterCeiling: Int, val filterStep: Int) {
    CALORIES(unit = "kcal", filterCeiling = 1500, filterStep = 50),
    PROTEIN(unit = "g", filterCeiling = 100, filterStep = 5),
    CARBS(unit = "g", filterCeiling = 150, filterStep = 5),
    FAT(unit = "g", filterCeiling = 100, filterStep = 5),
}

/** A bound left null is unlimited. Per serving, as Spoonacular reports it. */
data class NutrientRange(val min: Int? = null, val max: Int? = null) {
    val isEmpty: Boolean get() = min == null && max == null
}

/** One field per nutrient rather than a map, so Swift reads it without boxing. */
data class NutrientFilter(
    val calories: NutrientRange = NutrientRange(),
    val protein: NutrientRange = NutrientRange(),
    val carbs: NutrientRange = NutrientRange(),
    val fat: NutrientRange = NutrientRange(),
) {
    val isEmpty: Boolean get() = activeNutrients.isEmpty()

    /** The nutrients with at least one bound, in [Nutrient] order. */
    val activeNutrients: List<Nutrient> get() = Nutrient.entries.filterNot { range(it).isEmpty }

    fun range(nutrient: Nutrient): NutrientRange = when (nutrient) {
        Nutrient.CALORIES -> calories
        Nutrient.PROTEIN -> protein
        Nutrient.CARBS -> carbs
        Nutrient.FAT -> fat
    }

    fun with(nutrient: Nutrient, range: NutrientRange): NutrientFilter = when (nutrient) {
        Nutrient.CALORIES -> copy(calories = range)
        Nutrient.PROTEIN -> copy(protein = range)
        Nutrient.CARBS -> copy(carbs = range)
        Nutrient.FAT -> copy(fat = range)
    }

    // Presets merge into the filter, so "High protein" and "Low carb" can both be on.

    fun hasPreset(preset: NutrientPreset): Boolean =
        preset.filter.activeNutrients.all { range(it) == preset.filter.range(it) }

    fun applying(preset: NutrientPreset): NutrientFilter =
        preset.filter.activeNutrients.fold(this) { filter, nutrient -> filter.with(nutrient, preset.filter.range(nutrient)) }

    fun removing(preset: NutrientPreset): NutrientFilter =
        preset.filter.activeNutrients.fold(this) { filter, nutrient -> filter.with(nutrient, NutrientRange()) }

    companion object {
        /** For Swift, which does not see Kotlin default arguments. */
        val NONE: NutrientFilter = NutrientFilter()
    }
}

/** One-tap nutrient filters shown in the nutrition sheet. */
enum class NutrientPreset(val filter: NutrientFilter) {
    HIGH_PROTEIN(NutrientFilter(protein = NutrientRange(min = 30))),
    LOW_CARB(NutrientFilter(carbs = NutrientRange(max = 20))),
    UNDER_500_KCAL(NutrientFilter(calories = NutrientRange(max = 500))),
    LOW_FAT(NutrientFilter(fat = NutrientRange(max = 15))),
}

/** Per-result details that explain why a recipe matched the filters. */
data class RecipeMatch(
    /** Set when the search had ingredients. */
    val usedIngredientCount: Int? = null,
    val missedIngredientCount: Int? = null,
    /** Amounts per serving for the nutrients the search filtered on. */
    val nutrients: List<NutrientAmount> = emptyList(),
)

data class NutrientAmount(val nutrient: Nutrient, val amount: Double)

/**
 * Ingredient suggestions come from this local list instead of Spoonacular's autocomplete,
 * which costs a quota point per keystroke.
 */
object IngredientCatalog {

    const val MAX_INGREDIENTS = 10

    val common: List<String> = listOf(
        "chicken", "egg", "rice", "pasta", "potato", "tomato", "onion", "garlic", "cheese", "beef",
        "pork", "salmon", "shrimp", "tofu", "spinach", "broccoli", "carrot", "mushroom", "bell pepper",
        "zucchini", "avocado", "lemon", "lime", "milk", "butter", "yogurt", "bread", "flour", "oats",
        "chickpeas", "lentils", "black beans", "coconut milk", "ginger", "basil", "cilantro", "apple",
        "banana", "berries", "peanut butter", "bacon", "sausage", "tuna", "cucumber", "lettuce",
        "corn", "peas", "sweet potato", "cauliflower", "quinoa",
    )

    /** Lowercase and trimmed with single spaces, or null when blank. */
    fun normalize(name: String): String? =
        name.trim().lowercase().split(Regex("\\s+")).joinToString(" ").takeIf { it.isNotEmpty() }

    /**
     * Common ingredients containing [input], those starting with it first, skipping [exclude].
     * With blank input, returns the first few common ingredients.
     */
    fun suggestions(input: String, exclude: List<String>, limit: Int = 8): List<String> {
        val term = normalize(input)
        val candidates = common.filterNot { it in exclude }
        if (term == null) return candidates.take(limit)
        return candidates
            .filter { term in it }
            .sortedBy { if (it.startsWith(term)) 0 else 1 }
            .take(limit)
    }

    /** Adds [name] to [current] unless it is blank, already there, or the list is full. */
    fun add(current: List<String>, name: String): List<String> {
        val normalized = normalize(name) ?: return current
        if (normalized in current || current.size >= MAX_INGREDIENTS) return current
        return current + normalized
    }
}
