package com.example.hisabkitab.util

import java.util.Locale

object CurrencyUtil {
    fun formatPaiseToInr(paise: Long): String {
        val absPaise = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(paise)
        val rupees = absPaise / 100
        val remainingPaise = absPaise % 100
        val formatted = String.format(Locale.US, "₹%d.%02d", rupees, remainingPaise)
        return if (paise < 0) "-$formatted" else formatted
    }

    fun parseInrToPaise(input: String): Long? {
        val clean = input.trim().replace("₹", "").trim()
        if (clean.isBlank()) return null
        val parts = clean.split(".")
        if (parts.size > 2) return null

        val rupeesStr = parts[0]
        if (rupeesStr.isEmpty() || !rupeesStr.all { it.isDigit() }) return null
        val rupees = rupeesStr.toLongOrNull() ?: return null

        val paiseStr = if (parts.size == 2) parts[1] else ""
        if (!paiseStr.all { it.isDigit() }) return null

        val paise = when (paiseStr.length) {
            0 -> 0L
            1 -> (paiseStr.toLongOrNull() ?: return null) * 10
            2 -> paiseStr.toLongOrNull() ?: return null
            else -> return null // More than 2 decimal places not allowed
        }

        return (rupees * 100) + paise
    }
}
