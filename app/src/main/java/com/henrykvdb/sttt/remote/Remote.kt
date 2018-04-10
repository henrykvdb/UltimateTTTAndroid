package com.henrykvdb.sttt.remote

import com.flaghacker.sttt.common.Board
import com.henrykvdb.sttt.GameState

/**States the RemoteGame implementation can have*/
enum class RemoteState {
    NONE,
    LISTENING,
    CONNECTING,
    CONNECTED
}

/**Types of messages the RemoteGame implementation can send*/
enum class RemoteMessageType {
    UNDO,
    SETUP,
    BOARD_UPDATE;
}

/**Callback used to callback data from the remoteGame to the RemoteService*/
interface RemoteCallback {
    fun move(move: Byte)
    fun newGame(gs: GameState)
    fun undo(force: Boolean)
    fun toast(text: String)
    fun turnLocal()
}

/**Interface all remoteGame games must extend*/
interface RemoteGame {
    val remoteName: String?
    val localName: String
    val state: RemoteState
    val lastBoard: Board

    fun listen(gs: GameState)
    fun connect(adr: String)
    fun close()

    fun sendUndo(force: Boolean)
    fun sendBoard(board: Board)
}

/** Dummy implementation of remote game. Does nothing */
object DummyRemoteGame : RemoteGame {
    override val remoteName: String? = null
    override val localName = ""
    override val state = RemoteState.NONE
    override val lastBoard get() = throw IllegalStateException()

    override fun listen(gs: GameState) = Unit
    override fun connect(adr: String) = Unit
    override fun close() = Unit

    override fun sendUndo(force: Boolean) = Unit
    override fun sendBoard(board: Board) = Unit
}
