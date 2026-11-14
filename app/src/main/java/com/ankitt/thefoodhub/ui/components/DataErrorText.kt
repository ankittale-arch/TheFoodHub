package com.ankitt.thefoodhub.ui.components

import androidx.annotation.StringRes
import com.ankitt.thefoodhub.R
import com.ankitt.thefoodhub.core.DataError

@StringRes
fun DataError.messageRes(): Int = when (this) {
    DataError.Network -> R.string.error_network
    DataError.Unauthorized -> R.string.error_unauthorized
    DataError.QuotaExceeded -> R.string.error_quota
    DataError.NotFound -> R.string.error_not_found
    DataError.Server -> R.string.error_server
    DataError.Serialization, DataError.Unknown -> R.string.error_unknown
}
