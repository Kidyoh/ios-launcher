package dev.kidyoh.liquid.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Owns the Home Screen arrangement: pages, folders, widgets and the dock. Persists to JSON. */
class LayoutRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val apps: AppRepository,
    private val settings: SettingsStore,
) {
    private val file = File(context.filesDir, "home.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _layout = MutableStateFlow<HomeLayout?>(null)
    val layout: StateFlow<HomeLayout?> = _layout.asStateFlow()

    private val undo = ArrayDeque<HomeLayout>()
    private val redo = ArrayDeque<HomeLayout>()
    private val _history = MutableStateFlow(0 to 0)
    /** (undo depth, redo depth) so the edit-mode Undo / Redo buttons can enable themselves. */
    val history: StateFlow<Pair<Int, Int>> = _history.asStateFlow()

    private val columns get() = settings.value.columns

    init {
        scope.launch {
            val saved = withContext(Dispatchers.IO) { load() }?.let(::migrate)
            apps.loaded.first { it }
            _layout.value = HomeGrid.normalize(reconcile(saved ?: defaultLayout()), columns)
            persist()

            launch {
                apps.byId.collect { ids ->
                    if (ids.isEmpty()) return@collect
                    val cur = _layout.value ?: return@collect
                    val next = reconcile(cur)
                    if (next != cur) set(HomeGrid.normalize(next, columns))
                }
            }
            launch {
                apps.installed.collect { fresh ->
                    if (!settings.value.addNewApps) return@collect
                    val cur = _layout.value ?: return@collect
                    val pages = cur.pages.toMutableList()
                    val last = pages.lastOrNull().orEmpty() + fresh.map { AppItem(newId(), it) }
                    if (pages.isEmpty()) pages.add(last) else pages[pages.lastIndex] = last
                    set(HomeGrid.normalize(cur.copy(pages = pages), columns))
                }
            }
            launch {
                settings.state.map { it.columns }.distinctUntilChanged().collect { cols ->
                    val cur = _layout.value ?: return@collect
                    set(HomeGrid.normalize(cur.copy(dock = cur.dock.take(cols)), cols))
                }
            }
        }
    }

    /** Applies a user edit. Recorded for undo. */
    fun edit(block: (HomeLayout) -> HomeLayout) {
        val cur = _layout.value ?: return
        val next = HomeGrid.normalize(block(cur), columns)
        if (next == cur) return
        undo.addLast(cur)
        if (undo.size > 40) undo.removeFirst()
        redo.clear()
        set(next)
    }

    fun undo() {
        val prev = undo.removeLastOrNull() ?: return
        _layout.value?.let { redo.addLast(it) }
        set(prev)
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        _layout.value?.let { undo.addLast(it) }
        set(next)
    }

    fun clearHistory() {
        undo.clear(); redo.clear(); publishHistory()
    }

    fun reset() {
        undo.clear(); redo.clear()
        set(HomeGrid.normalize(defaultLayout(), columns))
    }

    private fun set(l: HomeLayout) {
        _layout.value = l
        publishHistory()
        persist()
    }

    private fun publishHistory() {
        _history.value = undo.size to redo.size
    }

    private fun persist() {
        val l = _layout.value ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val tmp = File(file.parentFile, "home.json.tmp")
                tmp.writeText(json.encodeToString(HomeLayout.serializer(), l))
                tmp.renameTo(file)
            }
        }
    }

    private fun load(): HomeLayout? = runCatching {
        if (file.exists()) json.decodeFromString(HomeLayout.serializer(), file.readText()) else null
    }.getOrNull()

    /** v2 made the first page match iOS: Weather then Calendar, instead of Calendar then Clock. */
    private fun migrate(l: HomeLayout): HomeLayout {
        val prefs = context.getSharedPreferences("layout", Context.MODE_PRIVATE)
        if (prefs.getInt("version", 1) >= 2) return l
        prefs.edit().putInt("version", 2).apply()
        val first = l.pages.firstOrNull() ?: return l
        val cal = first.indexOfFirst { it is WidgetItem && it.kind == WidgetKind.Calendar && it.size == WidgetSize.Small }
        val clock = first.indexOfFirst { it is WidgetItem && it.kind == WidgetKind.AnalogClock && it.size == WidgetSize.Small }
        if (cal != 0 || clock != 1) return l
        val page = first.toMutableList()
        page[0] = WidgetItem(newId(), WidgetKind.Weather, WidgetSize.Small)
        page[1] = first[0]
        return l.copy(pages = listOf(page) + l.pages.drop(1))
    }

    /** Drops apps that are no longer installed and folders that became empty. */
    private fun reconcile(l: HomeLayout): HomeLayout {
        val known = apps.byId.value
        if (known.isEmpty()) return l
        val pages = l.pages.map { page ->
            page.mapNotNull { item ->
                when (item) {
                    is AppItem -> item.takeIf { it.app in known }
                    is FolderItem -> item.copy(apps = item.apps.filter { it in known }).takeIf { it.apps.isNotEmpty() }
                    is WidgetItem -> item
                }
            }
        }
        return l.copy(pages = pages, dock = l.dock.filter { it in known })
    }

    fun defaultLayout(): HomeLayout {
        val all = apps.apps.value.filter { !it.isWork }
        val byPkg = all.groupBy { it.key.pkg }
        val pm = context.packageManager
        fun resolve(intent: Intent): AppEntry? {
            val pkg = runCatching {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            }.getOrNull() ?: return null
            if (pkg == "android") return null // the chooser, i.e. no default
            return byPkg[pkg]?.firstOrNull()
        }
        fun selector(category: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)

        // iOS dock: Phone, Safari, Messages, Music.
        val dock = listOfNotNull(
            resolve(Intent(Intent.ACTION_DIAL)),
            resolve(selector(Intent.CATEGORY_APP_BROWSER)) ?: resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            resolve(selector(Intent.CATEGORY_APP_MESSAGING)) ?: resolve(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            resolve(selector(Intent.CATEGORY_APP_MUSIC)) ?: resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        ).distinctBy { it.id }.take(columns).map { it.id }

        val priority = listOf(
            "camera", "photos", "gallery", "deskclock", "clock", "calendar", "settings", "maps", "android.gm",
            "youtube", "vending", "contacts", "calculator", "documentsui", "files", "keep", "notes", "weather",
            "wallet", "dev.kidyoh.liquid",
        )
        val rest = all.filter { it.id !in dock }
        val ranked = rest.sortedBy { app ->
            val p = app.key.pkg.lowercase()
            priority.indexOfFirst { it in p }.let { if (it < 0) priority.size else it }
        }
        val important = ranked.filter { a -> priority.any { it in a.key.pkg.lowercase() } }
        val googleExtras = rest.filter { it.key.pkg.startsWith("com.google.android.") && it !in important }

        val page1 = mutableListOf<HomeItem>(
            WidgetItem(newId(), WidgetKind.Weather, WidgetSize.Small),
            WidgetItem(newId(), WidgetKind.Calendar, WidgetSize.Small),
        )
        val used = mutableSetOf<String>()
        val folder = if (googleExtras.size >= 3) FolderItem(newId(), "Google", googleExtras.map { it.id }) else null
        folder?.let { used += it.apps }

        val pool = (important + rest.filter { it !in important }).filter { it.id !in used }
        val firstPageSlots = columns * HomeGrid.ROWS - 8 - (if (folder != null) 1 else 0)
        val p1Apps = pool.take(firstPageSlots)
        page1 += p1Apps.map { AppItem(newId(), it.id) }
        folder?.let { page1 += it }
        val page2 = pool.drop(firstPageSlots).take(columns * HomeGrid.ROWS).map { AppItem(newId(), it.id) }

        return HomeLayout(pages = listOf(page1, page2).filter { it.isNotEmpty() }, dock = dock)
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString().take(12)
    }
}
