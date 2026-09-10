package com.crimson.pixelshade

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class RuntimeTransport {
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    OTHER,
    NONE
}

internal data class RuntimeSystemStatus(
    val time: String,
    val date: String,
    val connectionLabel: String,
    val transport: RuntimeTransport,
    val batteryPercent: Int,
    val charging: Boolean
)

/**
 * Small, permission-light snapshot used by the replacement shade header.
 *
 * The shade is normally visible for only a short period, so a lightweight poll
 * avoids keeping another receiver or network callback alive after the panel is
 * dismissed while still keeping the clock, battery and connectivity state live.
 */
@Composable
internal fun rememberRuntimeSystemStatus(context: Context): RuntimeSystemStatus {
    var status by remember(context) { mutableStateOf(readRuntimeSystemStatus(context)) }

    LaunchedEffect(context) {
        while (true) {
            status = readRuntimeSystemStatus(context)
            delay(1_000L)
        }
    }

    return status
}

private fun readRuntimeSystemStatus(context: Context): RuntimeSystemStatus {
    val now = Date()
    val time = SimpleDateFormat("h:mm", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now)

    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    val network = runCatching { connectivityManager?.activeNetwork }.getOrNull()
    val capabilities = runCatching {
        network?.let { connectivityManager?.getNetworkCapabilities(it) }
    }.getOrNull()

    val transport = when {
        capabilities == null -> RuntimeTransport.NONE
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> RuntimeTransport.VPN
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> RuntimeTransport.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> RuntimeTransport.CELLULAR
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> RuntimeTransport.ETHERNET
        else -> RuntimeTransport.OTHER
    }

    val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    val connectionLabel = when {
        !validated && transport == RuntimeTransport.NONE -> "No internet"
        !validated -> "Limited connection"
        transport == RuntimeTransport.WIFI -> "Wi-Fi"
        transport == RuntimeTransport.CELLULAR -> {
            val carrier = runCatching {
                context.getSystemService(TelephonyManager::class.java)?.networkOperatorName
            }.getOrNull().orEmpty().trim()
            carrier.ifBlank { "Mobile network" }
        }
        transport == RuntimeTransport.ETHERNET -> "Ethernet"
        transport == RuntimeTransport.VPN -> "VPN"
        else -> "Connected"
    }

    val batteryManager = context.getSystemService(BatteryManager::class.java)
    val batteryPercent = runCatching {
        batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }.getOrDefault(-1).takeIf { it in 0..100 } ?: -1
    val charging = runCatching { batteryManager?.isCharging == true }.getOrDefault(false)

    return RuntimeSystemStatus(
        time = time,
        date = date,
        connectionLabel = connectionLabel,
        transport = transport,
        batteryPercent = batteryPercent,
        charging = charging
    )
}
