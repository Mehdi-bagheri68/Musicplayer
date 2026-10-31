package com.example.universalmusic.domain.error

import com.example.universalmusic.domain.model.ProviderId

enum class ProviderErrorCode {
    NETWORK_UNAVAILABLE, PROVIDER_TIMEOUT, PROVIDER_RATE_LIMITED, AUTH_REQUIRED, SUBSCRIPTION_REQUIRED,
    REGION_RESTRICTED, CONTENT_BLOCKED, STREAM_EXPIRED, UNSUPPORTED_FORMAT, OFFICIAL_PLAYER_REQUIRED,
    RIGHTS_NOT_CONFIRMED, UNKNOWN_PROVIDER_ERROR
}

class ProviderException(
    val providerId: ProviderId,
    val code: ProviderErrorCode,
    val safeMessage: String,
    val httpStatus: Int? = null,
    val retryAfterMs: Long? = null,
    cause: Throwable? = null
) : Exception(safeMessage, cause)
