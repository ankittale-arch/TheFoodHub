package com.ankitt.thefoodhub.data.remote

import com.ankitt.thefoodhub.core.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Spoonacular accepts the key as a header, which keeps it out of URLs and logs. */
internal const val API_KEY_HEADER = "x-api-key"

internal val spoonacularJson = Json {
    ignoreUnknownKeys = true
    // Spoonacular sometimes sends null for fields that are usually present; fall back to defaults.
    coerceInputValues = true
    explicitNulls = false
}

/** The engine is injected (OkHttp on Android, Darwin on iOS, MockEngine in tests). */
internal fun createHttpClient(engine: HttpClientEngine, config: AppConfig): HttpClient = HttpClient(engine) {
    // Status codes are mapped to DataError in safeApiCall instead of throwing.
    expectSuccess = false

    install(ContentNegotiation) {
        json(spoonacularJson)
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 30_000
    }
    if (config.isDebug) {
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.INFO
            sanitizeHeader { header -> header.equals(API_KEY_HEADER, ignoreCase = true) }
        }
    }
    defaultRequest {
        url(config.spoonacularBaseUrl)
        headers.append(API_KEY_HEADER, config.spoonacularApiKey)
        headers.append(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }
}
