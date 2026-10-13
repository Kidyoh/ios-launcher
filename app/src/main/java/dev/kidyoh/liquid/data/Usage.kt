package dev.kidyoh.liquid.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Usage(val count: Int, val last: Long)

/** Launch counts, used for Siri-style suggestions in Search, Today View and the App Library. */
class UsageTracker(context: Context) {
    private val prefs = context.getSharedPreferences("usage", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Map<String, Usage>> = _state.asStateFlow()

    fun record(appId: String) {
        val now = System.currentTimeMillis()
        val old = _state.value[appId]
        val next = Usage((old?.count ?: 0) + 1, now)
        _state.value = _state.value + (appId to next)
        prefs.edit { putString(appId, "${next.count}:${next.last}") }
    }

    /** Most relevant apps first: frequency, decayed by how long ago they were opened. */
    fun ranked(): List<String> {
        val now = System.currentTimeMillis()
        return _state.value.entries.sortedByDescending { (_, u) ->
            val days = (now - u.last) / 86_400_000.0
            u.count / (1.0 + days / 3.0)
        }.map { it.key }
    }

    private fun load(): Map<String, Usage> = prefs.all.mapNotNull { (k, v) ->
        val parts = (v as? String)?.split(':') ?: return@mapNotNull null
        val count = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val last = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        k to Usage(count, last)
    }.toMap()
}
