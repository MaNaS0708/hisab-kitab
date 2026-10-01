package com.example.hisabkitab.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.hisabkitab.R
import com.example.hisabkitab.databinding.FragmentHomeBinding
import com.example.hisabkitab.ui.auth.AuthUiState
import com.example.hisabkitab.ui.auth.BaseAuthFragment
import com.example.hisabkitab.ui.auth.ProfileUiState
import kotlinx.coroutines.launch

class HomeFragment : BaseAuthFragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.logoutButton.setOnClickListener { authViewModel.logout() }
        binding.retryButton.setOnClickListener { authViewModel.loadProfile() }
        observeAuthState { state ->
            binding.logoutButton.isEnabled = state !is AuthUiState.Loading
            binding.logoutLoading.visibility =
                if (state is AuthUiState.Loading) View.VISIBLE else View.GONE
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.profileState.collect(::renderProfile)
            }
        }
        if (authViewModel.profileState.value == null) {
            authViewModel.loadProfile()
        }
    }

    private fun renderProfile(state: ProfileUiState?) {
        when (state) {
            null, ProfileUiState.Loading -> {
                binding.profileShimmer.visibility = View.VISIBLE
                binding.profileShimmer.startShimmer()
                binding.profileContent.visibility = View.GONE
                binding.statusMessage.visibility = View.GONE
                binding.retryButton.visibility = View.GONE
            }
            is ProfileUiState.Loaded -> {
                binding.profileShimmer.stopShimmer()
                binding.profileShimmer.visibility = View.GONE
                binding.profileContent.visibility = View.VISIBLE
                binding.greeting.text =
                    getString(R.string.home_greeting, state.profile.greetingName)
                binding.statusMessage.visibility = View.GONE
                binding.retryButton.visibility = View.GONE
            }
            is ProfileUiState.Error -> {
                binding.profileShimmer.stopShimmer()
                binding.profileShimmer.visibility = View.GONE
                binding.profileContent.visibility = View.GONE
                binding.statusMessage.setTextColor(
                    requireContext().getColor(R.color.color_error)
                )
                binding.statusMessage.text = state.message
                binding.statusMessage.visibility = View.VISIBLE
                binding.retryButton.visibility =
                    if (state.retryable) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        binding.profileShimmer.stopShimmer()
        super.onDestroyView()
        _binding = null
    }
}
