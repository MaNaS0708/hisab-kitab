package com.example.hisabkitab

import com.example.hisabkitab.data.group.GroupRepository
import com.example.hisabkitab.data.models.Expense
import com.example.hisabkitab.data.models.Settlement
import org.junit.Test
import kotlinx.coroutines.runBlocking

class GroupRepositoryValidationTest {
    private val repository = GroupRepository(null)

    @Test
    fun addExpenseRejectsUnsupportedSplitBeforeWriting() {
        assertFails {
            repository.addExpense(
                groupId = "group",
                expense = Expense(
                    description = "Dinner",
                    amountPaise = 1_000,
                    paidByUid = "payer",
                    participantIds = listOf("payer"),
                    splitType = "invalid",
                    createdByUid = "actor"
                ),
                actorName = "Actor"
            )
        }
    }

    @Test
    fun addSettlementRejectsInvalidPartiesBeforeWriting() {
        assertFails {
            repository.addSettlement(
                groupId = "group",
                settlement = Settlement(
                    fromUid = "same",
                    toUid = "same",
                    amountPaise = 1_000,
                    createdByUid = "actor"
                ),
                fromName = "Same",
                toName = "Same"
            )
        }
    }

    private fun assertFails(block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected: validation completes before Firestore is accessed.
        }
    }
}
