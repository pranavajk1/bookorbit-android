package com.bookorbit.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the app can reach its server right now. Two things make it offline: the device has no
 * network at all, or it has one but the server didn't answer the last request (a home server seen
 * from outside the house, say). Screens use [offline] to show what's on the device instead of an
 * error; [OfflineCacheInterceptor] reports what each request found.
 */
@Singleton
class Connectivity @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    private val _deviceOnline = MutableStateFlow(currentlyOnline())
    /** True when the device has a network that reaches the internet (or at least a LAN). */
    val deviceOnline: StateFlow<Boolean> = _deviceOnline.asStateFlow()

    private val serverReachable = MutableStateFlow(true)

    /** True when there's no point asking the server: no network, or it stopped answering. */
    val offline: StateFlow<Boolean> = combine(_deviceOnline, serverReachable) { online, reachable -> !online || !reachable }
        .stateIn(scope, SharingStarted.Eagerly, !_deviceOnline.value)

    init {
        runCatching {
            manager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // A new network is a fresh chance: assume the server answers until a request says otherwise.
                    serverReachable.value = true
                    _deviceOnline.value = true
                }

                override fun onLost(network: Network) {
                    _deviceOnline.value = currentlyOnline()
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _deviceOnline.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            })
        }
    }

    /** A request got an answer from the server. */
    fun onServerAnswered() {
        if (!serverReachable.value) serverReachable.value = true
    }

    /** A request couldn't reach the server. */
    fun onServerUnreachable() {
        if (serverReachable.value) serverReachable.value = false
    }

    private fun currentlyOnline(): Boolean {
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
