/*
 * This file is part of Ultimate Tic Tac Toe.
 * Copyright (C) 2023 Henryk Van der Bruggen <henrykdev@gmail.com>
 *
 * This work is licensed under a Creative Commons
 * Attribution-NonCommercial-NoDerivatives 4.0 International License.
 *
 * You should have received a copy of the CC NC ND License along
 * with Ultimate Tic Tac Toe.  If not, see <https://creativecommons.org/>.
 */

package com.henrykvdb.sttt

import com.flaghacker.sttt.common.Board

/**Callback used to callback data from the remoteGame to the RemoteService*/
interface RemoteCallback {
	fun move(move: Byte)
	fun newGame(gs: GameState)
	fun undo(force: Boolean)
	fun toast(text: String)
	fun turnLocal()
}

class RemoteGame(){
	fun listen(gs: GameState){}
	fun connect(remoteId: Int){}
	fun close(){}

	fun sendUndo(ask: Boolean){}
	fun sendBoard(board: Board){}
}

fun isValidBoard(cBoard: Board, newBoard: Board): Boolean {
	if (!cBoard.availableMoves.contains(newBoard.lastMove!!)) return false
	return newBoard == cBoard.copy().apply { play(newBoard.lastMove!!) }
}
