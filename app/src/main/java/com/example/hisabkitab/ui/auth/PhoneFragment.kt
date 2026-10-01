package com.example.hisabkitab.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import com.example.hisabkitab.R
import com.example.hisabkitab.databinding.FragmentPhoneBinding
import androidx.appcompat.widget.PopupMenu

class PhoneFragment : BaseAuthFragment() {
    private var _binding: FragmentPhoneBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var retryAction: () -> Unit = {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhoneBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.countryCodeButton.setOnClickListener { showCountryPicker() }
        binding.phoneInput.doAfterTextChanged { binding.phoneLayout.error = null }
        binding.sendOtpButton.setOnClickListener { submitPhone() }
        binding.phoneInput.setOnEditorActionListener { editor, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(editor)
                submitPhone()
                true
            } else {
                false
            }
        }
        retryAction = { submitPhone() }
        binding.retryButton.setOnClickListener { retryAction() }
        observeAuthState { state ->
            bindProgress(
                state,
                binding.sendOtpButton,
                binding.loadingIndicator,
                AuthAction.PhoneRequest
            )
            bindStatus(state, binding.statusMessage, binding.retryButton) { retryAction() }
        }
    }

    private fun showCountryPicker() {
        PopupMenu(requireContext(), binding.countryCodeButton).apply {
            menuInflater.inflate(R.menu.country_code_picker, menu)
            setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.country_india) {
                    binding.countryCodeButton.text = getString(R.string.country_code_india)
                    true
                } else {
                    false
                }
            }
        }.show()
    }

    private fun submitPhone() {
        val digits = binding.phoneInput.text?.toString().orEmpty().filter(Char::isDigit)
        binding.phoneLayout.error =
            if (digits.length != 10) getString(R.string.phone_invalid) else null
        if (binding.phoneLayout.error != null) return
        hideKeyboard(binding.root)
        val phone = getString(R.string.phone_prefix) + digits
        retryAction = {
            authViewModel.beginPhoneVerification(requireActivity(), phone)
        }
        authViewModel.beginPhoneVerification(requireActivity(), phone)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
