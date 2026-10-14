package com.example.hisabkitab.ui.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisabkitab.data.auth.AuthRepository
import com.example.hisabkitab.data.group.GroupRepository
import com.example.hisabkitab.data.models.Expense
import com.example.hisabkitab.data.models.Group
import com.example.hisabkitab.data.models.GroupActivity
import com.example.hisabkitab.data.models.GroupMember
import com.example.hisabkitab.data.models.Settlement
import com.example.hisabkitab.data.models.UserProfile
import com.example.hisabkitab.data.user.UserRepository
import com.example.hisabkitab.util.BalanceCalculator
import com.example.hisabkitab.util.CustomSplitValidationResult
import com.example.hisabkitab.util.SplitCalculator
import com.example.hisabkitab.util.UsernameUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class GroupWithBalance(
    val group: Group,
    val netBalancePaise: Long
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(
        val profile: UserProfile,
        val groups: List<GroupWithBalance>,
        val totalOwedPaise: Long,
        val totalOwesPaise: Long
    ) : HomeUiState
    data class Error(val message: String, val retryable: Boolean) : HomeUiState
}

data class MemberWithBalance(
    val member: GroupMember,
    val netBalancePaise: Long
)

sealed interface GroupDetailUiState {
    data object Loading : GroupDetailUiState
    data class Content(
        val group: Group,
        val membersWithBalance: List<MemberWithBalance>,
        val expenses: List<Expense>,
        val settlements: List<Settlement>,
        val activities: List<GroupActivity>,
        val userNetBalancePaise: Long
    ) : GroupDetailUiState
    data class Error(val message: String) : GroupDetailUiState
}

sealed interface OperationState {
    data object Idle : OperationState
    data object Loading : OperationState
    data class Success(val message: String? = null, val resultId: String? = null) : OperationState
    data class Error(val message: String) : OperationState
}

class GroupViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val groupRepository = GroupRepository()

    private val _homeState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _detailState = MutableStateFlow<GroupDetailUiState>(GroupDetailUiState.Loading)
    val detailState: StateFlow<GroupDetailUiState> = _detailState.asStateFlow()

    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState.asStateFlow()

    fun resetOperationState() {
        _operationState.value = OperationState.Idle
    }

    fun loadHomeData() {
        viewModelScope.launch {
            val firebaseUser = authRepository.currentUser
            if (firebaseUser == null) {
                _homeState.value = HomeUiState.Error("User not authenticated.", retryable = false)
                return@launch
            }

            _homeState.value = HomeUiState.Loading
            try {
                val profile = userRepository.readProfile(firebaseUser.uid)

                groupRepository.observeUserGroups(profile.uid).collect { groups ->
                    val groupWithBalances = mutableListOf<GroupWithBalance>()
                    val allGroupBalances = mutableListOf<Map<String, Long>>()

                    for (group in groups) {
                        val expenses = groupRepository.getGroupExpensesOnce(group.id)
                        val settlements = groupRepository.getGroupSettlementsOnce(group.id)
                        val balanceMap = BalanceCalculator.calculateGroupBalances(expenses, settlements)

                        allGroupBalances.add(balanceMap)
                        val userNet = balanceMap.getOrDefault(profile.uid, 0L)
                        groupWithBalances.add(GroupWithBalance(group, userNet))
                    }

                    val summary = BalanceCalculator.calculateOverallSummary(allGroupBalances, profile.uid)

                    _homeState.value = HomeUiState.Content(
                        profile = profile,
                        groups = groupWithBalances,
                        totalOwedPaise = summary.userOwedPaise,
                        totalOwesPaise = summary.userOwesPaise
                    )
                }
            } catch (e: Exception) {
                _homeState.value = HomeUiState.Error(
                    e.message ?: "Failed to load groups.",
                    retryable = true
                )
            }
        }
    }

    fun loadGroupDetails(groupId: String) {
        viewModelScope.launch {
            val firebaseUser = authRepository.currentUser
            if (firebaseUser == null) {
                _detailState.value = GroupDetailUiState.Error("User not authenticated.")
                return@launch
            }

            _detailState.value = GroupDetailUiState.Loading
            try {
                val groupsFlow = groupRepository.observeUserGroups(firebaseUser.uid)
                val membersFlow = groupRepository.observeGroupMembers(groupId)
                val expensesFlow = groupRepository.observeGroupExpenses(groupId)
                val settlementsFlow = groupRepository.observeGroupSettlements(groupId)
                val activitiesFlow = groupRepository.observeGroupActivity(groupId)

                combine(
                    groupsFlow,
                    membersFlow,
                    expensesFlow,
                    settlementsFlow,
                    activitiesFlow
                ) { groups: List<Group>, members: List<GroupMember>, expenses: List<Expense>, settlements: List<Settlement>, activities: List<GroupActivity> ->
                    val group = groups.find { it.id == groupId }
                        ?: return@combine GroupDetailUiState.Error("Group not found or access revoked.")

                    val balancesMap = BalanceCalculator.calculateGroupBalances(expenses, settlements)

                    val membersWithBalance = members.map { member ->
                        MemberWithBalance(
                            member = member,
                            netBalancePaise = balancesMap.getOrDefault(member.uid, 0L)
                        )
                    }

                    val userNet = balancesMap.getOrDefault(firebaseUser.uid, 0L)

                    GroupDetailUiState.Content(
                        group = group,
                        membersWithBalance = membersWithBalance,
                        expenses = expenses,
                        settlements = settlements,
                        activities = activities,
                        userNetBalancePaise = userNet
                    )
                }.collect { state ->
                    _detailState.value = state
                }
            } catch (e: Exception) {
                _detailState.value = GroupDetailUiState.Error(e.message ?: "Error loading group.")
            }
        }
    }

    suspend fun findUserByUsername(rawUsername: String): UserProfile? {
        val normalized = UsernameUtil.normalize(rawUsername)
        if (!UsernameUtil.isValid(normalized)) return null
        return userRepository.findUserByUsername(normalized)
    }

    fun createGroup(groupName: String, memberUsernames: List<String>) {
        viewModelScope.launch {
            val firebaseUser = authRepository.currentUser ?: return@launch
            if (groupName.isBlank()) {
                _operationState.value = OperationState.Error("Group name is required.")
                return@launch
            }

            _operationState.value = OperationState.Loading
            try {
                val creator = userRepository.readProfile(firebaseUser.uid)
                val memberProfiles = mutableListOf<UserProfile>()

                for (username in memberUsernames.distinct()) {
                    val profile = userRepository.findUserByUsername(username)
                    if (profile != null && profile.uid != creator.uid) {
                        memberProfiles.add(profile)
                    }
                }

                val distinctProfiles = memberProfiles.distinctBy { it.uid }
                if (distinctProfiles.isEmpty()) {
                    _operationState.value = OperationState.Error("Please add at least one other group member.")
                    return@launch
                }

                val groupId = groupRepository.createGroup(groupName, creator, distinctProfiles)
                _operationState.value = OperationState.Success(resultId = groupId)
            } catch (e: Exception) {
                _operationState.value = OperationState.Error(e.message ?: "Failed to create group.")
            }
        }
    }

    fun addExpense(
        groupId: String,
        description: String,
        amountPaise: Long,
        paidByUid: String,
        participantIds: List<String>,
        splitType: String,
        customSharesByUid: Map<String, Long>
    ) {
        viewModelScope.launch {
            val firebaseUser = authRepository.currentUser ?: return@launch
            if (description.isBlank()) {
                _operationState.value = OperationState.Error("Description is required.")
                return@launch
            }
            if (amountPaise <= 0) {
                _operationState.value = OperationState.Error("Amount must be greater than zero.")
                return@launch
            }
            if (participantIds.isEmpty()) {
                _operationState.value = OperationState.Error("Select at least one participant.")
                return@launch
            }

            val shares = if (splitType == "custom") {
                val validation = SplitCalculator.validateCustomShares(amountPaise, customSharesByUid)
                if (validation is CustomSplitValidationResult.Invalid) {
                    _operationState.value = OperationState.Error(validation.message)
                    return@launch
                }
                customSharesByUid
            } else {
                SplitCalculator.calculateEqualShares(amountPaise, participantIds)
            }

            _operationState.value = OperationState.Loading
            try {
                val actorProfile = userRepository.readProfile(firebaseUser.uid)
                val expense = Expense(
                    description = description.trim(),
                    amountPaise = amountPaise,
                    paidByUid = paidByUid,
                    participantIds = participantIds,
                    splitType = splitType,
                    sharesByUid = shares,
                    createdByUid = firebaseUser.uid
                )

                groupRepository.addExpense(groupId, expense, actorProfile.greetingName)
                _operationState.value = OperationState.Success(message = "Expense added successfully.")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error(e.message ?: "Failed to add expense.")
            }
        }
    }

    fun addSettlement(
        groupId: String,
        fromUid: String,
        toUid: String,
        amountPaise: Long,
        fromName: String,
        toName: String
    ) {
        viewModelScope.launch {
            val firebaseUser = authRepository.currentUser ?: return@launch
            if (fromUid == toUid) {
                _operationState.value = OperationState.Error("Payer and recipient cannot be the same person.")
                return@launch
            }
            if (amountPaise <= 0) {
                _operationState.value = OperationState.Error("Amount must be greater than zero.")
                return@launch
            }

            _operationState.value = OperationState.Loading
            try {
                val settlement = Settlement(
                    fromUid = fromUid,
                    toUid = toUid,
                    amountPaise = amountPaise,
                    createdByUid = firebaseUser.uid
                )

                groupRepository.addSettlement(groupId, settlement, fromName, toName)
                _operationState.value = OperationState.Success(message = "Settlement recorded.")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error(e.message ?: "Failed to record settlement.")
            }
        }
    }
}
