package com.example.hisabkitab.util

import com.example.hisabkitab.data.models.Expense
import com.example.hisabkitab.data.models.Settlement

data class UserBalanceSummary(
    val userOwedPaise: Long, // Total amount user is owed across all groups (positive)
    val userOwesPaise: Long  // Total amount user owes across all groups (positive)
)

object BalanceCalculator {

    /**
     * Calculates net balance for each user in a single group based on expenses and settlements.
     * Returns a map of UID -> net balance in paise.
     * Positive value = user is owed money by the group.
     * Negative value = user owes money to the group.
     */
    fun calculateGroupBalances(
        expenses: List<Expense>,
        settlements: List<Settlement>
    ): Map<String, Long> {
        val balances = mutableMapOf<String, Long>()

        for (expense in expenses) {
            if (!SplitCalculator.isValidExpense(expense)) continue

            // Participants get debited their shares
            val effectiveShares = if (expense.splitType == "custom") {
                expense.sharesByUid
            } else if (expense.sharesByUid.isNotEmpty()) {
                expense.sharesByUid
            } else {
                SplitCalculator.calculateEqualShares(expense.amountPaise, expense.participantIds)
            }

            // Payer gets credited full amount
            val currentPayerBalance = balances.getOrDefault(expense.paidByUid, 0L)
            balances[expense.paidByUid] = currentPayerBalance + expense.amountPaise

            for ((uid, sharePaise) in effectiveShares) {
                val currentShareBalance = balances.getOrDefault(uid, 0L)
                balances[uid] = currentShareBalance - sharePaise
            }
        }

        for (settlement in settlements) {
            if (settlement.amountPaise <= 0) continue
            if (settlement.fromUid.isBlank() || settlement.toUid.isBlank()) continue
            if (settlement.fromUid == settlement.toUid) continue

            // fromUid paid money -> fromUid gets credited (+), reducing their debt or increasing what they're owed
            val currentFromBalance = balances.getOrDefault(settlement.fromUid, 0L)
            balances[settlement.fromUid] = currentFromBalance + settlement.amountPaise

            // toUid received money -> toUid gets debited (-), reducing what they are owed
            val currentToBalance = balances.getOrDefault(settlement.toUid, 0L)
            balances[settlement.toUid] = currentToBalance - settlement.amountPaise
        }

        return balances
    }

    /**
     * Given per-group balances maps for all groups the user belongs to,
     * calculates overall "You are owed" and "You owe" totals for [currentUid].
     */
    fun calculateOverallSummary(
        groupBalances: List<Map<String, Long>>,
        currentUid: String
    ): UserBalanceSummary {
        var totalOwed = 0L
        var totalOwes = 0L

        for (groupBalanceMap in groupBalances) {
            val net = groupBalanceMap.getOrDefault(currentUid, 0L)
            if (net > 0) {
                totalOwed += net
            } else if (net < 0) {
                totalOwes += if (net == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(net)
            }
        }

        return UserBalanceSummary(
            userOwedPaise = totalOwed,
            userOwesPaise = totalOwes
        )
    }
}
