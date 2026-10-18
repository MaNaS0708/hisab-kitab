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
            if (isAdded && findNavController().currentDestination?.id == R.id.groupDetailFragment) {
                val bundle = Bundle().apply { putString("groupId", groupId) }
                findNavController().navigate(R.id.action_group_detail_to_add_expense, bundle)
            }
        }

        binding.settleUpButton.setOnClickListener {
            if (isAdded && findNavController().currentDestination?.id == R.id.groupDetailFragment) {
                val bundle = Bundle().apply { putString("groupId", groupId) }
                findNavController().navigate(R.id.action_group_detail_to_settle_up, bundle)
            }
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
        val b = _binding ?: return
        val ctx = context ?: return
        when (state) {
            GroupDetailUiState.Loading -> {
                b.loadingIndicator.visibility = View.VISIBLE
                b.statusMessage.visibility = View.GONE
            }
            is GroupDetailUiState.Content -> {
                b.loadingIndicator.visibility = View.GONE
                b.statusMessage.visibility = View.GONE

                b.groupName.text = state.group.name

                val net = state.userNetBalancePaise
                when {
                    net > 0 -> {
                        b.userGroupStatus.text = getString(R.string.group_detail_owed, CurrencyUtil.formatPaiseToInr(net))
                        b.userGroupStatus.setTextColor(ctx.getColor(R.color.color_primary))
                    }
                    net < 0 -> {
                        val absPaise = if (net == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(net)
                        b.userGroupStatus.text = getString(R.string.group_detail_owe, CurrencyUtil.formatPaiseToInr(absPaise))
                        b.userGroupStatus.setTextColor(ctx.getColor(R.color.color_error))
                    }
                    else -> {
                        b.userGroupStatus.text = getString(R.string.group_detail_settled)
                        b.userGroupStatus.setTextColor(ctx.getColor(R.color.color_secondary))
                    }
                }

                b.membersRecycler.adapter = MembersAdapter(state.membersWithBalance)

                if (state.activities.isEmpty()) {
                    b.emptyActivityView.visibility = View.VISIBLE
                    b.activityRecycler.visibility = View.GONE
                } else {
                    b.emptyActivityView.visibility = View.GONE
                    b.activityRecycler.visibility = View.VISIBLE
                    b.activityRecycler.adapter = ActivityAdapter(state.activities)
                }
            }
            is GroupDetailUiState.Error -> {
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
