package com.example.mindshelf

import android.app.Application
import com.example.mindshelf.data.sync.NetworkContentSyncObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MindShelfApplication : Application() {

    @Inject
    lateinit var networkContentSyncObserver: NetworkContentSyncObserver

    override fun onCreate() {
        super.onCreate()
        networkContentSyncObserver.start()
    }
}
