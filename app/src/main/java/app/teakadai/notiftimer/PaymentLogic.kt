package app.teakadai.notiftimer

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

enum class Kind { PAY_TAPPED, SOUNDBOX, NOTIFICATION }

data class Event(
    val kind: Kind,
    val timeMs: Long,
    val pkg: String = "",
    val title: String = "",
    val text: String = "",
    val amountPaise: Long? = null,
    val direction: String = "?",
)

/** Pure logic with no Android dependencies, so it can be tested anywhere. */
object PaymentLogic {

    // Matches rupee amounts like Rs.1.00, Rs 45, INR 1,250.50 and the rupee sign.
    private val amountRegex =
        Regex("""(?<![A-Za-z])(?:₹|Rs\.?|INR)\s?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    private val moneyWords =
        Regex("""\b(credited|received|deposited|debited|paid|payment|upi|sent)\b""", RegexOption.IGNORE_CASE)

    private val inWords = listOf("received", "credited", "deposited", "paid you", "sent you", "added to")
    private val outWords = listOf("debited", "paid to", "sent to", "you paid", "you sent", "payment successful")

    /** First rupee amount in the text, in paise, or null. */
    fun findAmountPaise(text: String): Long? {
        val raw = amountRegex.find(text)?.groupValues?.get(1)?.replace(",", "") ?: return null
        val parts = raw.split(".")
        val rupees = parts[0].toLongOrNull() ?: return null
        val paise = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L else 0L
        return rupees * 100 + paise
    }

    /** "IN" if the text reads like money received, "OUT" if money sent, else "?". */
    fun direction(text: String): String {
        val t = text.lowercase(Locale.ROOT)
        if (inWords.any { t.contains(it) }) return "IN"
        if (outWords.any { t.contains(it) }) return "OUT"
        return "?"
    }

    fun isPaymentLike(title: String, text: String): Boolean {
        val all = "$title $text"
        return findAmountPaise(all) != null || moneyWords.containsMatchIn(all)
    }

    fun formatAmount(paise: Long): String =
        "₹" + (paise / 100) + "." + (paise % 100).toString().padStart(2, '0')

    fun clock(ms: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val f = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        f.timeZone = tz
        return f.format(Date(ms))
    }

    /** "+3.2s" style offset between two moments. */
    fun offset(fromMs: Long, toMs: Long): String =
        "%+.1fs".format(Locale.US, (toMs - fromMs) / 1000.0)

    /** The short summary shown at the top of the screen. */
    fun summarize(events: List<Event>, tz: TimeZone = TimeZone.getDefault()): List<String> {
        val start = events.filter { it.kind == Kind.PAY_TAPPED }.maxByOrNull { it.timeMs }
            ?: return listOf("Tap \"Pay tapped\" at the moment you press Pay on the other phone.")

        val after = events.filter { it.timeMs >= start.timeMs }
        val lines = mutableListOf("Pay tapped at ${clock(start.timeMs, tz)}")

        val moneyIn = after
            .filter { it.kind == Kind.NOTIFICATION && it.amountPaise != null && it.direction != "OUT" }
            .sortedBy { it.timeMs }
        if (moneyIn.isEmpty()) {
            lines += "Waiting for a money-in notification..."
        } else {
            for (e in moneyIn.take(4)) {
                lines += "Money-in notification ${offset(start.timeMs, e.timeMs)}  ${e.pkg}"
            }
        }

        val sound = after.filter { it.kind == Kind.SOUNDBOX }.minByOrNull { it.timeMs }
        if (sound != null) lines += "Soundbox spoke ${offset(start.timeMs, sound.timeMs)}"

        if (moneyIn.isNotEmpty() && sound != null) {
            val d = (moneyIn.first().timeMs - sound.timeMs) / 1000.0
            lines += when {
                abs(d) < 0.05 -> "The notification and the soundbox came together"
                d > 0 -> "The first notification came ${"%.1f".format(Locale.US, d)}s AFTER the soundbox"
                else -> "The first notification came ${"%.1f".format(Locale.US, -d)}s BEFORE the soundbox"
            }
        }
        return lines
    }
}
