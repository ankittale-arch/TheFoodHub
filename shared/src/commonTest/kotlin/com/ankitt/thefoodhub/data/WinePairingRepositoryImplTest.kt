package com.ankitt.thefoodhub.data

import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.data.repository.WinePairingRepositoryImpl
import com.ankitt.thefoodhub.domain.model.WineBottle
import com.ankitt.thefoodhub.domain.model.WinePairing
import com.ankitt.thefoodhub.testutil.FakeWinePairingDao
import com.ankitt.thefoodhub.testutil.FixedClock
import com.ankitt.thefoodhub.testutil.MockSpoonacular
import com.ankitt.thefoodhub.testutil.WINE_PAIRING_JSON
import com.ankitt.thefoodhub.testutil.WINE_PAIRING_NOT_FOUND_JSON
import com.ankitt.thefoodhub.testutil.respondJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class WinePairingRepositoryImplTest {

    private val dao = FakeWinePairingDao()
    private val clock = FixedClock(nowMillis = 1_700_000_000_000)

    private fun repository(mock: MockSpoonacular) = WinePairingRepositoryImpl(mock.api, dao, clock)

    @Test
    fun mapsThePairingAndTheSuggestedBottle() = runTest {
        val repo = repository(MockSpoonacular { respondJson(WINE_PAIRING_JSON) })

        assertEquals(
            Outcome.Success(
                WinePairing(
                    food = "pork",
                    wines = listOf("Pinot Noir", "Malbec", "Sangiovese"),
                    explanation = "Pork works well with red and white wine, depending on the flavors of the dish.",
                    suggestedBottle = WineBottle(
                        title = "Vinum Cellars Pinot Noir",
                        price = "$18.99",
                        imageUrl = "https://img.spoonacular.com/products/434328-312x231.jpg",
                        rating = 4.6,
                        ratingCount = 23,
                        link = "https://www.amazon.com/example",
                    ),
                ),
            ),
            repo.getPairing(" Pork "),
        )
    }

    @Test
    fun aCachedPairingCostsNoSecondCall() = runTest {
        val mock = MockSpoonacular { respondJson(WINE_PAIRING_JSON) }
        val repo = repository(mock)

        repo.getPairing("pork")
        val second = repo.getPairing("PORK")

        assertEquals(1, mock.requests.size)
        assertEquals("pork", mock.requests.single().url.parameters["food"])
        assertEquals("Pinot Noir", (second as Outcome.Success).value?.wines?.first())
    }

    @Test
    fun noPairingIsCachedToo() = runTest {
        val mock = MockSpoonacular { respondJson(WINE_PAIRING_NOT_FOUND_JSON) }
        val repo = repository(mock)

        assertEquals(Outcome.Success(null), repo.getPairing("tofu"))
        assertEquals(Outcome.Success(null), repo.getPairing("tofu"))
        assertEquals(1, mock.requests.size)
    }

    @Test
    fun anExpiredPairingIsFetchedAgain() = runTest {
        val mock = MockSpoonacular { respondJson(WINE_PAIRING_JSON) }
        val repo = repository(mock)
        repo.getPairing("pork")

        clock.nowMillis += 31.days.inWholeMilliseconds
        repo.getPairing("pork")

        assertEquals(2, mock.requests.size)
    }

    @Test
    fun failuresAreReportedAndNotCached() = runTest {
        val repo = repository(MockSpoonacular { respondJson("""{"status":"failure"}""", HttpStatusCode.PaymentRequired) })

        assertEquals(Outcome.Failure(DataError.QuotaExceeded), repo.getPairing("pork"))
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun pricesShowTwoDecimals() {
        assertEquals("$89.00", "$89.0".toTwoDecimalPrice())
        assertEquals("$18.99", "$18.99".toTwoDecimalPrice())
        assertEquals("$12.00", "$12".toTwoDecimalPrice())
        assertEquals("about 20 USD", "about 20 USD".toTwoDecimalPrice())
    }

    @Test
    fun wineListsDropCategoryLabelsAndPreferNamedWines() {
        // Spoonacular's real answer for "chocolate".
        val chocolate = listOf(
            "wine", "alcoholic drink", "ingredient", "food product category", "madeira", "red wine",
            "dessert wine", "port wine", "drink", "menu item type", "banyuls",
        )

        assertEquals(listOf("Madeira", "Port Wine", "Banyuls"), chocolate.toDisplayedWines())
        assertEquals(listOf("Pinot Noir", "Red Wine"), listOf("Pinot Noir", "red wine", "pinot noir").toDisplayedWines())
    }

    @Test
    fun explanationsDropASentenceBuiltFromCategoryLabels() {
        // Spoonacular's real answer for "chicken".
        val chicken = "White Wine, Alcoholic Drink, and Ingredient are great choices for Chicken. " +
            "White meat is typically paired with white wine as to not overpower the flavor."
        val pork = "Pork works well with red and white wine. Salty pork can be paired with riesling."

        assertEquals("White meat is typically paired with white wine as to not overpower the flavor.", chicken.withoutCategoryLabelSentence())
        assertEquals(pork, pork.withoutCategoryLabelSentence())
        assertEquals("Ingredient pairing only", "Ingredient pairing only".withoutCategoryLabelSentence())
    }
}
