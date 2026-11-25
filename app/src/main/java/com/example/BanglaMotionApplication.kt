package com.example

import android.app.Application
import com.example.core.di.StudioAppContainer

class BanglaMotionApplication : Application() {
    lateinit var appContainer: StudioAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = StudioAppContainer(this)
    }
}
