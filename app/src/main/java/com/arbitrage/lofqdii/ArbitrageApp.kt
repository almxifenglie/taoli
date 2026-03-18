package com.arbitrage.lofqdii

import android.app.Application
import android.content.Context

class ArbitrageApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
