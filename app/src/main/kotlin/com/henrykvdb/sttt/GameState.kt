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

import bots.MCTSBot
import bots.RandomBot
import common.Board
import common.Bot
import java.io.Serializable

enum class Source { LOCAL, AI, REMOTE }

open class GameState : Serializable {
    var players = Pair(Source.LOCAL, Source.LOCAL); private set
    var board = Board(); private set
    var history = mutableListOf(-1)
    var extraBot: Bot = RandomBot(); private set
    var remoteId = ""; private set

    /** Access methods (not extendable) **/

    // Player access methods
    @Synchronized fun nextSource() = if (board.nextPlayX) players.first else players.second
    @Synchronized fun otherSource() = if (board.nextPlayX) players.second else players.first
    @get:Synchronized val swapped : Boolean get() = players.second == Source.LOCAL
    @get:Synchronized val type : Source get() = when {
        players.first == Source.REMOTE || players.second == Source.REMOTE -> Source.REMOTE
        players.first == Source.AI || players.second == Source.AI -> Source.AI
        else -> Source.LOCAL
    }

    /** Play method **/

    @Throws(IllegalArgumentException::class)
    @Synchronized open fun play(source: Source, move: Byte): Boolean {
        val play = board.availableMoves.contains(move) && source == nextSource()
        if (play){
            board.play(move)
            history.add(move.toInt() and 0xFF)
        }
        return play
    }

    /** Methods to start new games **/

    @Synchronized open fun newLocal(board: Board = Board()){ // TODO using the board arg gives an invalid state
        this.players = Pair(Source.LOCAL, Source.LOCAL)
        this.board = Board()
        this.history = mutableListOf(-1)
        this.remoteId = ""
    }

    @Synchronized open fun turnLocal(){
        this.players = Pair(Source.LOCAL, Source.LOCAL)
        this.remoteId = ""
    }

    @Synchronized open fun newAi(swapped: Boolean, difficulty: Int){
        this.players = if (swapped) Pair(Source.AI, Source.LOCAL) else Pair(Source.LOCAL, Source.AI)
        this.board = Board()
        this.history = mutableListOf(-1)
        this.extraBot = MCTSBot(25_000_000) // TODO based on difficulty
        this.remoteId = ""
    }

    @Synchronized open fun newRemote(swapped: Boolean, history: List<Int>, remoteId: String){
        this.players = if (swapped) Pair(Source.REMOTE, Source.LOCAL) else Pair(Source.LOCAL, Source.REMOTE)
        this.history = history.toMutableList()
        this.remoteId = remoteId
        this.board = boardFromHistory(history)
    }

    @Synchronized fun undo(count:Int = 1): Boolean {
        if (history.size > count) {
            repeat(count) { history.removeLast() }
            this.board = boardFromHistory(history)
            return true
        }
        return false
    }

    companion object {
        private const val serialVersionUID: Long = 5744699802048496982L

        private fun boardFromHistory(history: List<Int>) = Board().apply {
                history.forEachIndexed { idx, mv -> if (idx > 0) this.play(mv.toByte()) }
        }
    }
}
