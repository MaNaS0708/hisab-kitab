package com.example.hisabkitab.data.user

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
                transaction.set(
                    document,
                    mapOf(
                        "uid" to user.uid,
                        "name" to if (provider == "phone") null else name,
                        "email" to if (provider == "phone") null else user.email,
                        "phone" to user.phoneNumber,
                        "provider" to provider,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                transaction.update(document, "lastLoginAt", FieldValue.serverTimestamp())
            }
            Unit
        }.await()
        return readProfile(user)
    }

    suspend fun readProfile(user: FirebaseUser): UserProfile {
        val snapshot = firestore.collection("users").document(user.uid).get().await()
        val provider = snapshot.getString("provider")
            ?: if (user.phoneNumber != null) "phone" else "password"
        return UserProfile(
            uid = user.uid,
            name = snapshot.getString("name") ?: user.displayName,
            email = snapshot.getString("email") ?: user.email,
            phone = snapshot.getString("phone") ?: user.phoneNumber,
            provider = provider
        )
    }
}
