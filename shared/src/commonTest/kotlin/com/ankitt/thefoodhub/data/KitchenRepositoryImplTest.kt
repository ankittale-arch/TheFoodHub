package com.ankitt.thefoodhub.data

import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.data.repository.KitchenRepositoryImpl
import com.ankitt.thefoodhub.domain.model.IngredientSubstitutes
import com.ankitt.thefoodhub.testutil.CONVERT_FLOUR_JSON
import com.ankitt.thefoodhub.testutil.FakeKitchenDao
import com.ankitt.thefoodhub.testutil.FixedClock
import com.ankitt.thefoodhub.testutil.MockSpoonacular
import com.ankitt.thefoodhub.testutil.SUBSTITUTES_BUTTER_JSON
import com.ankitt.thefoodhub.testutil.SUBSTITUTES_NOT_FOUND_JSON
import com.ankitt.thefoodhub.testutil.respondJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KitchenRepositoryImplTest {

    private val dao = FakeKitchenDao()
    private val clock = FixedClock(nowMillis = 1_700_000_000_000)

    private fun repository(mock: MockSpoonacular) = KitchenRepositoryImpl(mock.api, dao, clock)

    @Test
    fun looksUpOneCupInGrams() = runTest {
        val mock = MockSpoonacular { respondJson(CONVERT_FLOUR_JSON) }

        assertEquals(Outcome.Success(125.0), repository(mock).getGramsPerCup("  Flour "))

        val url = mock.requests.single().url
        assertEquals("/recipes/convert", url.encodedPath)
        assertEquals("flour", url.parameters["ingredientName"])
        assertEquals("1.0", url.parameters["sourceAmount"])
        assertEquals("cups", url.parameters["sourceUnit"])
        assertEquals("grams", url.parameters["targetUnit"])
    }

    @Test
    fun aSavedDensityCostsNoSecondCallAndIsReadableOffline() = runTest {
        val mock = MockSpoonacular { respondJson(CONVERT_FLOUR_JSON) }
        val repo = repository(mock)

        repo.getGramsPerCup("flour")
        assertEquals(Outcome.Success(125.0), repo.getGramsPerCup("FLOUR"))

        assertEquals(1, mock.requests.size)
        assertEquals(125.0, repo.savedGramsPerCup("Flour"))
        assertEquals(listOf("flour"), repo.observeSavedDensityIngredients().first())
    }

    @Test
    fun anUnconvertibleIngredientIsSavedAsNull() = runTest {
        val mock = MockSpoonacular { respondJson("""{"targetAmount": 0.0, "answer": ""}""") }
        val repo = repository(mock)

        assertEquals(Outcome.Success(null), repo.getGramsPerCup("air"))
        assertEquals(Outcome.Success(null), repo.getGramsPerCup("air"))

        assertEquals(1, mock.requests.size)
        assertEquals(emptyList(), repo.observeSavedDensityIngredients().first())
    }

    @Test
    fun aFailedLookupIsNotSaved() = runTest {
        val mock = MockSpoonacular { respondJson("{}", HttpStatusCode.PaymentRequired) }
        val repo = repository(mock)

        assertEquals(Outcome.Failure(DataError.QuotaExceeded), repo.getGramsPerCup("flour"))
        assertNull(repo.savedGramsPerCup("flour"))
        assertEquals(emptyMap(), dao.densities.value)
    }

    @Test
    fun cleansAndSavesSubstitutes() = runTest {
        val mock = MockSpoonacular { respondJson(SUBSTITUTES_BUTTER_JSON) }
        val repo = repository(mock)
        val expected = IngredientSubstitutes(
            ingredient = "butter",
            substitutes = listOf("1 cup = 7/8 cup shortening and 1/2 tsp salt", "1 cup = 1 cup margarine"),
        )

        assertEquals(Outcome.Success(expected), repo.getSubstitutes(" Butter"))
        assertEquals(Outcome.Success(expected), repo.getSubstitutes("butter"))

        val url = mock.requests.single().url
        assertEquals("/food/ingredients/substitutes", url.encodedPath)
        assertEquals("butter", url.parameters["ingredientName"])
        assertEquals(listOf("butter"), repo.observeSavedSubstituteIngredients().first())
    }

    @Test
    fun noSubstitutesIsSavedSoItIsNotPaidForAgain() = runTest {
        val mock = MockSpoonacular { respondJson(SUBSTITUTES_NOT_FOUND_JSON) }
        val repo = repository(mock)

        assertEquals(Outcome.Success(null), repo.getSubstitutes("unobtainium"))
        assertEquals(Outcome.Success(null), repo.getSubstitutes("unobtainium"))

        assertEquals(1, mock.requests.size)
        assertEquals(emptyList(), repo.observeSavedSubstituteIngredients().first())
    }
}
