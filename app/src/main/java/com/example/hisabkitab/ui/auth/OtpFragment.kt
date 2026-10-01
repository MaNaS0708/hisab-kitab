package com.example.hisabkitab.ui.auth

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.hisabkitab.R
import com.example.hisabkitab.databinding.FragmentOtpBinding
import kotlinx.coroutines.launch

class OtpFragment : BaseAuthFragment() {
    private var _binding: FragmentOtpBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var distributingPaste = false
    private var retryAction: () -> Unit = {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOtpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val fields = listOf(
            binding.digit1,
            binding.digit2,
            binding.digit3,
            binding.digit4,
            binding.digit5,
            binding.digit6
        )
        installOtpInputBehavior(fields)
        retryAction = { submitOtp(fields) }
        binding.backButton.setOnClickListener {
            authViewModel.resetState()
            findNavController().navigate(R.id.action_otp_to_phone)
        }
        binding.verifyOtpButton.setOnClickListener { submitOtp(fields) }
        binding.retryButton.setOnClickListener { retryAction() }
        binding.resendOtpButton.setOnClickListener {
            retryAction = { authViewModel.resendOtp(requireActivity()) }
            authViewModel.resendOtp(requireActivity())
        }
        fields.last().setOnEditorActionListener { editor, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(editor)
                submitOtp(fields)
                true
            } else {
                false
            }
        }
        observeAuthState { state ->
            bindProgress(
                state,
                binding.verifyOtpButton,
                binding.loadingIndicator,
                AuthAction.VerifyOtp
            )
            bindProgress(
                state,
                binding.resendOtpButton,
                binding.resendOtpLoadingIndicator,
                AuthAction.ResendOtp
            )
            binding.resendOtpButton.isEnabled =
                state !is AuthUiState.Loading && authViewModel.otpCooldown.value == 0
            bindStatus(state, binding.statusMessage, binding.retryButton) { retryAction() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    authViewModel.phoneNumber.collect { phone ->
                        binding.phoneValue.text =
                            getString(R.string.otp_sent_to, maskPhone(phone))
                    }
                }
                launch {
                    authViewModel.otpCooldown.collect { seconds ->
                        binding.resendOtpButton.text =
                            if (seconds > 0) {
                                getString(R.string.resend_otp_countdown, seconds)
                            } else {
                                getString(R.string.resend_otp)
                            }
                        binding.resendOtpButton.isEnabled =
                            seconds == 0 && authViewModel.state.value !is AuthUiState.Loading
                    }
                }
            }
        }
    }

    private fun maskPhone(phone: String): String {
        val digits = phone.filter(Char::isDigit)
        val countryPrefix = if (phone.startsWith("+91")) "+91 " else ""
        return countryPrefix + "••••••" + digits.takeLast(4)
    }

    private fun installOtpInputBehavior(fields: List<EditText>) {
        fields.forEachIndexed { index, field ->
            field.doAfterTextChanged { editable ->
                if (distributingPaste) return@doAfterTextChanged
                val digits = editable?.toString().orEmpty().filter(Char::isDigit)
                if (digits.length > 1) {
                    val available = fields.size - index
                    val pasted = digits.take(available)
                    distributingPaste = true
                    pasted.forEachIndexed { offset, digit ->
                        fields[index + offset].setText(digit.toString())
                    }
                    distributingPaste = false
                    val nextIndex = (index + pasted.length).coerceAtMost(fields.lastIndex)
                    fields[nextIndex].requestFocus()
                    if (index + pasted.length >= fields.size) {
                        hideKeyboard(field)
                    }
                } else if (digits.length == 1 && index < fields.lastIndex) {
                    fields[index + 1].requestFocus()
                }
            }
            field.setOnKeyListener { _, keyCode, event ->
                if (
                    keyCode == KeyEvent.KEYCODE_DEL &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    field.text.isNullOrEmpty() &&
                    index > 0
                ) {
                    fields[index - 1].apply {
                        text?.clear()
                        requestFocus()
                    }
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun submitOtp(fields: List<EditText>) {
        val code = fields.joinToString(separator = "") { it.text?.toString().orEmpty() }
        if (code.length != fields.size || code.any { !it.isDigit() }) {
            binding.statusMessage.text = getString(R.string.otp_invalid)
            binding.statusMessage.setTextColor(requireContext().getColor(R.color.color_error))
            binding.statusMessage.visibility = View.VISIBLE
            return
        }
        hideKeyboard(binding.root)
        retryAction = { authViewModel.verifyOtp(code) }
        authViewModel.verifyOtp(code)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
