package com.example.hisabkitab.util

import android.content.Context
import com.example.hisabkitab.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

class AuthErrorMapper(private val context: Context) {
    fun message(throwable: Throwable): String {
        if (throwable is FirebaseNetworkException ||
            throwable is IOException ||
            (throwable is FirebaseFirestoreException &&
                throwable.code == FirebaseFirestoreException.Code.UNAVAILABLE)
        ) {
            return context.getString(R.string.error_network)
        }

        val code = (throwable as? FirebaseAuthException)?.errorCode.orEmpty()
        val messageId = when {
            code.contains("WRONG_PASSWORD", ignoreCase = true) ||
                code.contains("INVALID_CREDENTIAL", ignoreCase = true) ->
                R.string.error_wrong_password
            code.contains("USER_NOT_FOUND", ignoreCase = true) ->
                R.string.error_user_not_found
            code.contains("EMAIL_ALREADY_IN_USE", ignoreCase = true) ->
                R.string.error_email_in_use
            code.contains("WEAK_PASSWORD", ignoreCase = true) ->
                R.string.error_weak_password
            code.contains("INVALID_VERIFICATION_CODE", ignoreCase = true) ||
                code.contains("SESSION_EXPIRED", ignoreCase = true) ->
                R.string.error_invalid_otp
            code.contains("INVALID_PHONE_NUMBER", ignoreCase = true) ->
                R.string.error_phone_invalid
            code.contains("TOO_MANY_REQUESTS", ignoreCase = true) ->
                R.string.error_too_many_requests
            else -> R.string.error_generic
        }
        return context.getString(messageId)
    }
}
