package com.example.hisabkitab.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import com.example.hisabkitab.R
import com.example.hisabkitab.databinding.FragmentForgotPasswordBinding

class ForgotPasswordFragment : BaseAuthFragment() {
    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var retryAction: () -> Unit = {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.backToLoginButton.setOnClickListener { findNavController().popBackStack() }
        binding.emailInput.doAfterTextChanged { binding.emailLayout.error = null }
        binding.sendResetButton.setOnClickListener { submitReset() }
        binding.emailInput.setOnEditorActionListener { editor, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(editor)
                submitReset()
                true
            } else {
                false
            }
        }
        retryAction = { submitReset() }
        binding.retryButton.setOnClickListener { retryAction() }
        observeAuthState { state ->
            if (state is AuthUiState.Success) {
                binding.requestGroup.visibility = View.GONE
                binding.successGroup.visibility = View.VISIBLE
                binding.successMessage.text =
                    getString(R.string.reset_email_sent, state.message.orEmpty())
            } else {
                binding.requestGroup.visibility = View.VISIBLE
                binding.successGroup.visibility = View.GONE
                bindProgress(
                    state,
                    binding.sendResetButton,
                    binding.loadingIndicator,
                    AuthAction.ResetPassword
                )
                bindStatus(state, binding.statusMessage, binding.retryButton) { retryAction() }
            }
        }
    }

    private fun submitReset() {
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        binding.emailLayout.error = when {
            email.isBlank() -> getString(R.string.email_required)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> getString(R.string.email_invalid)
            else -> null
        }
        if (binding.emailLayout.error != null) return
        hideKeyboard(binding.root)
        retryAction = { authViewModel.sendPasswordReset(email) }
        authViewModel.sendPasswordReset(email)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
