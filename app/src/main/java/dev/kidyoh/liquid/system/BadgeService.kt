package dev.kidyoh.liquid.system

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One notification as the iOS-style Notification Center shows it. */
data class NotificationItem(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val time: Long,
    val largeIcon: Bitmap?,
    val intent: PendingIntent?,
    val clearable: Boolean,
)

/** Unread counts for the red badges, plus the notifications themselves. */
object Badges {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
    /** Newest first. */
    val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

    internal fun publish(map: Map<String, Int>, items: List<NotificationItem> = _items.value) {
        _counts.value = map
        _items.value = items
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Swipe-to-clear from Notification Center. */
    fun dismiss(key: String) {
        _items.value = _items.value.filterNot { it.key == key }
        runCatching { BadgeService.instance?.cancelNotification(key) }
    }

    fun dismissAll() {
        val keys = _items.value.filter { it.clearable }.map { it.key }
        _items.value = _items.value.filterNot { it.clearable }
        runCatching { BadgeService.instance?.cancelNotifications(keys.toTypedArray()) }
    }
}

class BadgeService : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    override fun onListenerDisconnected() {
        instance = null
        Badges.publish(emptyMap(), emptyList())
    }

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val ranking = runCatching { currentRanking }.getOrNull()
        val r = Ranking()
        val visible = active.filter { sbn ->
            val n = sbn.notification
            (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0 && sbn.packageName != packageName
        }
        val counts = visible
            .filter { sbn -> sbn.isClearable && !sbn.isOngoing && (ranking == null || !ranking.getRanking(sbn.key, r) || r.canShowBadge()) }
            .groupBy { it.packageName }
            .mapValues { (_, list) -> list.sumOf { it.notification.number.takeIf { n -> n > 0 } ?: 1 } }
        val items = visible
            .sortedByDescending { it.postTime }
            .take(60)
            .mapNotNull { sbn ->
                val n = sbn.notification
                val extras = n.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) return@mapNotNull null
                val icon = runCatching { n.getLargeIcon()?.loadDrawable(this)?.toBitmap(96, 96) }.getOrNull()
                NotificationItem(sbn.key, sbn.packageName, title, text, sbn.postTime, icon, n.contentIntent, sbn.isClearable)
            }
        Badges.publish(counts, items)
    }

    companion object {
        @Volatile
        internal var instance: BadgeService? = null
    }
}
