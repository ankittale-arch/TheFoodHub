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
