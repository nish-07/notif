package app.teakadai.notiftimer

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

/** Small on-device log. Saved in the app's private storage only. */
object EventLog {
    private const val PREFS = "payment_timer"
    private const val KEY_EVENTS = "events"
    private const val KEY_CAPTURE_ALL = "capture_all"
    private const val MAX_EVENTS = 300

    private val events = ArrayList<Event>()
    private var loaded = false
    private val listeners = ArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EVENTS, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) events.add(fromJson(arr.getJSONObject(i)))
        } catch (_: Exception) {
            events.clear()
        }
    }

    @Synchronized
    fun add(context: Context, e: Event) {
        ensureLoaded(context)
        events.add(e)
        while (events.size > MAX_EVENTS) events.removeAt(0)
        save(context)
        notifyChanged()
    }

    @Synchronized
    fun snapshot(context: Context): List<Event> {
        ensureLoaded(context)
        return events.toList()
    }

    @Synchronized
    fun clear(context: Context) {
        ensureLoaded(context)
        events.clear()
        save(context)
        notifyChanged()
    }

    fun captureAll(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CAPTURE_ALL, false)

    fun setCaptureAll(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CAPTURE_ALL, value).apply()
    }

    /** Call from the main thread only. */
    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun notifyChanged() {
        main.post { listeners.toList().forEach { it() } }
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        events.forEach { arr.put(toJson(it)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_EVENTS, arr.toString()).apply()
    }

    private fun toJson(e: Event): JSONObject = JSONObject().apply {
        put("kind", e.kind.name)
        put("time", e.timeMs)
        put("pkg", e.pkg)
        put("title", e.title)
        put("text", e.text)
        put("amount", e.amountPaise ?: JSONObject.NULL)
        put("dir", e.direction)
    }

    private fun fromJson(o: JSONObject): Event = Event(
        kind = Kind.valueOf(o.getString("kind")),
        timeMs = o.getLong("time"),
        pkg = o.optString("pkg"),
        title = o.optString("title"),
        text = o.optString("text"),
        amountPaise = if (o.isNull("amount")) null else o.getLong("amount"),
        direction = o.optString("dir", "?"),
    )
}
