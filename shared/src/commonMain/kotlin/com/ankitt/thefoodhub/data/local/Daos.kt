package com.ankitt.thefoodhub.data.local

import androidx.room3.Dao
import androidx.room3.Insert
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
interface CookbookDao {

    @Query(
        """
        SELECT c.id, c.name, COUNT(cr.recipeId) AS recipeCount
        FROM cookbooks c LEFT JOIN cookbook_recipes cr ON cr.cookbookId = c.id
        GROUP BY c.id
        ORDER BY c.name
        """,
    )
    fun observeAllWithCounts(): Flow<List<CookbookWithCount>>

    @Query(
        """
        SELECT f.* FROM favorites f
        INNER JOIN cookbook_recipes cr ON cr.recipeId = f.recipeId
        WHERE cr.cookbookId = :cookbookId
        ORDER BY cr.addedAtEpochMillis DESC
        """,
    )
    fun observeRecipes(cookbookId: Long): Flow<List<FavoriteEntity>>

    @Query("SELECT cookbookId FROM cookbook_recipes WHERE recipeId = :recipeId")
    fun observeCookbookIds(recipeId: Int): Flow<List<Long>>

    @Query("SELECT cookbookId FROM cookbook_recipes WHERE recipeId = :recipeId")
    suspend fun cookbookIds(recipeId: Int): List<Long>

    /** Case-insensitive: the name column uses NOCASE. Pass the cookbook being renamed as [exceptId], or 0. */
    @Query("SELECT EXISTS(SELECT 1 FROM cookbooks WHERE name = :name AND id != :exceptId)")
    suspend fun isNameTaken(name: String, exceptId: Long): Boolean

    @Insert
    suspend fun insert(cookbook: CookbookEntity): Long

    @Query("UPDATE cookbooks SET name = :name WHERE id = :cookbookId")
    suspend fun rename(cookbookId: Long, name: String)

    @Query("DELETE FROM cookbooks WHERE id = :cookbookId")
    suspend fun delete(cookbookId: Long)

    /**
     * Inserting through SELECT skips cookbooks that were deleted in the meantime and recipes that
     * are not favorites, either of which would otherwise fail a foreign key.
     */
    @Query(
        """
        INSERT OR IGNORE INTO cookbook_recipes (cookbookId, recipeId, addedAtEpochMillis)
        SELECT id, :recipeId, :addedAtEpochMillis FROM cookbooks
        WHERE id IN (:cookbookIds) AND EXISTS(SELECT 1 FROM favorites WHERE recipeId = :recipeId)
        """,
    )
    suspend fun addRecipe(recipeId: Int, cookbookIds: List<Long>, addedAtEpochMillis: Long)

    @Query("DELETE FROM cookbook_recipes WHERE cookbookId = :cookbookId AND recipeId = :recipeId")
    suspend fun removeRecipe(cookbookId: Long, recipeId: Int)
}

@Dao
interface RecipeNotesDao {

    @Query("SELECT * FROM recipe_notes WHERE recipeId = :recipeId")
    fun observeNote(recipeId: Int): Flow<RecipeNoteEntity?>

    @Query("SELECT * FROM ingredient_tweaks WHERE recipeId = :recipeId")
    fun observeTweaks(recipeId: Int): Flow<List<IngredientTweakEntity>>

    @Upsert
    suspend fun upsertNote(note: RecipeNoteEntity)

    @Query("DELETE FROM recipe_notes WHERE recipeId = :recipeId")
    suspend fun deleteNote(recipeId: Int)

    @Upsert
    suspend fun upsertTweak(tweak: IngredientTweakEntity)

    @Query("DELETE FROM ingredient_tweaks WHERE recipeId = :recipeId AND ingredientLine = :ingredientLine")
    suspend fun deleteTweak(recipeId: Int, ingredientLine: String)
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
