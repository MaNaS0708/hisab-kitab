package com.example.hisabkitab.data.user

data class UserProfile(
    val uid: String,
    val name: String?,
    val email: String?,
    val phone: String?,
    val provider: String
) {
    val greetingName: String
        get() = name?.takeIf(String::isNotBlank)
            ?: phone?.takeIf(String::isNotBlank)
            ?: email.orEmpty()
}
