package ir.mohitban.app.domain.model

/**
 * The four kinds of area the Department of Environment manages. The profile keeps one text,
 * the full title ("پارک ملی گلستان", "منطقه حفاظت‌شده کاغذکنان"), so older profiles that only
 * hold a bare name keep working.
 */
object AreaTypes {
    val ALL = listOf("پارک ملی", "اثر طبیعی ملی", "پناهگاه حیات وحش", "منطقه حفاظت‌شده")

    private fun norm(s: String) = s.replace('ي', 'ی').replace('ك', 'ک').replace("‌", " ").replace(Regex("\\s+"), " ").trim()

    /** Splits a stored title into (type, name); the type is empty for an old bare name. */
    fun split(title: String): Pair<String, String> {
        val t = norm(title)
        for (type in ALL) {
            val n = norm(type)
            if (t == n) return type to ""
            if (t.startsWith("$n ")) return type to t.removePrefix(n).trim()
        }
        return "" to title.trim()
    }

    fun join(type: String, name: String): String = listOf(type.trim(), name.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    /** The full title for display: "منطقه‌ی X" for an old bare name. */
    fun title(stored: String): String {
        val (type, name) = split(stored)
        return when {
            name.isBlank() && type.isBlank() -> ""
            type.isBlank() -> "منطقه‌ی $name"
            else -> join(type, name)
        }
    }
}
