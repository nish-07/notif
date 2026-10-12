package app.teakadai.notiftimer

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var summaryView: TextView
    private lateinit var logView: TextView
    private val listener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Payment timer"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Install this on the phone that RECEIVES the money. Pay Rs 1 from another phone. " +
                "This app has no internet permission, so nothing leaves your phone."
            textSize = 14f
            setPadding(0, dp(4), 0, dp(12))
        })

        statusView = TextView(this).apply { textSize = 16f; setTypeface(typeface, Typeface.BOLD) }
        root.addView(statusView)
        root.addView(button("Open notification access settings") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("Pay tapped") { mark(Kind.PAY_TAPPED) }, weight())
        row.addView(button("Soundbox spoke") { mark(Kind.SOUNDBOX) }, weight())
        row.addView(button("Clear") { EventLog.clear(applicationContext) }, weight())
        root.addView(row)

        root.addView(CheckBox(this).apply {
            text = "Also record non-payment notifications (private, only for debugging)"
            isChecked = EventLog.captureAll(applicationContext)
            setOnCheckedChangeListener { _, checked -> EventLog.setCaptureAll(applicationContext, checked) }
        })

        summaryView = TextView(this).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(summaryView)

        logView = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        root.addView(logView)

        root.addView(button("Copy log") { copyLog() })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        EventLog.addListener(listener)
        render()
    }

    override fun onPause() {
        EventLog.removeListener(listener)
        super.onPause()
    }

    private fun mark(kind: Kind) {
        EventLog.add(applicationContext, Event(kind, System.currentTimeMillis()))
    }

    private fun accessEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == packageName }
    }

    private fun render() {
        statusView.text = if (accessEnabled()) {
            "Notification access: ON (listening)"
        } else {
            "Notification access: OFF. Open the settings below and switch it on."
        }
        val all = EventLog.snapshot(applicationContext)
        summaryView.text = PaymentLogic.summarize(all).joinToString("\n")
        logView.text = logText(all)
    }

    private fun logText(all: List<Event>): String {
        val start = all.filter { it.kind == Kind.PAY_TAPPED }.maxByOrNull { it.timeMs }?.timeMs
        return all.sortedByDescending { it.timeMs }.joinToString("\n\n") { e ->
            val rel = if (start != null && e.timeMs >= start) "  " + PaymentLogic.offset(start, e.timeMs) else ""
            val head = PaymentLogic.clock(e.timeMs) + rel
            when (e.kind) {
                Kind.PAY_TAPPED -> "$head  >> PAY TAPPED"
                Kind.SOUNDBOX -> "$head  >> SOUNDBOX SPOKE"
                Kind.NOTIFICATION -> {
                    val amount = e.amountPaise?.let { "  " + PaymentLogic.formatAmount(it) + " " + e.direction } ?: ""
                    "$head$amount\n${e.pkg}\n${e.title}: ${e.text}"
                }
            }
        }
    }

    private fun copyLog() {
        val all = EventLog.snapshot(applicationContext)
        val text = PaymentLogic.summarize(all).joinToString("\n") + "\n\n" + logText(all)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("payment timer log", text))
        Toast.makeText(this, "Copied. Check it for private details before sharing.", Toast.LENGTH_LONG).show()
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

}
