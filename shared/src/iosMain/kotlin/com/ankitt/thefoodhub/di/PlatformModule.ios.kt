package com.ankitt.thefoodhub.di

import androidx.room3.Room
import com.ankitt.thefoodhub.data.local.FoodHubDatabase
import com.ankitt.thefoodhub.data.local.buildFoodHubDatabase
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

internal actual val platformModule: Module = module {
    single<HttpClientEngine> { Darwin.create() }
    single<FoodHubDatabase> { createDatabase() }
}

private fun createDatabase(): FoodHubDatabase {
    val dbPath = "${applicationSupportDirectory()}/${FoodHubDatabase.FILE_NAME}"
    return Room.databaseBuilder<FoodHubDatabase>(name = dbPath).buildFoodHubDatabase()
}

/** Application Support is not shown to the user, unlike Documents. It may not exist yet, hence `create = true`. */
private fun applicationSupportDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(url?.path) { "Application Support directory is unavailable" }
}
