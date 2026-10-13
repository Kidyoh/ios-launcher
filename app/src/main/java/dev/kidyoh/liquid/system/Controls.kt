package dev.kidyoh.liquid.system

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Small wrappers around what Android lets a launcher control directly, for Control Center. */
object Controls {

    fun open(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // ---- Connectivity: Android 10+ only lets apps open the system panels, not flip the switches.

    fun wifiOn(context: Context) = runCatching { context.getSystemService(WifiManager::class.java).isWifiEnabled }.getOrDefault(false)

    fun bluetoothOn(context: Context) = runCatching { context.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true }.getOrDefault(false)

    fun airplaneOn(context: Context) = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    fun openWifi(context: Context) = open(context, if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS))
    fun openInternet(context: Context) = open(context, if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIRELESS_SETTINGS))
    fun openBluetooth(context: Context) = open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    fun openAirplane(context: Context) = open(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
    fun openCast(context: Context) = open(context, Intent(Settings.ACTION_CAST_SETTINGS))

    // ---- Settings that need "Modify system settings".

    fun canWrite(context: Context) = Settings.System.canWrite(context)

    fun askWrite(context: Context) = open(
        context,
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")),
    )

    fun rotationLocked(context: Context) =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0

    fun setRotationLocked(context: Context, locked: Boolean): Boolean {
        if (!canWrite(context)) { askWrite(context); return false }
        return runCatching { Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (locked) 0 else 1) }.getOrDefault(false)
    }

    fun brightness(context: Context): Float =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f

    fun setBrightness(context: Context, v: Float): Boolean {
        if (!canWrite(context)) return false
        return runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (v.coerceIn(0.02f, 1f) * 255).toInt())
        }.getOrDefault(false)
    }

    // ---- Volume

    fun volume(context: Context): Float {
        val am = context.getSystemService(AudioManager::class.java)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
    }

    fun setVolume(context: Context, v: Float) {
        val am = context.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, (v.coerceIn(0f, 1f) * max).toInt(), 0) }
    }

    // ---- Do Not Disturb

    fun dndOn(context: Context) =
        context.getSystemService(NotificationManager::class.java).currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL

    fun setDnd(context: Context, on: Boolean): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            open(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return false
        }
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        return true
    }

    // ---- Shortcuts

    fun camera(context: Context) = open(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    fun calculator(context: Context) = open(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
    fun timer(context: Context) = open(context, Intent(android.provider.AlarmClock.ACTION_SHOW_TIMERS))
    fun alarms(context: Context) = open(context, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
    fun settings(context: Context) = open(context, Intent(Settings.ACTION_SETTINGS))
}

/** Flashlight state that follows the system (so the Quick Settings tile and ours agree). */
class Torch(context: Context) {
    private val cm = context.getSystemService(CameraManager::class.java)
    val id: String? = runCatching {
        cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()
    val on = mutableStateOf(false)
    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == id) on.value = enabled
        }
    }

    fun start() = runCatching { cm.registerTorchCallback(callback, Handler(Looper.getMainLooper())) }
    fun stop() = runCatching { cm.unregisterTorchCallback(callback) }
    fun toggle() {
        val i = id ?: return
        runCatching { cm.setTorchMode(i, !on.value) }
    }
}

@Composable
fun rememberTorch(): Torch {
    val ctx = LocalContext.current
    val torch = remember { Torch(ctx.applicationContext) }
    DisposableEffect(torch) {
        torch.start()
        onDispose { torch.stop() }
    }
    return torch
}

data class NowPlaying(
    val title: String?,
    val artist: String?,
    val art: Bitmap?,
    val playing: Boolean,
    val controller: MediaController?,
)

/** The active media session (needs notification access, which badges already use). */
@Composable
fun rememberNowPlaying(): State<NowPlaying> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(NowPlaying(null, null, null, false, null)) }
    DisposableEffect(ctx) {
        val msm = ctx.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(ctx, BadgeService::class.java)
        var current: MediaController? = null
        fun publish() {
            val c = current
            val m = c?.metadata
            state.value = NowPlaying(
                title = m?.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART),
                playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING,
                controller = c,
            )
        }
        val cb = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
            override fun onPlaybackStateChanged(s: PlaybackState?) = publish()
        }
        fun pick(list: List<MediaController>?) {
            current?.unregisterCallback(cb)
            current = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
            current?.registerCallback(cb, Handler(Looper.getMainLooper()))
            publish()
        }
        val sessions = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
        runCatching {
            pick(msm.getActiveSessions(listener))
            msm.addOnActiveSessionsChangedListener(sessions, listener)
        }
        onDispose {
            current?.unregisterCallback(cb)
            runCatching { msm.removeOnActiveSessionsChangedListener(sessions) }
        }
    }
    return state
}
