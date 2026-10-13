package dev.kidyoh.liquid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.kidyoh.liquid.system.Badges
import dev.kidyoh.liquid.system.SystemActions
import dev.kidyoh.liquid.ui.settings.SettingsApp
import dev.kidyoh.liquid.ui.settings.SettingsHost
import dev.kidyoh.liquid.ui.theme.LiquidTheme
import dev.kidyoh.liquid.ui.theme.isDark

/** Onboarding and settings. This is the icon in the app list. */
class SettingsActivity : ComponentActivity() {
    private var isDefault by mutableStateOf(false)
    private var badgeAccess by mutableStateOf(false)

    private val requestHome = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
        if (isDefault) {
            (application as LiquidApp).settings.update { it.copy(onboarded = true) }
            SystemActions.goHome(this)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        refresh()
        val app = application as LiquidApp
        val start = intent.getStringExtra(EXTRA_SCREEN)
        setContent {
            val settings by app.settings.state.collectAsState()
            LiquidTheme(settings.theme.isDark()) {
                SettingsApp(
                    start,
                    SettingsHost(
                        isDefault = isDefault,
                        badgeAccess = badgeAccess,
                        onMakeDefault = { requestHome.launch(SystemActions.defaultHomeIntent(this)) },
                        onBadgeAccess = { runCatching { startActivity(SystemActions.notificationAccessIntent()) } },
                        onFinishOnboarding = { app.settings.update { it.copy(onboarded = true) } },
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        isDefault = SystemActions.isDefaultHome(this)
        badgeAccess = Badges.hasAccess(this)
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
    }
}
