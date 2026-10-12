package app.teakadai.notiftimer

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads notifications as they arrive. By default it keeps only payment-like ones
 * (an amount, or words like "received" / "credited") and ignores everything else.
 */
class PaymentWatcher : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        val extras = n.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val all = "$title $text"
        val paymentLike = PaymentLogic.isPaymentLike(title, text)
        if (!paymentLike && !EventLog.captureAll(applicationContext)) return

        EventLog.add(
            applicationContext,
            Event(
                kind = Kind.NOTIFICATION,
                timeMs = sbn.postTime,
                pkg = sbn.packageName,
                title = title,
                text = text,
                amountPaise = PaymentLogic.findAmountPaise(all),
                direction = PaymentLogic.direction(all),
            ),
        )
    }
}
