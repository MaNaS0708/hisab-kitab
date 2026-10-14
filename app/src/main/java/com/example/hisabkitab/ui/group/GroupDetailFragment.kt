package com.example.hisabkitab.ui.group

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hisabkitab.R
import com.example.hisabkitab.data.models.GroupActivity
import com.example.hisabkitab.databinding.FragmentGroupDetailBinding
import com.example.hisabkitab.databinding.ItemLedgerBinding
import com.example.hisabkitab.databinding.ItemMemberBalanceBinding
import com.example.hisabkitab.util.CurrencyUtil
import kotlinx.coroutines.launch

class GroupDetailFragment : Fragment() {
    private var _binding: FragmentGroupDetailBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val groupViewModel: GroupViewModel by activityViewModels()
    private var groupId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        groupId = arguments?.getString("groupId").orEmpty()

        binding.backButton.setOnClickListener { findNavController().popBackStack() }

        binding.membersRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.activityRecycler.layoutManager = LinearLayoutManager(requireContext())

        binding.addExpenseButton.setOnClickListener {
            val bundle = Bundle().apply { putString("groupId", groupId) }
            findNavController().navigate(R.id.action_group_detail_to_add_expense, bundle)
        }

        binding.settleUpButton.setOnClickListener {
            val bundle = Bundle().apply { putString("groupId", groupId) }
            findNavController().navigate(R.id.action_group_detail_to_settle_up, bundle)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                groupViewModel.detailState.collect(::renderDetailState)
            }
        }

        if (groupId.isNotBlank()) {
            groupViewModel.loadGroupDetails(groupId)
        }
    }

    private fun renderDetailState(state: GroupDetailUiState) {
        when (state) {
            GroupDetailUiState.Loading -> {
                binding.loadingIndicator.visibility = View.VISIBLE
                binding.statusMessage.visibility = View.GONE
            }
            is GroupDetailUiState.Content -> {
                binding.loadingIndicator.visibility = View.GONE
                binding.statusMessage.visibility = View.GONE

                binding.groupName.text = state.group.name

                val net = state.userNetBalancePaise
                val context = requireContext()
                when {
                    net > 0 -> {
                        binding.userGroupStatus.text = getString(R.string.group_detail_owed, CurrencyUtil.formatPaiseToInr(net))
                        binding.userGroupStatus.setTextColor(context.getColor(R.color.color_primary))
                    }
                    net < 0 -> {
                        binding.userGroupStatus.text = getString(R.string.group_detail_owe, CurrencyUtil.formatPaiseToInr(Math.abs(net)))
                        binding.userGroupStatus.setTextColor(context.getColor(R.color.color_error))
                    }
                    else -> {
                        binding.userGroupStatus.text = getString(R.string.group_detail_settled)
                        binding.userGroupStatus.setTextColor(context.getColor(R.color.color_secondary))
                    }
                }

                binding.membersRecycler.adapter = MembersAdapter(state.membersWithBalance)

                if (state.activities.isEmpty()) {
                    binding.emptyActivityView.visibility = View.VISIBLE
                    binding.activityRecycler.visibility = View.GONE
                } else {
                    binding.emptyActivityView.visibility = View.GONE
                    binding.activityRecycler.visibility = View.VISIBLE
                    binding.activityRecycler.adapter = ActivityAdapter(state.activities)
                }
            }
            is GroupDetailUiState.Error -> {
                binding.loadingIndicator.visibility = View.GONE
                binding.statusMessage.text = state.message
                binding.statusMessage.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class MembersAdapter(
        private val members: List<MemberWithBalance>
    ) : RecyclerView.Adapter<MembersAdapter.MemberViewHolder>() {

        class MemberViewHolder(val binding: ItemMemberBalanceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
            val binding = ItemMemberBalanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return MemberViewHolder(binding)
        }

        override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
            val item = members[position]
            with(holder.binding) {
                memberName.text = item.member.displayName
                memberHandle.text = if (item.member.username.isNotBlank()) "@${item.member.username}" else ""

                val net = item.netBalancePaise
                val context = root.context
                when {
                    net > 0 -> {
                        memberNetBalance.text = context.getString(R.string.member_gets_back, CurrencyUtil.formatPaiseToInr(net))
                        memberNetBalance.setTextColor(context.getColor(R.color.color_primary))
                    }
                    net < 0 -> {
                        memberNetBalance.text = context.getString(R.string.member_owes, CurrencyUtil.formatPaiseToInr(Math.abs(net)))
                        memberNetBalance.setTextColor(context.getColor(R.color.color_error))
                    }
                    else -> {
                        memberNetBalance.text = context.getString(R.string.settled_up)
                        memberNetBalance.setTextColor(context.getColor(R.color.color_secondary))
                    }
                }
            }
        }

        override fun getItemCount(): Int = members.size
    }

    private class ActivityAdapter(
        private val activities: List<GroupActivity>
    ) : RecyclerView.Adapter<ActivityAdapter.ActivityViewHolder>() {

        class ActivityViewHolder(val binding: ItemLedgerBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
            val binding = ItemLedgerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ActivityViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
            val item = activities[position]
            with(holder.binding) {
                activityMessage.text = item.message
                val dateStr = item.createdAt?.let { DateFormat.format("MMM dd, yyyy h:mm a", it).toString() }
                    ?: root.context.getString(R.string.just_now)
                activityTimestamp.text = dateStr
            }
        }


        override fun getItemCount(): Int = activities.size
    }
}
