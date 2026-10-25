package ir.mohitban.app.core.text

object Validators {

    /** Iranian national ID (کد ملی) with check digit. Returns null when valid. */
    fun nationalId(raw: String): String? {
        val code = raw.digitsOnly()
        if (code.isEmpty()) return "کد ملی را وارد کنید"
        if (code.length != 10) return "کد ملی باید ۱۰ رقم باشد"
        if (code.all { it == code[0] }) return "کد ملی معتبر نیست"
        val sum = (0 until 9).sumOf { (code[it] - '0') * (10 - it) }
        val r = sum % 11
        val check = code[9] - '0'
        val ok = if (r < 2) check == r else check == 11 - r
        return if (ok) null else "کد ملی معتبر نیست"
    }

    /** Iranian mobile number: 09xxxxxxxxx */
    fun mobile(raw: String): String? {
        val m = raw.digitsOnly()
        return when {
            m.isEmpty() -> "شماره همراه را وارد کنید"
            !Regex("^09\\d{9}$").matches(m) -> "شماره همراه باید ۱۱ رقم و با ۰۹ شروع شود"
            else -> null
        }
    }

    fun required(value: String, label: String): String? =
        if (value.isBlank()) "$label را وارد کنید" else null

    /** Persian letters, spaces and ZWNJ only (for names). */
    fun personName(value: String, label: String): String? {
        if (value.isBlank()) return "$label را وارد کنید"
        if (value.trim().length < 2) return "$label کوتاه است"
        val ok = value.all { it.isLetter() || it == ' ' || it == '‌' || it == '-' }
        return if (ok) null else "$label فقط باید شامل حروف باشد"
    }
}
