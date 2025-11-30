package com.henrykvdb.sttt

import android.app.Application
import kotlinx.coroutines.GlobalScope

class StttApplication : Application() {
    lateinit var appContainer: AppContainer
    // Container of objects shared across the whole app
    inner class AppContainer {
        val billingDataSource = BillingDataSource.getInstance(
            this@StttApplication, GlobalScope
        )
    }

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer()
    }
}