package dev.kidyoh.liquid

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import dev.kidyoh.liquid.system.Permissions
import dev.kidyoh.liquid.ui.home.HomeController
import dev.kidyoh.liquid.ui.home.HomeScreen
import dev.kidyoh.liquid.ui.theme.LiquidTheme
import dev.kidyoh.liquid.ui.theme.isDark
import kotlinx.coroutines.flow.MutableSharedFlow

/** The Home Screen. Registered for the HOME intent so it can be the default launcher. */
class MainActivity : ComponentActivity() {
    private val homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    @androidx.annotation.VisibleForTesting
    internal var controller: HomeController? = null

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Permissions.onResult()
        (application as LiquidApp).weather.refresh(maxAgeMs = 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as LiquidApp
        Permissions.request = { permissions.launch(it) }

        setContent {
            val settings by app.settings.state.collectAsState()
            val scope = rememberCoroutineScope()
            val home = remember { HomeController(app, scope) }
            controller = home

            // A launcher never closes on Back; it only peels away whatever is open.
            BackHandler { home.dismissTop() }

            // Jiggle mode hides the status bar to make room for Edit / Done, like iOS.
            LaunchedEffect(home.editMode) {
                val c = WindowCompat.getInsetsController(window, window.decorView)
                if (home.editMode) c.hide(WindowInsetsCompat.Type.statusBars()) else c.show(WindowInsetsCompat.Type.statusBars())
            }

            LiquidTheme(settings.theme.isDark()) {
                HomeScreen(home, homePresses)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val isHome = intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
        if (isHome && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            homePresses.tryEmit(Unit)
        }
    }

    override fun onResume() {
        super.onResume()
        (application as LiquidApp).wallpaper.refreshIfSystem()
        (application as LiquidApp).weather.refresh()
        dev.kidyoh.liquid.ui.island.IslandOverlay.sync(this)
    }

    override fun onStop() {
        super.onStop()
        controller?.cancelDrag()
        controller?.menu = null
        controller?.spotlight = false
        controller?.notificationCenter = false
        controller?.controlCenter = false
    }
}
