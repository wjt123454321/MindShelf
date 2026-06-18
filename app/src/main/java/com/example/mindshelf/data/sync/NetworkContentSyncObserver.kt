package com.example.mindshelf.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.mindshelf.data.repository.ContentSyncRepository
import com.example.mindshelf.data.sync.SyncCoordinator
import com.example.mindshelf.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** 网络可用时自动将本地待同步笔记/知识库推送到服务端。 */
@Singleton
class NetworkContentSyncObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentSyncRepository: ContentSyncRepository,
    private val syncCoordinator: SyncCoordinator,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val syncMutex = Mutex()
    private var started = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scheduleSync()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities,
        ) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ) {
                scheduleSync()
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        connectivityManager.registerDefaultNetworkCallback(callback)
        if (isNetworkValidated()) {
            scheduleSync()
        }
    }

    fun scheduleSync() {
        scope.launch {
            syncMutex.withLock {
                runCatching { contentSyncRepository.syncAllPending() }
                runCatching { syncCoordinator.syncAll() }
            }
        }
    }

    private fun isNetworkValidated(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
