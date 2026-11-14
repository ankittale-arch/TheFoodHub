package com.ankitt.thefoodhub.data

import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.data.remote.API_KEY_HEADER
import com.ankitt.thefoodhub.testutil.MockSpoonacular
import com.ankitt.thefoodhub.testutil.RANDOM_RECIPES_JSON
import com.ankitt.thefoodhub.testutil.SEARCH_RESPONSE_JSON
import com.ankitt.thefoodhub.testutil.TEST_API_KEY
import com.ankitt.thefoodhub.testutil.respondJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SpoonacularApiTest {

    @Test
    fun searchSendsKeyAsHeaderAndParsesResults() = runTest {
        val mock = MockSpoonacular { respondJson(SEARCH_RESPONSE_JSON) }

        val outcome = mock.api.searchRecipes(query = "garlic pasta", offset = 20, number = 10)

        val request = mock.requests.single()
        assertEquals("https://api.spoonacular.com/recipes/complexSearch", request.url.toString().substringBefore('?'))
        assertEquals("garlic pasta", request.url.parameters["query"])
        assertEquals("20", request.url.parameters["offset"])
        assertEquals("10", request.url.parameters["number"])
        assertEquals(TEST_API_KEY, request.headers[API_KEY_HEADER])
        assertNull(request.url.parameters["apiKey"], "API key must not leak into the URL")

        val page = assertIs<Outcome.Success<*>>(outcome).value as com.ankitt.thefoodhub.data.remote.dto.SearchResponseDto
        assertEquals(86, page.totalResults)
        assertEquals(listOf(716429, 715538), page.results.map { it.id })
        assertNull(page.results[1].image)
    }

    @Test
    fun randomRecipesSendsHyphenatedTagParametersAndParses() = runTest {
        val mock = MockSpoonacular { respondJson(RANDOM_RECIPES_JSON) }

        val outcome = mock.api.getRandomRecipes(
            number = 10,
            includeTags = listOf("vegetarian", "dessert"),
            excludeTags = listOf("dairy"),
        )

        val request = mock.requests.single()
        assertEquals("https://api.spoonacular.com/recipes/random", request.url.toString().substringBefore('?'))
        assertEquals("10", request.url.parameters["number"])
        assertEquals("vegetarian,dessert", request.url.parameters["include-tags"])
        assertEquals("dairy", request.url.parameters["exclude-tags"])
        assertEquals("false", request.url.parameters["includeNutrition"])
        assertEquals(TEST_API_KEY, request.headers[API_KEY_HEADER])

        val recipes = assertIs<Outcome.Success<com.ankitt.thefoodhub.data.remote.dto.RandomRecipesResponseDto>>(outcome).value.recipes
        assertEquals(listOf(1001, 1002, 1001), recipes.map { it.id })
        assertEquals(385, recipes[0].aggregateLikes)
        assertEquals(96.0, recipes[0].spoonacularScore)
        assertNull(recipes[1].aggregateLikes)
    }

    @Test
    fun randomRecipesOmitsEmptyTagParameters() = runTest {
        val mock = MockSpoonacular { respondJson(RANDOM_RECIPES_JSON) }

        mock.api.getRandomRecipes(number = 5)

        val parameters = mock.requests.single().url.parameters
        assertNull(parameters["include-tags"])
        assertNull(parameters["exclude-tags"])
    }

    @Test
    fun mapsHttpStatusesToDataErrors() = runTest {
        val expectations = mapOf(
            HttpStatusCode.Unauthorized to DataError.Unauthorized,
            HttpStatusCode.PaymentRequired to DataError.QuotaExceeded,
            HttpStatusCode.TooManyRequests to DataError.QuotaExceeded,
            HttpStatusCode.NotFound to DataError.NotFound,
            HttpStatusCode.InternalServerError to DataError.Server,
            HttpStatusCode.BadRequest to DataError.Unknown,
        )
        expectations.forEach { (status, expected) ->
            val mock = MockSpoonacular { respondJson("""{"status":"failure"}""", status) }
            assertEquals(Outcome.Failure(expected), mock.api.getRecipeInformation(1), "status $status")
        }
    }

    @Test
    fun malformedBodyIsASerializationError() = runTest {
        val mock = MockSpoonacular { respondJson("""{"results": "not a list"}""") }

        assertEquals(Outcome.Failure(DataError.Serialization), mock.api.searchRecipes("x", 0, 10))
    }

    @Test
    fun transportFailureIsANetworkError() = runTest {
        val mock = MockSpoonacular { throw IOException("offline") }

        assertEquals(Outcome.Failure(DataError.Network), mock.api.searchRecipes("x", 0, 10))
    }
}
