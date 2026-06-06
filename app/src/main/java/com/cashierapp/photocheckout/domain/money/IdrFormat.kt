package com.cashierapp.photocheckout.domain.money

public object IdrFormat {
    private val validInput = Regex("""^(IDR\s*)?([0-9]{1,3}(\.[0-9]{3})*|[0-9]+)$""")

    public fun format(valueMinor: Long): String {
        require(valueMinor >= 0) { "IDR amount must not be negative." }

        return valueMinor
            .toString()
            .reversed()
            .chunked(3)
            .joinToString(separator = ".")
            .reversed()
    }

    public fun parse(input: String): Long {
        val normalized = input.trim()
        require(validInput.matches(normalized)) { "Invalid IDR amount." }

        return normalized
            .removePrefix("IDR")
            .trim()
            .replace(".", "")
            .toLong()
    }
}
