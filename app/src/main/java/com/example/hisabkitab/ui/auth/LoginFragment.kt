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
import com.example.hisabkitab.databinding.FragmentLoginBinding

class LoginFragment : BaseAuthFragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.emailInput.doAfterTextChanged { binding.emailLayout.error = null }
        binding.passwordInput.doAfterTextChanged { binding.passwordLayout.error = null }
        binding.loginButton.setOnClickListener { submitLogin() }
        binding.passwordInput.setOnEditorActionListener { editor, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(editor)
                submitLogin()
                true
            } else {
                false
            }
        }
        binding.forgotPasswordButton.setOnClickListener {
            authViewModel.resetState()
            findNavController().navigate(R.id.action_login_to_forgot_password)
        }
        binding.createAccountButton.setOnClickListener {
            authViewModel.resetState()
            findNavController().navigate(R.id.action_login_to_signup)
        }
        binding.continueWithPhoneButton.setOnClickListener {
            authViewModel.resetState()
            findNavController().navigate(R.id.action_login_to_phone)
        }
        binding.retryButton.setOnClickListener { submitLogin() }
        observeAuthState { state ->
            bindProgress(
                state,
                binding.loginButton,
                binding.loadingIndicator,
                AuthAction.Login
            )
            bindStatus(state, binding.statusMessage, binding.retryButton) { submitLogin() }
        }
    }

    private fun submitLogin() {
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        binding.emailLayout.error = when {
            email.isBlank() -> getString(R.string.email_required)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> getString(R.string.email_invalid)
            else -> null
        }
        binding.passwordLayout.error =
            if (password.isBlank()) getString(R.string.password_required) else null
        if (binding.emailLayout.error != null || binding.passwordLayout.error != null) return
        hideKeyboard(binding.root)
        authViewModel.login(email, password)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
