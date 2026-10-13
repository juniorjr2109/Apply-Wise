package com.webwizards.applywise.domain

/** Error raised by the data layer with a message that is safe to show to users. */
class AppException(
    val status: Int,
    override val message: String,
    val fieldErrors: Map<String, List<String>> = emptyMap()
) : Exception(message) {
    val isUnauthorized get() = status == 401
    val isNetwork get() = status == 0
}

enum class MatchType(val label: String, val short: String) {
    Strong("Strong Match", "Strong"), Good("Good Match", "Good"), Reach("Reach", "Reach");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.label == value } ?: Good
    }
}
