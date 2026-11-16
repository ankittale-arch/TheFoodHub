package com.ankitt.thefoodhub.feature

import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.domain.model.IngredientSubstitutes
import com.ankitt.thefoodhub.domain.model.MeasureUnit
import com.ankitt.thefoodhub.feature.calculator.CalculatorIntent
import com.ankitt.thefoodhub.feature.calculator.CalculatorTool
import com.ankitt.thefoodhub.feature.calculator.CalculatorViewModel
import com.ankitt.thefoodhub.feature.calculator.ConversionIntent
import com.ankitt.thefoodhub.feature.calculator.ConversionResult
import com.ankitt.thefoodhub.feature.calculator.DensityBasis
import com.ankitt.thefoodhub.feature.calculator.SubstitutesIntent
import com.ankitt.thefoodhub.feature.calculator.SubstitutesResult
import com.ankitt.thefoodhub.testutil.FakeKitchenRepository
import com.ankitt.thefoodhub.testutil.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalculatorViewModelTest : MainDispatcherTest() {

    private val repository = FakeKitchenRepository()

    private fun CalculatorViewModel.conversionResult() = state.value.conversion.result
    private fun CalculatorViewModel.substitutesResult() = state.value.substitutes.result

    @Test
    fun startsOnFlourCupsToGramsWaitingForALookup() = runTest {
        val viewModel = CalculatorViewModel(repository)
        advanceUntilIdle()

        assertEquals(CalculatorTool.Convert, viewModel.state.value.tool)
        assertEquals(ConversionResult.NeedsLookup, viewModel.conversionResult())
        assertTrue(repository.densityCalls.isEmpty(), "Opening the screen must not spend quota")
    }

    @Test
    fun sameDimensionConvertsLocallyAsYouType() = runTest {
        val viewModel = CalculatorViewModel(repository)
        viewModel.onIntent(ConversionIntent.ToUnitChanged(MeasureUnit.Tablespoons))
        viewModel.onIntent(ConversionIntent.AmountChanged("1/2"))

        assertEquals(
            ConversionResult.Ready(amount = "8", unit = MeasureUnit.Tablespoons, isSingular = false, basis = null),
            viewModel.conversionResult(),
        )
        assertTrue(repository.densityCalls.isEmpty())
    }

    @Test
    fun invalidAmountAndMissingIngredientAreReported() = runTest {
        val viewModel = CalculatorViewModel(repository)

        viewModel.onIntent(ConversionIntent.AmountChanged("abc"))
        assertEquals(ConversionResult.InvalidAmount, viewModel.conversionResult())

        viewModel.onIntent(ConversionIntent.AmountChanged("1"))
        viewModel.onIntent(ConversionIntent.IngredientChanged("  "))
        assertEquals(ConversionResult.NeedsIngredient, viewModel.conversionResult())
    }

    @Test
    fun typingAnIngredientNeverCallsSpoonacular() = runTest {
        val viewModel = CalculatorViewModel(repository)
        "sugar".indices.forEach { end -> viewModel.onIntent(ConversionIntent.IngredientChanged("sugar".substring(0, end + 1))) }
        advanceUntilIdle()

        assertTrue(repository.densityCalls.isEmpty())
        assertEquals(ConversionResult.NeedsLookup, viewModel.conversionResult())
    }

    @Test
    fun convertLooksUpTheDensityOnceThenRecomputesForFree() = runTest {
        val gate = CompletableDeferred<Outcome<Double?>>()
        repository.onGetGramsPerCup = { gate.await() }
        val viewModel = CalculatorViewModel(repository)
        viewModel.onIntent(ConversionIntent.AmountChanged("2.5"))

        viewModel.onIntent(ConversionIntent.Convert)
        advanceUntilIdle()
        assertEquals(ConversionResult.Loading, viewModel.conversionResult())

        gate.complete(Outcome.Success(125.0))
        advanceUntilIdle()
        assertEquals(
            ConversionResult.Ready("313", MeasureUnit.Grams, isSingular = false, basis = DensityBasis("flour", "125")),
            viewModel.conversionResult(),
        )

        viewModel.onIntent(ConversionIntent.SwapUnits)
        viewModel.onIntent(ConversionIntent.AmountChanged("125"))
        viewModel.onIntent(ConversionIntent.Convert)
        advanceUntilIdle()

        assertEquals(
            ConversionResult.Ready("1", MeasureUnit.Cups, isSingular = true, basis = DensityBasis("flour", "125")),
            viewModel.conversionResult(),
        )
        assertEquals(listOf("flour"), repository.densityCalls)
    }

    @Test
    fun aDensitySavedInAnEarlierSessionIsUsedWithoutAsking() = runTest {
        repository.saved["flour"] = 125.0
        val viewModel = CalculatorViewModel(repository)
        advanceUntilIdle()

        assertEquals(
            ConversionResult.Ready("125", MeasureUnit.Grams, isSingular = false, basis = DensityBasis("flour", "125")),
            viewModel.conversionResult(),
        )
        assertTrue(repository.densityCalls.isEmpty())
    }

    @Test
    fun selectingASuggestionConvertsStraightAway() = runTest {
        repository.onGetGramsPerCup = { Outcome.Success(200.0) }
        val viewModel = CalculatorViewModel(repository)

        viewModel.onIntent(ConversionIntent.SelectIngredient("Sugar"))
        advanceUntilIdle()

        assertEquals("sugar", (viewModel.conversionResult() as ConversionResult.Ready).basis?.ingredient)
        assertEquals(listOf("sugar"), repository.densityCalls)
    }

    @Test
    fun unconvertibleAndFailedLookups() = runTest {
        repository.onGetGramsPerCup = { Outcome.Success(null) }
        val viewModel = CalculatorViewModel(repository)
        viewModel.onIntent(ConversionIntent.Convert)
        advanceUntilIdle()
        assertEquals(ConversionResult.Unconvertible("flour"), viewModel.conversionResult())

        repository.onGetGramsPerCup = { Outcome.Failure(DataError.Network) }
        viewModel.onIntent(ConversionIntent.IngredientChanged("rice"))
        viewModel.onIntent(ConversionIntent.Convert)
        advanceUntilIdle()
        assertEquals(ConversionResult.Failed(DataError.Network), viewModel.conversionResult())

        // Retry works once the network is back.
        repository.onGetGramsPerCup = { Outcome.Success(185.0) }
        viewModel.onIntent(ConversionIntent.Convert)
        advanceUntilIdle()
        assertTrue(viewModel.conversionResult() is ConversionResult.Ready)
    }

    @Test
    fun suggestionsPutSavedIngredientsFirst() = runTest {
        val viewModel = CalculatorViewModel(repository)
        repository.savedDensityIngredients.value = listOf("quinoa", "sugar")
        repository.savedSubstituteIngredients.value = listOf("molasses")
        advanceUntilIdle()

        with(viewModel.state.value) {
            assertEquals(listOf("quinoa", "sugar", "flour", "brown sugar"), conversion.suggestions.take(4))
            assertEquals(CalculatorViewModel.MAX_SUGGESTIONS, conversion.suggestions.size)
            assertEquals("molasses", substitutes.suggestions.first())
        }
    }

    @Test
    fun findsSubstitutesOnlyWhenAsked() = runTest {
        val viewModel = CalculatorViewModel(repository)
        viewModel.onIntent(CalculatorIntent.SelectTool(CalculatorTool.Substitute))
        viewModel.onIntent(SubstitutesIntent.QueryChanged(" Butter "))
        advanceUntilIdle()
        assertEquals(SubstitutesResult.Idle, viewModel.substitutesResult())

        viewModel.onIntent(SubstitutesIntent.Find)
        assertEquals(SubstitutesResult.Loading("butter"), viewModel.substitutesResult())
        advanceUntilIdle()

        assertEquals(
            SubstitutesResult.Ready(IngredientSubstitutes("butter", listOf("1 cup = 1 cup margarine"))),
            viewModel.substitutesResult(),
        )
        assertEquals(listOf("butter"), repository.substituteCalls)
    }

    @Test
    fun substitutesNotFoundFailedAndBlank() = runTest {
        val viewModel = CalculatorViewModel(repository)

        viewModel.onIntent(SubstitutesIntent.Find)
        advanceUntilIdle()
        assertTrue(repository.substituteCalls.isEmpty(), "A blank query is not looked up")

        repository.onGetSubstitutes = { Outcome.Success(null) }
        viewModel.onIntent(SubstitutesIntent.SelectIngredient("Saffron"))
        advanceUntilIdle()
        assertEquals(SubstitutesResult.NotFound("saffron"), viewModel.substitutesResult())
        assertEquals("Saffron", viewModel.state.value.substitutes.query)

        repository.onGetSubstitutes = { Outcome.Failure(DataError.QuotaExceeded) }
        viewModel.onIntent(SubstitutesIntent.SelectIngredient("egg"))
        advanceUntilIdle()
        assertEquals(SubstitutesResult.Failed("egg", DataError.QuotaExceeded), viewModel.substitutesResult())
    }
}
