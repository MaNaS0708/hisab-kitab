package com.example.hisabkitab.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

open class AuthRepository(
    private val firebaseAuth: FirebaseAuth? = null
) {
    protected val auth: FirebaseAuth
        get() = firebaseAuth ?: FirebaseAuth.getInstance()

    open val currentUser: FirebaseUser?
        get() = auth.currentUser

    open val currentUid: String?
        get() = currentUser?.uid

    suspend fun signIn(email: String, password: String): FirebaseUser =
        auth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Firebase sign-in completed without a user.")

    suspend fun createAccount(email: String, password: String): FirebaseUser =
        auth.createUserWithEmailAndPassword(email, password).await().user
            ?: error("Firebase account creation completed without a user.")

    suspend fun setDisplayName(user: FirebaseUser, name: String) {
        user.updateProfile(
            UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()
        ).await()
    }

    suspend fun sendEmailVerification(user: FirebaseUser) {
        user.sendEmailVerification().await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun reloadUser(user: FirebaseUser): FirebaseUser {
        user.reload().await()
        return auth.currentUser ?: user
    }

    fun signOut() {
        auth.signOut()
    }
}
