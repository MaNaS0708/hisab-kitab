package com.example.hisabkitab.data.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class UserProfile(
    val uid: String = "",
    val username: String? = null,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val provider: String = "password",
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null,
    @ServerTimestamp val lastLoginAt: Date? = null
) {
    val greetingName: String
        get() = name?.takeIf(String::isNotBlank)
            ?: username?.let { "@$it" }
            ?: phone?.takeIf(String::isNotBlank)
            ?: email.orEmpty()

    val displayHandle: String
        get() = username?.let { "@$it" } ?: email ?: phone.orEmpty()
}

data class Group(
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val memberIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)

data class GroupMember(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    @ServerTimestamp val joinedAt: Date? = null
)

data class Expense(
    val id: String = "",
    val description: String = "",
    val amountPaise: Long = 0L,
    val paidByUid: String = "",
    val participantIds: List<String> = emptyList(),
    val splitType: String = "equal",
    val sharesByUid: Map<String, Long> = emptyMap(),
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Date? = null
)

data class Settlement(
    val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val amountPaise: Long = 0L,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Date? = null
)

data class GroupActivity(
    val id: String = "",
    val type: String = "",
    val actorUid: String = "",
    val message: String = "",
    @ServerTimestamp val createdAt: Date? = null
)
