package com.ankitt.thefoodhub.testutil

import com.ankitt.thefoodhub.core.AppConfig
import com.ankitt.thefoodhub.data.remote.SpoonacularApi
import com.ankitt.thefoodhub.data.remote.createHttpClient
import com.ankitt.thefoodhub.domain.model.Ingredient
import com.ankitt.thefoodhub.domain.model.InstructionStep
import com.ankitt.thefoodhub.domain.model.PopularRecipe
import com.ankitt.thefoodhub.domain.model.RecipeDetail
import com.ankitt.thefoodhub.domain.model.RecipePage
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import com.ankitt.thefoodhub.domain.model.WinePairing
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.time.Clock
import kotlin.time.Instant

const val TEST_API_KEY = "test-api-key"

fun recipeSummary(id: Int) = RecipeSummary(id = id, title = "Recipe $id", imageUrl = "https://img/$id.jpg")

fun recipePage(ids: IntRange, offset: Int = 0, totalResults: Int = 100) =
    RecipePage(recipes = ids.map(::recipeSummary), offset = offset, totalResults = totalResults)

fun popularRecipe(
    id: Int,
    likes: Int? = null,
    readyInMinutes: Int? = 20,
    winePairingFood: String? = null,
) = PopularRecipe(
    id = id,
    title = "Popular $id",
    imageUrl = "https://img/$id.jpg",
    readyInMinutes = readyInMinutes,
    likes = likes,
    rating = 4.5,
    winePairingFood = winePairingFood,
)

fun winePairing(food: String) = WinePairing(
    food = food,
    wines = listOf("Pinot Noir"),
    explanation = "Goes with $food.",
    suggestedBottle = null,
)

fun recipeDetail(id: Int) = RecipeDetail(
    id = id,
    title = "Recipe $id",
    imageUrl = "https://img/$id.jpg",
    readyInMinutes = 30,
    servings = 2,
    healthScore = 50,
    sourceName = "Source",
    sourceUrl = "https://source/$id",
    summaryHtml = "A <b>tasty</b> dish",
    diets = listOf("vegan"),
    cuisines = emptyList(),
    dishTypes = listOf("dinner"),
    ingredients = listOf(Ingredient(name = "flour", original = "1 cup flour", imageUrl = null)),
    steps = listOf(InstructionStep(number = 1, text = "Mix")),
)

class FixedClock(var nowMillis: Long = 1_000L) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis)
}

/** A real [SpoonacularApi] (real client config and JSON) backed by Ktor's MockEngine. */
internal class MockSpoonacular(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val engine = MockEngine(handler)
    val api = SpoonacularApi(createHttpClient(engine, AppConfig(spoonacularApiKey = TEST_API_KEY)))
    val requests: List<HttpRequestData> get() = engine.requestHistory
}

fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))

val SEARCH_RESPONSE_JSON = """
    {
      "results": [
        { "id": 716429, "title": "Pasta with Garlic", "image": "https://img.spoonacular.com/recipes/716429-312x231.jpg", "imageType": "jpg" },
        { "id": 715538, "title": "Bruschetta", "image": null }
      ],
      "offset": 0,
      "number": 2,
      "totalResults": 86
    }
""".trimIndent()

/** complexSearch with includeIngredients, fillIngredients and nutrient filters. */
val FILTERED_SEARCH_RESPONSE_JSON = """
    {
      "results": [
        {
          "id": 638308, "title": "Chicken Fajitas", "image": "https://img.spoonacular.com/recipes/638308-312x231.jpg",
          "usedIngredientCount": 2, "missedIngredientCount": 3,
          "usedIngredients": [ { "id": 5006, "name": "chicken" } ],
          "nutrition": { "nutrients": [
            { "name": "Protein", "amount": 42.5, "unit": "g" },
            { "name": "Calories", "amount": 512.3, "unit": "kcal" },
            { "name": "Vitamin C", "amount": 12.0, "unit": "mg" }
          ] }
        },
        { "id": 715538, "title": "Bruschetta", "image": null }
      ],
      "offset": 0,
      "number": 2,
      "totalResults": 2
    }
""".trimIndent()

/** `food/wine/pairing?food=pork`, trimmed from a real response. */
val WINE_PAIRING_JSON = """
    {
      "pairedWines": ["pinot noir", "malbec", "sangiovese"],
      "pairingText": "Pork works well with red and white wine, depending on the flavors of the dish.",
      "productMatches": [
        {
          "id": 434328, "title": "Vinum Cellars Pinot Noir", "description": "Ripe cherry.",
          "price": "$18.99", "imageUrl": "https://img.spoonacular.com/products/434328-312x231.jpg",
          "averageRating": 0.92, "ratingCount": 23.0, "score": 0.88, "link": "https://www.amazon.com/example"
        }
      ]
    }
""".trimIndent()

/** What Spoonacular answers, with status 200, when it has no pairing for a food. */
val WINE_PAIRING_NOT_FOUND_JSON = """
    { "status": "failure", "message": "Could not find a wine pairing for Cider-Braised Pork Shoulder" }
""".trimIndent()

val RANDOM_RECIPES_JSON = """
    {
      "recipes": [
        {
          "id": 1001,
          "title": "Leek Pasta with Miso Sauce",
          "image": "https://img.spoonacular.com/recipes/1001-556x370.jpg",
          "readyInMinutes": 25,
          "servings": 2,
          "aggregateLikes": 385,
          "spoonacularScore": 96.0,
          "healthScore": 40.0,
          "summary": "Creamy <b>pasta</b>",
          "extendedIngredients": [ { "id": 1, "name": "leek", "original": "1 leek", "image": "leek.png" } ],
          "analyzedInstructions": [ { "name": "", "steps": [ { "number": 1, "step": "Cook." } ] } ]
        },
        {
          "id": 1002,
          "title": "Plain Toast",
          "image": "",
          "readyInMinutes": null,
          "aggregateLikes": null,
          "spoonacularScore": null
        },
        { "id": 1001, "title": "Leek Pasta with Miso Sauce (duplicate)" }
      ]
    }
""".trimIndent()

val RECIPE_INFORMATION_JSON = """
    {
      "id": 716429,
      "title": "Pasta with Garlic",
      "image": "https://img.spoonacular.com/recipes/716429-556x370.jpg",
      "servings": 2,
      "readyInMinutes": 45,
      "healthScore": 18.7,
      "sourceName": "Full Belly Sisters",
      "sourceUrl": "https://fullbellysisters.blogspot.com/pasta",
      "summary": "A <b>great</b> pasta",
      "vegetarian": null,
      "diets": ["lacto ovo vegetarian"],
      "cuisines": [],
      "dishTypes": ["lunch", "main course"],
      "extendedIngredients": [
        { "id": 1001, "name": "butter", "original": "1 tbsp butter", "image": "butter-sliced.jpg", "amount": 1.0, "unit": "tbsp" },
        { "id": 2, "name": "salt", "original": "", "image": null },
        { "id": 3, "name": null, "original": null, "image": null }
      ],
      "analyzedInstructions": [
        { "name": "", "steps": [ { "number": 1, "step": "Boil the pasta. " }, { "number": 2, "step": "Drain." } ] },
        { "name": "Sauce", "steps": [ { "number": 1, "step": "Melt the butter." } ] }
      ]
    }
""".trimIndent()
