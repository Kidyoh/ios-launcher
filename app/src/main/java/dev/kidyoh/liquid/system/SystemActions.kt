package dev.kidyoh.liquid.system

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.SearchManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.Toast
import dev.kidyoh.liquid.data.AppEntry

object SystemActions {

    fun launch(context: Context, view: View, app: AppEntry, bounds: Rect?) {
        val la = context.getSystemService(LauncherApps::class.java)
        val opts = bounds?.let {
            ActivityOptions.makeClipRevealAnimation(view, it.left, it.top, it.width(), it.height()).toBundle()
        }
        try {
            la.startMainActivity(app.component, app.user, bounds, opts)
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shortcuts(context: Context, app: AppEntry): List<ShortcutInfo> {
        val la = context.getSystemService(LauncherApps::class.java)
        if (!runCatching { la.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(app.key.pkg)
            .setActivity(app.component)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        return runCatching { la.getShortcuts(q, app.user) }.getOrNull().orEmpty()
            .filter { it.isEnabled }
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .take(4)
    }

    fun shortcutIcon(context: Context, s: ShortcutInfo): Drawable? = runCatching {
        context.getSystemService(LauncherApps::class.java).getShortcutIconDrawable(s, context.resources.displayMetrics.densityDpi)
    }.getOrNull()

    fun startShortcut(context: Context, s: ShortcutInfo, bounds: Rect?) {
        runCatching { context.getSystemService(LauncherApps::class.java).startShortcut(s, bounds, null) }
            .onFailure { Toast.makeText(context, "Shortcut unavailable", Toast.LENGTH_SHORT).show() }
    }

    fun appInfo(context: Context, app: AppEntry) {
        runCatching { context.getSystemService(LauncherApps::class.java).startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun canUninstall(app: AppEntry): Boolean {
        val f = app.flags
        return (f and ApplicationInfo.FLAG_SYSTEM) == 0 || (f and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    fun uninstall(context: Context, app: AppEntry) {
        val i = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.key.pkg, null))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }

    fun share(context: Context, app: AppEntry) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "${app.label}: https://play.google.com/store/apps/details?id=${app.key.pkg}")
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun webSearch(context: Context, query: String) {
        val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(search)
        } catch (e: ActivityNotFoundException) {
            val url = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Pulls down the system notification shade (what a swipe from the top edge does on iOS). */
    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context) {
        runCatching {
            val sbm = context.getSystemService("statusbar")
            sbm.javaClass.getMethod("expandNotificationsPanel").invoke(sbm)
        }
    }

    fun isDefaultHome(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == context.packageName
    }

    /** Intent that asks the user to make us the default Home app. */
    fun defaultHomeIntent(activity: Activity): Intent {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = activity.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    fun notificationAccessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun goHome(context: Context) {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
