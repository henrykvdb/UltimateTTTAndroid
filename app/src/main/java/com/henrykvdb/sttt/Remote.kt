package com.henrykvdb.sttt

import com.flaghacker.sttt.common.Board

/**Messages send between MainActivity and RemoteService using the EventBus library*/
class RemoteMessage{
    enum class Type {
        MOVE,
        NEWGAME,
        UNDO,
        TOAST,
        TURNLOCAL,
    }
}

/**States the remoteGame game can have*/
enum class RemoteState {
    NONE,
    LISTENING,
    CONNECTING,
    CONNECTED
}

/**Types of messages the remoteGame game can send*/
enum class RemoteMessageType {
    UNDO,
    SETUP,
    BOARD_UPDATE;
}

/**Callback used to callback data to the UI*/
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

    override fun listen(gs: GameState) = Unit
    override fun connect(adr: String) = Unit
    override fun close() = Unit

    override fun sendUndo(force: Boolean) = Unit
    override fun sendBoard(board: Board) = Unit
    override fun lastBoard() = throw IllegalStateException("DummyGame's lastBoard method should not be called")
}