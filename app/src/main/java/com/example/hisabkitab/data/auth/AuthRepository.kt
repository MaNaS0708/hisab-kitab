package com.example.hisabkitab.data.auth

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class AuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    suspend fun signIn(email: String, password: String): FirebaseUser =
        firebaseAuth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Firebase sign-in completed without a user.")

    suspend fun createAccount(email: String, password: String): FirebaseUser =
        firebaseAuth.createUserWithEmailAndPassword(email, password).await().user
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
        firebaseAuth.sendPasswordResetEmail(email).await()
    }

    suspend fun reloadUser(user: FirebaseUser): FirebaseUser {
        user.reload().await()
        return firebaseAuth.currentUser ?: user
    }

    suspend fun signInWithPhoneCredential(credential: com.google.firebase.auth.PhoneAuthCredential): FirebaseUser =
        firebaseAuth.signInWithCredential(credential).await().user
            ?: error("Firebase phone sign-in completed without a user.")

    fun verifyPhoneNumber(
        activity: Activity,
        phone: String,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks,
        token: PhoneAuthProvider.ForceResendingToken? = null
    ) {
        val optionsBuilder = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        if (token != null) {
            optionsBuilder.setForceResendingToken(token)
        }
        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
    }

    fun signOut() {
        firebaseAuth.signOut()
    }
}
