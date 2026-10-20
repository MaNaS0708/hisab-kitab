package com.example.hisabkitab.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisabkitab.R
import com.example.hisabkitab.data.auth.AuthRepository
import com.example.hisabkitab.data.models.UserProfile
import com.example.hisabkitab.data.user.UserRepository
import com.example.hisabkitab.util.AppLog
import com.example.hisabkitab.util.AuthErrorMapper
import com.example.hisabkitab.util.NetworkMonitor
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data class Loading(val action: AuthAction) : AuthUiState
    data class Success(val message: String? = null) : AuthUiState
    data class Error(val message: String, val retryable: Boolean = false) : AuthUiState
}

enum class AuthAction {
    Gate,
    Login,
    Signup,
    VerifyEmail,
    ResendEmail,
    ResetPassword,
    SetUsername
}

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Loaded(val profile: UserProfile) : ProfileUiState
    data class Error(val message: String, val retryable: Boolean) : ProfileUiState
}

sealed interface AuthEvent {
    data object Login : AuthEvent
    data object VerifyEmail : AuthEvent
    data object UsernameOnboarding : AuthEvent
    data object Home : AuthEvent
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val networkMonitor = NetworkMonitor(application)
    private val errorMapper = AuthErrorMapper(application)

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _profileState = MutableStateFlow<ProfileUiState?>(null)
    val profileState: StateFlow<ProfileUiState?> = _profileState.asStateFlow()

    private val eventChannel = Channel<AuthEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val _verificationEmail = MutableStateFlow("")
    val verificationEmail: StateFlow<String> = _verificationEmail.asStateFlow()

    private val _emailCooldown = MutableStateFlow(0)
    val emailCooldown: StateFlow<Int> = _emailCooldown.asStateFlow()

    private var emailCooldownJob: Job? = null

    fun resolveAuthGate() {
        AppLog.i("AuthViewModel", "resolveAuthGate initiated")
        viewModelScope.launch {
            _state.value = AuthUiState.Loading(AuthAction.Gate)
            try {
                val user = authRepository.currentUser
                if (user == null) {
                    AppLog.i("AuthViewModel", "resolveAuthGate: No current user logged in -> Login event")
                    _state.value = AuthUiState.Idle
                    eventChannel.send(AuthEvent.Login)
                    return@launch
                }
                AppLog.i("AuthViewModel", "resolveAuthGate: Current user found uid=${user.uid}, email=${user.email}")
                routeUserAfterAuth(user)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                AppLog.e("AuthViewModel", "Exception in resolveAuthGate", exception)
                setFailure(exception)
            }
        }
    }

    fun login(email: String, password: String) {
        AppLog.i("AuthViewModel", "login requested for email=$email")
        viewModelScope.launch {
            if (!requireNetwork()) return@launch
            _state.value = AuthUiState.Loading(AuthAction.Login)
            try {
                val user = authRepository.signIn(email, password)
                AppLog.i("AuthViewModel", "signIn successful for uid=${user.uid}")
                routeUserAfterAuth(user)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                AppLog.e("AuthViewModel", "Exception during login", exception)
                setFailure(exception)
            }
        }
    }

    fun createAccount(name: String, email: String, password: String) {
        AppLog.i("AuthViewModel", "createAccount requested for email=$email, name=$name")
        viewModelScope.launch {
            if (!requireNetwork()) return@launch
            _state.value = AuthUiState.Loading(AuthAction.Signup)
            try {
                val existingUser = authRepository.currentUser
                    ?.takeIf { it.email.equals(email, ignoreCase = true) }
                val user = existingUser ?: authRepository.createAccount(email, password)
                AppLog.i("AuthViewModel", "Firebase user account created uid=${user.uid}")

                try {
                    authRepository.setDisplayName(user, name)
                } catch (e: Exception) {
                    AppLog.w("AuthViewModel", "Could not set Firebase display name", e)
                }

                val profile = userRepository.ensureUserDocument(user, name)
                _verificationEmail.value = user.email.orEmpty()

                try {
                    authRepository.sendEmailVerification(user)
                    startEmailCooldown()
                } catch (e: Exception) {
                    AppLog.w("AuthViewModel", "Could not send verification email on signup", e)
                }

                _state.value = AuthUiState.Idle
                if (profile.username.isNullOrBlank()) {
                    AppLog.i("AuthViewModel", "Profile username is blank -> UsernameOnboarding event")
                    eventChannel.send(AuthEvent.UsernameOnboarding)
                } else {
                    AppLog.i("AuthViewModel", "Profile username present -> VerifyEmail event")
                    eventChannel.send(AuthEvent.VerifyEmail)
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                AppLog.e("AuthViewModel", "Exception during createAccount", exception)
                setFailure(exception)
            }
        }
    }

    fun saveUsername(rawUsername: String, name: String?) {
        AppLog.i("AuthViewModel", "saveUsername requested for username=$rawUsername")
        viewModelScope.launch {
            if (!requireNetwork()) return@launch
            val user = authRepository.currentUser
            if (user == null) {
                AppLog.w("AuthViewModel", "saveUsername: No active Firebase user -> Login event")
                _state.value = AuthUiState.Idle
                eventChannel.send(AuthEvent.Login)
                return@launch
            }
            _state.value = AuthUiState.Loading(AuthAction.SetUsername)
            try {
                userRepository.reserveUsernameAndSaveProfile(user, rawUsername, name)
                _state.value = AuthUiState.Idle
                if (!user.isEmailVerified) {
                    _verificationEmail.value = user.email.orEmpty()
                    AppLog.i("AuthViewModel", "Username saved; email unverified -> VerifyEmail event")
                    eventChannel.send(AuthEvent.VerifyEmail)
                } else {
                    AppLog.i("AuthViewModel", "Username saved; email verified -> Home event")
                    eventChannel.send(AuthEvent.Home)
                }
            } catch (exception: IllegalStateException) {
                AppLog.w("AuthViewModel", "Username taken or invalid: ${exception.message}")
                _state.value = AuthUiState.Error(
                    exception.message ?: getString(R.string.username_taken)
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                AppLog.e("AuthViewModel", "Exception during saveUsername", exception)
                setFailure(exception)
            }
        }
    }

    fun verifyEmail() {
        AppLog.i("AuthViewModel", "verifyEmail check requested")
        viewModelScope.launch {
            if (!requireNetwork()) return@launch
            _state.value = AuthUiState.Loading(AuthAction.VerifyEmail)
            val user = authRepository.currentUser
            if (user == null) {
                AppLog.w("AuthViewModel", "verifyEmail: No current user -> Login event")
                _state.value = AuthUiState.Idle
                eventChannel.send(AuthEvent.Login)
                return@launch
            }
            try {
                val refreshedUser = authRepository.reloadUser(user)
                if (refreshedUser.isEmailVerified) {
                    val profile = userRepository.readProfile(user.uid)
                    _state.value = AuthUiState.Idle
                    if (profile.username.isNullOrBlank()) {
                        AppLog.i("AuthViewModel", "Email verified, username blank -> UsernameOnboarding event")
                        eventChannel.send(AuthEvent.UsernameOnboarding)
                    } else {
                        AppLog.i("AuthViewModel", "Email verified, username present -> Home event")
                        eventChannel.send(AuthEvent.Home)
                    }
                } else {
                    AppLog.i("AuthViewModel", "Email verification pending")
                    _state.value = AuthUiState.Error(getString(R.string.verification_pending))
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                AppLog.e("AuthViewModel", "Exception during verifyEmail", exception)
                setFailure(exception)
            }
        }
    }

    fun resendVerificationEmail() {
        AppLog.i("AuthViewModel", "resendVerificationEmail requested")
        viewModelScope.launch {
            if (_emailCooldown.value > 0 || !requireNetwork()) return@launch
            val user = authRepository.currentUser
            if (user == null) {
                _state.value = AuthUiState.Error(getString(R.string.error_generic))
                return@launch
            }
            _state.value = AuthUiState.Loading(AuthAction.ResendEmail)
            try {
                authRepository.sendEmailVerification(user)
                _state.value = AuthUiState.Success(getString(R.string.email_resend_success))
                startEmailCooldown()
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                AppLog.e("AuthViewModel", "Exception during resendVerificationEmail", exception)
                setFailure(exception)
            }
        }
    }

    fun sendPasswordReset(email: String) {
        AppLog.i("AuthViewModel", "sendPasswordReset requested for email=$email")
        viewModelScope.launch {
            if (!requireNetwork()) return@launch
            _state.value = AuthUiState.Loading(AuthAction.ResetPassword)
            try {
                authRepository.sendPasswordReset(email)
                _state.value = AuthUiState.Success(email)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                AppLog.e("AuthViewModel", "Exception during sendPasswordReset", exception)
                setFailure(exception)
            }
        }
    }

    fun loadProfile() {
        AppLog.d("AuthViewModel", "loadProfile requested")
        viewModelScope.launch {
            val user = authRepository.currentUser
            if (user == null) {
                AppLog.w("AuthViewModel", "loadProfile: No current user -> Login event")
                eventChannel.send(AuthEvent.Login)
                return@launch
            }
            _profileState.value = ProfileUiState.Loading
            if (!networkMonitor.isConnected()) {
                _profileState.value =
                    ProfileUiState.Error(getString(R.string.error_network), retryable = true)
                return@launch
            }
            try {
                val profile = userRepository.readProfile(user.uid)
                _profileState.value = ProfileUiState.Loaded(profile)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                AppLog.e("AuthViewModel", "Exception in loadProfile", exception)
                _profileState.value = ProfileUiState.Error(
                    errorMapper.message(exception),
                    retryable = isNetworkFailure(exception)
                )
            }
        }
    }

    fun logout() {
        AppLog.i("AuthViewModel", "logout requested")
        authRepository.signOut()
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setUserId("")
        } catch (_: Exception) {}
        _state.value = AuthUiState.Idle
        _profileState.value = null
        eventChannel.trySend(AuthEvent.Login)
    }

    fun switchAccount() {
        logout()
    }

    fun resetState() {
        _state.value = AuthUiState.Idle
    }

    private suspend fun routeUserAfterAuth(user: FirebaseUser, name: String? = null) {
        AppLog.i("AuthViewModel", "routeUserAfterAuth for uid=${user.uid}")
        val profile = userRepository.ensureUserDocument(user, name ?: user.displayName)
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setUserId(user.uid)
        } catch (_: Exception) {}

        _state.value = AuthUiState.Idle
        when {
            profile.username.isNullOrBlank() -> {
                AppLog.i("AuthViewModel", "routeUserAfterAuth: username blank -> UsernameOnboarding event")
                eventChannel.send(AuthEvent.UsernameOnboarding)
            }
            !user.isEmailVerified -> {
                _verificationEmail.value = user.email.orEmpty()
                AppLog.i("AuthViewModel", "routeUserAfterAuth: email unverified -> VerifyEmail event")
                eventChannel.send(AuthEvent.VerifyEmail)
            }
            else -> {
                AppLog.i("AuthViewModel", "routeUserAfterAuth: user verified & has username -> Home event")
                eventChannel.send(AuthEvent.Home)
            }
        }
    }

    private fun startEmailCooldown() {
        emailCooldownJob?.cancel()
        emailCooldownJob = startCooldown(_emailCooldown, 60)
    }

    private fun startCooldown(flow: MutableStateFlow<Int>, seconds: Int): Job =
        viewModelScope.launch {
            flow.value = seconds
            while (flow.value > 0) {
                delay(1_000)
                flow.value -= 1
            }
        }

    private fun requireNetwork(): Boolean {
        if (networkMonitor.isConnected()) return true
        _state.value = AuthUiState.Error(getString(R.string.error_network), retryable = true)
        return false
    }

    private fun setFailure(exception: Throwable) {
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(exception)
        } catch (_: Exception) {}
        _state.value = AuthUiState.Error(
            errorMapper.message(exception),
            retryable = isNetworkFailure(exception)
        )
    }

    private fun isNetworkFailure(exception: Throwable): Boolean =
        !networkMonitor.isConnected() ||
            exception is FirebaseNetworkException ||
            exception is IOException ||
            (exception is FirebaseFirestoreException &&
                exception.code == FirebaseFirestoreException.Code.UNAVAILABLE)

    private fun getString(resourceId: Int): String =
        getApplication<Application>().getString(resourceId)
}
