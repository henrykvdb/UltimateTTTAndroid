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

import com.flaghacker.sttt.bots.RandomBot
import com.flaghacker.sttt.common.Board
import com.flaghacker.sttt.common.Bot
import com.flaghacker.sttt.common.Player
import java.io.Serializable
import java.util.*

enum class Source {
	LOCAL,
	AI,
	REMOTE
}

class GameState private constructor(val players: Players, val boards: LinkedList<Board>, val extraBot: Bot, val remoteId: String) : Serializable {
	val board get() = boards.peek()!!
	fun pushBoard(board: Board) = boards.push(board)
	fun popBoard() = boards.pop() ?: null

	val type = when {
		Source.REMOTE in players -> Source.REMOTE
		Source.AI in players -> Source.AI
		else -> Source.LOCAL
	}

	fun nextSource() = if (board.nextPlayer == Player.PLAYER) players.first else players.second
	fun otherSource() = if (board.nextPlayer == Player.PLAYER) players.second else players.first
	class Players(val first: Source, val second: Source) : Serializable {
		operator fun contains(source: Source) = (first == source || second == source)
		fun swap() = Players(second, first)

		companion object {
			private const val serialVersionUID = 5619757295352382870L
		}
	}

	class Builder {
		private var remoteId: String = ""
		private var players = Players(Source.LOCAL, Source.LOCAL)
		private var boards = listOf(Board())
		private var swapped = false
		private var extraBot: Bot = RandomBot()

		fun build() = GameState(if (swapped) players.swap() else players, LinkedList(boards), extraBot, remoteId)
		fun boards(boards: List<Board>) = apply { this.boards = LinkedList(boards) }
		fun board(board: Board) = this.boards(listOf(board))
		fun swapped(swapped: Boolean) = apply { this.swapped = swapped }
		fun ai(extraBot: Bot) = apply { this.extraBot = extraBot;players = Players(Source.LOCAL, Source.AI) }
		fun remote(id:String) = apply { players = Players(Source.LOCAL, Source.REMOTE); this.remoteId = id }
		fun gs(gs: GameState) = apply {
			players = gs.players
			boards = gs.boards
			extraBot = gs.extraBot
			remoteId = gs.remoteId
		}
	}

	companion object {
		private const val serialVersionUID = -3051602110955747927L
	}
}
