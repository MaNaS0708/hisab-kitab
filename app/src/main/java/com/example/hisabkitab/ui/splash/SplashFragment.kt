package com.example.hisabkitab.ui.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.hisabkitab.databinding.FragmentSplashBinding
import com.example.hisabkitab.ui.auth.AuthUiState
import com.example.hisabkitab.ui.auth.BaseAuthFragment
import com.example.hisabkitab.util.AppLog

class SplashFragment : BaseAuthFragment() {
    private var _binding: FragmentSplashBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        AppLog.d("SplashFragment", "onCreateView")
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AppLog.d("SplashFragment", "onViewCreated")

        binding.retryButton.setOnClickListener {
            AppLog.i("SplashFragment", "Retry button clicked -> resolveAuthGate")
            authViewModel.resolveAuthGate()
        }

        observeAuthState { state ->
            val b = _binding ?: return@observeAuthState
            AppLog.d("SplashFragment", "observeAuthState received: ${state.javaClass.simpleName}")
            try {
                when (state) {
                    AuthUiState.Idle, is AuthUiState.Loading -> {
                        b.loadingSpinner.visibility = View.VISIBLE
                        b.loadingMessage.visibility = View.VISIBLE
                        b.errorMessage.visibility = View.GONE
                        b.retryButton.visibility = View.GONE
                    }
                    is AuthUiState.Error -> {
                        b.loadingSpinner.visibility = View.GONE
                        b.loadingMessage.visibility = View.GONE
                        b.errorMessage.text = state.message
                        b.errorMessage.visibility = View.VISIBLE
                        b.retryButton.visibility =
                            if (state.retryable) View.VISIBLE else View.GONE
                    }
                    is AuthUiState.Success -> Unit
                }
            } catch (e: Exception) {
                AppLog.e("SplashFragment", "Error updating UI in observeAuthState", e)
            }
        }

        if (authViewModel.state.value is AuthUiState.Idle) {
            AppLog.i("SplashFragment", "State is Idle -> resolving auth gate")
            authViewModel.resolveAuthGate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        AppLog.d("SplashFragment", "onDestroyView")
        _binding = null
    }
}
