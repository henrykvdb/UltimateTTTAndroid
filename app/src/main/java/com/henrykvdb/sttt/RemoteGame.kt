package com.henrykvdb.sttt

import com.flaghacker.sttt.common.Board

//States the remote game can have
enum class RemoteState {
    NONE,
    LISTENING,
    CONNECTING,
    CONNECTED
}

//Types of messages the remote game can send
enum class RemoteMessage {
    UNDO,
    SETUP,
    BOARD_UPDATE
}

//Callback used to callback data to the UI
interface RemoteCallback{
    fun move(move: Byte)
    fun newGame(gs: GameState)
    fun undo(force: Boolean)
    fun toast(text: String)
    fun turnLocal()
}

//Interface all remote games must extend
interface RemoteGame {
    fun listen(gs: GameState)
    fun connect(adr: String)
    fun close()

    fun sendUndo(force: Boolean)
    fun sendBoard(board: Board)

    fun getRemoteName(): String?
    fun getLocalName(): String
    fun getState(): RemoteState
}