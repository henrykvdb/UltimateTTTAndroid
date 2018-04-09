package com.henrykvdb.sttt

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

enum class RemoteType {
    NONE,
    BLUETOOTH
}

class RemoteService : Service() {
    private var remoteGame: RemoteGame = DummyRemoteGame
    private var type = RemoteType.NONE

    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder? = binder
    inner class LocalBinder : Binder() {
        fun getService() = this@RemoteService
    }

    fun remoteGame() = remoteGame

    fun getType() = type
    fun setType(type: RemoteType, callback: RemoteCallback) {
        remoteGame.close()
        this.type = type

        remoteGame = when (type) {
            RemoteType.BLUETOOTH -> BtGame(resources)
            RemoteType.NONE -> DummyRemoteGame
        }
    }
}