package com.example.geminimultimodalliveapi.error

sealed class AppError {
    data class Network(val message: String) : AppError()
    data class Permission(val type: String) : AppError()
    object AuthExpired : AppError()
    data class Api(val code: Int, val message: String) : AppError()
    data class Tool(val name: String, val message: String) : AppError()

    companion object {
        fun isAuthException(e: Throwable): Boolean {
            val msg = e.message?.lowercase() ?: ""
            return e is com.google.api.client.googleapis.json.GoogleJsonResponseException && (e.statusCode == 401 || e.statusCode == 403) ||
                   e is com.google.android.gms.auth.UserRecoverableAuthException ||
                   msg.contains("auth") || msg.contains("401") || msg.contains("403") || msg.contains("credential")
        }

        fun fromThrowable(e: Throwable): AppError {
            if (isAuthException(e)) {
                return AuthExpired
            }
            val msg = e.message ?: "Unknown error"
            val lowerMsg = msg.lowercase()
            return when {
                e is java.net.UnknownHostException || 
                e is java.net.ConnectException || 
                e is java.net.SocketTimeoutException || 
                lowerMsg.contains("network") || 
                lowerMsg.contains("timeout") || 
                lowerMsg.contains("connect") -> Network(msg)
                e is com.google.api.client.googleapis.json.GoogleJsonResponseException -> Api(e.statusCode, e.details?.message ?: msg)
                else -> Api(-1, msg)
            }
        }
    }
}
