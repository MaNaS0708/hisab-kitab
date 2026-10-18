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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

open class GroupViewModel(
    application: Application,
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val groupRepository: GroupRepository = GroupRepository()
) : AndroidViewModel(application) {

    private var homeLoadJob: Job? = null
    private var groupDetailJob: Job? = null
    private var activeDetailGroupId: String? = null

    private val _homeState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _detailState = MutableStateFlow<GroupDetailUiState>(GroupDetailUiState.Loading)
    val detailState: StateFlow<GroupDetailUiState> = _detailState.asStateFlow()

    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState.asStateFlow()

    fun resetOperationState() {
        _operationState.value = OperationState.Idle
    }

    fun stopObservingGroupDetails() {
        groupDetailJob?.cancel()
        groupDetailJob = null
        activeDetailGroupId = null
    }

    fun clearGroupDetails() {
        stopObservingGroupDetails()
        _detailState.value = GroupDetailUiState.Loading
    }

    fun resetViewModelState() {
        homeLoadJob?.cancel()
        homeLoadJob = null
        stopObservingGroupDetails()
        _homeState.value = HomeUiState.Loading
        _detailState.value = GroupDetailUiState.Loading
        _operationState.value = OperationState.Idle
    }

    fun loadHomeData() {
        homeLoadJob?.cancel()
        stopObservingGroupDetails()

        _homeState.value = HomeUiState.Loading
        homeLoadJob = viewModelScope.launch {
            val currentUid = authRepository.currentUid
            if (currentUid == null) {
                _homeState.value = HomeUiState.Error("User not authenticated.", retryable = false)
                return@launch
            }

            try {
                val profile = userRepository.readProfile(currentUid)

                groupRepository.observeUserGroups(profile.uid).collect { groups ->
                    val groupWithBalances = mutableListOf<GroupWithBalance>()
                    val allGroupBalances = mutableListOf<Map<String, Long>>()

                    for (group in groups) {
                        val expenses = groupRepository.getGroupExpensesOnce(group.id)
                        val settlements = groupRepository.getGroupSettlementsOnce(group.id)
                        val validExpenses = expenses.filter { SplitCalculator.isValidExpense(it) }
                        val balanceMap = BalanceCalculator.calculateGroupBalances(validExpenses, settlements)

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
                if (e is CancellationException) throw e
                _homeState.value = HomeUiState.Error(
                    e.message ?: "Failed to load groups.",
                    retryable = true
                )
            }
        }
    }

    fun loadGroupDetails(groupId: String, forceReload: Boolean = false) {
        if (groupId.isBlank()) {
            _detailState.value = GroupDetailUiState.Error("Invalid group ID.")
            return
        }

        // Avoid duplicate collector setup if already observing this active group in a non-error state (e.g. Add Expense or Settle Up)
        if (!forceReload && activeDetailGroupId == groupId && groupDetailJob?.isActive == true && _detailState.value !is GroupDetailUiState.Error) {
            return
        }

        homeLoadJob?.cancel()
        homeLoadJob = null

        groupDetailJob?.cancel()
        activeDetailGroupId = groupId
        _detailState.value = GroupDetailUiState.Loading

        groupDetailJob = viewModelScope.launch {
            val currentUid = authRepository.currentUid
            if (currentUid == null) {
                if (activeDetailGroupId == groupId) {
                    _detailState.value = GroupDetailUiState.Error("User not authenticated.")
                }
                return@launch
            }

            try {
                val groupsFlow = groupRepository.observeUserGroups(currentUid)
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

                    val validExpenses = expenses.filter { SplitCalculator.isValidExpense(it) }
                    val balancesMap = BalanceCalculator.calculateGroupBalances(validExpenses, settlements)

                    val membersWithBalance = members.map { member ->
                        MemberWithBalance(
                            member = member,
                            netBalancePaise = balancesMap.getOrDefault(member.uid, 0L)
                        )
                    }

                    val userNet = balancesMap.getOrDefault(currentUid, 0L)

                    GroupDetailUiState.Content(
                        group = group,
                        membersWithBalance = membersWithBalance,
                        expenses = validExpenses,
                        settlements = settlements,
                        activities = activities,
                        userNetBalancePaise = userNet
                    )
                }.collect { state ->
                    if (activeDetailGroupId == groupId) {
                        _detailState.value = state
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (activeDetailGroupId == groupId) {
                    _detailState.value = GroupDetailUiState.Error(e.message ?: "Error loading group.")
                }
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
            val currentUid = authRepository.currentUid ?: return@launch
            if (groupName.isBlank()) {
                _operationState.value = OperationState.Error("Group name is required.")
                return@launch
            }

            _operationState.value = OperationState.Loading
            try {
                val creator = userRepository.readProfile(currentUid)
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
                if (e is CancellationException) throw e
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
            val currentUid = authRepository.currentUid ?: return@launch
            val trimmedDesc = description.trim()
            if (trimmedDesc.isBlank()) {
                _operationState.value = OperationState.Error("Description is required.")
                return@launch
            }
            if (trimmedDesc.length > 200) {
                _operationState.value = OperationState.Error("Description must be 200 characters or fewer.")
                return@launch
            }
            if (amountPaise <= 0) {
                _operationState.value = OperationState.Error("Amount must be greater than zero.")
                return@launch
            }

            val distinctParticipants = participantIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (distinctParticipants.isEmpty()) {
                _operationState.value = OperationState.Error("Select at least one participant.")
                return@launch
            }

            val shares = when (splitType) {
                "custom" -> {
                    val validation = SplitCalculator.validateCustomShares(
                        totalAmountPaise = amountPaise,
                        sharesByUid = customSharesByUid,
                        participantUids = distinctParticipants
                    )
                    if (validation is CustomSplitValidationResult.Invalid) {
                        _operationState.value = OperationState.Error(validation.message)
                        return@launch
                    }
                    customSharesByUid
                }
                "equal" -> {
                    SplitCalculator.calculateEqualShares(amountPaise, distinctParticipants)
                }
                else -> {
                    _operationState.value = OperationState.Error("Unsupported split type.")
                    return@launch
                }
            }

            _operationState.value = OperationState.Loading
            try {
                val actorProfile = userRepository.readProfile(currentUid)
                val expense = Expense(
                    description = trimmedDesc,
                    amountPaise = amountPaise,
                    paidByUid = paidByUid,
                    participantIds = distinctParticipants,
                    splitType = splitType,
                    sharesByUid = shares,
                    createdByUid = currentUid
                )

                groupRepository.addExpense(groupId, expense, actorProfile.greetingName)
                _operationState.value = OperationState.Success(message = "Expense added successfully.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
            val currentUid = authRepository.currentUid ?: return@launch
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
                    createdByUid = currentUid
                )

                groupRepository.addSettlement(groupId, settlement, fromName, toName)
                _operationState.value = OperationState.Success(message = "Settlement recorded.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _operationState.value = OperationState.Error(e.message ?: "Failed to record settlement.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        homeLoadJob?.cancel()
        stopObservingGroupDetails()
    }
}
