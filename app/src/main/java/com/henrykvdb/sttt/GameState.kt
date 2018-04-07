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

class GameState private constructor(val players: Players, val boards: LinkedList<Board>, val extraBot: Bot) : Serializable {
    fun board() = boards.peek()!!
    fun pushBoard(board: Board) = boards.push(board)
    fun popBoard() {
        boards.pop()
    }

    fun isHuman() = (players.first == Source.LOCAL && players.second == Source.LOCAL)
    fun isRemote() = players.contains(Source.REMOTE)
    fun isAi() = players.contains(Source.AI)

    fun nextSource() = if (board().nextPlayer() == Player.PLAYER) players.first else players.second
    fun otherSource() = if (board().nextPlayer() == Player.PLAYER) players.second else players.first

    companion object {
        private const val serialVersionUID = -3051602110955747927L
    }

    class Players(val first: Source, val second: Source) : Serializable {
        operator fun contains(source: Source) = (first == source || second == source)
        fun swap(): Players = Players(second, first)

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