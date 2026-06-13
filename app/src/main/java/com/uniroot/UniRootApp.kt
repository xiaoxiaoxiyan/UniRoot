package com.uniroot

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class UniRootApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: UniRootApp
            private set
    }
}
