package dev.zeroinput.userdata

data class PersonalExpression(
    val id: String,
    val value: String,
    val name: String,
    val keywords: String,
    val group: String,
)

data class PersonalExpressions(
    val custom: List<PersonalExpression> = emptyList(),
    val favorites: Set<String> = emptySet(),
)

data class PersonalExpressionSnapshot(val data: PersonalExpressions, val revision: Long)

enum class ExpressionFailure { INVALID, DUPLICATE, CAPACITY, CORRUPT, CANCELLED, STORAGE }

class ExpressionException(val failure: ExpressionFailure) : Exception(failure.name)

object ExpressionLimits {
    const val TEXT = 128
    const val NAME = 48
    const val KEYWORDS = 256
    const val GROUP = 32
    const val CUSTOM = 256
    const val FAVORITES = 256
    const val BYTES = 512 * 1024

    fun validText(value: String, max: Int = TEXT, allowEmpty: Boolean = false): Boolean {
        if (value.length > max || !allowEmpty && value.isBlank()) return false
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char.isISOControl() || char in '\u202a'..'\u202e' || char in '\u2066'..'\u2069' ||
                char == '\u2028' || char == '\u2029') return false
            if (char.isHighSurrogate()) {
                if (index + 1 == value.length || !value[index + 1].isLowSurrogate()) return false
                index++
            } else if (char.isLowSurrogate()) return false
            index++
        }
        return true
    }

    fun validate(entry: PersonalExpression) {
        if (!validText(entry.value) || !validText(entry.name, NAME) ||
            !validText(entry.keywords, KEYWORDS, allowEmpty = true) ||
            entry.group.length !in 1..GROUP || entry.group.any { it !in 'a'..'z' && it != '_' } ||
            entry.id.length != 36 || entry.id.any { it !in '0'..'9' && it !in 'a'..'f' && it != '-' }) {
            throw ExpressionException(ExpressionFailure.INVALID)
        }
    }
}
