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
