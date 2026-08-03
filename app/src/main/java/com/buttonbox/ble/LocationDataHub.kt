package com.buttonbox.ble

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Process-wide Android location owner shared by the stock screen and Dashboard Lab.
 * Updates continue during activity transitions, but stop shortly after every visible
 * consumer releases the hub. Each visible Activity owns its listener while active;
 * PR #34 destroys Main when LAB opens, so Main is not retained behind LAB.
 */
object LocationDataHub {
    interface Listener { fun onLocation(location: Location) }

    const val REQUEST_INTERVAL_MS = 100L
    const val MIN_UPDATE_INTERVAL_MS = 50L
    const val MAX_UPDATE_DELAY_MS = 100L
    const val REQUEST_PRIORITY = Priority.PRIORITY_HIGH_ACCURACY

    private val lifecycleOwnerId = LifecycleDiagnostics.registerManager("LocationDataHub")

    private const val STOP_GRACE_MS = 1_500L
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val consumers = linkedSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var client: FusedLocationProviderClient? = null
    private var active = false
    private var everStarted = false
    private var appContext: Context? = null
    private var lastCallbackElapsedMs = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val callbackElapsedMs = SystemClock.elapsedRealtime()
            LifecycleDiagnostics.increment(lifecycleOwnerId, "callbacks")
            LifecycleDiagnostics.setCounter(
                lifecycleOwnerId,
                "lastCallbackIntervalMs",
                if (lastCallbackElapsedMs > 0L) callbackElapsedMs - lastCallbackElapsedMs else 0L
            )
            LifecycleDiagnostics.setCounter(lifecycleOwnerId, "lastCallbackElapsedMs", callbackElapsedMs)
            LifecycleDiagnostics.setCounter(lifecycleOwnerId, "lastSourceTimeEpochMs", location.time)
            lastCallbackElapsedMs = callbackElapsedMs
            val mapped = GpsSampleMapper.map(
                GpsSampleMapper.Source(
                    speedMetresPerSecond = location.speed,
                    speedAvailable = location.hasSpeed(),
                    accuracyMetres = location.accuracy.takeIf { location.hasAccuracy() },
                    speedAccuracyMetresPerSecond = location.speedAccuracyMetersPerSecond
                        .takeIf { android.os.Build.VERSION.SDK_INT >= 26 && location.hasSpeedAccuracy() },
                    bearingDegrees = location.bearing.takeIf { location.hasBearing() },
                    altitudeMetres = location.altitude.takeIf { location.hasAltitude() },
                    fixTimeEpochMs = location.time
                )
            )
            LifecycleDiagnostics.increment(lifecycleOwnerId, "mappings")
            DashboardDataHub.updateGps(
                speedKmh = mapped.speedKilometresPerHour,
                accuracyM = mapped.accuracyMetres,
                speedAccuracyKmh = mapped.speedAccuracyKilometresPerHour,
                bearingDeg = mapped.bearingDegrees,
                altitudeM = mapped.altitudeMetres,
                fixTimeEpochMs = mapped.fixTimeEpochMs
            )
            val fanOut = GpsListenerDispatcher.dispatch(listeners) { it.onLocation(location) }
            LifecycleDiagnostics.increment(lifecycleOwnerId, "fanOutAttempts", fanOut.attempted.toLong())
            LifecycleDiagnostics.increment(lifecycleOwnerId, "fanOutDeliveries", fanOut.delivered.toLong())
            LifecycleDiagnostics.increment(lifecycleOwnerId, "listenerFailures", fanOut.failed.toLong())
        }
    }

    private val stopRunnable = Runnable {
        synchronized(LocationDataHub) {
            if (consumers.isNotEmpty()) return@synchronized
            stopNow()
        }
    }

    fun registerListener(listener: Listener) {
        listeners.add(listener)
        LifecycleDiagnostics.setCounter(lifecycleOwnerId, "listeners", listeners.size.toLong())
        LifecycleDiagnostics.mark(lifecycleOwnerId, "listener-registered", "count=${listeners.size}")
    }

    fun unregisterListener(listener: Listener) {
        listeners.remove(listener)
        LifecycleDiagnostics.setCounter(lifecycleOwnerId, "listeners", listeners.size.toLong())
        LifecycleDiagnostics.mark(lifecycleOwnerId, "listener-unregistered", "count=${listeners.size}")
    }

    @Synchronized
    fun acquire(context: Context, consumer: String): Boolean {
        val application = context.applicationContext
        appContext = application
        consumers.add(consumer)
        LifecycleDiagnostics.setCounter(lifecycleOwnerId, "consumers", consumers.size.toLong())
        LifecycleDiagnostics.mark(lifecycleOwnerId, "acquire", "consumer=${consumer.take(40)} count=${consumers.size}")
        handler.removeCallbacks(stopRunnable)
        if (ContextCompat.checkSelfPermission(application, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            DashboardDataHub.setGpsPermission(false)
            return false
        }
        DashboardDataHub.setGpsPermission(true)
        start(application)
        return true
    }

    @Synchronized
    fun release(context: Context, consumer: String) {
        appContext = context.applicationContext
        consumers.remove(consumer)
        LifecycleDiagnostics.setCounter(lifecycleOwnerId, "consumers", consumers.size.toLong())
        LifecycleDiagnostics.mark(lifecycleOwnerId, "release", "consumer=${consumer.take(40)} count=${consumers.size}")
        if (consumers.isEmpty()) {
            handler.removeCallbacks(stopRunnable)
            handler.postDelayed(stopRunnable, STOP_GRACE_MS)
        }
    }

    private fun resetCallbackIntervalDiagnostics() {
        lastCallbackElapsedMs = 0L
        LifecycleDiagnostics.setCounter(lifecycleOwnerId, "lastCallbackIntervalMs", 0L)
    }

    @SuppressLint("MissingPermission")
    private fun start(context: Context) {
        if (active) return
        resetCallbackIntervalDiagnostics()
        LifecycleDiagnostics.mark(lifecycleOwnerId, "start-requested")
        val fused = client ?: LocationServices.getFusedLocationProviderClient(context).also { client = it }
        val request = LocationRequest.Builder(REQUEST_PRIORITY, REQUEST_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMaxUpdateDelayMillis(MAX_UPDATE_DELAY_MS)
            .build()
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            active = true
            LifecycleDiagnostics.mark(lifecycleOwnerId, "active")
            DashboardDataHub.setGpsActive(true)
            val event = if (everStarted) "GPS updates resumed (shared high precision mode)" else "GPS updates initialized (shared high precision mode)"
            everStarted = true
            DiagnosticStore.addEvent(context, "GPS", event)
        } catch (_: SecurityException) {
            DashboardDataHub.setGpsPermission(false)
            DiagnosticStore.addEvent(context, "GPS", "Location permission denied")
        }
    }

    @Synchronized
    private fun stopNow() {
        resetCallbackIntervalDiagnostics()
        if (!active) return
        try { client?.removeLocationUpdates(callback) } catch (_: Exception) { }
        active = false
        LifecycleDiagnostics.mark(lifecycleOwnerId, "paused")
        DashboardDataHub.setGpsActive(false)
        appContext?.let { DiagnosticStore.addEvent(it, "GPS", "GPS updates paused") }
    }
}
