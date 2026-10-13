package dev.kidyoh.liquid.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

data class DayEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean, val color: Int)

/** Today's remaining events for the Calendar widget. */
object CalendarEvents {
    /** Screenshot tests set this, since there's no calendar provider there. */
    @Volatile
    var testEvents: List<DayEvent>? = null

    fun hasPermission(context: Context) = testEvents != null ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Null when we may not read the calendar. */
    fun today(context: Context): List<DayEvent>? {
        testEvents?.let { return it }
        if (!hasPermission(context)) return null
        val now = System.currentTimeMillis()
        val end = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.timeInMillis
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
        )
        return runCatching {
            CalendarContract.Instances.query(context.contentResolver, projection, now - 12 * 3_600_000L, end)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val e = DayEvent(c.getString(0).orEmpty().ifBlank { "Event" }, c.getLong(1), c.getLong(2), c.getInt(3) == 1, c.getInt(4))
                        if (e.end > now && (e.allDay || e.begin < end)) add(e)
                    }
                }.sortedWith(compareBy({ !it.allDay }, { it.begin }))
            }.orEmpty()
        }.getOrNull()
    }

    /** "7–9:30AM", "1–6AM", or "07:00–09:30" on 24-hour phones. */
    fun range(context: Context, e: DayEvent): String {
        if (e.allDay) return "All-day"
        if (DateFormat.is24HourFormat(context)) {
            val f = { t: Long -> DateFormat.format("HH:mm", t).toString() }
            return "${f(e.begin)}–${f(e.end)}"
        }
        fun parts(t: Long): Pair<String, String> {
            val c = Calendar.getInstance().apply { timeInMillis = t }
            val h = (c.get(Calendar.HOUR) + 11) % 12 + 1
            val m = c.get(Calendar.MINUTE)
            val time = if (m == 0) "$h" else "%d:%02d".format(Locale.US, h, m)
            return time to if (c.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        }
        val (a, ap) = parts(e.begin)
        val (b, bp) = parts(e.end)
        return if (ap == bp) "$a–$b$bp" else "$a$ap–$b$bp"
    }
}
