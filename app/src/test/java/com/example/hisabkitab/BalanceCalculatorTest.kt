package com.example.hisabkitab

import com.example.hisabkitab.data.models.Expense
import com.example.hisabkitab.data.models.Settlement
import com.example.hisabkitab.util.BalanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceCalculatorTest {

    @Test
    fun testEqualSplit() {
        // Alice pays ₹600 (60000 paise) split equally among Alice, Bob, Charlie (20000 each)
        // Alice: +60000 - 20000 = +40000 (is owed ₹400)
        // Bob: -20000 (owes ₹200)
        // Charlie: -20000 (owes ₹200)
        val exp = Expense(
            id = "exp1",
            description = "Dinner",
            amountPaise = 60000L,
            paidByUid = "alice",
            participantIds = listOf("alice", "bob", "charlie"),
            splitType = "equal"
        )

        val balances = BalanceCalculator.calculateGroupBalances(listOf(exp), emptyList())
        assertEquals(40000L, balances["alice"])
        assertEquals(-20000L, balances["bob"])
        assertEquals(-20000L, balances["charlie"])
    }

    @Test
    fun testCustomSplit500With400And100() {
        // A pays ₹500 (50000 paise).
        // Custom shares: A ₹400 (40000 paise) + B ₹100 (10000 paise).
        // A net: +50000 - 40000 = +10000 (A is owed ₹100)
        // B net: -10000 (B owes ₹100)
        val exp = Expense(
            id = "exp_custom",
            description = "Groceries",
            amountPaise = 50000L,
            paidByUid = "userA",
            participantIds = listOf("userA", "userB"),
            splitType = "custom",
            sharesByUid = mapOf("userA" to 40000L, "userB" to 10000L)
        )

        val balances = BalanceCalculator.calculateGroupBalances(listOf(exp), emptyList())
        assertEquals(10000L, balances["userA"])
        assertEquals(-10000L, balances["userB"])
    }

    @Test
    fun testSettlementReducesBalance() {
        // Continuing from the ₹500 custom split (A is owed ₹100, B owes ₹100):
        val exp = Expense(
            id = "exp_custom",
            description = "Groceries",
            amountPaise = 50000L,
            paidByUid = "userA",
            participantIds = listOf("userA", "userB"),
            splitType = "custom",
            sharesByUid = mapOf("userA" to 40000L, "userB" to 10000L)
        )

        // B settles ₹100 (10000 paise) to A
        val settlement = Settlement(
            id = "set1",
            fromUid = "userB",
            toUid = "userA",
            amountPaise = 10000L
        )

        val balances = BalanceCalculator.calculateGroupBalances(listOf(exp), listOf(settlement))
        // Both users are settled up to 0
        assertEquals(0L, balances["userA"])
        assertEquals(0L, balances["userB"])
    }

    @Test
    fun testNoBalanceChangesFromInvalidData() {
        val invalidExpenses = listOf(
            // Zero amount
            Expense(id = "e1", description = "Zero", amountPaise = 0L, paidByUid = "alice", participantIds = listOf("alice", "bob")),
            // Negative amount
            Expense(id = "e2", description = "Negative", amountPaise = -5000L, paidByUid = "alice", participantIds = listOf("alice", "bob")),
            // Blank payer
            Expense(id = "e3", description = "Blank payer", amountPaise = 10000L, paidByUid = "", participantIds = listOf("alice", "bob")),
            // Empty participants
            Expense(id = "e4", description = "No participants", amountPaise = 10000L, paidByUid = "alice", participantIds = emptyList()),
            // Blank description
            Expense(id = "e5", description = "   ", amountPaise = 10000L, paidByUid = "alice", participantIds = listOf("alice", "bob"))
        )

        val invalidSettlements = listOf(
            // Zero amount
            Settlement(id = "s1", fromUid = "alice", toUid = "bob", amountPaise = 0L),
            // Negative amount
            Settlement(id = "s2", fromUid = "alice", toUid = "bob", amountPaise = -2000L),
            // Blank fromUid
            Settlement(id = "s3", fromUid = "", toUid = "bob", amountPaise = 2000L),
            // Blank toUid
            Settlement(id = "s4", fromUid = "alice", toUid = "", amountPaise = 2000L),
            // Same from and to
            Settlement(id = "s5", fromUid = "alice", toUid = "alice", amountPaise = 2000L)
        )

        val balances = BalanceCalculator.calculateGroupBalances(invalidExpenses, invalidSettlements)
        assertTrue(balances.isEmpty())
    }

    @Test
    fun testMalformedCustomSplitExpensesDoNotAffectBalances() {
        val malformedCustomExpenses = listOf(
            // 1. Shares do not add up to amountPaise (40000 + 5000 = 45000 != 50000)
            Expense(
                id = "m1",
                description = "Sum mismatch",
                amountPaise = 50000L,
                paidByUid = "alice",
                participantIds = listOf("alice", "bob"),
                splitType = "custom",
                sharesByUid = mapOf("alice" to 40000L, "bob" to 5000L)
            ),
            // 2. Negative share fabricating credit/debt
            Expense(
                id = "m2",
                description = "Negative share",
                amountPaise = 50000L,
                paidByUid = "alice",
                participantIds = listOf("alice", "bob"),
                splitType = "custom",
                sharesByUid = mapOf("alice" to 60000L, "bob" to -10000L)
            ),
            // 3. Key/participant mismatch (shares contains non-participant "intruder")
            Expense(
                id = "m3",
                description = "Key mismatch non-participant",
                amountPaise = 50000L,
                paidByUid = "alice",
                participantIds = listOf("alice", "bob"),
                splitType = "custom",
                sharesByUid = mapOf("alice" to 25000L, "intruder" to 25000L)
            ),
            // 4. Missing participant from custom shares
            Expense(
                id = "m4",
                description = "Missing participant key",
                amountPaise = 50000L,
                paidByUid = "alice",
                participantIds = listOf("alice", "bob"),
                splitType = "custom",
                sharesByUid = mapOf("alice" to 50000L)
            ),
            // 5. Zero-only shares
            Expense(
                id = "m5",
                description = "All zero shares",
                amountPaise = 50000L,
                paidByUid = "alice",
                participantIds = listOf("alice", "bob"),
                splitType = "custom",
                sharesByUid = mapOf("alice" to 0L, "bob" to 0L)
            ),
            // 6. Duplicate participant IDs
            Expense(
                id = "m6",
                description = "Duplicate participants",
                amountPaise = 50000L,
                paidByUid = "alice",
                participantIds = listOf("alice", "alice"),
                splitType = "custom",
                sharesByUid = mapOf("alice" to 50000L)
            ),
            // 7. Empty shares map for custom split
            Expense(
                id = "m7",
                description = "Empty shares map",
                amountPaise = 50000L,
                paidByUid = "alice",
                participantIds = listOf("alice", "bob"),
                splitType = "custom",
                sharesByUid = emptyMap()
            )
        )

        // All malformed custom expenses alone should result in empty balances
        val balancesFromMalformed = BalanceCalculator.calculateGroupBalances(malformedCustomExpenses, emptyList())
        assertTrue(balancesFromMalformed.isEmpty())

        // When mixed with a legitimate expense, only the legitimate expense affects balances
        val validExpense = Expense(
            id = "valid1",
            description = "Valid Lunch",
            amountPaise = 3000L,
            paidByUid = "alice",
            participantIds = listOf("alice", "bob"),
            splitType = "equal",
            sharesByUid = mapOf("alice" to 1500L, "bob" to 1500L)
        )

        val mixedBalances = BalanceCalculator.calculateGroupBalances(
            malformedCustomExpenses + validExpense,
            emptyList()
        )
        assertEquals(1500L, mixedBalances["alice"])
        assertEquals(-1500L, mixedBalances["bob"])
    }

    @Test
    fun testOverallSummaryCalculation() {
        // Group 1: User is owed 20000 paise (₹200)
        val g1 = mapOf("my_uid" to 20000L, "other" to -20000L)
        // Group 2: User owes 50000 paise (₹500)
        val g2 = mapOf("my_uid" to -50000L, "other" to 50000L)

        val summary = BalanceCalculator.calculateOverallSummary(listOf(g1, g2), "my_uid")
        assertEquals(20000L, summary.userOwedPaise)
        assertEquals(50000L, summary.userOwesPaise)
    }
}
