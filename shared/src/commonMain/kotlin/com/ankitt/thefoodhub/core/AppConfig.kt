package com.ankitt.thefoodhub.core

/**
 * Configuration that each platform app passes in when it starts the shared module.
 *
 * The API key defaults to the one generated from `local.properties` (see `generateSecrets` in
 * shared/build.gradle.kts), so both apps get the same key without their own secrets setup.
 */
data class AppConfig(
    val spoonacularApiKey: String = Secrets.SPOONACULAR_API_KEY,
    val isDebug: Boolean = false,
    val spoonacularBaseUrl: String = SPOONACULAR_BASE_URL,
) {
    /** Keeps the key out of logs and crash reports if the config is ever printed. */
    override fun toString(): String =
        "AppConfig(spoonacularApiKey=<redacted>, isDebug=$isDebug, spoonacularBaseUrl=$spoonacularBaseUrl)"

    companion object {
        const val SPOONACULAR_BASE_URL = "https://api.spoonacular.com/"
    }
}
