package com.example.hisabkitab.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hisabkitab.R
import com.example.hisabkitab.databinding.FragmentHomeBinding
import com.example.hisabkitab.databinding.ItemGroupBinding
import com.example.hisabkitab.ui.auth.AuthUiState
import com.example.hisabkitab.ui.auth.BaseAuthFragment
import com.example.hisabkitab.ui.group.GroupViewModel
import com.example.hisabkitab.ui.group.GroupWithBalance
import com.example.hisabkitab.ui.group.HomeUiState
import com.example.hisabkitab.util.CurrencyUtil
import kotlinx.coroutines.launch

class HomeFragment : BaseAuthFragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val groupViewModel: GroupViewModel by activityViewModels()

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

        binding.groupsRecycler.layoutManager = LinearLayoutManager(requireContext())

        binding.logoutButton.setOnClickListener { authViewModel.logout() }
        binding.retryButton.setOnClickListener { groupViewModel.loadHomeData() }

        val navigateToCreateGroup = {
            findNavController().navigate(R.id.action_home_to_create_group)
        }
        binding.createGroupFab.setOnClickListener { navigateToCreateGroup() }
        binding.createGroupButton.setOnClickListener { navigateToCreateGroup() }
        binding.createFirstGroupButton.setOnClickListener { navigateToCreateGroup() }

        observeAuthState { state ->
            binding.logoutButton.isEnabled = state !is AuthUiState.Loading
            binding.logoutLoading.visibility =
                if (state is AuthUiState.Loading) View.VISIBLE else View.GONE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                groupViewModel.homeState.collect(::renderHomeState)
            }
        }

        groupViewModel.loadHomeData()
    }

    private fun renderHomeState(state: HomeUiState) {
        when (state) {
            HomeUiState.Loading -> {
                binding.profileContent.visibility = View.GONE
                binding.groupsLoadingIndicator.visibility = View.VISIBLE
                binding.errorLayout.visibility = View.GONE
                binding.emptyGroupsView.visibility = View.GONE
                binding.groupsRecycler.visibility = View.GONE
                binding.totalOwedAmount.text = CurrencyUtil.formatPaiseToInr(0L)
                binding.totalOwesAmount.text = CurrencyUtil.formatPaiseToInr(0L)
            }
            is HomeUiState.Content -> {
                binding.profileContent.visibility = View.VISIBLE
                binding.greeting.text =
                    getString(R.string.home_greeting, state.profile.greetingName)
                binding.userEmailSubtitle.text =
                    state.profile.displayHandle.ifBlank { getString(R.string.signed_in_account) }

                binding.totalOwedAmount.text = CurrencyUtil.formatPaiseToInr(state.totalOwedPaise)
                binding.totalOwesAmount.text = CurrencyUtil.formatPaiseToInr(state.totalOwesPaise)

                binding.groupsLoadingIndicator.visibility = View.GONE
                binding.errorLayout.visibility = View.GONE

                if (state.groups.isEmpty()) {
                    binding.emptyGroupsView.visibility = View.VISIBLE
                    binding.groupsRecycler.visibility = View.GONE
                } else {
                    binding.emptyGroupsView.visibility = View.GONE
                    binding.groupsRecycler.visibility = View.VISIBLE
                    binding.groupsRecycler.adapter = GroupsAdapter(state.groups) { item ->
                        val bundle = Bundle().apply {
                            putString("groupId", item.group.id)
                        }
                        findNavController().navigate(R.id.action_home_to_group_detail, bundle)
                    }
                }
            }
            is HomeUiState.Error -> {
                binding.profileContent.visibility = View.GONE
                binding.groupsLoadingIndicator.visibility = View.GONE
                binding.emptyGroupsView.visibility = View.GONE
                binding.groupsRecycler.visibility = View.GONE

                binding.statusMessage.text = state.message
                binding.retryButton.visibility = if (state.retryable) View.VISIBLE else View.GONE
                binding.errorLayout.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class GroupsAdapter(
        private val groups: List<GroupWithBalance>,
        private val onGroupClick: (GroupWithBalance) -> Unit
    ) : RecyclerView.Adapter<GroupsAdapter.GroupViewHolder>() {

        class GroupViewHolder(val binding: ItemGroupBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
            val binding = ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return GroupViewHolder(binding)
        }

        override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
            val item = groups[position]
            val context = holder.itemView.context
            with(holder.binding) {
                groupName.text = item.group.name
                val memberCount = item.group.memberIds.size
                groupMembersCount.text = context.getString(R.string.group_members_count, memberCount)

                when {
                    item.netBalancePaise > 0 -> {
                        groupNetBalance.text = context.getString(
                            R.string.group_balance_owed,
                            CurrencyUtil.formatPaiseToInr(item.netBalancePaise)
                        )
                        groupNetBalance.setTextColor(context.getColor(R.color.color_primary))
                    }
                    item.netBalancePaise < 0 -> {
                        groupNetBalance.text = context.getString(
                            R.string.group_balance_owe,
                            CurrencyUtil.formatPaiseToInr(Math.abs(item.netBalancePaise))
                        )
                        groupNetBalance.setTextColor(context.getColor(R.color.color_error))
                    }
                    else -> {
                        groupNetBalance.text = context.getString(R.string.settled_up)
                        groupNetBalance.setTextColor(context.getColor(R.color.color_secondary))
                    }
                }

                root.setOnClickListener { onGroupClick(item) }
            }
        }

        override fun getItemCount(): Int = groups.size
    }
}
