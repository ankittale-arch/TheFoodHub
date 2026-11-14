package com.ankitt.thefoodhub.data.remote

import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.core.Outcome
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/** Runs a request and turns transport failures and HTTP status codes into [Outcome]. */
internal suspend inline fun <reified T> safeApiCall(request: () -> HttpResponse): Outcome<T> {
    val response = try {
        request()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        // Includes HttpRequestTimeoutException and engine connection errors.
        return Outcome.Failure(DataError.Network)
    } catch (e: Exception) {
        return Outcome.Failure(DataError.Unknown)
    }
    return response.toOutcome()
}

internal suspend inline fun <reified T> HttpResponse.toOutcome(): Outcome<T> = when (status.value) {
    in 200..299 -> try {
        Outcome.Success(body<T>())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ContentConvertException) {
        Outcome.Failure(DataError.Serialization)
    } catch (e: SerializationException) {
        Outcome.Failure(DataError.Serialization)
    } catch (e: IOException) {
        Outcome.Failure(DataError.Network)
    }
    401, 403 -> Outcome.Failure(DataError.Unauthorized)
    402, 429 -> Outcome.Failure(DataError.QuotaExceeded)
    404 -> Outcome.Failure(DataError.NotFound)
    in 500..599 -> Outcome.Failure(DataError.Server)
    else -> Outcome.Failure(DataError.Unknown)
}
