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
    fun setType(type: RemoteType) {
        remoteGame.close()
        this.type = type

        remoteGame = when (type) {
            RemoteType.BLUETOOTH -> BtGame(callback, resources)
            RemoteType.NONE -> DummyRemoteGame
        }
    }

    private val callback = object : RemoteCallback {
        override fun newGame(gs: GameState) = this@RemoteService.sendBroadcast(Intent(INTENT_NEWGAME).putExtra(INTENT_DATA, gs))
        override fun undo(force: Boolean) = this@RemoteService.sendBroadcast(Intent(INTENT_UNDO).putExtra(INTENT_DATA, force))
        override fun toast(text: String) = this@RemoteService.sendBroadcast(Intent(INTENT_TOAST).putExtra(INTENT_DATA, text))
        override fun move(move: Byte) = this@RemoteService.sendBroadcast(Intent(INTENT_MOVE).putExtra(INTENT_DATA, move))
        override fun turnLocal() = this@RemoteService.sendBroadcast(Intent(INTENT_TURNLOCAL))
    }
}