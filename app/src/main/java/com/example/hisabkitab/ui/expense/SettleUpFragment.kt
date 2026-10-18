package com.example.hisabkitab.ui.expense

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.hisabkitab.R
import com.example.hisabkitab.data.models.GroupMember
import com.example.hisabkitab.databinding.FragmentSettleUpBinding
import com.example.hisabkitab.ui.group.GroupDetailUiState
import com.example.hisabkitab.ui.group.GroupViewModel
import com.example.hisabkitab.ui.group.OperationState
import com.example.hisabkitab.util.CurrencyUtil
import kotlinx.coroutines.launch

class SettleUpFragment : Fragment() {
    private var _binding: FragmentSettleUpBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val groupViewModel: GroupViewModel by activityViewModels()
    private var groupId: String = ""
    private var members: List<GroupMember> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettleUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        groupId = arguments?.getString("groupId").orEmpty()
        groupViewModel.resetOperationState()

        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.amountInput.doAfterTextChanged { binding.amountLayout.error = null }
        binding.recordSettlementButton.setOnClickListener { submitSettlement() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    groupViewModel.detailState.collect { state ->
                        if (state is GroupDetailUiState.Content) {
                            members = state.membersWithBalance.map { it.member }
                            setupSpinners()
                        }
                    }
                }
                launch {
                    groupViewModel.operationState.collect(::renderOperationState)
                }
            }
        }

        if (groupId.isNotBlank()) {
            groupViewModel.loadGroupDetails(groupId)
        }
    }

    private fun setupSpinners() {
        if (members.isEmpty()) return
        val memberNames = members.map { it.displayName }

        val fromAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, memberNames)
        fromAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.fromSpinner.adapter = fromAdapter

        val toAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, memberNames)
        toAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.toSpinner.adapter = toAdapter

        if (members.size > 1) {
            binding.toSpinner.setSelection(1)
        }
    }

    private fun submitSettlement() {
        val fromPos = binding.fromSpinner.selectedItemPosition
        val toPos = binding.toSpinner.selectedItemPosition

        if (fromPos !in members.indices || toPos !in members.indices) return

        val fromMember = members[fromPos]
        val toMember = members[toPos]

        if (fromMember.uid == toMember.uid) {
            binding.statusMessage.text = getString(R.string.same_payer_recipient)
            binding.statusMessage.visibility = View.VISIBLE
            return
        }

        val rawAmount = binding.amountInput.text?.toString().orEmpty()
        val amountPaise = CurrencyUtil.parseInrToPaise(rawAmount)

        if (amountPaise == null || amountPaise <= 0) {
            binding.amountLayout.error = getString(R.string.invalid_amount)
            return
        }

        binding.statusMessage.visibility = View.GONE
        groupViewModel.addSettlement(
            groupId = groupId,
            fromUid = fromMember.uid,
            toUid = toMember.uid,
            amountPaise = amountPaise,
            fromName = fromMember.displayName,
            toName = toMember.displayName
        )
    }

    private fun renderOperationState(state: OperationState) {
        val b = _binding ?: return
        when (state) {
            OperationState.Idle -> {
                b.recordSettlementButton.isEnabled = true
                b.loadingIndicator.visibility = View.GONE
                b.statusMessage.visibility = View.GONE
            }
            OperationState.Loading -> {
                b.recordSettlementButton.isEnabled = false
                b.loadingIndicator.visibility = View.VISIBLE
                b.statusMessage.visibility = View.GONE
            }
            is OperationState.Success -> {
                b.recordSettlementButton.isEnabled = true
                b.loadingIndicator.visibility = View.GONE
                if (isAdded) findNavController().popBackStack()
            }
            is OperationState.Error -> {
                b.recordSettlementButton.isEnabled = true
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
