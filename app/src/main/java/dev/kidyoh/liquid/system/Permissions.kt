package dev.kidyoh.liquid.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lets Home Screen widgets ask for runtime permissions through the activity. */
object Permissions {
    /** Set by MainActivity. */
    var request: ((Array<String>) -> Unit)? = null

    private val _granted = MutableStateFlow(0)
    /** Bumps whenever a permission result comes back, so widgets re-read their data. */
    val changes: StateFlow<Int> = _granted.asStateFlow()

    fun ask(vararg permissions: String) {
        request?.invoke(arrayOf(*permissions))
    }

    fun onResult() {
        _granted.value++
    }
}
