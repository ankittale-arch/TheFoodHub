package com.ankitt.thefoodhub.core

/** Result of a data-layer operation that can fail with a [DataError]. */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>
    data class Failure(val error: DataError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

val Outcome<*>.errorOrNull: DataError?
    get() = (this as? Outcome.Failure)?.error
