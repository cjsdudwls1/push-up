package com.pushuprpg.app

import android.app.Application

class PushupApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.onAppStart()
    }
}
