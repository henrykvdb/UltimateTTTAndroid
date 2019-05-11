/*
 * This file is part of Super Tic Tac Toe.
 * Copyright (C) 2018 Henryk Van der Bruggen <henrykdev@gmail.com>
 *
 * Super Tic Tac Toe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Super Tic Tac Toe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Super Tic Tac Toe.  If not, see <http://www.gnu.org/licenses/>.
 */

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

	fun sendUndo(ask: Boolean)
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

	override fun sendUndo(ask: Boolean) = Unit
	override fun sendBoard(board: Board) = Unit
}

fun isValidBoard(cBoard: Board, newBoard: Board): Boolean {
	if (!cBoard.availableMoves.contains(newBoard.lastMove!!)) return false
	return newBoard == cBoard.copy().apply { play(newBoard.lastMove!!) }
}
