package com.example.hisabkitab.ui.auth

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.hisabkitab.R
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

abstract class BaseAuthFragment : Fragment() {
    protected val authViewModel: AuthViewModel by activityViewModels()

    protected fun observeAuthState(onState: (AuthUiState) -> Unit = {}) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { authViewModel.state.collect(onState) }
                launch { authViewModel.events.collect(::handleEvent) }
            }
        }
    }

    protected fun bindStatus(
        state: AuthUiState,
        messageView: TextView,
        retryButton: MaterialButton? = null,
        onRetry: () -> Unit = {}
    ) {
        when (state) {
            AuthUiState.Idle, is AuthUiState.Loading -> {
                messageView.visibility = View.GONE
                retryButton?.visibility = View.GONE
            }
            is AuthUiState.Success -> {
                val message = state.message
                if (message.isNullOrBlank()) {
                    messageView.visibility = View.GONE
                } else {
                    messageView.setTextColor(requireContext().getColor(R.color.color_primary))
                    messageView.text = message
                    messageView.visibility = View.VISIBLE
                }
                retryButton?.visibility = View.GONE
            }
            is AuthUiState.Error -> {
                messageView.setTextColor(requireContext().getColor(R.color.color_error))
                messageView.text = state.message
                messageView.visibility = View.VISIBLE
                retryButton?.visibility = if (state.retryable) View.VISIBLE else View.GONE
                retryButton?.setOnClickListener { onRetry() }
            }
        }
    }

    protected fun bindProgress(
        state: AuthUiState,
        button: MaterialButton,
        indicator: ProgressBar,
        action: AuthAction
    ) {
        val requestInProgress = state is AuthUiState.Loading
        button.isEnabled = !requestInProgress
        indicator.visibility =
            if (state is AuthUiState.Loading && state.action == action) View.VISIBLE else View.GONE
    }

    protected fun hideKeyboard(view: View) {
        val inputMethodManager =
            context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    protected fun safeNavigate(actionId: Int, destinationId: Int? = null) {
        if (!isAdded || isDetached || view == null) return
        try {
            val navController = findNavController()
            val currentDestId = navController.currentDestination?.id
            if (destinationId != null && currentDestId == destinationId) {
                return
            }
            navController.navigate(actionId)
        } catch (_: Exception) {
            // Guard against in-flight navigation races
        }
    }

    private fun handleEvent(event: AuthEvent) {
        when (event) {
            AuthEvent.Login -> safeNavigate(R.id.action_global_login, R.id.loginFragment)
            AuthEvent.UsernameOnboarding -> safeNavigate(R.id.action_global_username_onboarding, R.id.usernameOnboardingFragment)
            AuthEvent.VerifyEmail -> safeNavigate(R.id.action_global_verify_email, R.id.verifyEmailFragment)
            AuthEvent.Home -> safeNavigate(R.id.action_global_home, R.id.homeFragment)
        }
    }
}
