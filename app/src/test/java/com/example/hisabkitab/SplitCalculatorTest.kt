package com.example.hisabkitab

import com.example.hisabkitab.util.CustomSplitValidationResult
import com.example.hisabkitab.util.SplitCalculator
import org.junit.Assert.assertEquals
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
    fun testEqualSplitWithRemainder() {
        // ₹10 = 1000 paise split between 3 users -> 334, 333, 333 paise (sum = 1000)
        val uids = listOf("userA", "userB", "userC")
        val shares = SplitCalculator.calculateEqualShares(1000L, uids)

        // Deterministically sorted: userA, userB, userC
        assertEquals(334L, shares["userA"])
        assertEquals(333L, shares["userB"])
        assertEquals(333L, shares["userC"])
        assertEquals(1000L, shares.values.sum())
    }

    @Test
    fun testCustomSplitExample500Equals400Plus100() {
        // ₹500 = 50000 paise total. My share = ₹400 (40000 paise), friend's share = ₹100 (10000 paise)
        val totalPaise = 50000L
        val shares = mapOf(
            "my_uid" to 40000L,
            "friend_uid" to 10000L
        )

        val result = SplitCalculator.validateCustomShares(totalPaise, shares)
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
}
