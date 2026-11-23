package com.stashly.app.core

/** Machine-readable error categories. Stored in the database as the enum name. */
enum class ErrorCode {
    INVALID_URL, UNSUPPORTED_SOURCE, RESTRICTED_SOURCE, NO_MEDIA, UNSUPPORTED_FORMAT, UNSAFE_FILE,
    NETWORK, SERVER_UNAVAILABLE, NOT_FOUND, AUTH_REQUIRED, INSUFFICIENT_STORAGE, PERMISSION_DENIED,
    INTERRUPTED, DUPLICATE, CORRUPTED, CANCELLED, UNKNOWN;

    /** True when retrying later (resuming) can plausibly succeed. */
    val isRetryable: Boolean
        get() = this == NETWORK || this == SERVER_UNAVAILABLE || this == INTERRUPTED
}

/** The single exception type used across layers; [userMessage] is always safe to show in the UI. */
class AppError(
    val code: ErrorCode,
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause) {
    companion object {
        fun from(t: Throwable): AppError = when (t) {
            is AppError -> t
            is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException,
            is javax.net.ssl.SSLException ->
                AppError(ErrorCode.NETWORK, "Network problem. Check your connection and try again.", t)
            is java.io.IOException ->
                if (t.message.orEmpty().contains("ENOSPC", true) || t.message.orEmpty().contains("No space", true))
                    AppError(ErrorCode.INSUFFICIENT_STORAGE, "Not enough free storage on this device.", t)
                else AppError(ErrorCode.NETWORK, "The connection was interrupted.", t)
            is SecurityException ->
                AppError(ErrorCode.PERMISSION_DENIED, "Permission denied while accessing storage.", t)
            else -> AppError(ErrorCode.UNKNOWN, "Something went wrong. Please try again.", t)
        }
    }
}
