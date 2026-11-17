package com.ankitt.thefoodhub.domain.model

/** A user-made folder of favorites, e.g. "Quick Weeknight". A recipe can be in several cookbooks. */
data class Cookbook(
    val id: Long,
    val name: String,
    val recipeCount: Int,
) {
    companion object {
        const val MAX_NAME_LENGTH = 40

        /** Trims the name and collapses runs of whitespace, so "  Meal   Prep " is saved as "Meal Prep". */
        fun normalizeName(name: String): String = name.trim().replace(WHITESPACE, " ")

        private val WHITESPACE = Regex("\\s+")
    }
}

/** Why a cookbook name was not saved. */
enum class CookbookNameError {
    Blank,
    TooLong,

    /** Another cookbook already has this name (ignoring case). */
    Taken,
}

sealed interface CookbookNameResult {
    data class Saved(val cookbookId: Long) : CookbookNameResult
    data class Rejected(val error: CookbookNameError) : CookbookNameResult
}
