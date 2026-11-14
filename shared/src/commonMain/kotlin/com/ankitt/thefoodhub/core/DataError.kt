package com.ankitt.thefoodhub.core

/** Failures the data layer can report. Each platform maps them to user-facing text. */
enum class DataError {
    /** No connectivity, DNS failure, or timeout. */
    Network,

    /** HTTP 401: the Spoonacular API key is missing or invalid. */
    Unauthorized,

    /** HTTP 402/429: the daily point quota or rate limit is used up. */
    QuotaExceeded,

    /** HTTP 404. */
    NotFound,

    /** HTTP 5xx. */
    Server,

    /** The response body did not match the expected JSON. */
    Serialization,

    Unknown,
}
