package com.example.hisabkitab.data.user

import com.example.hisabkitab.data.models.UserProfile
import com.example.hisabkitab.util.AppLog
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
        AppLog.i("UserRepository", "ensureUserDocument called for uid=${user.uid}, email=${user.email}")
        val document = db.collection("users").document(user.uid)
        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(document)
                if (!snapshot.exists()) {
                    AppLog.i("UserRepository", "Creating initial user document for uid=${user.uid}")
                    val initialData = mutableMapOf<String, Any?>(
                        "uid" to user.uid,
                        "email" to user.email,
                        "provider" to "password",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "lastLoginAt" to FieldValue.serverTimestamp()
                    )
                    if (!name.isNullOrBlank()) {
                        initialData["name"] = name
                    } else if (!user.displayName.isNullOrBlank()) {
                        initialData["name"] = user.displayName
                    }
                    transaction.set(document, initialData)
                } else {
                    AppLog.i("UserRepository", "Updating lastLoginAt for existing user uid=${user.uid}")
                    val updates = mutableMapOf<String, Any?>(
                        "lastLoginAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (snapshot.getString("email").isNullOrBlank() && user.email != null) {
                        updates["email"] = user.email
                    }
                    val currentName = snapshot.getString("name")
                    if (currentName.isNullOrBlank() && !name.isNullOrBlank()) {
                        updates["name"] = name
                    } else if (currentName.isNullOrBlank() && !user.displayName.isNullOrBlank()) {
                        updates["name"] = user.displayName
                    }
                    if (snapshot.get("createdAt") == null) {
                        updates["createdAt"] = FieldValue.serverTimestamp()
                    }
                    if (snapshot.getString("provider").isNullOrBlank()) {
                        updates["provider"] = "password"
                    }
                    transaction.update(document, updates)
                }
                Unit
            }.await()
            AppLog.i("UserRepository", "ensureUserDocument transaction completed for uid=${user.uid}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e("UserRepository", "Error in ensureUserDocument transaction for uid=${user.uid}", e)
        }

        return readProfile(user)
    }

    suspend fun reserveUsernameAndSaveProfile(
        user: FirebaseUser,
        rawUsername: String,
        name: String?
    ): UserProfile {
        val normalized = UsernameUtil.normalize(rawUsername)
        AppLog.i("UserRepository", "reserveUsernameAndSaveProfile called for uid=${user.uid}, username=$normalized")
        if (!UsernameUtil.isValid(normalized)) {
            AppLog.e("UserRepository", "Invalid username format: $normalized")
            error("Invalid username format.")
        }

        val usernameDoc = db.collection("usernames").document(normalized)
        val userDoc = db.collection("users").document(user.uid)

        try {
            db.runTransaction { transaction ->
                val uSnap = transaction.get(usernameDoc)
                if (uSnap.exists()) {
                    val existingUid = uSnap.getString("uid")
                    if (existingUid != user.uid) {
                        AppLog.w("UserRepository", "Username $normalized is taken by $existingUid")
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
            AppLog.i("UserRepository", "Username $normalized reserved successfully for uid=${user.uid}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e("UserRepository", "Error reserving username $normalized for uid=${user.uid}", e)
            throw e
        }

        return readProfile(user)
    }

    open suspend fun readProfile(user: FirebaseUser): UserProfile {
        return try {
            val profile = readProfile(user.uid)
            profile.copy(
                email = profile.email ?: user.email,
                name = profile.name ?: user.displayName
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.w("UserRepository", "Error reading profile for uid=${user.uid}, returning fallback from FirebaseUser", e)
            UserProfile(
                uid = user.uid,
                email = user.email,
                name = user.displayName,
                provider = "password"
            )
        }
    }

    open suspend fun readProfile(uid: String): UserProfile {
        AppLog.d("UserRepository", "readProfile called for uid=$uid")
        return try {
            val snapshot = db.collection("users").document(uid).get().await()
            if (!snapshot.exists()) {
                AppLog.w("UserRepository", "Profile document for uid=$uid does not exist, using default profile")
                return UserProfile(uid = uid, provider = "password")
            }
            val provider = snapshot.getString("provider") ?: "password"
            UserProfile(
                uid = uid,
                username = snapshot.getString("username"),
                name = snapshot.getString("name"),
                email = snapshot.getString("email"),
                provider = provider,
                createdAt = snapshot.getDate("createdAt"),
                updatedAt = snapshot.getDate("updatedAt"),
                lastLoginAt = snapshot.getDate("lastLoginAt")
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e("UserRepository", "Error reading profile for uid=$uid", e)
            UserProfile(uid = uid, provider = "password")
        }
    }

    open suspend fun findUserByUsername(rawUsername: String): UserProfile? {
        val normalized = UsernameUtil.normalize(rawUsername)
        AppLog.d("UserRepository", "findUserByUsername called for username=$normalized")
        if (!UsernameUtil.isValid(normalized)) return null

        return try {
            val usernameDoc = db.collection("usernames").document(normalized).get().await()
            if (!usernameDoc.exists()) {
                AppLog.d("UserRepository", "Username $normalized not found in usernames collection")
                return null
            }

            val targetUid = usernameDoc.getString("uid") ?: return null
            val docDisplayName = usernameDoc.getString("displayName")

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
            AppLog.w("UserRepository", "Error looking up username $normalized", exception)
            null
        }
    }
}
