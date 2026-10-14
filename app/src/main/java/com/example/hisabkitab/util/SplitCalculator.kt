package com.example.hisabkitab.util

object SplitCalculator {
    fun calculateEqualShares(
        totalAmountPaise: Long,
        participantUids: List<String>
    ): Map<String, Long> {
        require(totalAmountPaise > 0) { "Total amount must be greater than zero." }
        require(participantUids.isNotEmpty()) { "Participant list cannot be empty." }

        // Sort UIDs deterministically
        val sortedUids = participantUids.distinct().sorted()
        val count = sortedUids.size
        val baseShare = totalAmountPaise / count
        val remainder = (totalAmountPaise % count).toInt()

        val shares = mutableMapOf<String, Long>()
        sortedUids.forEachIndexed { index, uid ->
            val extra = if (index < remainder) 1L else 0L
            shares[uid] = baseShare + extra
        }

        check(shares.values.sum() == totalAmountPaise) { "Shares sum must equal total amount" }
        return shares
    }

    fun validateCustomShares(
        totalAmountPaise: Long,
        sharesByUid: Map<String, Long>
    ): CustomSplitValidationResult {
        if (totalAmountPaise <= 0) {
            return CustomSplitValidationResult.Invalid("Total amount must be greater than zero.")
        }
        if (sharesByUid.isEmpty()) {
            return CustomSplitValidationResult.Invalid("Select at least one participant.")
        }
        if (sharesByUid.values.any { it < 0 }) {
            return CustomSplitValidationResult.Invalid("Shares cannot be negative.")
        }
        val currentSum = sharesByUid.values.sum()
        if (currentSum != totalAmountPaise) {
            val diff = totalAmountPaise - currentSum
            val diffFormatted = CurrencyUtil.formatPaiseToInr(Math.abs(diff))
            return if (diff > 0) {
                CustomSplitValidationResult.Invalid("Sum of shares is short by $diffFormatted.")
            } else {
                CustomSplitValidationResult.Invalid("Sum of shares exceeds total by $diffFormatted.")
            }
        }
        if (sharesByUid.values.all { it == 0L }) {
            return CustomSplitValidationResult.Invalid("At least one participant must have a positive share.")
        }
        return CustomSplitValidationResult.Valid
    }
}

sealed interface CustomSplitValidationResult {
    data object Valid : CustomSplitValidationResult
    data class Invalid(val message: String) : CustomSplitValidationResult
}
