package com.ankitt.thefoodhub.di

import android.content.Context
import androidx.room3.Room
import com.ankitt.thefoodhub.data.local.FoodHubDatabase
import com.ankitt.thefoodhub.data.local.buildFoodHubDatabase
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformModule: Module = module {
    single<HttpClientEngine> { OkHttp.create() }
    single<FoodHubDatabase> { createDatabase(androidContext()) }
}

private fun createDatabase(context: Context): FoodHubDatabase {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(FoodHubDatabase.FILE_NAME)
    return Room.databaseBuilder<FoodHubDatabase>(context = appContext, name = dbFile.absolutePath)
        .buildFoodHubDatabase()
}
