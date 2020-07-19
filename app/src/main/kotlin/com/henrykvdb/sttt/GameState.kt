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

package sttt

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

class GameState private constructor(val players: Players, val boards: LinkedList<Board>, val extraBot: Bot) : Serializable {
	fun board() = boards.peek()!!
	fun pushBoard(board: Board) = boards.push(board)
	fun popBoard() = boards.pop() ?: null

	val type = when {
		Source.REMOTE in players -> Source.REMOTE
		Source.AI in players -> Source.AI
		else -> Source.LOCAL
	}

	fun nextSource() = if (board().nextPlayer == Player.PLAYER) players.first else players.second
	fun otherSource() = if (board().nextPlayer == Player.PLAYER) players.second else players.first

	companion object {
		private const val serialVersionUID = -3051602110955747927L
	}

	class Players(val first: Source, val second: Source) : Serializable {
		operator fun contains(source: Source) = (first == source || second == source)
		fun swap() = Players(second, first)

		companion object {
			private const val serialVersionUID = 5619757295352382870L
		}
	}

	class Builder {
		private var players = Players(Source.LOCAL, Source.LOCAL)
		private var boards = listOf(Board())
		private var swapped = false
		private var extraBot: Bot = RandomBot()

		fun build(): GameState = GameState(if (swapped) players.swap() else players, LinkedList(boards), extraBot)
		fun boards(boards: List<Board>) = apply { this.boards = LinkedList(boards) }
		fun board(board: Board): Builder = this.boards(listOf(board))
		fun swapped(swapped: Boolean) = apply { this.swapped = swapped }
		fun ai(extraBot: Bot) = apply { this.extraBot = extraBot;players = Players(Source.LOCAL, Source.AI) }
		fun bt() = apply { players = Players(Source.LOCAL, Source.REMOTE) }
		fun gs(gs: GameState) = apply {
			players = gs.players
			boards = gs.boards
			extraBot = gs.extraBot
		}
	}
}