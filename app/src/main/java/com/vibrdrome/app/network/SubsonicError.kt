package com.vibrdrome.app.network

sealed class SubsonicError : Exception() {
    data class HttpError(val code: Int) : SubsonicError()
    data class ApiError(val code: Int, override val message: String) : SubsonicError()
    data object NoServerConfigured : SubsonicError()
    data class DecodingError(override val cause: Throwable) : SubsonicError()
    data object NetworkUnavailable : SubsonicError()
    data object InvalidURL : SubsonicError()

    val userMessage: String
        get() = when (this) {
            is HttpError -> httpMessage(code)
            is ApiError -> apiMessage(code, message)
            is NoServerConfigured -> "No server configured. Add a server in Settings."
            is DecodingError -> "Unexpected response from server. It may be incompatible."
            is NetworkUnavailable -> "No network connection. Check your internet and try again."
            is InvalidURL -> "Invalid server URL. Check your server address."
        }

    companion object {
        private fun httpMessage(code: Int): String = when (code) {
            401 -> "Authentication failed. Check your credentials."
            403 -> "Access denied. Your account may not have permission."
            404 -> "The requested item was not found on the server."
            in 500..599 -> "The server encountered an error. Please try again later."
            else -> "Server returned an error (HTTP $code)."
        }

        private val apiMessages = mapOf(
            10 to "Required parameter is missing.",
            20 to "Server version is incompatible with this app.",
            30 to "Server version is too old for this feature.",
            40 to "Wrong username or password.",
            41 to "Token authentication not supported. Check server settings.",
            50 to "You don't have permission for this action.",
            60 to "Trial period has expired.",
            70 to "The requested item was not found.",
        )

        private fun apiMessage(code: Int, message: String): String {
            apiMessages[code]?.let { return it }
            return message.ifEmpty { "Server error." }
        }

        fun userMessage(error: Throwable): String {
            if (error is SubsonicError) return error.userMessage
            if (error is java.net.UnknownHostException) return "Cannot reach the server. Check the address and try again."
            if (error is java.net.SocketTimeoutException) return "Connection timed out. The server may be unreachable."
            if (error is java.net.ConnectException) return "Cannot connect to the server."
            if (error is javax.net.ssl.SSLException) return "Secure connection failed. Check your server's SSL certificate."
            return "Something went wrong. Please try again."
        }
    }
}
