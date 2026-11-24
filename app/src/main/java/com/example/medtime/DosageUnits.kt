package com.example.medtime

// The units of a dosage, so "2" always says 2 of WHAT (for example "2 tablets")
object DosageUnits {

    // Shown in the scrolling wheel
    val units = arrayOf(
        "tablet", "capsule", "ml", "mg", "drop", "puff",
        "teaspoon", "tablespoon", "patch", "injection", "unit", "piece"
    )

    // Units that get an "s" when the amount is not 1: 2 tablets, 1 tablet
    private val plural = setOf(
        "tablet", "capsule", "drop", "puff", "teaspoon", "tablespoon",
        "patch", "injection", "unit", "piece"
    )

    // (2, "tablet") -> "2 tablets"     (1, "tablet") -> "1 tablet"     (5, "ml") -> "5 ml"
    fun format(amount: String, unit: String): String {
        val isOne = amount == "1"
        val word = if (unit in plural && !isOne) unit + "s" else unit
        return "$amount $word"
    }

    // Reads a saved dosage back into (amount, unit index). null = could not read it.
    fun parse(text: String): Pair<String, Int>? {
        val match = Regex("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([A-Za-z]*)\\s*$").find(text) ?: return null
        val amount = match.groupValues[1]
        val word = match.groupValues[2].lowercase()
        if (word.isEmpty()) return Pair(amount, 0)
        val singular = if (word.endsWith("s") && word.dropLast(1) in plural) word.dropLast(1) else word
        val index = units.indexOf(singular)
        return if (index >= 0) Pair(amount, index) else null
    }

    // Old medicines may have only a number: add "tablet(s)" so it is clear
    fun readable(saved: String): String {
        val trimmed = saved.trim()
        if (Regex("^[0-9]+(\\.[0-9]+)?$").matches(trimmed)) return format(trimmed, "tablet")
        return trimmed
    }
}