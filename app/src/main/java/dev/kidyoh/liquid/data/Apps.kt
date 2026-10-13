package dev.kidyoh.liquid.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

/** Stable identity of a launchable activity, persisted as "pkg/cls#userSerial". */
data class AppKey(val pkg: String, val cls: String, val user: Long) {
    val flat: String get() = "$pkg/$cls#$user"

    companion object {
        fun parse(s: String): AppKey? {
            val slash = s.indexOf('/')
            val hash = s.lastIndexOf('#')
            if (slash <= 0 || hash <= slash) return null
            return AppKey(s.substring(0, slash), s.substring(slash + 1, hash), s.substring(hash + 1).toLongOrNull() ?: return null)
        }
    }
}

enum class AppCategory(val title: String) {
    Social("Social"),
    Entertainment("Entertainment"),
    Productivity("Productivity & Finance"),
    Creativity("Creativity"),
    Information("Information & Reading"),
    Travel("Travel"),
    Games("Games"),
    Utilities("Utilities"),
    Other("Other"),
}

class AppEntry(
    val key: AppKey,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val category: AppCategory,
    val installTime: Long,
    val isWork: Boolean,
    /** ApplicationInfo flags, e.g. to tell whether the app can be uninstalled. */
    val flags: Int,
    /** Loads the launcher icon at the given density. */
    val iconLoader: (Int) -> Drawable?,
) {
    val id: String get() = key.flat

    fun relabel(newLabel: String) = AppEntry(key, newLabel, component, user, category, installTime, isWork, flags, iconLoader)
}

class AppRepository(private val context: Context, private val scope: CoroutineScope) {
    val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    /** All launchable apps across profiles, sorted by label. */
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _byId = MutableStateFlow<Map<String, AppEntry>>(emptyMap())
    val byId: StateFlow<Map<String, AppEntry>> = _byId.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _installed = MutableSharedFlow<List<String>>(extraBufferCapacity = 8)
    /** App ids that were freshly installed while we were running. */
    val installed: SharedFlow<List<String>> = _installed

    private var reloadJob: Job? = null
    private val labelPrefs = context.getSharedPreferences("labels", Context.MODE_PRIVATE)
    private var raw: List<AppEntry> = emptyList()

    /** Home Screen names the user picked with Rename… */
    fun customLabel(id: String): String? = labelPrefs.getString(id, null)

    fun setLabel(id: String, label: String?) {
        labelPrefs.edit().apply { if (label.isNullOrBlank()) remove(id) else putString(id, label.trim()) }.apply()
        publish(raw)
    }

    private fun publish(list: List<AppEntry>) {
        raw = list
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val shown = list.map { a -> customLabel(a.id)?.let { a.relabel(it) } ?: a }.sortedWith { a, b -> collator.compare(a.label, b.label) }
        _apps.value = shown
        _byId.value = shown.associateBy { it.id }
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload(newPackage = packageName)
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    fun reload(newPackage: String? = null) {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            val list = withContext(Dispatchers.IO) { query() }
            val before = _byId.value.keys
            publish(list)
            _loaded.value = true
            if (newPackage != null) {
                val fresh = list.filter { it.key.pkg == newPackage && it.id !in before }.map { it.id }
                if (fresh.isNotEmpty()) _installed.tryEmit(fresh)
            }
        }
    }

    /** Screenshot tests have no installed apps, so they hand in their own. */
    fun replaceForTest(list: List<AppEntry>) {
        reloadJob?.cancel()
        launcherApps.unregisterCallback(callback)
        publish(list)
        _loaded.value = true
    }

    private fun query(): List<AppEntry> {
        val me = Process.myUserHandle()
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        return userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList()).map { info ->
                val cn = info.componentName
                AppEntry(
                    key = AppKey(cn.packageName, cn.className, serial),
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { cn.packageName },
                    component = cn,
                    user = user,
                    category = categorize(info.applicationInfo),
                    installTime = info.firstInstallTime,
                    isWork = user != me,
                    flags = info.applicationInfo.flags,
                    iconLoader = { dpi -> info.getIcon(dpi) },
                )
            }
        }.distinctBy { it.id }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun categorize(ai: ApplicationInfo): AppCategory {
        when (ai.category) {
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.Games
            ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.Social
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.Entertainment
            ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.Creativity
            ApplicationInfo.CATEGORY_NEWS -> return AppCategory.Information
            ApplicationInfo.CATEGORY_MAPS -> return AppCategory.Travel
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.Productivity
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> return AppCategory.Utilities
        }
        val p = ai.packageName.lowercase()
        fun has(vararg words: String) = words.any { it in p }
        return when {
            has("whatsapp", "telegram", "instagram", "facebook", "messenger", "twitter", "snapchat", "discord",
                "tiktok", "musically", "reddit", "linkedin", "signal", "threads", "mms", "messaging", "wechat") -> AppCategory.Social
            has("youtube", "netflix", "spotify", "music", "video", "primevideo", "disney", "twitch", "podcast", "hulu") -> AppCategory.Entertainment
            has("android.gm", "mail", "docs", "sheets", "slides", "office", "drive", "bank", "wallet", "pay", "notion",
                "calendar", "keep", "notes", "outlook", "teams", "slack", "zoom", "finance") -> AppCategory.Productivity
            has("photo", "gallery", "camera", "editor", "canva", "lightroom", "snapseed", "picsart") -> AppCategory.Creativity
            has("news", "kindle", "books", "reader", "medium", "wiki", "weather", "chrome", "browser", "firefox") -> AppCategory.Information
            has("maps", "uber", "lyft", "bolt", "airbnb", "booking", "trip", "travel", "waze") -> AppCategory.Travel
            has("settings", "clock", "calculator", "files", "filemanager", "contacts", "dialer", "phone", "vending",
                "store", "android", "google", "samsung", "xiaomi", "oneplus", "launcher", "recorder", "compass") -> AppCategory.Utilities
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 -> AppCategory.Utilities
            else -> AppCategory.Other
        }
    }
}
