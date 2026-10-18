package com.example.hisabkitab.ui.expense

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.hisabkitab.R
import com.example.hisabkitab.data.models.GroupMember
import com.example.hisabkitab.databinding.FragmentAddExpenseBinding
import com.example.hisabkitab.databinding.ItemCustomShareBinding
import com.example.hisabkitab.ui.group.GroupDetailUiState
import com.example.hisabkitab.ui.group.GroupViewModel
import com.example.hisabkitab.ui.group.OperationState
import com.example.hisabkitab.util.CurrencyUtil
import com.example.hisabkitab.util.CustomSplitValidationResult
import com.example.hisabkitab.util.SplitCalculator
import kotlinx.coroutines.launch

class AddExpenseFragment : Fragment() {
    private var _binding: FragmentAddExpenseBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val groupViewModel: GroupViewModel by activityViewModels()
    private var groupId: String = ""

    private var members: List<GroupMember> = emptyList()
    private val selectedParticipantUids = mutableSetOf<String>()
    private val customShareRawInputs = mutableMapOf<String, String>()
    private val customShareBindings = mutableMapOf<String, ItemCustomShareBinding>()

    private var currentSplitMode = "equal"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        groupId = arguments?.getString("groupId").orEmpty()
        groupViewModel.resetOperationState()

        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.descriptionInput.doAfterTextChanged {
            binding.descriptionLayout.error = null
            validateForm()
        }
        binding.amountInput.doAfterTextChanged {
            binding.amountLayout.error = null
            updateSplitViews()
            validateForm()
        }

        binding.splitModeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentSplitMode = if (checkedId == R.id.split_custom_button) "custom" else "equal"
                updateSplitViews()
                validateForm()
            }
        }
        binding.splitModeToggleGroup.check(R.id.split_equal_button)

        binding.saveExpenseButton.setOnClickListener { submitExpense() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    groupViewModel.detailState.collect { state ->
                        if (state is GroupDetailUiState.Content) {
                            members = state.membersWithBalance.map { it.member }
                            setupMembersUI()
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

    private fun setupMembersUI() {
        if (members.isEmpty()) return

        val memberNames = members.map { it.displayName }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, memberNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.paidBySpinner.adapter = adapter

        if (selectedParticipantUids.isEmpty()) {
            members.forEach { selectedParticipantUids.add(it.uid) }
        }

        binding.participantsCheckboxesContainer.removeAllViews()
        members.forEach { member ->
            val checkBox = CheckBox(requireContext()).apply {
                text = member.displayName
                isChecked = selectedParticipantUids.contains(member.uid)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedParticipantUids.add(member.uid)
                    } else {
                        selectedParticipantUids.remove(member.uid)
                    }
                    updateSplitViews()
                    validateForm()
                }
            }
            binding.participantsCheckboxesContainer.addView(checkBox)
        }

        updateSplitViews()
        validateForm()
    }

    private fun updateSplitViews() {
        val totalPaise = CurrencyUtil.parseInrToPaise(binding.amountInput.text?.toString().orEmpty()) ?: 0L
        val selectedList = members.filter { selectedParticipantUids.contains(it.uid) }

        if (currentSplitMode == "equal") {
            binding.equalSplitPreview.visibility = View.VISIBLE
            binding.customSplitContainer.visibility = View.GONE

            if (totalPaise > 0 && selectedList.isNotEmpty()) {
                val perPersonPaise = totalPaise / selectedList.size
                val perPersonStr = CurrencyUtil.formatPaiseToInr(perPersonPaise)
                binding.equalSplitPreview.text = getString(
                    R.string.equal_split_preview_format,
                    perPersonStr,
                    selectedList.size
                )
            } else {
                binding.equalSplitPreview.text = getString(R.string.equal_split_hint)
            }
        } else {
            binding.equalSplitPreview.visibility = View.GONE
            binding.customSplitContainer.visibility = View.VISIBLE

            rebuildCustomShareList(selectedList, totalPaise)
        }
    }

    private fun rebuildCustomShareList(selectedMembers: List<GroupMember>, totalPaise: Long) {
        val selectedUids = selectedMembers.map { it.uid }.toSet()

        // 1. Remove views for members that are no longer selected (retaining their values in customShareRawInputs)
        val uidsToRemove = customShareBindings.keys.filterNot { selectedUids.contains(it) }
        for (uid in uidsToRemove) {
            val bindingToRemove = customShareBindings.remove(uid)
            if (bindingToRemove != null) {
                binding.customSharesListContainer.removeView(bindingToRemove.root)
            }
        }

        // 2. Add views for newly selected members and retain existing ones
        selectedMembers.forEach { member ->
            val existingBinding = customShareBindings[member.uid]
            if (existingBinding == null) {
                val shareBinding = ItemCustomShareBinding.inflate(
                    LayoutInflater.from(requireContext()),
                    binding.customSharesListContainer,
                    false
                )
                shareBinding.participantName.text = member.displayName

                val savedValue = customShareRawInputs[member.uid].orEmpty()
                shareBinding.customShareInput.setText(savedValue)

                shareBinding.customShareInput.doAfterTextChanged { editable ->
                    val typed = editable?.toString().orEmpty()
                    customShareRawInputs[member.uid] = typed
                    val currentTotal = CurrencyUtil.parseInrToPaise(binding.amountInput.text?.toString().orEmpty()) ?: 0L
                    updateUnassignedAmount(currentTotal)
                    validateForm()
                }

                binding.customSharesListContainer.addView(shareBinding.root)
                customShareBindings[member.uid] = shareBinding
            } else {
                existingBinding.participantName.text = member.displayName
            }
        }

        updateUnassignedAmount(totalPaise)
    }

    private fun updateUnassignedAmount(totalPaise: Long) {
        var currentSum = 0L
        for (uid in selectedParticipantUids) {
            val raw = customShareRawInputs[uid].orEmpty()
            val parsed = CurrencyUtil.parseInrToPaise(raw) ?: 0L
            currentSum += parsed
        }

        val unassigned = totalPaise - currentSum
        val unassignedStr = CurrencyUtil.formatPaiseToInr(unassigned)
        binding.unassignedAmountText.text = getString(R.string.unassigned_amount, unassignedStr)

        if (unassigned == 0L && totalPaise > 0) {
            binding.unassignedAmountText.setTextColor(requireContext().getColor(R.color.color_primary))
        } else {
            binding.unassignedAmountText.setTextColor(requireContext().getColor(R.color.color_error))
        }
    }

    private fun validateForm(): Boolean {
        val totalPaise = CurrencyUtil.parseInrToPaise(binding.amountInput.text?.toString().orEmpty())
        val description = binding.descriptionInput.text?.toString()?.trim().orEmpty()

        if (totalPaise == null || totalPaise <= 0L) {
            binding.saveExpenseButton.isEnabled = false
            return false
        }

        if (selectedParticipantUids.isEmpty()) {
            binding.saveExpenseButton.isEnabled = false
            return false
        }

        if (description.isBlank()) {
            binding.saveExpenseButton.isEnabled = false
            return false
        }

        if (currentSplitMode == "custom") {
            var currentSum = 0L
            for (uid in selectedParticipantUids) {
                val raw = customShareRawInputs[uid]?.trim().orEmpty()
                if (raw.isEmpty()) {
                    binding.saveExpenseButton.isEnabled = false
                    return false
                }
                val parsed = CurrencyUtil.parseInrToPaise(raw)
                if (parsed == null || parsed < 0L) {
                    binding.saveExpenseButton.isEnabled = false
                    return false
                }
                currentSum += parsed
            }

            if (currentSum != totalPaise) {
                binding.saveExpenseButton.isEnabled = false
                return false
            }
        }

        binding.saveExpenseButton.isEnabled = true
        return true
    }

    private fun submitExpense() {
        if (!validateForm()) return

        val description = binding.descriptionInput.text?.toString()?.trim().orEmpty()
        val totalPaise = CurrencyUtil.parseInrToPaise(binding.amountInput.text?.toString().orEmpty()) ?: return

        val paidByPosition = binding.paidBySpinner.selectedItemPosition
        if (paidByPosition !in members.indices) return
        val paidByUid = members[paidByPosition].uid

        val participantList = selectedParticipantUids.filter { it.isNotBlank() }.distinct()
        if (participantList.isEmpty()) {
            binding.statusMessage.text = getString(R.string.select_at_least_one_participant)
            binding.statusMessage.visibility = View.VISIBLE
            return
        }

        val customSharesMap = mutableMapOf<String, Long>()
        if (currentSplitMode == "custom") {
            for (uid in participantList) {
                val raw = customShareRawInputs[uid].orEmpty()
                val parsed = CurrencyUtil.parseInrToPaise(raw) ?: 0L
                customSharesMap[uid] = parsed
            }

            val validation = SplitCalculator.validateCustomShares(
                totalAmountPaise = totalPaise,
                sharesByUid = customSharesMap,
                participantUids = participantList
            )
            if (validation is CustomSplitValidationResult.Invalid) {
                binding.statusMessage.text = validation.message
                binding.statusMessage.visibility = View.VISIBLE
                return
            }
        }

        binding.statusMessage.visibility = View.GONE
        groupViewModel.addExpense(
            groupId = groupId,
            description = description,
            amountPaise = totalPaise,
            paidByUid = paidByUid,
            participantIds = participantList,
            splitType = currentSplitMode,
            customSharesByUid = customSharesMap
        )
    }

    private fun renderOperationState(state: OperationState) {
        when (state) {
            OperationState.Idle -> {
                binding.loadingIndicator.visibility = View.GONE
                binding.statusMessage.visibility = View.GONE
                validateForm()
            }
            OperationState.Loading -> {
                binding.saveExpenseButton.isEnabled = false
                binding.loadingIndicator.visibility = View.VISIBLE
                binding.statusMessage.visibility = View.GONE
            }
            is OperationState.Success -> {
                binding.saveExpenseButton.isEnabled = true
                binding.loadingIndicator.visibility = View.GONE
                findNavController().popBackStack()
            }
            is OperationState.Error -> {
                binding.loadingIndicator.visibility = View.GONE
                binding.statusMessage.text = state.message
                binding.statusMessage.visibility = View.VISIBLE
                validateForm()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

