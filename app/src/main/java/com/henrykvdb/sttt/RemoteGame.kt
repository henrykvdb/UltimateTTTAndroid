package com.henrykvdb.sttt

import com.flaghacker.sttt.common.Board

enum class RemoteState {
    NONE,
    LISTENING,
    CONNECTING,
    CONNECTED
}

interface RemoteCallback{
    fun sendMove()
}

interface RemoteGame {
    fun listen(gs: GameState)
    fun connect(adr: String)
    fun close()

    fun sendUndo(force: Boolean)
    fun sendBoard(board: Board)

    fun getRemoteName(): String
    fun getLocalName(): String
    fun getState(): RemoteState
}