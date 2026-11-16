package com.ankitt.thefoodhub.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.domain.model.IngredientSubstitutes
import com.ankitt.thefoodhub.domain.model.MeasureUnit
import com.ankitt.thefoodhub.feature.calculator.CalculatorIntent
import com.ankitt.thefoodhub.feature.calculator.CalculatorState
import com.ankitt.thefoodhub.feature.calculator.CalculatorTool
import com.ankitt.thefoodhub.feature.calculator.ConversionIntent
import com.ankitt.thefoodhub.feature.calculator.ConversionResult
import com.ankitt.thefoodhub.feature.calculator.ConversionState
import com.ankitt.thefoodhub.feature.calculator.DensityBasis
import com.ankitt.thefoodhub.feature.calculator.SubstitutesIntent
import com.ankitt.thefoodhub.feature.calculator.SubstitutesResult
import com.ankitt.thefoodhub.feature.calculator.SubstitutesState
import com.ankitt.thefoodhub.ui.calculator.CalculatorScreen
import com.ankitt.thefoodhub.ui.theme.TheFoodHubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalculatorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<CalculatorIntent>()

    private fun setContent(state: CalculatorState) {
        composeRule.setContent {
            TheFoodHubTheme {
                CalculatorScreen(state = state, onIntent = { intents += it })
            }
        }
    }

    @Test
    fun showsAMassToVolumeResultWithItsBasis() {
        setContent(
            CalculatorState(
                conversion = ConversionState(
                    amountText = "2.5",
                    result = ConversionResult.Ready("313", MeasureUnit.Grams, isSingular = false, basis = DensityBasis("flour", "125")),
                ),
            ),
        )

        composeRule.onNodeWithText("313 grams").assertIsDisplayed()
        composeRule.onNodeWithText("Based on 1 cup of flour ≈ 125 g · saved for offline use").assertIsDisplayed()
    }

    @Test
    fun singularUnitsAndTheOfflineCaption() {
        setContent(
            CalculatorState(
                conversion = ConversionState(
                    from = MeasureUnit.Tablespoons,
                    to = MeasureUnit.Cups,
                    result = ConversionResult.Ready("1", MeasureUnit.Cups, isSingular = true, basis = null),
                ),
            ),
        )

        composeRule.onNodeWithText("1 cup").assertIsDisplayed()
        composeRule.onNodeWithText("Standard US kitchen measures · works offline").assertIsDisplayed()
    }

    @Test
    fun typingAndControlsSendIntents() {
        setContent(CalculatorState(conversion = ConversionState(result = ConversionResult.NeedsLookup)))

        composeRule.onNodeWithText("Amount").performTextReplacement("1 1/2")
        composeRule.onNodeWithContentDescription("Swap units").performClick()
        composeRule.onNodeWithText("sugar").performClick()

        assertEquals(
            listOf(
                ConversionIntent.AmountChanged("1 1/2"),
                ConversionIntent.SwapUnits,
                ConversionIntent.SelectIngredient("sugar"),
            ),
            intents,
        )
    }

    @Test
    fun aFailedLookupOffersRetry() {
        setContent(CalculatorState(conversion = ConversionState(result = ConversionResult.Failed(DataError.Network))))

        composeRule.onNodeWithText("Can’t reach Spoonacular. Check your connection.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(listOf<CalculatorIntent>(ConversionIntent.Convert), intents)
    }

    @Test
    fun substitutesAreListedAndSuggestionsLookUp() {
        setContent(
            CalculatorState(
                tool = CalculatorTool.Substitute,
                substitutes = SubstitutesState(
                    query = "butter",
                    result = SubstitutesResult.Ready(IngredientSubstitutes("butter", listOf("1 cup = 1 cup margarine"))),
                ),
            ),
        )

        composeRule.onNodeWithText("Instead of butter").assertIsDisplayed()
        composeRule.onNodeWithText("1 cup = 1 cup margarine").assertIsDisplayed()

        composeRule.onNodeWithText("egg").performClick()
        composeRule.onNodeWithText("Convert").performClick()

        assertTrue(SubstitutesIntent.SelectIngredient("egg") in intents)
        assertTrue(CalculatorIntent.SelectTool(CalculatorTool.Convert) in intents)
    }
}
