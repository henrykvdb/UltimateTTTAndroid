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
