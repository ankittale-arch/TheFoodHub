package com.ankitt.thefoodhub.feature.calculator

import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.domain.model.IngredientSubstitutes
import com.ankitt.thefoodhub.domain.model.KitchenMath
import com.ankitt.thefoodhub.domain.model.MeasureUnit

/** The two calculators behind the segmented control. */
enum class CalculatorTool { Convert, Substitute }

data class CalculatorState(
    val tool: CalculatorTool = CalculatorTool.Convert,
    val conversion: ConversionState = ConversionState(),
    val substitutes: SubstitutesState = SubstitutesState(),
)

data class ConversionState(
    /** As typed; "1 1/2", "1/2", "2,5" and "2.5" are all understood. */
    val amountText: String = "1",
    val from: MeasureUnit = MeasureUnit.Cups,
    val to: MeasureUnit = MeasureUnit.Grams,
    /** Only used between mass and volume (see [needsIngredient]). */
    val ingredient: String = "flour",
    /** Ingredients to pick from: ones already saved on the device first, then common ones. */
    val suggestions: List<String> = CalculatorViewModel.COMMON_DENSITY_INGREDIENTS,
    val result: ConversionResult = ConversionResult.InvalidAmount,
) {
    /** Mass ↔ volume depends on the ingredient (a cup of flour weighs less than a cup of sugar). */
    val needsIngredient: Boolean get() = KitchenMath.needsDensity(from, to)
}

sealed interface ConversionResult {
    /** The amount is empty, zero, or can't be read. */
    data object InvalidAmount : ConversionResult

    /** Mass ↔ volume, but no ingredient is entered. */
    data object NeedsIngredient : ConversionResult

    /** The ingredient's density is not on the device yet; [ConversionIntent.Convert] looks it up (1 point). */
    data object NeedsLookup : ConversionResult

    data object Loading : ConversionResult

    data class Ready(
        /** Rounded for the kitchen by [KitchenMath.formatAmount], e.g. "312.5". */
        val amount: String,
        val unit: MeasureUnit,
        /** Whether to use the singular unit name ("1 cup"). */
        val isSingular: Boolean,
        /** Set for mass ↔ volume: what the result is based on, e.g. flour at "125" g per cup. */
        val basis: DensityBasis?,
    ) : ConversionResult

    /** Spoonacular can't convert this ingredient between mass and volume. */
    data class Unconvertible(val ingredient: String) : ConversionResult

    data class Failed(val error: DataError) : ConversionResult
}

/** The density a mass ↔ volume result used; saved on the device, so it works offline from then on. */
data class DensityBasis(val ingredient: String, val gramsPerCup: String)

data class SubstitutesState(
    val query: String = "",
    /** Ingredients to pick from: ones already saved on the device first, then common ones. */
    val suggestions: List<String> = CalculatorViewModel.COMMON_SUBSTITUTE_INGREDIENTS,
    val result: SubstitutesResult = SubstitutesResult.Idle,
)

sealed interface SubstitutesResult {
    /** Nothing looked up yet. */
    data object Idle : SubstitutesResult

    data class Loading(val ingredient: String) : SubstitutesResult

    data class Ready(val substitutes: IngredientSubstitutes) : SubstitutesResult

    data class NotFound(val ingredient: String) : SubstitutesResult

    data class Failed(val ingredient: String, val error: DataError) : SubstitutesResult
}

sealed interface CalculatorIntent {
    data class SelectTool(val tool: CalculatorTool) : CalculatorIntent
}

/**
 * Conversion intents. Changing the amount or the units updates the result as the user types,
 * because that never costs quota; only [Convert] or [SelectIngredient] may call Spoonacular.
 */
sealed interface ConversionIntent : CalculatorIntent {
    data class AmountChanged(val text: String) : ConversionIntent
    data class FromUnitChanged(val unit: MeasureUnit) : ConversionIntent
    data class ToUnitChanged(val unit: MeasureUnit) : ConversionIntent
    data object SwapUnits : ConversionIntent
    data class IngredientChanged(val text: String) : ConversionIntent

    /** A suggestion was tapped: use it and convert straight away. */
    data class SelectIngredient(val ingredient: String) : ConversionIntent

    /** Looks up the ingredient's density when it isn't saved yet; also the retry after a failure. */
    data object Convert : ConversionIntent
}

sealed interface SubstitutesIntent : CalculatorIntent {
    data class QueryChanged(val text: String) : SubstitutesIntent

    /** Looks up the current query; also the retry after a failure. */
    data object Find : SubstitutesIntent

    /** A suggestion was tapped: use it and look it up straight away. */
    data class SelectIngredient(val ingredient: String) : SubstitutesIntent
}
