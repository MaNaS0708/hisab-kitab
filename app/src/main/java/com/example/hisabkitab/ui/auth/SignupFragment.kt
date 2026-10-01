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
import com.example.hisabkitab.databinding.FragmentSignupBinding

class SignupFragment : BaseAuthFragment() {
    private var _binding: FragmentSignupBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backButton.setOnClickListener {
            authViewModel.resetState()
            findNavController().popBackStack()
        }
        binding.nameInput.doAfterTextChanged { binding.nameLayout.error = null }
        binding.emailInput.doAfterTextChanged { binding.emailLayout.error = null }
        binding.passwordInput.doAfterTextChanged { binding.passwordLayout.error = null }
        binding.confirmPasswordInput.doAfterTextChanged { binding.confirmPasswordLayout.error = null }
        binding.signupButton.setOnClickListener { submitSignup() }
        binding.confirmPasswordInput.setOnEditorActionListener { editor, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(editor)
                submitSignup()
                true
            } else {
                false
            }
        }
        binding.retryButton.setOnClickListener { submitSignup() }
        observeAuthState { state ->
            bindProgress(
                state,
                binding.signupButton,
                binding.loadingIndicator,
                AuthAction.Signup
            )
            bindStatus(state, binding.statusMessage, binding.retryButton) { submitSignup() }
        }
    }

    private fun submitSignup() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        val confirmation = binding.confirmPasswordInput.text?.toString().orEmpty()

        binding.nameLayout.error =
            if (name.isBlank()) getString(R.string.name_required) else null
        binding.emailLayout.error = when {
            email.isBlank() -> getString(R.string.email_required)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> getString(R.string.email_invalid)
            else -> null
        }
        binding.passwordLayout.error = when {
            password.length < 8 -> getString(R.string.password_too_short)
            password.none(Char::isLetter) || password.none(Char::isDigit) ->
                getString(R.string.password_needs_letter_number)
            else -> null
        }
        binding.confirmPasswordLayout.error = when {
            confirmation.isBlank() -> getString(R.string.password_required)
            confirmation != password -> getString(R.string.passwords_do_not_match)
            else -> null
        }
        if (
            binding.nameLayout.error != null ||
            binding.emailLayout.error != null ||
            binding.passwordLayout.error != null ||
            binding.confirmPasswordLayout.error != null
        ) {
            return
        }
        hideKeyboard(binding.root)
        authViewModel.createAccount(name, email, password)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
