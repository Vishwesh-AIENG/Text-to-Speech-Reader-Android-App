package com.app.ttsreader.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observes network connectivity using [ConnectivityManager.NetworkCallback].
 *
 * ## Usage
 * ```kotlin
 * networkMonitor.isOnline
 *     .onEach { online -> /* update UI */ }
 *     .launchIn(viewModelScope)
 * ```
 *
 * ## Contract
 * - Emits `true` immediately if a network with INTERNET capability is active.
 * - Emits `false` when the last network with INTERNET capability is lost.
 * - [distinctUntilChanged] ensures only transitions are emitted, not every callback.
 * - The callback is unregistered when the collector cancels (via [awaitClose]).
 *
 * ## Permissions
 * Requires `android.permission.ACCESS_NETWORK_STATE` in the manifest.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /**
     * `true` when at least one network with [NetworkCapabilities.NET_CAPABILITY_INTERNET]
     * is connected; `false` otherwise.
     */
    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                // A network was lost; check if any other network remains online
                val stillOnline = connectivityManager
                    .activeNetwork
                    ?.let { connectivityManager.getNetworkCapabilities(it) }
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                trySend(stillOnline)
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Emit the current state immediately so collectors don't wait for next transition
        val currentlyOnline = connectivityManager
            .activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        trySend(currentlyOnline)

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
