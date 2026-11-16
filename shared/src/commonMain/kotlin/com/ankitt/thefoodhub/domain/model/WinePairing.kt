package com.ankitt.thefoodhub.domain.model

/** Wines that go with a food, as suggested by Spoonacular's sommelier data. */
data class WinePairing(
    /** The food the pairing was looked up for, e.g. "pork". */
    val food: String,
    /** Wine types in title case, best match first, e.g. "Pinot Noir". May be empty when only [explanation] is known. */
    val wines: List<String>,
    /**
     * Why these wines suit the food. (Not called `description`: Swift would see it as
     * `description_`, since that name belongs to NSObject.)
     */
    val explanation: String,
    /** A bottle to try, when Spoonacular suggests one. */
    val suggestedBottle: WineBottle?,
)

data class WineBottle(
    val title: String,
    /** Formatted by Spoonacular, including the currency, e.g. "$18.99". */
    val price: String?,
    val imageUrl: String?,
    /** 0.0–5.0, converted from Spoonacular's 0–1 score. */
    val rating: Double?,
    val ratingCount: Int?,
    /** The shop page for the bottle. */
    val link: String?,
)
