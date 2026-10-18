package com.example.hisabkitab.ui.group

import android.app.Application
import com.example.hisabkitab.data.auth.AuthRepository
import com.example.hisabkitab.data.group.GroupRepository
import com.example.hisabkitab.data.models.Expense
import com.example.hisabkitab.data.models.Group
import com.example.hisabkitab.data.models.GroupActivity
import com.example.hisabkitab.data.models.GroupMember
import com.example.hisabkitab.data.models.Settlement
import com.example.hisabkitab.data.models.UserProfile
import com.example.hisabkitab.data.user.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val testUser = UserProfile(
        uid = "user1",
        name = "Test User",
        email = "test@example.com"
    )

    private class FakeAuthRepository(
        override val currentUid: String? = "user1"
    ) : AuthRepository(null)

    private class FakeUserRepository(
        private val profile: UserProfile
    ) : UserRepository(null) {
        override suspend fun readProfile(uid: String): UserProfile = profile
    }

    private class FakeGroupRepository : GroupRepository(null) {
        val userGroupsFlow = MutableSharedFlow<List<Group>>(replay = 1)
        val groupMembersFlows = mutableMapOf<String, MutableSharedFlow<List<GroupMember>>>()
        val groupExpensesFlows = mutableMapOf<String, MutableSharedFlow<List<Expense>>>()
        val groupSettlementsFlows = mutableMapOf<String, MutableSharedFlow<List<Settlement>>>()
        val groupActivityFlows = mutableMapOf<String, MutableSharedFlow<List<GroupActivity>>>()

        var addedExpense: Expense? = null

        fun getOrCreateMembersFlow(groupId: String) =
            groupMembersFlows.getOrPut(groupId) { MutableSharedFlow(replay = 1) }

        fun getOrCreateExpensesFlow(groupId: String) =
            groupExpensesFlows.getOrPut(groupId) { MutableSharedFlow(replay = 1) }

        fun getOrCreateSettlementsFlow(groupId: String) =
            groupSettlementsFlows.getOrPut(groupId) { MutableSharedFlow(replay = 1) }

        fun getOrCreateActivityFlow(groupId: String) =
            groupActivityFlows.getOrPut(groupId) { MutableSharedFlow(replay = 1) }

        override fun observeUserGroups(uid: String): Flow<List<Group>> = userGroupsFlow

        override fun observeGroupMembers(groupId: String): Flow<List<GroupMember>> =
            getOrCreateMembersFlow(groupId)

        override fun observeGroupExpenses(groupId: String): Flow<List<Expense>> =
            getOrCreateExpensesFlow(groupId)

        override fun observeGroupSettlements(groupId: String): Flow<List<Settlement>> =
            getOrCreateSettlementsFlow(groupId)

        override fun observeGroupActivity(groupId: String): Flow<List<GroupActivity>> =
            getOrCreateActivityFlow(groupId)

        override suspend fun getGroupExpensesOnce(groupId: String): List<Expense> = emptyList()

        override suspend fun getGroupSettlementsOnce(groupId: String): List<Settlement> = emptyList()

        override suspend fun addExpense(groupId: String, expense: Expense, actorName: String) {
            addedExpense = expense
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSwitchingGroupsCancelsPreviousJobAndPreventsStaleWrites() = testScope.runTest {
        val fakeAuth = FakeAuthRepository("user1")
        val fakeUser = FakeUserRepository(testUser)
        val fakeGroup = FakeGroupRepository()

        val viewModel = GroupViewModel(
            application = Application(),
            authRepository = fakeAuth,
            userRepository = fakeUser,
            groupRepository = fakeGroup
        )

        val groupA = Group(id = "groupA", name = "Group Alpha", memberIds = listOf("user1", "user2"))
        val groupB = Group(id = "groupB", name = "Group Beta", memberIds = listOf("user1", "user3"))

        fakeGroup.userGroupsFlow.emit(listOf(groupA, groupB))

        // Open group A
        viewModel.loadGroupDetails("groupA")
        fakeGroup.getOrCreateMembersFlow("groupA").emit(listOf(GroupMember("user1", displayName = "User 1")))
        fakeGroup.getOrCreateExpensesFlow("groupA").emit(emptyList())
        fakeGroup.getOrCreateSettlementsFlow("groupA").emit(emptyList())
        fakeGroup.getOrCreateActivityFlow("groupA").emit(emptyList())
        advanceUntilIdle()

        assertTrue(viewModel.detailState.value is GroupDetailUiState.Content)
        assertEquals("groupA", (viewModel.detailState.value as GroupDetailUiState.Content).group.id)

        // Immediately switch to group B
        viewModel.loadGroupDetails("groupB")
        advanceUntilIdle()

        // Group detail state resets to Loading for new group
        assertTrue(viewModel.detailState.value is GroupDetailUiState.Loading)

        // Delayed/stale emission arrives for group A
        fakeGroup.getOrCreateMembersFlow("groupA").emit(
            listOf(GroupMember("user1", displayName = "User 1 Stale Emission"))
        )
        advanceUntilIdle()

        // Verify group A's stale emission did NOT overwrite detailState
        assertNotEquals("groupA", (viewModel.detailState.value as? GroupDetailUiState.Content)?.group?.id)

        // Now group B emits
        fakeGroup.getOrCreateMembersFlow("groupB").emit(listOf(GroupMember("user1", displayName = "User 1 in B")))
        fakeGroup.getOrCreateExpensesFlow("groupB").emit(emptyList())
        fakeGroup.getOrCreateSettlementsFlow("groupB").emit(emptyList())
        fakeGroup.getOrCreateActivityFlow("groupB").emit(emptyList())
        advanceUntilIdle()

        assertTrue(viewModel.detailState.value is GroupDetailUiState.Content)
        assertEquals("groupB", (viewModel.detailState.value as GroupDetailUiState.Content).group.id)
    }

    @Test
    fun testNavigatingToSameGroupDoesNotRecreateJobOrFlickerLoading() = testScope.runTest {
        val fakeAuth = FakeAuthRepository("user1")
        val fakeUser = FakeUserRepository(testUser)
        val fakeGroup = FakeGroupRepository()

        val viewModel = GroupViewModel(
            application = Application(),
            authRepository = fakeAuth,
            userRepository = fakeUser,
            groupRepository = fakeGroup
        )

        val groupA = Group(id = "groupA", name = "Group Alpha", memberIds = listOf("user1"))
        fakeGroup.userGroupsFlow.emit(listOf(groupA))

        viewModel.loadGroupDetails("groupA")
        fakeGroup.getOrCreateMembersFlow("groupA").emit(listOf(GroupMember("user1", displayName = "User 1")))
        fakeGroup.getOrCreateExpensesFlow("groupA").emit(emptyList())
        fakeGroup.getOrCreateSettlementsFlow("groupA").emit(emptyList())
        fakeGroup.getOrCreateActivityFlow("groupA").emit(emptyList())
        advanceUntilIdle()

        assertTrue(viewModel.detailState.value is GroupDetailUiState.Content)

        // Calling loadGroupDetails again with the same groupId (e.g. returning from AddExpense)
        viewModel.loadGroupDetails("groupA")
        // It should NOT reset to Loading
        assertTrue(viewModel.detailState.value is GroupDetailUiState.Content)
    }

    @Test
    fun testLoadHomeDataStopsGroupDetailJob() = testScope.runTest {
        val fakeAuth = FakeAuthRepository("user1")
        val fakeUser = FakeUserRepository(testUser)
        val fakeGroup = FakeGroupRepository()

        val viewModel = GroupViewModel(
            application = Application(),
            authRepository = fakeAuth,
            userRepository = fakeUser,
            groupRepository = fakeGroup
        )

        val groupA = Group(id = "groupA", name = "Group Alpha", memberIds = listOf("user1"))
        fakeGroup.userGroupsFlow.emit(listOf(groupA))

        viewModel.loadGroupDetails("groupA")
        fakeGroup.getOrCreateMembersFlow("groupA").emit(listOf(GroupMember("user1", displayName = "User 1")))
        fakeGroup.getOrCreateExpensesFlow("groupA").emit(emptyList())
        fakeGroup.getOrCreateSettlementsFlow("groupA").emit(emptyList())
        fakeGroup.getOrCreateActivityFlow("groupA").emit(emptyList())
        advanceUntilIdle()

        assertTrue(viewModel.detailState.value is GroupDetailUiState.Content)

        // User navigates back to Home
        viewModel.loadHomeData()
        advanceUntilIdle()

        assertTrue(viewModel.homeState.value is HomeUiState.Content)

        // Late emission on group A should be ignored because group detail was stopped
        fakeGroup.getOrCreateMembersFlow("groupA").emit(listOf(GroupMember("user1", displayName = "User 1 Late")))
        advanceUntilIdle()

        // Detail state is not updated by stopped listener
        assertNotEquals(
            "User 1 Late",
            (viewModel.detailState.value as? GroupDetailUiState.Content)?.membersWithBalance?.firstOrNull()?.member?.displayName
        )
    }

    @Test
    fun testLoadGroupDetailsCancelsHomeLoadJob() = testScope.runTest {
        val fakeAuth = FakeAuthRepository("user1")
        val fakeUser = FakeUserRepository(testUser)
        val fakeGroup = FakeGroupRepository()

        val viewModel = GroupViewModel(
            application = Application(),
            authRepository = fakeAuth,
            userRepository = fakeUser,
            groupRepository = fakeGroup
        )

        val groupA = Group(id = "groupA", name = "Group Alpha", memberIds = listOf("user1"))
        fakeGroup.userGroupsFlow.emit(listOf(groupA))

        // Start on Home
        viewModel.loadHomeData()
        advanceUntilIdle()
        assertTrue(viewModel.homeState.value is HomeUiState.Content)

        // Open Group Detail
        viewModel.loadGroupDetails("groupA")
        fakeGroup.getOrCreateMembersFlow("groupA").emit(listOf(GroupMember("user1", displayName = "User 1")))
        fakeGroup.getOrCreateExpensesFlow("groupA").emit(emptyList())
        fakeGroup.getOrCreateSettlementsFlow("groupA").emit(emptyList())
        fakeGroup.getOrCreateActivityFlow("groupA").emit(emptyList())
        advanceUntilIdle()
        assertTrue(viewModel.detailState.value is GroupDetailUiState.Content)

        // Late emission on user groups flow should not update home data while on detail
        val initialGroupCount = (viewModel.homeState.value as HomeUiState.Content).groups.size
        fakeGroup.userGroupsFlow.emit(listOf(groupA, Group(id = "groupB", name = "Group Beta", memberIds = listOf("user1"))))
        advanceUntilIdle()

        assertEquals(initialGroupCount, (viewModel.homeState.value as HomeUiState.Content).groups.size)
    }

    @Test
    fun testAddExpenseDefensiveValidationRejectsMalformedCustomSplit() = testScope.runTest {
        val fakeAuth = FakeAuthRepository("user1")
        val fakeUser = FakeUserRepository(testUser)
        val fakeGroup = FakeGroupRepository()

        val viewModel = GroupViewModel(
            application = Application(),
            authRepository = fakeAuth,
            userRepository = fakeUser,
            groupRepository = fakeGroup
        )

        // Custom shares sum mismatch
        viewModel.addExpense(
            groupId = "g1",
            description = "Lunch",
            amountPaise = 5000L,
            paidByUid = "user1",
            participantIds = listOf("user1", "user2"),
            splitType = "custom",
            customSharesByUid = mapOf("user1" to 3000L, "user2" to 1000L) // Sum 4000 != 5000
        )
        advanceUntilIdle()

        assertTrue(viewModel.operationState.value is OperationState.Error)
        assertEquals(null, fakeGroup.addedExpense)

        // Key mismatch: participantIds has user1 and user2, shares has user1 and user3
        viewModel.addExpense(
            groupId = "g1",
            description = "Dinner",
            amountPaise = 5000L,
            paidByUid = "user1",
            participantIds = listOf("user1", "user2"),
            splitType = "custom",
            customSharesByUid = mapOf("user1" to 3000L, "user3" to 2000L)
        )
        advanceUntilIdle()

        assertTrue(viewModel.operationState.value is OperationState.Error)
        assertEquals("Shares do not match the selected participants.", (viewModel.operationState.value as OperationState.Error).message)
        assertEquals(null, fakeGroup.addedExpense)
    }

    @Test
    fun testAddExpenseAcceptsValidEqualSplit() = testScope.runTest {
        val fakeAuth = FakeAuthRepository("user1")
        val fakeUser = FakeUserRepository(testUser)
        val fakeGroup = FakeGroupRepository()

        val viewModel = GroupViewModel(
            application = Application(),
            authRepository = fakeAuth,
            userRepository = fakeUser,
            groupRepository = fakeGroup
        )

        viewModel.addExpense(
            groupId = "g1",
            description = "Breakfast",
            amountPaise = 3000L,
            paidByUid = "user1",
            participantIds = listOf("user1", "user2", "user1"), // Contains duplicate to verify normalization
            splitType = "equal",
            customSharesByUid = emptyMap()
        )
        advanceUntilIdle()

        assertTrue(viewModel.operationState.value is OperationState.Success)
        val added = fakeGroup.addedExpense
        assertTrue(added != null)
        assertEquals(listOf("user1", "user2"), added!!.participantIds)
        assertEquals(mapOf("user1" to 1500L, "user2" to 1500L), added.sharesByUid)
    }
}
