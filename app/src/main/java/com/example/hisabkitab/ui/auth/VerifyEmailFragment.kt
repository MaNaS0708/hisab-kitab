package com.example.hisabkitab.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.hisabkitab.R
import com.example.hisabkitab.databinding.FragmentVerifyEmailBinding
import kotlinx.coroutines.launch

class VerifyEmailFragment : BaseAuthFragment() {
    private var _binding: FragmentVerifyEmailBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var retryAction: () -> Unit = {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVerifyEmailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        retryAction = authViewModel::resendVerificationEmail
        binding.verifiedButton.setOnClickListener {
            retryAction = authViewModel::verifyEmail
            authViewModel.verifyEmail()
        }
        binding.resendEmailButton.setOnClickListener {
            retryAction = authViewModel::resendVerificationEmail
            authViewModel.resendVerificationEmail()
        }
        binding.differentAccountButton.setOnClickListener {
            authViewModel.switchAccount()
        }
        binding.retryButton.setOnClickListener { retryAction() }
        observeAuthState { state ->
            bindProgress(
                state,
                binding.verifiedButton,
                binding.loadingIndicator,
                AuthAction.VerifyEmail
            )
            bindProgress(
                state,
                binding.resendEmailButton,
                binding.resendEmailLoadingIndicator,
                AuthAction.ResendEmail
            )
            binding.resendEmailButton.isEnabled =
                state !is AuthUiState.Loading && authViewModel.emailCooldown.value == 0
            binding.differentAccountButton.isEnabled = state !is AuthUiState.Loading
            bindStatus(state, binding.statusMessage, binding.retryButton) { retryAction() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    authViewModel.verificationEmail.collect { email ->
                        binding.emailValue.text = email
                    }
                }
                launch {
                    authViewModel.emailCooldown.collect { seconds ->
                        binding.resendEmailButton.text =
                            if (seconds > 0) {
                                getString(R.string.resend_email_countdown, seconds)
                            } else {
                                getString(R.string.resend_email)
                            }
                        binding.resendEmailButton.isEnabled =
                            seconds == 0 && authViewModel.state.value !is AuthUiState.Loading
                    }
                }
            }
        }
        if (savedInstanceState == null && authViewModel.emailCooldown.value == 0) {
            binding.resendEmailButton.text = getString(R.string.resend_email)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
