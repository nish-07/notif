import app.teakadai.notiftimer.Event
import app.teakadai.notiftimer.Kind
import app.teakadai.notiftimer.PaymentLogic
import java.util.TimeZone

var failures = 0
var checks = 0

fun <T> check(name: String, actual: T, expected: T) {
    checks++
    if (actual != expected) {
        failures++
        println("FAIL  $name\n   expected: $expected\n   actual:   $actual")
    }
}

fun main() {
    // --- amounts -----------------------------------------------------------
    val amounts = listOf(
        "₹1" to 100L,
        "₹ 1" to 100L,
        "Received Rs.1.00 from RAJ" to 100L,
        "Rs. 45 credited" to 4500L,
        "INR 1,250.50 received" to 125050L,
        "A/c credited Rs.100.37 on 06-Oct" to 10037L,
        "Rs.1,25,000.5 deposited" to 12500050L,
        "Payment of ₹0.5 received" to 50L,
        "₹45 received. Balance ₹1,200" to 4500L,
        "Rs 5" to 500L,
        "Cars 5 available" to null,
        "Dears 12 left" to null,
        "No money words here" to null,
        "Rs.1. Done" to 100L,
    )
    for ((text, expected) in amounts) check("amount: $text", PaymentLogic.findAmountPaise(text), expected)

    // --- direction ---------------------------------------------------------
    val directions = listOf(
        "Payment received ₹1 from RAJ KUMAR" to "IN",
        "Received ₹1 from Raj" to "IN",
        "Raj paid you ₹1" to "IN",
        "Rs.1.00 credited to A/c XX1234" to "IN",
        "Raj sent you ₹5" to "IN",
        "You paid ₹1 to Shop" to "OUT",
        "Rs.1.00 debited from A/c XX1234" to "OUT",
        "Payment successful ₹1" to "OUT",
        "Your UPI ₹1" to "?",
        "Hello there" to "?",
    )
    for ((text, expected) in directions) check("direction: $text", PaymentLogic.direction(text), expected)

    // --- payment-like filter -------------------------------------------------
    check("payment-like amount", PaymentLogic.isPaymentLike("Paytm", "₹1 received"), true)
    check("payment-like words", PaymentLogic.isPaymentLike("UPI", "Money credited"), true)
    check("not payment-like", PaymentLogic.isPaymentLike("WhatsApp", "Meet at 5? Bring chai"), false)
    check("not payment-like 2", PaymentLogic.isPaymentLike("Mom", "Come home soon"), false)

    // --- formatting ----------------------------------------------------------
    check("format amount", PaymentLogic.formatAmount(100), "₹1.00")
    check("format amount 2", PaymentLogic.formatAmount(10037), "₹100.37")
    check("format amount 3", PaymentLogic.formatAmount(5), "₹0.05")
    val utc = TimeZone.getTimeZone("UTC")
    val ist = TimeZone.getTimeZone("Asia/Kolkata")
    check("clock utc", PaymentLogic.clock(0L, utc), "00:00:00.000")
    check("clock ist", PaymentLogic.clock(0L, ist), "05:30:00.000")
    check("clock millis", PaymentLogic.clock(1_234L, utc), "00:00:01.234")
    check("offset +", PaymentLogic.offset(1000, 4200), "+3.2s")
    check("offset -", PaymentLogic.offset(4200, 1000), "-3.2s")

    // --- summary -------------------------------------------------------------
    check("summary needs marker", PaymentLogic.summarize(emptyList(), utc).size, 1)

    val t0 = 100_000L
    val pay = Event(Kind.PAY_TAPPED, t0)
    val phonepe = Event(Kind.NOTIFICATION, t0 + 4_100, pkg = "com.phonepe.business", amountPaise = 100, direction = "IN")
    val sms = Event(Kind.NOTIFICATION, t0 + 9_800, pkg = "com.google.android.apps.messaging", amountPaise = 100, direction = "IN")
    val debit = Event(Kind.NOTIFICATION, t0 + 1_000, pkg = "some.bank", amountPaise = 100, direction = "OUT")
    val sound = Event(Kind.SOUNDBOX, t0 + 3_600)

    val s1 = PaymentLogic.summarize(listOf(pay), utc)
    check("waiting line", s1.last(), "Waiting for a money-in notification...")

    val s2 = PaymentLogic.summarize(listOf(pay, phonepe, sms, debit, sound), utc)
    check("pay line", s2[0], "Pay tapped at 00:01:40.000")
    check("first notification", s2[1], "Money-in notification +4.1s  com.phonepe.business")
    check("second notification (sms)", s2[2], "Money-in notification +9.8s  com.google.android.apps.messaging")
    check("soundbox line", s2[3], "Soundbox spoke +3.6s")
    check("comparison after", s2[4], "The first notification came 0.5s AFTER the soundbox")
    check("debit ignored", s2.any { it.contains("some.bank") }, false)

    val early = Event(Kind.NOTIFICATION, t0 + 2_000, pkg = "x", amountPaise = 100, direction = "?")
    val s3 = PaymentLogic.summarize(listOf(pay, early, sound), utc)
    check("comparison before", s3.last(), "The first notification came 1.6s BEFORE the soundbox")

    val together = Event(Kind.NOTIFICATION, t0 + 3_600, pkg = "x", amountPaise = 100, direction = "IN")
    check("together", PaymentLogic.summarize(listOf(pay, together, sound), utc).last(), "The notification and the soundbox came together")

    // A notification from BEFORE the latest tap must not count.
    val old = Event(Kind.NOTIFICATION, t0 - 5_000, pkg = "old", amountPaise = 100, direction = "IN")
    check("old ignored", PaymentLogic.summarize(listOf(old, pay), utc).any { it.contains("old") }, false)

    // Newest "Pay tapped" wins.
    val pay2 = Event(Kind.PAY_TAPPED, t0 + 20_000)
    check("latest marker", PaymentLogic.summarize(listOf(pay, pay2), utc)[0], "Pay tapped at 00:02:00.000")

    println("$checks checks, $failures failed")
    if (failures > 0) kotlin.system.exitProcess(1)
}
