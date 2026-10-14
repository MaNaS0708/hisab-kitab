package com.example.hisabkitab.data.user

import com.example.hisabkitab.data.models.UserProfile
import com.example.hisabkitab.util.UsernameUtil
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun ensureUserDocument(
        user: FirebaseUser,
        name: String?,
        provider: String
    ): UserProfile {
        val document = firestore.collection("users").document(user.uid)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(document)
            if (!snapshot.exists()) {
                val initialData = mutableMapOf<String, Any?>(
                    "uid" to user.uid,
                    "name" to if (provider == "phone") null else name,
                    "email" to if (provider == "phone") null else user.email,
                    "phone" to user.phoneNumber,
                    "provider" to provider,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "lastLoginAt" to FieldValue.serverTimestamp()
                )
                transaction.set(document, initialData)
            } else {
                val updates = mutableMapOf<String, Any?>(
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (snapshot.getString("email").isNullOrBlank() && user.email != null) {
                    updates["email"] = user.email
                }
                if (snapshot.getString("phone").isNullOrBlank() && user.phoneNumber != null) {
                    updates["phone"] = user.phoneNumber
                }
                if (snapshot.getString("name").isNullOrBlank() && !name.isNullOrBlank()) {
                    updates["name"] = name
                }
                transaction.update(document, updates)
            }
            Unit
        }.await()
        return readProfile(user.uid)
    }

    suspend fun reserveUsernameAndSaveProfile(
        user: FirebaseUser,
        rawUsername: String,
        name: String?
    ): UserProfile {
        val normalized = UsernameUtil.normalize(rawUsername)
        if (!UsernameUtil.isValid(normalized)) {
            error("Invalid username format.")
        }

        val usernameDoc = firestore.collection("usernames").document(normalized)
        val userDoc = firestore.collection("users").document(user.uid)

        firestore.runTransaction { transaction ->
            val uSnap = transaction.get(usernameDoc)
            if (uSnap.exists()) {
                val existingUid = uSnap.getString("uid")
                if (existingUid != user.uid) {
                    throw IllegalStateException("Username is taken.")
                }
            } else {
                val usernameData = mutableMapOf<String, Any?>(
                    "uid" to user.uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (!name.isNullOrBlank()) {
                    usernameData["displayName"] = name
                }
                transaction.set(usernameDoc, usernameData)
            }

            val userData = mutableMapOf<String, Any?>(
                "uid" to user.uid,
                "username" to normalized,
                "email" to (user.email ?: transaction.get(userDoc).getString("email")),
                "phone" to (user.phoneNumber ?: transaction.get(userDoc).getString("phone")),
                "provider" to (if (user.phoneNumber != null) "phone" else "password"),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!name.isNullOrBlank()) {
                userData["name"] = name
            }

            transaction.set(userDoc, userData, SetOptions.merge())
            Unit
        }.await()

        return readProfile(user.uid)
    }

    suspend fun readProfile(user: FirebaseUser): UserProfile {
        return readProfile(user.uid)
    }

    suspend fun readProfile(uid: String): UserProfile {
        val snapshot = firestore.collection("users").document(uid).get().await()
        if (!snapshot.exists()) {
            error("User profile not found.")
        }
        val provider = snapshot.getString("provider") ?: "password"
        return UserProfile(
            uid = uid,
            username = snapshot.getString("username"),
            name = snapshot.getString("name"),
            email = snapshot.getString("email"),
            phone = snapshot.getString("phone"),
            provider = provider,
            createdAt = snapshot.getDate("createdAt"),
            updatedAt = snapshot.getDate("updatedAt"),
            lastLoginAt = snapshot.getDate("lastLoginAt")
        )
    }

    suspend fun findUserByUsername(rawUsername: String): UserProfile? {
        val normalized = UsernameUtil.normalize(rawUsername)
        if (!UsernameUtil.isValid(normalized)) return null

        val usernameDoc = firestore.collection("usernames").document(normalized).get().await()
        if (!usernameDoc.exists()) return null

        val targetUid = usernameDoc.getString("uid") ?: return null
        val docDisplayName = usernameDoc.getString("displayName")

        return try {
            val userDoc = firestore.collection("users").document(targetUid).get().await()
            if (userDoc.exists()) {
                UserProfile(
                    uid = targetUid,
                    username = normalized,
                    name = userDoc.getString("name") ?: docDisplayName,
                    email = userDoc.getString("email"),
                    phone = userDoc.getString("phone"),
                    provider = userDoc.getString("provider") ?: "password"
                )
            } else {
                UserProfile(
                    uid = targetUid,
                    username = normalized,
                    name = docDisplayName
                )
            }
        } catch (_: Exception) {
            UserProfile(
                uid = targetUid,
                username = normalized,
                name = docDisplayName
            )
        }
    }
}

