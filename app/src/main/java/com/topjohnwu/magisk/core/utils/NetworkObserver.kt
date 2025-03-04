package com.topjohnwu.magisk.core.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager
import androidx.collection.ArraySet
import androidx.core.content.getSystemService
import com.topjohnwu.magisk.ktx.registerRuntimeReceiver

typealias ConnectionCallback = (Boolean) -> Unit

class NetworkObserver(
    context: Context,
    private val callback: ConnectionCallback
) {
    private val manager = context.getSystemService<ConnectivityManager>()!!

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        private val activeList = ArraySet<Network>()

        override fun onAvailable(network: Network) {
            activeList.add(network)
            callback(true)
        }
        override fun onLost(network: Network) {
            activeList.remove(network)
            callback(!activeList.isEmpty())
        }
    }

    private val receiver = object : BroadcastReceiver() {
        private fun Context.isIdleMode(): Boolean {
            val pwm = getSystemService<PowerManager>() ?: return true
            val isIgnoringOptimizations = pwm.isIgnoringBatteryOptimizations(packageName)
            return pwm.isDeviceIdleMode && !isIgnoringOptimizations
        }
        override fun onReceive(context: Context, intent: Intent) {
            if (context.isIdleMode()) {
                callback(false)
            } else {
                getCurrentState()
            }
        }
    }

    init {
        val builder = NetworkRequest.Builder()
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        manager.registerNetworkCallback(builder.build(), networkCallback)
        val filter = IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        context.applicationContext.registerRuntimeReceiver(receiver, filter)
    }

    fun getCurrentState() {
        callback(manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false)
    }

    companion object {
        fun observe(context: Context, callback: ConnectionCallback): NetworkObserver {
            return NetworkObserver(context, callback).apply { getCurrentState() }
        }
    }
}
