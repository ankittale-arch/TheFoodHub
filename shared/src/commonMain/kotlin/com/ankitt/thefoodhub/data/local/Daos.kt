package com.ankitt.thefoodhub.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observe(id: Int): Flow<RecipeEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM recipes WHERE id = :id)")
    suspend fun exists(id: Int): Boolean

    @Upsert
    suspend fun upsert(recipe: RecipeEntity)
}

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY savedAtEpochMillis DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT recipeId FROM favorites")
    fun observeIds(): Flow<List<Int>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE recipeId = :recipeId)")
    fun observeIsFavorite(recipeId: Int): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE recipeId = :recipeId)")
    suspend fun isFavorite(recipeId: Int): Boolean

    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE recipeId = :recipeId")
    suspend fun delete(recipeId: Int)
}

@Dao
interface WinePairingDao {

    @Query("SELECT * FROM wine_pairings WHERE food = :food")
    suspend fun get(food: String): WinePairingEntity?

    @Upsert
    suspend fun upsert(pairing: WinePairingEntity)
}

@Dao
interface KitchenDao {

    @Query("SELECT * FROM ingredient_densities WHERE ingredient = :ingredient")
    suspend fun getDensity(ingredient: String): IngredientDensityEntity?

    @Upsert
    suspend fun upsertDensity(density: IngredientDensityEntity)

    @Query("SELECT ingredient FROM ingredient_densities WHERE gramsPerCup IS NOT NULL ORDER BY cachedAtEpochMillis DESC")
    fun observeDensityIngredients(): Flow<List<String>>

    @Query("SELECT * FROM ingredient_substitutes WHERE ingredient = :ingredient")
    suspend fun getSubstitutes(ingredient: String): IngredientSubstitutesEntity?

    @Upsert
    suspend fun upsertSubstitutes(substitutes: IngredientSubstitutesEntity)

    @Query("SELECT ingredient FROM ingredient_substitutes WHERE substitutes != '[]' ORDER BY cachedAtEpochMillis DESC")
    fun observeSubstituteIngredients(): Flow<List<String>>
}
