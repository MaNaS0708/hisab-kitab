package com.example.hisabkitab.util

import com.example.hisabkitab.data.models.Expense

object SplitCalculator {
    fun calculateEqualShares(
        totalAmountPaise: Long,
        participantUids: List<String>
    ): Map<String, Long> {
        require(totalAmountPaise > 0) { "Total amount must be greater than zero." }
        require(participantUids.isNotEmpty()) { "Participant list cannot be empty." }
        require(participantUids.none { it.isBlank() }) { "Participant list cannot contain blank IDs." }

        // Sort distinct UIDs deterministically
        val sortedUids = participantUids.distinct().sorted()
        require(sortedUids.isNotEmpty()) { "Participant list cannot be empty." }

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
        sharesByUid: Map<String, Long>,
        participantUids: List<String>? = null
    ): CustomSplitValidationResult {
        if (totalAmountPaise <= 0) {
            return CustomSplitValidationResult.Invalid("Total amount must be greater than zero.")
        }
        if (sharesByUid.isEmpty()) {
            return CustomSplitValidationResult.Invalid("Select at least one participant.")
        }
        if (sharesByUid.keys.any { it.isBlank() }) {
            return CustomSplitValidationResult.Invalid("Participant ID cannot be empty.")
        }
        if (participantUids != null) {
            if (participantUids.any { it.isBlank() }) {
                return CustomSplitValidationResult.Invalid("Participant list cannot contain empty IDs.")
            }
            val distinctParticipants = participantUids.distinct()
            if (participantUids.size != distinctParticipants.size) {
                return CustomSplitValidationResult.Invalid("Participant list contains duplicate entries.")
            }
            if (sharesByUid.keys != distinctParticipants.toSet()) {
                return CustomSplitValidationResult.Invalid("Shares do not match the selected participants.")
            }
        }
        if (sharesByUid.values.any { it < 0 }) {
            return CustomSplitValidationResult.Invalid("Shares cannot be negative.")
        }
        if (sharesByUid.values.all { it == 0L }) {
            return CustomSplitValidationResult.Invalid("At least one participant must have a positive share.")
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
        return CustomSplitValidationResult.Valid
    }

    fun isValidExpense(expense: Expense): Boolean {
        if (expense.amountPaise <= 0L) return false
        if (expense.paidByUid.isBlank()) return false
        if (expense.description.isBlank()) return false
        if (expense.participantIds.isEmpty()) return false
        if (expense.participantIds.any { it.isBlank() }) return false
        if (expense.participantIds.size != expense.participantIds.distinct().size) return false

        val participantSet = expense.participantIds.toSet()

        return when (expense.splitType) {
            "custom" -> {
                if (expense.sharesByUid.isEmpty()) return false
                if (expense.sharesByUid.keys != participantSet) return false
                if (expense.sharesByUid.values.any { it < 0L }) return false
                if (expense.sharesByUid.values.all { it == 0L }) return false
                expense.sharesByUid.values.sum() == expense.amountPaise
            }
            "equal" -> {
                if (expense.sharesByUid.isNotEmpty()) {
                    if (expense.sharesByUid.keys != participantSet) return false
                    if (expense.sharesByUid.values.any { it < 0L }) return false
                    expense.sharesByUid.values.sum() == expense.amountPaise
                } else {
                    true
                }
            }
            else -> false
        }
    }
}

sealed interface CustomSplitValidationResult {
    data object Valid : CustomSplitValidationResult
    data class Invalid(val message: String) : CustomSplitValidationResult
}
