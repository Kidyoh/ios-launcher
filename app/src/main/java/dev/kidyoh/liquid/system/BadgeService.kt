package dev.kidyoh.liquid.system

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Counts unread notifications per package for the red iOS-style badges. */
object Badges {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    internal fun publish(map: Map<String, Int>) {
        _counts.value = map
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}

class BadgeService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
    override fun onListenerDisconnected() = Badges.publish(emptyMap())

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val ranking = runCatching { currentRanking }.getOrNull()
        val r = Ranking()
        val counts = active
            .filter { sbn ->
                val n = sbn.notification
                sbn.isClearable && !sbn.isOngoing && (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                    (ranking == null || !ranking.getRanking(sbn.key, r) || r.canShowBadge())
            }
            .groupBy { it.packageName }
            .mapValues { (_, list) -> list.sumOf { it.notification.number.takeIf { n -> n > 0 } ?: 1 } }
        Badges.publish(counts)
    }
}
