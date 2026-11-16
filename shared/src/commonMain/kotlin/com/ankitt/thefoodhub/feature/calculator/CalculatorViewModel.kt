package com.ankitt.thefoodhub.feature.calculator

import androidx.lifecycle.viewModelScope
import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.core.mvi.MviViewModel
import com.ankitt.thefoodhub.domain.model.KitchenMath
import com.ankitt.thefoodhub.domain.repository.KitchenRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The kitchen calculator: unit conversions and ingredient substitutes.
 *
 * Conversions within mass or within volume are worked out on the device and need no network.
 * Mass ↔ volume needs the ingredient's density, which is looked up on Spoonacular once per
 * ingredient, only on an explicit action (never per keystroke), and then saved, so later
 * conversions of that ingredient are instant and work offline. Substitutes work the same way.
 * Errors are shown inline, hence no effect type.
 */
class CalculatorViewModel(
    private val repository: KitchenRepository,
) : MviViewModel<CalculatorState, CalculatorIntent, Nothing>(CalculatorState()) {

    /** Densities by normalized ingredient; a null value means Spoonacular can't convert it. */
    private val densities = mutableMapOf<String, Double?>()

    /** Ingredients already checked against the device's saved densities. */
    private val savedChecked = mutableSetOf<String>()
    private var savedCheckJob: Job? = null

    private var lookupKey: String? = null
    private var lookupJob: Job? = null

    private var substitutesJob: Job? = null

    init {
        repository.observeSavedDensityIngredients()
            .onEach { saved ->
                updateConversion { copy(suggestions = suggestionsFrom(saved, COMMON_DENSITY_INGREDIENTS)) }
            }
            .launchIn(viewModelScope)
        repository.observeSavedSubstituteIngredients()
            .onEach { saved ->
                updateSubstitutes { copy(suggestions = suggestionsFrom(saved, COMMON_SUBSTITUTE_INGREDIENTS)) }
            }
            .launchIn(viewModelScope)
        recompute()
    }

    override fun onIntent(intent: CalculatorIntent) {
        when (intent) {
            is CalculatorIntent.SelectTool -> updateState { copy(tool = intent.tool) }
            is ConversionIntent -> onConversionIntent(intent)
            is SubstitutesIntent -> onSubstitutesIntent(intent)
        }
    }

    // Conversions

    private fun onConversionIntent(intent: ConversionIntent) {
        when (intent) {
            is ConversionIntent.AmountChanged -> updateConversion { copy(amountText = intent.text) }
            is ConversionIntent.FromUnitChanged -> updateConversion { copy(from = intent.unit) }
            is ConversionIntent.ToUnitChanged -> updateConversion { copy(to = intent.unit) }
            ConversionIntent.SwapUnits -> updateConversion { copy(from = to, to = from) }
            is ConversionIntent.IngredientChanged -> updateConversion { copy(ingredient = intent.text) }
            is ConversionIntent.SelectIngredient -> {
                updateConversion { copy(ingredient = intent.ingredient) }
                recompute()
                lookUpDensity()
                return
            }
            ConversionIntent.Convert -> {
                lookUpDensity()
                return
            }
        }
        recompute()
    }

    /** Works out the result from what is known, without spending quota. */
    private fun recompute() {
        val conversion = currentState.conversion
        val result = resultFor(conversion)
        updateConversion { copy(result = result) }

        // A density saved in an earlier session is free to use: check for it once per ingredient.
        val key = KitchenMath.normalizeIngredient(conversion.ingredient)
        if (result == ConversionResult.NeedsLookup && key !in savedChecked) {
            savedCheckJob?.cancel()
            savedCheckJob = viewModelScope.launch {
                val saved = repository.savedGramsPerCup(key)
                savedChecked += key
                if (saved != null) {
                    densities[key] = saved
                    recompute()
                }
            }
        }
    }

    private fun resultFor(conversion: ConversionState): ConversionResult {
        val amount = KitchenMath.parseAmount(conversion.amountText) ?: return ConversionResult.InvalidAmount
        if (!conversion.needsIngredient) {
            return ready(KitchenMath.convert(amount, conversion.from, conversion.to)!!, conversion, basis = null)
        }
        val key = KitchenMath.normalizeIngredient(conversion.ingredient)
        if (key.isEmpty()) return ConversionResult.NeedsIngredient
        if (key !in densities) {
            return if (key == lookupKey && lookupJob?.isActive == true) ConversionResult.Loading else ConversionResult.NeedsLookup
        }
        val gramsPerCup = densities[key] ?: return ConversionResult.Unconvertible(key)
        val converted = KitchenMath.convert(amount, conversion.from, conversion.to, gramsPerCup)
            ?: return ConversionResult.Unconvertible(key)
        return ready(converted, conversion, DensityBasis(key, KitchenMath.formatAmount(gramsPerCup)))
    }

    private fun ready(value: Double, conversion: ConversionState, basis: DensityBasis?): ConversionResult.Ready {
        val amount = KitchenMath.formatAmount(value)
        return ConversionResult.Ready(amount = amount, unit = conversion.to, isSingular = amount == "1", basis = basis)
    }

    /** Fetches the density when the current conversion needs one that isn't known yet. */
    private fun lookUpDensity() {
        val conversion = currentState.conversion
        val key = KitchenMath.normalizeIngredient(conversion.ingredient)
        if (!conversion.needsIngredient || key.isEmpty() || key in densities) return
        if (key == lookupKey && lookupJob?.isActive == true) return

        // Not cancelled when the ingredient changes: the answer is saved and worth keeping either way.
        lookupKey = key
        // Set before launching: on Main.immediate a saved answer can complete the job synchronously.
        updateConversion { copy(result = ConversionResult.Loading) }
        lookupJob = viewModelScope.launch {
            when (val outcome = repository.getGramsPerCup(key)) {
                is Outcome.Success -> {
                    densities[key] = outcome.value
                    recompute()
                }
                is Outcome.Failure -> {
                    if (KitchenMath.normalizeIngredient(currentState.conversion.ingredient) == key) {
                        updateConversion { copy(result = ConversionResult.Failed(outcome.error)) }
                    }
                }
            }
        }
    }

    // Substitutes

    private fun onSubstitutesIntent(intent: SubstitutesIntent) {
        when (intent) {
            is SubstitutesIntent.QueryChanged -> updateSubstitutes { copy(query = intent.text) }
            SubstitutesIntent.Find -> findSubstitutes()
            is SubstitutesIntent.SelectIngredient -> {
                updateSubstitutes { copy(query = intent.ingredient) }
                findSubstitutes()
            }
        }
    }

    private fun findSubstitutes() {
        val key = KitchenMath.normalizeIngredient(currentState.substitutes.query)
        if (key.isEmpty()) return
        val result = currentState.substitutes.result
        if (result is SubstitutesResult.Loading && result.ingredient == key) return

        substitutesJob?.cancel()
        updateSubstitutes { copy(result = SubstitutesResult.Loading(key)) }
        substitutesJob = viewModelScope.launch {
            val newResult = when (val outcome = repository.getSubstitutes(key)) {
                is Outcome.Success -> outcome.value?.let { SubstitutesResult.Ready(it) } ?: SubstitutesResult.NotFound(key)
                is Outcome.Failure -> SubstitutesResult.Failed(key, outcome.error)
            }
            updateSubstitutes { copy(result = newResult) }
        }
    }

    // Helpers

    private fun updateConversion(reduce: ConversionState.() -> ConversionState) =
        updateState { copy(conversion = conversion.reduce()) }

    private fun updateSubstitutes(reduce: SubstitutesState.() -> SubstitutesState) =
        updateState { copy(substitutes = substitutes.reduce()) }

    private fun suggestionsFrom(saved: List<String>, common: List<String>): List<String> =
        (saved + common).distinct().take(MAX_SUGGESTIONS)

    companion object {
        const val MAX_SUGGESTIONS = 8

        val COMMON_DENSITY_INGREDIENTS = listOf("flour", "sugar", "brown sugar", "butter", "rice", "oats", "honey", "milk")

        val COMMON_SUBSTITUTE_INGREDIENTS =
            listOf("butter", "egg", "buttermilk", "baking powder", "sour cream", "heavy cream", "brown sugar", "cornstarch")
    }
}
