package com.ankitt.thefoodhub.data.local

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.ColumnTypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [
        RecipeEntity::class,
        FavoriteEntity::class,
        WinePairingEntity::class,
        IngredientDensityEntity::class,
        IngredientSubstitutesEntity::class,
        CookbookEntity::class,
        CookbookRecipeEntity::class,
        RecipeNoteEntity::class,
        IngredientTweakEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        // 2 adds the wine_pairings table.
        AutoMigration(from = 1, to = 2),
        // 3 adds the kitchen calculator's ingredient_densities and ingredient_substitutes tables.
        AutoMigration(from = 2, to = 3),
        // 4 adds cookbooks and the cookbook_recipes junction table.
        AutoMigration(from = 3, to = 4),
        // 5 adds the user's recipe_notes and ingredient_tweaks.
        AutoMigration(from = 4, to = 5),
    ],
)
@ColumnTypeConverters(RoomConverters::class)
@ConstructedBy(FoodHubDatabaseConstructor::class)
abstract class FoodHubDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun winePairingDao(): WinePairingDao
    abstract fun kitchenDao(): KitchenDao
    abstract fun cookbookDao(): CookbookDao
    abstract fun recipeNotesDao(): RecipeNotesDao

    companion object {
        const val FILE_NAME = "foodhub.db"
    }
}

/** Room's KSP processor generates the `actual` for each platform. */
@Suppress("KotlinNoActualForExpect")
expect object FoodHubDatabaseConstructor : RoomDatabaseConstructor<FoodHubDatabase> {
    override fun initialize(): FoodHubDatabase
}

/** Platforms provide the builder (file location); configuration shared by both lives here. */
internal fun RoomDatabase.Builder<FoodHubDatabase>.buildFoodHubDatabase(): FoodHubDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
