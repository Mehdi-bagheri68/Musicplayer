package com.example.universalmusic.core.common

import android.util.Log
import com.example.universalmusic.BuildConfig

object SafeLogger {
    private val SENSITIVE_PARAM = Regex(
        "(?i)(client_id|client_secret|access_token|refresh_token|api_key|x-api-key|signature|sig|token|auth)=([^&\\s]+)"
    )
    private val BEARER = Regex("(?i)(Bearer\\s+)[A-Za-z0-9\\-._~+/]+=*")

    fun sanitize(raw: String): String = raw
        .replace(SENSITIVE_PARAM) { "${it.groupValues[1]}=[REDACTED]" }
        .replace(BEARER) { "${it.groupValues[1]}[REDACTED]" }

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, sanitize(message))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(tag, sanitize(message), throwable)
    }
}
