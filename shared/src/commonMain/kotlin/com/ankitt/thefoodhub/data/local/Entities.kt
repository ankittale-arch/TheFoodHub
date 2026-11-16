package com.ankitt.thefoodhub.data.local

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

/** Cached recipe details. Ingredient and step lists are stored as JSON via [RoomConverters]. */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val imageUrl: String?,
    val readyInMinutes: Int?,
    val servings: Int?,
    val healthScore: Int?,
    val sourceName: String?,
    val sourceUrl: String?,
    val summaryHtml: String,
    val diets: List<String>,
    val cuisines: List<String>,
    val dishTypes: List<String>,
    val ingredients: List<StoredIngredient>,
    val steps: List<StoredStep>,
    val cachedAtEpochMillis: Long,
)

@Serializable
data class StoredIngredient(
    val name: String,
    val original: String,
    val imageUrl: String?,
)

@Serializable
data class StoredStep(
    val number: Int,
    val text: String,
)

/**
 * Favorites keep their own copy of the list data, so a recipe can be saved straight from
 * search results without fetching its details first.
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val recipeId: Int,
    val title: String,
    val imageUrl: String?,
    val savedAtEpochMillis: Long,
)

/**
 * A cached wine pairing, keyed by the food it was looked up for. A row with no wines and a blank
 * description records that Spoonacular has no pairing, so that answer is not paid for again.
 */
@Entity(tableName = "wine_pairings")
data class WinePairingEntity(
    @PrimaryKey val food: String,
    val wines: List<String>,
    val description: String,
    val suggestedBottle: StoredWineBottle?,
    val cachedAtEpochMillis: Long,
)

@Serializable
data class StoredWineBottle(
    val title: String,
    val price: String?,
    val imageUrl: String?,
    val rating: Double?,
    val ratingCount: Int?,
    val link: String?,
)

/**
 * An ingredient's density, used to convert between mass and volume. A null [gramsPerCup] records
 * that Spoonacular can't convert the ingredient, so that answer is not paid for again.
 */
@Entity(tableName = "ingredient_densities")
data class IngredientDensityEntity(
    @PrimaryKey val ingredient: String,
    val gramsPerCup: Double?,
    val cachedAtEpochMillis: Long,
)

/** Saved substitutes for an ingredient. An empty list records that Spoonacular has none. */
@Entity(tableName = "ingredient_substitutes")
data class IngredientSubstitutesEntity(
    @PrimaryKey val ingredient: String,
    val substitutes: List<String>,
    val cachedAtEpochMillis: Long,
)
