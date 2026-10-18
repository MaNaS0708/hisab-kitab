package com.example.hisabkitab.ui.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.hisabkitab.R
import com.example.hisabkitab.databinding.FragmentCreateGroupBinding
import com.example.hisabkitab.util.UsernameUtil
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class CreateGroupFragment : Fragment() {
    private var _binding: FragmentCreateGroupBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val groupViewModel: GroupViewModel by activityViewModels()
    private val addedUsernames = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        groupViewModel.resetOperationState()

        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.groupNameInput.doAfterTextChanged { binding.groupNameLayout.error = null }
        binding.searchUsernameInput.doAfterTextChanged { binding.memberSearchStatus.visibility = View.GONE }

        binding.addMemberButton.setOnClickListener { searchAndAddMember() }
        binding.createGroupButton.setOnClickListener { submitCreateGroup() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                groupViewModel.operationState.collect(::renderOperationState)
            }
        }
    }

    private fun searchAndAddMember() {
        val rawUsername = binding.searchUsernameInput.text?.toString().orEmpty()
        val normalized = UsernameUtil.normalize(rawUsername)

        if (!UsernameUtil.isValid(normalized)) {
            binding.memberSearchStatus.text = UsernameUtil.validationErrorMessage(normalized) ?: "Invalid username."
            binding.memberSearchStatus.visibility = View.VISIBLE
            return
        }

        val currentCreatorUsername = (groupViewModel.homeState.value as? HomeUiState.Content)?.profile?.username
        if (currentCreatorUsername != null && normalized == currentCreatorUsername) {
            binding.memberSearchStatus.text = getString(R.string.cannot_add_self)
            binding.memberSearchStatus.visibility = View.VISIBLE
            return
        }

        if (addedUsernames.contains(normalized)) {
            binding.memberSearchStatus.text = getString(R.string.already_member)
            binding.memberSearchStatus.visibility = View.VISIBLE
            return
        }

        binding.addMemberButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val profile = groupViewModel.findUserByUsername(normalized)
                binding.addMemberButton.isEnabled = true
                val currentCreatorUid = (groupViewModel.homeState.value as? HomeUiState.Content)?.profile?.uid
                if (profile == null) {
                    binding.memberSearchStatus.text = getString(R.string.user_not_found)
                    binding.memberSearchStatus.visibility = View.VISIBLE
                } else if (profile.uid == currentCreatorUid) {
                    binding.memberSearchStatus.text = getString(R.string.cannot_add_self)
                    binding.memberSearchStatus.visibility = View.VISIBLE
                } else {
                    binding.memberSearchStatus.visibility = View.GONE
                    addedUsernames.add(normalized)
                    addChipForMember(profile.username ?: normalized, profile.name)
                    binding.searchUsernameInput.setText("")
                }
            } catch (e: Exception) {
                binding.addMemberButton.isEnabled = true
                binding.memberSearchStatus.text = e.message ?: getString(R.string.user_not_found)
                binding.memberSearchStatus.visibility = View.VISIBLE
            }
        }
    }


    private fun addChipForMember(username: String, name: String?) {
        val chip = Chip(requireContext()).apply {
            text = name?.let { "$it (@$username)" } ?: "@$username"
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                addedUsernames.remove(username)
                binding.membersChipGroup.removeView(this)
            }
        }
        binding.membersChipGroup.addView(chip)
    }

    private fun submitCreateGroup() {
        val groupName = binding.groupNameInput.text?.toString()?.trim().orEmpty()
        if (groupName.isBlank()) {
            binding.groupNameLayout.error = getString(R.string.group_name_required)
            return
        }
        if (addedUsernames.isEmpty()) {
            binding.statusMessage.text = getString(R.string.at_least_two_members)
            binding.statusMessage.visibility = View.VISIBLE
            return
        }

        groupViewModel.createGroup(groupName, addedUsernames.distinct())
    }

    private fun renderOperationState(state: OperationState) {
        val b = _binding ?: return
        when (state) {
            OperationState.Idle -> {
                b.createGroupButton.isEnabled = true
                b.loadingIndicator.visibility = View.GONE
                b.statusMessage.visibility = View.GONE
            }
            OperationState.Loading -> {
                b.createGroupButton.isEnabled = false
                b.loadingIndicator.visibility = View.VISIBLE
                b.statusMessage.visibility = View.GONE
            }
            is OperationState.Success -> {
                b.createGroupButton.isEnabled = true
                b.loadingIndicator.visibility = View.GONE
                if (isAdded) findNavController().popBackStack()
            }
            is OperationState.Error -> {
                b.createGroupButton.isEnabled = true
                b.loadingIndicator.visibility = View.GONE
                b.statusMessage.text = state.message
                b.statusMessage.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
