package dev.kidyoh.liquid.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class WeatherNow(
    val place: String,
    val temp: Int,
    val high: Int,
    val low: Int,
    val code: Int,
    val isDay: Boolean,
    val fetched: Long,
) {
    val condition: String get() = describe(code, isDay)

    companion object {
        /** WMO weather codes, worded the way iOS Weather does. */
        fun describe(code: Int, isDay: Boolean): String = when (code) {
            0 -> if (isDay) "Sunny" else "Clear"
            1 -> if (isDay) "Mostly Sunny" else "Mostly Clear"
            2 -> "Partly Cloudy"
            3 -> "Mostly Cloudy"
            45, 48 -> "Foggy"
            51, 53, 55 -> "Drizzle"
            56, 57, 66, 67 -> "Freezing Rain"
            61, 80 -> "Light Rain"
            63, 81 -> "Rain"
            65, 82 -> "Heavy Rain"
            71, 73, 75, 77, 85, 86 -> "Snow"
            95 -> "Thunderstorms"
            96, 99 -> "Hail"
            else -> "Cloudy"
        }
    }
}

sealed interface WeatherState {
    data object NeedsPermission : WeatherState
    data object Loading : WeatherState
    data class Ready(val weather: WeatherNow) : WeatherState
    data object Unavailable : WeatherState
}

/** Current weather for the Weather widget, from Open-Meteo (free, no key) and your approximate location. */
class WeatherRepository(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(initial())
    val state: StateFlow<WeatherState> = _state.asStateFlow()
    private var job: Job? = null
    private var frozen = false

    val fahrenheit: Boolean
        get() = Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Refreshes if the reading is older than [maxAgeMs]. */
    fun refresh(maxAgeMs: Long = 30 * 60_000L) {
        if (frozen) return
        if (!hasPermission()) {
            if (_state.value !is WeatherState.Ready) _state.value = WeatherState.NeedsPermission
            return
        }
        val cur = (_state.value as? WeatherState.Ready)?.weather
        if (cur != null && System.currentTimeMillis() - cur.fetched < maxAgeMs) return
        if (job?.isActive == true) return
        if (cur == null) _state.value = WeatherState.Loading
        job = scope.launch {
            val w = withContext(Dispatchers.IO) { runCatching { fetch() }.getOrNull() }
            when {
                w != null -> { _state.value = WeatherState.Ready(w); save(w) }
                cur == null -> _state.value = WeatherState.Unavailable
            }
        }
    }

    /** Screenshot tests have no network or location. */
    fun replaceForTest(w: WeatherNow) {
        frozen = true
        _state.value = WeatherState.Ready(w)
    }

    private suspend fun fetch(): WeatherNow? {
        val loc = location() ?: return null
        val unit = if (fahrenheit) "&temperature_unit=fahrenheit" else ""
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f".format(Locale.US, loc.latitude, loc.longitude) +
                "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1$unit"
        )
        val body = (url.openConnection() as HttpURLConnection).run {
            connectTimeout = 10_000
            readTimeout = 10_000
            try { inputStream.bufferedReader().readText() } finally { disconnect() }
        }
        val json = JSONObject(body)
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        return WeatherNow(
            place = placeName(loc),
            temp = current.getDouble("temperature_2m").roundToInt(),
            high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            fetched = System.currentTimeMillis(),
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun location(): Location? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val last = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 3_600_000L) return last
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return last
        val fresh = if (Build.VERSION.SDK_INT >= 30) {
            withTimeoutOrNull(12_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    lm.getCurrentLocation(provider, null, context.mainExecutor) { cont.resume(it) }
                }
            }
        } else null
        return fresh ?: last
    }

    @Suppress("DEPRECATION")
    private fun placeName(loc: Location): String = runCatching {
        val a = Geocoder(context).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
        a?.subLocality ?: a?.locality ?: a?.subAdminArea ?: a?.adminArea
    }.getOrNull() ?: "My Location"

    private fun initial(): WeatherState {
        val raw = prefs.getString("last", null) ?: return WeatherState.Loading
        return runCatching {
            val o = JSONObject(raw)
            WeatherState.Ready(
                WeatherNow(o.getString("place"), o.getInt("temp"), o.getInt("high"), o.getInt("low"), o.getInt("code"), o.getBoolean("day"), o.getLong("at"))
            )
        }.getOrDefault(WeatherState.Loading)
    }

    private fun save(w: WeatherNow) = prefs.edit {
        putString("last", JSONObject().apply {
            put("place", w.place); put("temp", w.temp); put("high", w.high); put("low", w.low)
            put("code", w.code); put("day", w.isDay); put("at", w.fetched)
        }.toString())
    }
}
