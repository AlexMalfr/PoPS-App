package com.malfreyt.alexandre.pops_app.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

const val DEFAULT_OASIS_BASE_URL = "https://polytech-saclay.oasis.aouka.org/"

fun normalizeOasisBaseUrl(value: String): String? {
    val url = value.trim().ifBlank { DEFAULT_OASIS_BASE_URL }.toHttpUrlOrNull() ?: return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) return null
    return if (url.encodedPath.endsWith('/')) url.toString() else url.newBuilder().addPathSegment("").build().toString()
}
