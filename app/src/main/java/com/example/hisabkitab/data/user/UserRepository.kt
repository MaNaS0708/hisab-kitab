package com.example.hisabkitab.data.user

import com.example.hisabkitab.data.models.UserProfile
import com.example.hisabkitab.util.UsernameUtil
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

open class UserRepository(
    private val firestore: FirebaseFirestore? = null
) {
    protected val db: FirebaseFirestore
        get() = firestore ?: FirebaseFirestore.getInstance()

    suspend fun ensureUserDocument(
        user: FirebaseUser,
        name: String?
    ): UserProfile {
        val document = db.collection("users").document(user.uid)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(document)
            if (!snapshot.exists()) {
                val initialData = mutableMapOf<String, Any?>(
                    "uid" to user.uid,
                    "name" to name,
                    "email" to user.email,
                    "provider" to "password",
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

        val usernameDoc = db.collection("usernames").document(normalized)
        val userDoc = db.collection("users").document(user.uid)

        db.runTransaction { transaction ->
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
                "provider" to "password",
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

    open suspend fun readProfile(user: FirebaseUser): UserProfile {
        return readProfile(user.uid)
    }

    open suspend fun readProfile(uid: String): UserProfile {
        val snapshot = db.collection("users").document(uid).get().await()
        if (!snapshot.exists()) {
            error("User profile not found.")
        }
        val provider = snapshot.getString("provider") ?: "password"
        return UserProfile(
            uid = uid,
            username = snapshot.getString("username"),
            name = snapshot.getString("name"),
            email = snapshot.getString("email"),
            provider = provider,
            createdAt = snapshot.getDate("createdAt"),
            updatedAt = snapshot.getDate("updatedAt"),
            lastLoginAt = snapshot.getDate("lastLoginAt")
        )
    }

    open suspend fun findUserByUsername(rawUsername: String): UserProfile? {
        val normalized = UsernameUtil.normalize(rawUsername)
        if (!UsernameUtil.isValid(normalized)) return null

        val usernameDoc = db.collection("usernames").document(normalized).get().await()
        if (!usernameDoc.exists()) return null

        val targetUid = usernameDoc.getString("uid") ?: return null
        val docDisplayName = usernameDoc.getString("displayName")

        return try {
            val userDoc = db.collection("users").document(targetUid).get().await()
            if (userDoc.exists()) {
                UserProfile(
                    uid = targetUid,
                    username = normalized,
                    name = userDoc.getString("name") ?: docDisplayName,
                    email = userDoc.getString("email"),
                    provider = userDoc.getString("provider") ?: "password"
                )
            } else {
                UserProfile(
                    uid = targetUid,
                    username = normalized,
                    name = docDisplayName
                )
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            UserProfile(
                uid = targetUid,
                username = normalized,
                name = docDisplayName
            )
        }
    }
}

