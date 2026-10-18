package com.example.hisabkitab

import com.example.hisabkitab.data.models.Expense
import com.example.hisabkitab.util.CustomSplitValidationResult
import com.example.hisabkitab.util.SplitCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitCalculatorTest {

    @Test
    fun testEqualSplitExactDivision() {
        // ₹300 = 30000 paise split between 3 users -> 10000 paise each
        val uids = listOf("user1", "user2", "user3")
        val shares = SplitCalculator.calculateEqualShares(30000L, uids)

        assertEquals(10000L, shares["user1"])
        assertEquals(10000L, shares["user2"])
        assertEquals(10000L, shares["user3"])
        assertEquals(30000L, shares.values.sum())
    }

    @Test
    fun testEqualSplitWithRemainderAndPaiseRounding() {
        // ₹10 = 1000 paise split between 3 users -> 334, 333, 333 paise (sum = 1000)
        val uids = listOf("userA", "userB", "userC")
        val shares = SplitCalculator.calculateEqualShares(1000L, uids)

        assertEquals(334L, shares["userA"])
        assertEquals(333L, shares["userB"])
        assertEquals(333L, shares["userC"])
        assertEquals(1000L, shares.values.sum())

        // ₹10.01 = 1001 paise split between 3 users -> 334, 334, 333 paise (sum = 1001)
        val shares1001 = SplitCalculator.calculateEqualShares(1001L, uids)
        assertEquals(334L, shares1001["userA"])
        assertEquals(334L, shares1001["userB"])
        assertEquals(333L, shares1001["userC"])
        assertEquals(1001L, shares1001.values.sum())

        // ₹100 = 10000 paise split between 6 users -> 1667*4 + 1666*2 = 10000
        val sixUsers = listOf("u1", "u2", "u3", "u4", "u5", "u6")
        val shares10000 = SplitCalculator.calculateEqualShares(10000L, sixUsers)
        assertEquals(1667L, shares10000["u1"])
        assertEquals(1667L, shares10000["u2"])
        assertEquals(1667L, shares10000["u3"])
        assertEquals(1667L, shares10000["u4"])
        assertEquals(1666L, shares10000["u5"])
        assertEquals(1666L, shares10000["u6"])
        assertEquals(10000L, shares10000.values.sum())
    }

    @Test
    fun testEqualSplitDuplicateParticipantsNormalized() {
        // Duplicate participant IDs normalized to distinct entries
        val uidsWithDuplicates = listOf("userA", "userB", "userA", "userC", "userB")
        val shares = SplitCalculator.calculateEqualShares(3000L, uidsWithDuplicates)

        assertEquals(3, shares.size)
        assertEquals(1000L, shares["userA"])
        assertEquals(1000L, shares["userB"])
        assertEquals(1000L, shares["userC"])
        assertEquals(3000L, shares.values.sum())
    }

    @Test
    fun testCustomSplitExample500Equals400Plus100() {
        // ₹500 = 50000 paise total. My share = ₹400 (40000 paise), friend's share = ₹100 (10000 paise)
        val totalPaise = 50000L
        val shares = mapOf(
            "my_uid" to 40000L,
            "friend_uid" to 10000L
        )

        val result = SplitCalculator.validateCustomShares(totalPaise, shares, listOf("my_uid", "friend_uid"))
        assertTrue(result is CustomSplitValidationResult.Valid)
        assertEquals(totalPaise, shares.values.sum())
    }

    @Test
    fun testCustomSplitValidationErrors() {
        val totalPaise = 50000L

        // Mismatched sum (40000 + 5000 = 45000 != 50000)
        val invalidSum = mapOf("user1" to 40000L, "user2" to 5000L)
        assertTrue(SplitCalculator.validateCustomShares(totalPaise, invalidSum) is CustomSplitValidationResult.Invalid)

        // Negative share
        val negativeShare = mapOf("user1" to 60000L, "user2" to -10000L)
        assertTrue(SplitCalculator.validateCustomShares(totalPaise, negativeShare) is CustomSplitValidationResult.Invalid)

        // All zero shares
        val zeroShares = mapOf("user1" to 0L, "user2" to 0L)
        assertTrue(SplitCalculator.validateCustomShares(totalPaise, zeroShares) is CustomSplitValidationResult.Invalid)
    }

    @Test
    fun testCustomSplitDuplicateParticipantsRejected() {
        val shares = mapOf("user1" to 30000L, "user2" to 20000L)
        val duplicateParticipants = listOf("user1", "user2", "user1")
        val result = SplitCalculator.validateCustomShares(50000L, shares, duplicateParticipants)
        assertTrue(result is CustomSplitValidationResult.Invalid)
        assertEquals("Participant list contains duplicate entries.", (result as CustomSplitValidationResult.Invalid).message)
    }

    @Test
    fun testCustomSplitKeyParticipantMismatch() {
        val totalPaise = 50000L
        val participants = listOf("user1", "user2")

        // Shares map has different keys
        val mismatchKeys = mapOf("user1" to 30000L, "user3" to 20000L)
        val result1 = SplitCalculator.validateCustomShares(totalPaise, mismatchKeys, participants)
        assertTrue(result1 is CustomSplitValidationResult.Invalid)
        assertEquals("Shares do not match the selected participants.", (result1 as CustomSplitValidationResult.Invalid).message)

        // Shares map has extra key
        val extraKey = mapOf("user1" to 30000L, "user2" to 20000L, "user3" to 0L)
        val result2 = SplitCalculator.validateCustomShares(50000L, extraKey, participants)
        assertTrue(result2 is CustomSplitValidationResult.Invalid)
        assertEquals("Shares do not match the selected participants.", (result2 as CustomSplitValidationResult.Invalid).message)

        // Shares map missing a participant
        val missingKey = mapOf("user1" to 50000L)
        val result3 = SplitCalculator.validateCustomShares(totalPaise, missingKey, participants)
        assertTrue(result3 is CustomSplitValidationResult.Invalid)
        assertEquals("Shares do not match the selected participants.", (result3 as CustomSplitValidationResult.Invalid).message)
    }

    @Test
    fun testCustomSplitInvalidEmptyKeys() {
        val totalPaise = 50000L

        // Empty string key in shares map
        val emptyKeyShare = mapOf("" to 50000L)
        val result1 = SplitCalculator.validateCustomShares(totalPaise, emptyKeyShare)
        assertTrue(result1 is CustomSplitValidationResult.Invalid)
        assertEquals("Participant ID cannot be empty.", (result1 as CustomSplitValidationResult.Invalid).message)

        // Blank string in participant list
        val validShares = mapOf("user1" to 50000L)
        val blankParticipantList = listOf("user1", " ")
        val result2 = SplitCalculator.validateCustomShares(totalPaise, validShares, blankParticipantList)
        assertTrue(result2 is CustomSplitValidationResult.Invalid)
        assertEquals("Participant list cannot contain empty IDs.", (result2 as CustomSplitValidationResult.Invalid).message)
    }

    @Test
    fun testIsValidExpense() {
        val validEqual = Expense(
            id = "e1",
            description = "Lunch",
            amountPaise = 3000L,
            paidByUid = "u1",
            participantIds = listOf("u1", "u2", "u3"),
            splitType = "equal",
            sharesByUid = mapOf("u1" to 1000L, "u2" to 1000L, "u3" to 1000L)
        )
        assertTrue(SplitCalculator.isValidExpense(validEqual))

        val validCustom = Expense(
            id = "e2",
            description = "Dinner",
            amountPaise = 5000L,
            paidByUid = "u1",
            participantIds = listOf("u1", "u2"),
            splitType = "custom",
            sharesByUid = mapOf("u1" to 3000L, "u2" to 2000L)
        )
        assertTrue(SplitCalculator.isValidExpense(validCustom))

        // Reject duplicate participantIds
        val duplicateParticipants = validCustom.copy(participantIds = listOf("u1", "u1"))
        assertFalse(SplitCalculator.isValidExpense(duplicateParticipants))

        // Reject key/participant mismatch in custom shares
        val keyMismatch = validCustom.copy(sharesByUid = mapOf("u1" to 3000L, "u3" to 2000L))
        assertFalse(SplitCalculator.isValidExpense(keyMismatch))

        // Reject invalid empty keys
        val emptyKey = validCustom.copy(participantIds = listOf("u1", ""), sharesByUid = mapOf("u1" to 5000L, "" to 0L))
        assertFalse(SplitCalculator.isValidExpense(emptyKey))

        // Reject zero-only shares
        val zeroOnlyShares = validCustom.copy(amountPaise = 0L, sharesByUid = mapOf("u1" to 0L, "u2" to 0L))
        assertFalse(SplitCalculator.isValidExpense(zeroOnlyShares))

        // Reject sum mismatch in custom split
        val sumMismatch = validCustom.copy(sharesByUid = mapOf("u1" to 3000L, "u2" to 1000L))
        assertFalse(SplitCalculator.isValidExpense(sumMismatch))

        // Reject negative share
        val negativeShare = validCustom.copy(sharesByUid = mapOf("u1" to 6000L, "u2" to -1000L))
        assertFalse(SplitCalculator.isValidExpense(negativeShare))
    }
}
