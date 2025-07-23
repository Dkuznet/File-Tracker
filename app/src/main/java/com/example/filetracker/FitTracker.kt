package com.example.filetracker

import android.app.Application

class FitTracker : Application() {
    companion object {
        lateinit var instance: FitTracker
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}