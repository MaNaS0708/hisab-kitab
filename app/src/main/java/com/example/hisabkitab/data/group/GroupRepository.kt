package com.example.hisabkitab.data.group

import com.example.hisabkitab.data.models.Expense
import com.example.hisabkitab.data.models.Group
import com.example.hisabkitab.data.models.GroupActivity
import com.example.hisabkitab.data.models.GroupMember
import com.example.hisabkitab.data.models.Settlement
import com.example.hisabkitab.data.models.UserProfile
import com.example.hisabkitab.util.CurrencyUtil
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class GroupRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun createGroup(
        groupName: String,
        creator: UserProfile,
        members: List<UserProfile>
    ): String {
        require(groupName.isNotBlank()) { "Group name cannot be blank." }
        val allMembers = (members + creator).distinctBy { it.uid }

        val groupRef = firestore.collection("groups").document()
        val groupId = groupRef.id

        val batch = firestore.batch()

        batch.set(
            groupRef,
            mapOf(
                "id" to groupId,
                "name" to groupName.trim(),
                "createdBy" to creator.uid,
                "memberIds" to allMembers.map { it.uid },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        allMembers.forEach { member ->
            val memberRef = groupRef.collection("members").document(member.uid)
            batch.set(
                memberRef,
                mapOf(
                    "uid" to member.uid,
                    "username" to (member.username ?: ""),
                    "displayName" to member.greetingName,
                    "joinedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val activityRef = groupRef.collection("activity").document()
        batch.set(
            activityRef,
            mapOf(
                "id" to activityRef.id,
                "type" to "group_created",
                "actorUid" to creator.uid,
                "message" to "${creator.greetingName} created group \"${groupName.trim()}\"",
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        batch.commit().await()
        return groupId
    }

    fun observeUserGroups(uid: String): Flow<List<Group>> = callbackFlow {
        val query = firestore.collection("groups")
            .whereArrayContains("memberIds", uid)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val groups = snapshot.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val name = doc.getString("name") ?: return@mapNotNull null
                    val createdBy = doc.getString("createdBy") ?: ""
                    @Suppress("UNCHECKED_CAST")
                    val memberIds = (doc.get("memberIds") as? List<String>) ?: emptyList()
                    Group(
                        id = id,
                        name = name,
                        createdBy = createdBy,
                        memberIds = memberIds,
                        createdAt = doc.getDate("createdAt"),
                        updatedAt = doc.getDate("updatedAt")
                    )
                }.sortedByDescending { it.updatedAt }
                trySend(groups)
            }
        }
        awaitClose { registration.remove() }
    }

    fun observeGroupMembers(groupId: String): Flow<List<GroupMember>> = callbackFlow {
        val registration = firestore.collection("groups")
            .document(groupId)
            .collection("members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val members = snapshot.documents.mapNotNull { doc ->
                        val uid = doc.getString("uid") ?: doc.id
                        val username = doc.getString("username") ?: ""
                        val displayName = doc.getString("displayName") ?: ""
                        GroupMember(
                            uid = uid,
                            username = username,
                            displayName = displayName,
                            joinedAt = doc.getDate("joinedAt")
                        )
                    }
                    trySend(members)
                }
            }
        awaitClose { registration.remove() }
    }

    fun observeGroupExpenses(groupId: String): Flow<List<Expense>> = callbackFlow {
        val registration = firestore.collection("groups")
            .document(groupId)
            .collection("expenses")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val expenses = snapshot.documents.mapNotNull { doc ->
                        val id = doc.getString("id") ?: doc.id
                        val description = doc.getString("description") ?: ""
                        val amountPaise = doc.getLong("amountPaise") ?: 0L
                        val paidByUid = doc.getString("paidByUid") ?: ""
                        @Suppress("UNCHECKED_CAST")
                        val participantIds = (doc.get("participantIds") as? List<String>) ?: emptyList()
                        val splitType = doc.getString("splitType") ?: "equal"
                        @Suppress("UNCHECKED_CAST")
                        val sharesRaw = doc.get("sharesByUid") as? Map<String, Long> ?: emptyMap()
                        val createdByUid = doc.getString("createdByUid") ?: ""
                        Expense(
                            id = id,
                            description = description,
                            amountPaise = amountPaise,
                            paidByUid = paidByUid,
                            participantIds = participantIds,
                            splitType = splitType,
                            sharesByUid = sharesRaw,
                            createdByUid = createdByUid,
                            createdAt = doc.getDate("createdAt")
                        )
                    }.sortedByDescending { it.createdAt }
                    trySend(expenses)
                }
            }
        awaitClose { registration.remove() }
    }

    fun observeGroupSettlements(groupId: String): Flow<List<Settlement>> = callbackFlow {
        val registration = firestore.collection("groups")
            .document(groupId)
            .collection("settlements")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val settlements = snapshot.documents.mapNotNull { doc ->
                        val id = doc.getString("id") ?: doc.id
                        val fromUid = doc.getString("fromUid") ?: ""
                        val toUid = doc.getString("toUid") ?: ""
                        val amountPaise = doc.getLong("amountPaise") ?: 0L
                        val createdByUid = doc.getString("createdByUid") ?: ""
                        Settlement(
                            id = id,
                            fromUid = fromUid,
                            toUid = toUid,
                            amountPaise = amountPaise,
                            createdByUid = createdByUid,
                            createdAt = doc.getDate("createdAt")
                        )
                    }.sortedByDescending { it.createdAt }
                    trySend(settlements)
                }
            }
        awaitClose { registration.remove() }
    }

    fun observeGroupActivity(groupId: String): Flow<List<GroupActivity>> = callbackFlow {
        val registration = firestore.collection("groups")
            .document(groupId)
            .collection("activity")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val activities = snapshot.documents.mapNotNull { doc ->
                        val id = doc.getString("id") ?: doc.id
                        val type = doc.getString("type") ?: ""
                        val actorUid = doc.getString("actorUid") ?: ""
                        val message = doc.getString("message") ?: ""
                        GroupActivity(
                            id = id,
                            type = type,
                            actorUid = actorUid,
                            message = message,
                            createdAt = doc.getDate("createdAt")
                        )
                    }.sortedByDescending { it.createdAt }
                    trySend(activities)
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun getGroupExpensesOnce(groupId: String): List<Expense> {
        val snapshot = firestore.collection("groups")
            .document(groupId)
            .collection("expenses")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val id = doc.getString("id") ?: doc.id
            val description = doc.getString("description") ?: ""
            val amountPaise = doc.getLong("amountPaise") ?: 0L
            val paidByUid = doc.getString("paidByUid") ?: ""
            @Suppress("UNCHECKED_CAST")
            val participantIds = (doc.get("participantIds") as? List<String>) ?: emptyList()
            val splitType = doc.getString("splitType") ?: "equal"
            @Suppress("UNCHECKED_CAST")
            val sharesRaw = doc.get("sharesByUid") as? Map<String, Long> ?: emptyMap()
            val createdByUid = doc.getString("createdByUid") ?: ""
            Expense(
                id = id,
                description = description,
                amountPaise = amountPaise,
                paidByUid = paidByUid,
                participantIds = participantIds,
                splitType = splitType,
                sharesByUid = sharesRaw,
                createdByUid = createdByUid,
                createdAt = doc.getDate("createdAt")
            )
        }
    }

    suspend fun getGroupSettlementsOnce(groupId: String): List<Settlement> {
        val snapshot = firestore.collection("groups")
            .document(groupId)
            .collection("settlements")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val id = doc.getString("id") ?: doc.id
            val fromUid = doc.getString("fromUid") ?: ""
            val toUid = doc.getString("toUid") ?: ""
            val amountPaise = doc.getLong("amountPaise") ?: 0L
            val createdByUid = doc.getString("createdByUid") ?: ""
            Settlement(
                id = id,
                fromUid = fromUid,
                toUid = toUid,
                amountPaise = amountPaise,
                createdByUid = createdByUid,
                createdAt = doc.getDate("createdAt")
            )
        }
    }

    suspend fun addExpense(
        groupId: String,
        expense: Expense,
        actorName: String
    ) {
        val groupRef = firestore.collection("groups").document(groupId)
        val expenseRef = groupRef.collection("expenses").document()
        val activityRef = groupRef.collection("activity").document()

        val expenseId = expenseRef.id
        val finalExpense = expense.copy(id = expenseId)

        val batch = firestore.batch()
        batch.set(
            expenseRef,
            mapOf(
                "id" to expenseId,
                "description" to finalExpense.description,
                "amountPaise" to finalExpense.amountPaise,
                "paidByUid" to finalExpense.paidByUid,
                "participantIds" to finalExpense.participantIds,
                "splitType" to finalExpense.splitType,
                "sharesByUid" to finalExpense.sharesByUid,
                "createdByUid" to finalExpense.createdByUid,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        val amountFormatted = CurrencyUtil.formatPaiseToInr(finalExpense.amountPaise)
        batch.set(
            activityRef,
            mapOf(
                "id" to activityRef.id,
                "type" to "expense_added",
                "actorUid" to finalExpense.createdByUid,
                "message" to "$actorName added \"${finalExpense.description}\" ($amountFormatted)",
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        batch.update(groupRef, "updatedAt", FieldValue.serverTimestamp())
        batch.commit().await()
    }

    suspend fun addSettlement(
        groupId: String,
        settlement: Settlement,
        fromName: String,
        toName: String
    ) {
        val groupRef = firestore.collection("groups").document(groupId)
        val settlementRef = groupRef.collection("settlements").document()
        val activityRef = groupRef.collection("activity").document()

        val settlementId = settlementRef.id
        val finalSettlement = settlement.copy(id = settlementId)

        val batch = firestore.batch()
        batch.set(
            settlementRef,
            mapOf(
                "id" to settlementId,
                "fromUid" to finalSettlement.fromUid,
                "toUid" to finalSettlement.toUid,
                "amountPaise" to finalSettlement.amountPaise,
                "createdByUid" to finalSettlement.createdByUid,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        val amountFormatted = CurrencyUtil.formatPaiseToInr(finalSettlement.amountPaise)
        batch.set(
            activityRef,
            mapOf(
                "id" to activityRef.id,
                "type" to "settlement_added",
                "actorUid" to finalSettlement.createdByUid,
                "message" to "$fromName paid $toName $amountFormatted",
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        batch.update(groupRef, "updatedAt", FieldValue.serverTimestamp())
        batch.commit().await()
    }
}
