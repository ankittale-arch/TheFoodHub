package com.ankitt.thefoodhub.data.local

import androidx.room3.ColumnTypeConverter
import kotlinx.serialization.json.Json

class RoomConverters {

    @ColumnTypeConverter
    fun stringsToJson(value: List<String>): String = json.encodeToString(value)

    @ColumnTypeConverter
    fun jsonToStrings(value: String): List<String> = json.decodeFromString(value)

    @ColumnTypeConverter
    fun ingredientsToJson(value: List<StoredIngredient>): String = json.encodeToString(value)

    @ColumnTypeConverter
    fun jsonToIngredients(value: String): List<StoredIngredient> = json.decodeFromString(value)

    @ColumnTypeConverter
    fun stepsToJson(value: List<StoredStep>): String = json.encodeToString(value)

    @ColumnTypeConverter
    fun jsonToSteps(value: String): List<StoredStep> = json.decodeFromString(value)

    @ColumnTypeConverter
    fun wineBottleToJson(value: StoredWineBottle?): String? = value?.let { json.encodeToString(it) }

    @ColumnTypeConverter
    fun jsonToWineBottle(value: String?): StoredWineBottle? = value?.let { json.decodeFromString(it) }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
