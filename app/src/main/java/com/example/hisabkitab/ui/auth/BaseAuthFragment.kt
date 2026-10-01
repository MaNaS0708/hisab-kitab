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
        retryButton: MaterialButton,
        onRetry: () -> Unit
    ) {
        when (state) {
            AuthUiState.Idle, is AuthUiState.Loading -> {
                messageView.visibility = View.GONE
                retryButton.visibility = View.GONE
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
                retryButton.visibility = View.GONE
            }
            is AuthUiState.Error -> {
                messageView.setTextColor(requireContext().getColor(R.color.color_error))
                messageView.text = state.message
                messageView.visibility = View.VISIBLE
                retryButton.visibility = if (state.retryable) View.VISIBLE else View.GONE
                retryButton.setOnClickListener { onRetry() }
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
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun handleEvent(event: AuthEvent) {
        val navController = findNavController()
        when (event) {
            AuthEvent.Login -> navController.navigate(R.id.action_global_login)
            AuthEvent.VerifyEmail -> navController.navigate(R.id.action_global_verify_email)
            AuthEvent.Home -> navController.navigate(R.id.action_global_home)
            AuthEvent.Otp -> {
                if (navController.currentDestination?.id == R.id.phoneFragment) {
                    navController.navigate(R.id.action_phone_to_otp)
                }
            }
        }
    }
}
