package com.example.hisabkitab.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import com.example.hisabkitab.databinding.FragmentUsernameOnboardingBinding
import com.example.hisabkitab.util.UsernameUtil

class UsernameOnboardingFragment : BaseAuthFragment() {
    private var _binding: FragmentUsernameOnboardingBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUsernameOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.usernameInput.doAfterTextChanged { binding.usernameLayout.error = null }
        binding.continueButton.setOnClickListener { submitUsername() }
        binding.usernameInput.setOnEditorActionListener { editor, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(editor)
                submitUsername()
                true
            } else {
                false
            }
        }
        observeAuthState { state ->
            bindProgress(
                state,
                binding.continueButton,
                binding.loadingIndicator,
                AuthAction.SetUsername
            )
            bindStatus(state, binding.statusMessage)
        }
    }

    private fun submitUsername() {
        val rawUsername = binding.usernameInput.text?.toString().orEmpty()
        val name = binding.nameInput.text?.toString()?.trim()

        val validationError = UsernameUtil.validationErrorMessage(rawUsername)
        if (validationError != null) {
            binding.usernameLayout.error = validationError
            return
        }

        hideKeyboard(binding.root)
        authViewModel.saveUsername(rawUsername, name)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
