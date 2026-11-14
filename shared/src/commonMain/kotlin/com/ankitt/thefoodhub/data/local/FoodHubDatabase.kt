package com.ankitt.thefoodhub.data.local

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.ColumnTypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [RecipeEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = true,
)
@ColumnTypeConverters(RoomConverters::class)
@ConstructedBy(FoodHubDatabaseConstructor::class)
abstract class FoodHubDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun favoriteDao(): FavoriteDao

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
