package com.example.hisabkitab.ui.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.hisabkitab.databinding.FragmentSplashBinding
import com.example.hisabkitab.ui.auth.AuthUiState
import com.example.hisabkitab.ui.auth.BaseAuthFragment

class SplashFragment : BaseAuthFragment() {
    private var _binding: FragmentSplashBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.retryButton.setOnClickListener { authViewModel.resolveAuthGate() }
        observeAuthState { state ->
            when (state) {
                AuthUiState.Idle, is AuthUiState.Loading -> {
                    binding.errorMessage.visibility = View.GONE
                    binding.retryButton.visibility = View.GONE
                }
                is AuthUiState.Error -> {
                    binding.errorMessage.text = state.message
                    binding.errorMessage.visibility = View.VISIBLE
                    binding.retryButton.visibility =
                        if (state.retryable) View.VISIBLE else View.GONE
                }
                is AuthUiState.Success -> Unit
            }
        }
        if (authViewModel.state.value is AuthUiState.Idle) {
            authViewModel.resolveAuthGate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
