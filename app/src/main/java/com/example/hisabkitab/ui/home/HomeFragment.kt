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
import com.example.hisabkitab.util.AppLog
import com.example.hisabkitab.util.CurrencyUtil
import kotlinx.coroutines.launch

class HomeFragment : BaseAuthFragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val groupViewModel: GroupViewModel by activityViewModels()
    private var groupsAdapter: GroupsAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        AppLog.d("HomeFragment", "onCreateView")
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AppLog.d("HomeFragment", "onViewCreated")

        try {
            groupsAdapter = GroupsAdapter { item ->
                AppLog.i("HomeFragment", "Group clicked id=${item.group.id}, name=${item.group.name}")
                if (isAdded && findNavController().currentDestination?.id == R.id.homeFragment) {
                    val bundle = Bundle().apply {
                        putString("groupId", item.group.id)
                    }
                    findNavController().navigate(R.id.action_home_to_group_detail, bundle)
                }
            }
            binding.groupsRecycler.layoutManager = LinearLayoutManager(requireContext())
            binding.groupsRecycler.adapter = groupsAdapter

            binding.logoutButton.setOnClickListener {
                AppLog.i("HomeFragment", "Logout clicked")
                if (isAdded) authViewModel.logout()
            }
            binding.retryButton.setOnClickListener {
                AppLog.i("HomeFragment", "Retry clicked -> groupViewModel.loadHomeData()")
                groupViewModel.loadHomeData()
            }

            val navigateToCreateGroup = {
                AppLog.i("HomeFragment", "Create Group requested")
                if (isAdded && findNavController().currentDestination?.id == R.id.homeFragment) {
                    findNavController().navigate(R.id.action_home_to_create_group)
                }
            }
            binding.createGroupFab.setOnClickListener { navigateToCreateGroup() }
            binding.createGroupButton.setOnClickListener { navigateToCreateGroup() }
            binding.createFirstGroupButton.setOnClickListener { navigateToCreateGroup() }

            observeAuthState { state ->
                val b = _binding ?: return@observeAuthState
                b.logoutButton.isEnabled = state !is AuthUiState.Loading
                b.logoutLoading.visibility =
                    if (state is AuthUiState.Loading) View.VISIBLE else View.GONE
            }

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    groupViewModel.homeState.collect(::renderHomeState)
                }
            }

            AppLog.i("HomeFragment", "Triggering groupViewModel.loadHomeData()")
            groupViewModel.loadHomeData()
        } catch (e: Exception) {
            AppLog.e("HomeFragment", "Exception during HomeFragment onViewCreated setup", e)
        }
    }

    private fun renderHomeState(state: HomeUiState) {
        val b = _binding ?: return
        val ctx = context ?: return
        AppLog.d("HomeFragment", "renderHomeState called with ${state.javaClass.simpleName}")

        try {
            when (state) {
                HomeUiState.Loading -> {
                    b.profileContent.visibility = View.GONE
                    b.groupsLoadingIndicator.visibility = View.VISIBLE
                    b.errorLayout.visibility = View.GONE
                    b.emptyGroupsView.visibility = View.GONE
                    b.groupsRecycler.visibility = View.GONE
                    b.totalOwedAmount.text = CurrencyUtil.formatPaiseToInr(0L)
                    b.totalOwesAmount.text = CurrencyUtil.formatPaiseToInr(0L)
                }
                is HomeUiState.Content -> {
                    b.profileContent.visibility = View.VISIBLE
                    b.greeting.text =
                        getString(R.string.home_greeting, state.profile.greetingName)
                    b.userEmailSubtitle.text =
                        state.profile.displayHandle.ifBlank { getString(R.string.signed_in_account) }

                    b.totalOwedAmount.text = CurrencyUtil.formatPaiseToInr(state.totalOwedPaise)
                    b.totalOwesAmount.text = CurrencyUtil.formatPaiseToInr(state.totalOwesPaise)

                    b.groupsLoadingIndicator.visibility = View.GONE
                    b.errorLayout.visibility = View.GONE

                    if (state.groups.isEmpty()) {
                        AppLog.i("HomeFragment", "Groups list is empty -> showing emptyGroupsView")
                        b.emptyGroupsView.visibility = View.VISIBLE
                        b.groupsRecycler.visibility = View.GONE
                    } else {
                        AppLog.i("HomeFragment", "Displaying ${state.groups.size} groups")
                        b.emptyGroupsView.visibility = View.GONE
                        b.groupsRecycler.visibility = View.VISIBLE
                        groupsAdapter?.submitList(state.groups)
                    }
                }
                is HomeUiState.Error -> {
                    AppLog.w("HomeFragment", "HomeUiState Error: ${state.message}")
                    b.profileContent.visibility = View.GONE
                    b.groupsLoadingIndicator.visibility = View.GONE
                    b.emptyGroupsView.visibility = View.GONE
                    b.groupsRecycler.visibility = View.GONE

                    b.statusMessage.text = state.message
                    b.retryButton.visibility = if (state.retryable) View.VISIBLE else View.GONE
                    b.errorLayout.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            AppLog.e("HomeFragment", "Exception rendering Home state", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        AppLog.d("HomeFragment", "onDestroyView")
        groupsAdapter = null
        _binding = null
    }

    private class GroupsAdapter(
        private val onGroupClick: (GroupWithBalance) -> Unit
    ) : RecyclerView.Adapter<GroupsAdapter.GroupViewHolder>() {

        private var groups: List<GroupWithBalance> = emptyList()

        fun submitList(newGroups: List<GroupWithBalance>) {
            groups = newGroups
            notifyDataSetChanged()
        }

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
                        val absPaise = if (item.netBalancePaise == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(item.netBalancePaise)
                        groupNetBalance.text = context.getString(
                            R.string.group_balance_owe,
                            CurrencyUtil.formatPaiseToInr(absPaise)
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
