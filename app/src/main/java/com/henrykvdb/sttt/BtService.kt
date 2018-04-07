package com.henrykvdb.sttt

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

enum class RemoteType{
    NONE,
    BLUETOOTH
}

class BtService : Service() {
    private val mBinder = LocalBinder()

    private var remoteGame: RemoteGame? = null
    private var type = RemoteType.NONE

    override fun onBind(intent: Intent): IBinder? = mBinder
    inner class LocalBinder : Binder() {
        fun getService() = this@BtService
    }

    fun getRemoteGame() = remoteGame

    fun getType() = type

    fun setType(type: RemoteType, callback: RemoteCallback){
        remoteGame?.close()
        this.type = type

        if (type == RemoteType.BLUETOOTH){
            remoteGame = BtGame(callback)
        }
    }
}