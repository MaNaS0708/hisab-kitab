package com.example.hisabkitab.util

object UsernameUtil {
    private val USERNAME_REGEX = Regex("^[a-z0-9_]{3,20}$")

    fun normalize(raw: String): String = raw.trim().lowercase()

    fun isValid(username: String): Boolean {
        val normalized = normalize(username)
        return USERNAME_REGEX.matches(normalized)
    }

    fun validationErrorMessage(username: String): String? {
        val normalized = normalize(username)
        return when {
            normalized.isBlank() -> "Username is required."
            normalized.length < 3 -> "Username must be at least 3 characters."
            normalized.length > 20 -> "Username must be at most 20 characters."
            !normalized.matches(Regex("^[a-z0-9_]+$")) -> "Only lowercase letters, numbers, and underscores allowed."
            else -> null
        }
    }
}
