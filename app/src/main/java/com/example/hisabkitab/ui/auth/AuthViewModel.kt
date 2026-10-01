package com.example.hisabkitab.ui.auth

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisabkitab.R
import com.example.hisabkitab.data.auth.AuthRepository
import com.example.hisabkitab.data.user.UserProfile
import com.example.hisabkitab.data.user.UserRepository
import com.example.hisabkitab.util.AuthErrorMapper
import com.example.hisabkitab.util.NetworkMonitor
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
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
    PhoneRequest,
    VerifyOtp,
    ResendOtp
}

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Loaded(val profile: UserProfile) : ProfileUiState
    data class Error(val message: String, val retryable: Boolean) : ProfileUiState
}

sealed interface AuthEvent {
    data object Login : AuthEvent
    data object VerifyEmail : AuthEvent
    data object Home : AuthEvent
    data object Otp : AuthEvent
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val networkMonitor = NetworkMonitor(application)
    private val errorMapper = AuthErrorMapper(application)

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state = _state.asStateFlow()

    private val _profileState = MutableStateFlow<ProfileUiState?>(null)
    val profileState = _profileState.asStateFlow()

    private val eventChannel = Channel<AuthEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val _verificationEmail = MutableStateFlow("")
    val verificationEmail = _verificationEmail.asStateFlow()

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber = _phoneNumber.asStateFlow()

    private val _emailCooldown = MutableStateFlow(0)
    val emailCooldown = _emailCooldown.asStateFlow()

    private val _otpCooldown = MutableStateFlow(0)
    val otpCooldown = _otpCooldown.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var phoneSignInStarted = false
    private var emailCooldownJob: Job? = null
    private var otpCooldownJob: Job? = null

    fun resolveAuthGate() {
        viewModelScope.launch {
            _state.value = AuthUiState.Loading(AuthAction.Gate)
            val user = authRepository.currentUser
            if (user == null) {
                _state.value = AuthUiState.Idle
                eventChannel.send(AuthEvent.Login)
                return@launch
            }
            try {
                val provider = providerFor(user)
                userRepository.ensureUserDocument(user, user.displayName, provider)
                _state.value = AuthUiState.Idle
                val phoneUser = user.providerData.any { it.providerId == "phone" }
                if (!phoneUser && !user.isEmailVerified) {
                    _verificationEmail.value = user.email.orEmpty()
                    eventChannel.send(AuthEvent.VerifyEmail)
                } else {
                    eventChannel.send(AuthEvent.Home)
                }
            } catch (exception: FirebaseException) {
                setFailure(exception)
            } catch (exception: IOException) {
                setFailure(exception)
            } catch (exception: IllegalStateException) {
                setFailure(exception)
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            if (!requireNetwork()) return@launch
            _state.value = AuthUiState.Loading(AuthAction.Login)
            try {
                val user = authRepository.signIn(email, password)
                userRepository.ensureUserDocument(user, user.displayName, "password")
                _verificationEmail.value = user.email.orEmpty()
                _state.value = AuthUiState.Idle
                if (!user.isEmailVerified) {
                    eventChannel.send(AuthEvent.VerifyEmail)
                } else {
                    eventChannel.send(AuthEvent.Home)
                }
            } catch (exception: FirebaseException) {
                setFailure(exception)
            } catch (exception: IOException) {
                setFailure(exception)
            } catch (exception: IllegalStateException) {
                setFailure(exception)
            }
        }
    }

    fun createAccount(name: String, email: String, password: String) {
        viewModelScope.launch {
            if (!requireNetwork()) return@launch
            _state.value = AuthUiState.Loading(AuthAction.Signup)
            try {
                val existingUser = authRepository.currentUser
                    ?.takeIf { it.email.equals(email, ignoreCase = true) }
                val user = existingUser ?: authRepository.createAccount(email, password)
                authRepository.setDisplayName(user, name)
                userRepository.ensureUserDocument(user, name, "password")
                _verificationEmail.value = user.email.orEmpty()
                try {
                    authRepository.sendEmailVerification(user)
                    startEmailCooldown()
                } catch (exception: FirebaseException) {
                    setFailure(exception)
                    eventChannel.send(AuthEvent.VerifyEmail)
                    return@launch
                } catch (exception: IOException) {
                    setFailure(exception)
                    eventChannel.send(AuthEvent.VerifyEmail)
                    return@launch
                }
                _state.value = AuthUiState.Idle
                eventChannel.send(AuthEvent.VerifyEmail)
            } catch (exception: FirebaseException) {
                setFailure(exception)
            } catch (exception: IOException) {
                setFailure(exception)
            } catch (exception: IllegalStateException) {
                setFailure(exception)
            }
        }
    }

    fun verifyEmail() {
        viewModelScope.launch {
            if (!requireNetwork()) return@launch
            _state.value = AuthUiState.Loading(AuthAction.VerifyEmail)
            val user = authRepository.currentUser
            if (user == null) {
                _state.value = AuthUiState.Idle
                eventChannel.send(AuthEvent.Login)
                return@launch
            }
            try {
                val refreshedUser = authRepository.reloadUser(user)
                if (refreshedUser.isEmailVerified) {
                    _state.value = AuthUiState.Idle
                    eventChannel.send(AuthEvent.Home)
                } else {
                    _state.value = AuthUiState.Error(getString(R.string.verification_pending))
                }
            } catch (exception: FirebaseException) {
                setFailure(exception)
            } catch (exception: IOException) {
                setFailure(exception)
            } catch (exception: IllegalStateException) {
                setFailure(exception)
            }
        }
    }

    fun resendVerificationEmail() {
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
            } catch (exception: FirebaseException) {
                setFailure(exception)
            } catch (exception: IOException) {
                setFailure(exception)
            }
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            if (!requireNetwork()) return@launch
            _state.value = AuthUiState.Loading(AuthAction.ResetPassword)
            try {
                authRepository.sendPasswordReset(email)
                _state.value = AuthUiState.Success(email)
            } catch (exception: FirebaseException) {
                setFailure(exception)
            } catch (exception: IOException) {
                setFailure(exception)
            }
        }
    }

    fun beginPhoneVerification(activity: Activity, phone: String) {
        _phoneNumber.value = phone
        phoneSignInStarted = false
        requestPhoneVerification(activity, phone, null, navigateToOtp = true)
    }

    fun resendOtp(activity: Activity) {
        val phone = _phoneNumber.value
        if (phone.isBlank() || _otpCooldown.value > 0) return
        phoneSignInStarted = false
        requestPhoneVerification(activity, phone, resendToken, navigateToOtp = false)
    }

    fun verifyOtp(code: String) {
        if (phoneSignInStarted) return
        val id = verificationId
        if (id == null) {
            _state.value = AuthUiState.Error(getString(R.string.error_invalid_otp))
            return
        }
        viewModelScope.launch {
            if (!requireNetwork()) return@launch
            phoneSignInStarted = true
            _state.value = AuthUiState.Loading(AuthAction.VerifyOtp)
            try {
                completePhoneSignIn(PhoneAuthProvider.getCredential(id, code))
            } catch (exception: FirebaseException) {
                phoneSignInStarted = false
                setFailure(exception)
            } catch (exception: IOException) {
                phoneSignInStarted = false
                setFailure(exception)
            } catch (exception: IllegalStateException) {
                phoneSignInStarted = false
                setFailure(exception)
            }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            val user = authRepository.currentUser
            if (user == null) {
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
                _profileState.value = ProfileUiState.Loaded(userRepository.readProfile(user))
            } catch (exception: FirebaseException) {
                _profileState.value = ProfileUiState.Error(
                    errorMapper.message(exception),
                    retryable = isNetworkFailure(exception)
                )
            } catch (exception: IOException) {
                _profileState.value = ProfileUiState.Error(
                    errorMapper.message(exception),
                    retryable = true
                )
            }
        }
    }

    fun logout() {
        authRepository.signOut()
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

    private fun requestPhoneVerification(
        activity: Activity,
        phone: String,
        token: PhoneAuthProvider.ForceResendingToken?,
        navigateToOtp: Boolean
    ) {
        if (!requireNetwork()) return
        _state.value = AuthUiState.Loading(
            if (navigateToOtp) AuthAction.PhoneRequest else AuthAction.ResendOtp
        )
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                if (phoneSignInStarted) return
                phoneSignInStarted = true
                viewModelScope.launch {
                    _state.value = AuthUiState.Loading(AuthAction.VerifyOtp)
                    try {
                        completePhoneSignIn(credential)
                    } catch (exception: FirebaseException) {
                        phoneSignInStarted = false
                        setFailure(exception)
                    } catch (exception: IOException) {
                        phoneSignInStarted = false
                        setFailure(exception)
                    } catch (exception: IllegalStateException) {
                        phoneSignInStarted = false
                        setFailure(exception)
                    }
                }
            }

            override fun onVerificationFailed(exception: FirebaseException) {
                phoneSignInStarted = false
                _state.value = AuthUiState.Error(
                    errorMapper.message(exception),
                    retryable = isNetworkFailure(exception)
                )
            }

            override fun onCodeSent(
                id: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                if (phoneSignInStarted) return
                verificationId = id
                resendToken = token
                _state.value = AuthUiState.Success(getString(R.string.otp_sent))
                startOtpCooldown()
                if (navigateToOtp) {
                    eventChannel.trySend(AuthEvent.Otp)
                }
            }
        }
        try {
            authRepository.verifyPhoneNumber(activity, phone, callbacks, token)
        } catch (exception: FirebaseException) {
            setFailure(exception)
        }
    }

    private suspend fun completePhoneSignIn(credential: PhoneAuthCredential) {
        val user = authRepository.signInWithPhoneCredential(credential)
        userRepository.ensureUserDocument(user, name = null, provider = "phone")
        _state.value = AuthUiState.Idle
        eventChannel.send(AuthEvent.Home)
    }

    private fun providerFor(user: FirebaseUser): String =
        if (user.providerData.any { it.providerId == "phone" }) "phone" else "password"

    private fun startEmailCooldown() {
        emailCooldownJob?.cancel()
        emailCooldownJob = startCooldown(_emailCooldown, 60)
    }

    private fun startOtpCooldown() {
        otpCooldownJob?.cancel()
        otpCooldownJob = startCooldown(_otpCooldown, 30)
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
