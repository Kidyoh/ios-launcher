package dev.kidyoh.liquid.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.edit
import dev.kidyoh.liquid.data.AppEntry
import dev.kidyoh.liquid.data.AppRepository
import dev.kidyoh.liquid.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Decides which apps wear an iPhone-style icon: the user's own picks first, then (when the setting is on)
 * automatic matches for built-in apps — by default-app role, then by well-known package names.
 */
class IconPack(
    private val context: Context,
    private val scope: CoroutineScope,
    private val apps: AppRepository,
    private val settings: SettingsStore,
) {
    private val prefs = context.getSharedPreferences("icon_overrides", Context.MODE_PRIVATE)
    @Volatile private var auto: Map<String, PackIcon> = emptyMap()
    private val _version = MutableStateFlow(0)
    /** Bumps whenever any app's icon choice changes, so icons re-render. */
    val version: StateFlow<Int> = _version.asStateFlow()

    init {
        scope.launch {
            apps.apps.collect { list ->
                auto = withContext(Dispatchers.IO) { runCatching { match(list) }.getOrDefault(emptyMap()) }
                _version.value++
            }
        }
        scope.launch {
            settings.state.map { it.iosIcons }.distinctUntilChanged().collect { _version.value++ }
        }
    }

    fun iconFor(app: AppEntry): PackIcon? {
        when (val o = prefs.getString(app.id, null)) {
            null -> Unit
            ORIGINAL -> return null
            else -> PackIcon.entries.firstOrNull { it.name == o }?.let { return it }
        }
        return if (settings.value.iosIcons) auto[app.id] else null
    }

    /** null = automatic, [original] = always the app's own icon. */
    fun set(appId: String, icon: PackIcon?, original: Boolean = false) {
        prefs.edit {
            when {
                original -> putString(appId, ORIGINAL)
                icon == null -> remove(appId)
                else -> putString(appId, icon.name)
            }
        }
        _version.value++
    }

    fun choice(appId: String): String? = prefs.getString(appId, null)

    private fun match(list: List<AppEntry>): Map<String, PackIcon> {
        val pm = context.packageManager
        fun pkg(intent: Intent): String? = runCatching {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()?.takeIf { it != "android" }
        fun cat(c: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, c)

        val roles = buildList {
            add(PackIcon.Phone to pkg(Intent(Intent.ACTION_DIAL)))
            add(PackIcon.Messages to pkg(cat(Intent.CATEGORY_APP_MESSAGING)))
            add(PackIcon.Browser to pkg(cat(Intent.CATEGORY_APP_BROWSER)))
            add(PackIcon.Mail to pkg(cat(Intent.CATEGORY_APP_EMAIL)))
            add(PackIcon.Calendar to pkg(cat(Intent.CATEGORY_APP_CALENDAR)))
            add(PackIcon.Contacts to pkg(cat(Intent.CATEGORY_APP_CONTACTS)))
            add(PackIcon.Photos to pkg(cat(Intent.CATEGORY_APP_GALLERY)))
            add(PackIcon.Music to pkg(cat(Intent.CATEGORY_APP_MUSIC)))
            add(PackIcon.Maps to pkg(cat(Intent.CATEGORY_APP_MAPS)))
            add(PackIcon.Calculator to pkg(cat(Intent.CATEGORY_APP_CALCULATOR)))
            add(PackIcon.Camera to pkg(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)))
            add(PackIcon.Settings to pkg(Intent(Settings.ACTION_SETTINGS)))
            add(PackIcon.Clock to pkg(Intent(AlarmClock.ACTION_SHOW_ALARMS)))
            add(PackIcon.Store to pkg(cat(Intent.CATEGORY_APP_MARKET)))
            if (Build.VERSION.SDK_INT >= 29) add(PackIcon.Files to pkg(cat(Intent.CATEGORY_APP_FILES)))
        }

        val mine = list.filter { !it.isWork }
        val byPkg = mine.groupBy { it.key.pkg }
        val out = mutableMapOf<String, PackIcon>()
        val taken = mutableSetOf<PackIcon>()
        fun assign(icon: PackIcon, p: String?) {
            if (p == null || icon in taken) return
            val acts = byPkg[p] ?: return
            // Dialer packages often also hold Contacts; pick the right activity for each.
            val wantsContacts = icon == PackIcon.Contacts
            val a = acts.firstOrNull { it.key.cls.contains("contact", ignoreCase = true) == wantsContacts && it.id !in out }
                ?: acts.firstOrNull { it.id !in out && acts.size == 1 }
                ?: return
            out[a.id] = icon
            taken += icon
        }
        roles.forEach { (icon, p) -> assign(icon, p) }
        KNOWN.forEach { (icon, pkgs) -> pkgs.forEach { assign(icon, it) } }
        return out
    }

    companion object {
        const val ORIGINAL = "original"

        private val KNOWN: List<Pair<PackIcon, List<String>>> = listOf(
            PackIcon.Phone to listOf("com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer", "com.oneplus.dialer"),
            PackIcon.Messages to listOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms", "com.android.messaging"),
            PackIcon.Browser to listOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.brave.browser", "com.opera.browser", "com.microsoft.emmx", "com.mi.globalbrowser"),
            PackIcon.Mail to listOf("com.google.android.gm", "com.samsung.android.email.provider", "com.microsoft.office.outlook"),
            PackIcon.Camera to listOf("com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.android.camera", "com.android.camera2", "com.oplus.camera", "com.oneplus.camera", "com.motorola.camera3"),
            PackIcon.Photos to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery", "com.android.gallery3d", "com.oneplus.gallery", "com.coloros.gallery3d"),
            PackIcon.Settings to listOf("com.android.settings"),
            PackIcon.Clock to listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock", "com.oneplus.deskclock"),
            PackIcon.Calendar to listOf("com.google.android.calendar", "com.samsung.android.calendar", "com.android.calendar"),
            PackIcon.Maps to listOf("com.google.android.apps.maps"),
            PackIcon.Weather to listOf("com.sec.android.daemonapp", "com.miui.weather2", "com.google.android.apps.weather"),
            PackIcon.Notes to listOf("com.google.android.keep", "com.samsung.android.app.notes", "com.miui.notes"),
            PackIcon.Reminders to listOf("com.google.android.apps.tasks", "com.samsung.android.app.reminder"),
            PackIcon.Calculator to listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator", "com.android.calculator2", "com.miui.calculator"),
            PackIcon.Contacts to listOf("com.google.android.contacts", "com.samsung.android.app.contacts", "com.android.contacts"),
            PackIcon.Store to listOf("com.android.vending"),
            PackIcon.Files to listOf("com.google.android.apps.nbu.files", "com.google.android.documentsui", "com.android.documentsui", "com.sec.android.app.myfiles", "com.mi.android.globalFileexplorer"),
            PackIcon.Health to listOf("com.google.android.apps.fitness", "com.sec.android.app.shealth"),
            PackIcon.Wallet to listOf("com.google.android.apps.walletnfcrel"),
            PackIcon.VoiceMemos to listOf("com.google.android.apps.recorder", "com.sec.android.app.voicenote"),
            PackIcon.Books to listOf("com.google.android.apps.books"),
            PackIcon.Podcasts to listOf("com.google.android.apps.podcasts"),
            PackIcon.FaceTime to listOf("com.google.android.apps.tachyon"),
            PackIcon.Translate to listOf("com.google.android.apps.translate"),
            PackIcon.Video to listOf("com.google.android.videos"),
            PackIcon.Music to listOf("com.google.android.apps.youtube.music", "com.sec.android.app.music"),
        )
    }
}
