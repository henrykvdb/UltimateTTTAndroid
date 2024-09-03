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

import android.content.SharedPreferences
import androidx.core.content.edit
import bots.MCTSBotDropoff
import bots.RandomBot
import common.Board
import common.Bot
import java.io.Serializable

enum class Source { LOCAL, AI, REMOTE }
private fun sourceFromOrdinal(ordinal: Int): Source {
    return enumValues<Source>().firstOrNull { it.ordinal == ordinal }!!
}

open class GameState : Serializable {
    // State
    var players = Pair(Source.LOCAL, Source.LOCAL); private set
    var history = mutableListOf(-1)
    private var extraBotDifficulty = 0
    var remoteId = ""; private set

    // Derived from state
    var board = Board(); private set
    var extraBot: Bot = RandomBot(); private set


    /** Access methods (not extendable) **/

    // Player access methods
    @Synchronized fun nextSource() = if (history.size % 2 == 1) players.first else players.second
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
        this.remoteId = ""
        if (this.type == Source.REMOTE){
            this.players = Pair(Source.LOCAL, Source.LOCAL)
        }
    }

    @Synchronized open fun newAi(swapped: Boolean, difficulty: Int){
        this.players = if (swapped) Pair(Source.AI, Source.LOCAL) else Pair(Source.LOCAL, Source.AI)
        this.board = Board()
        this.history = mutableListOf(-1)
        this.extraBotDifficulty = difficulty
        this.extraBot = createBot(difficulty)
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
            repeat(count) { history.removeAt(history.lastIndex) }
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

        fun createBot(difficulty: Int): Bot {
            return when(difficulty){
                0 -> RandomBot()
                1 -> MCTSBotDropoff(6 * 350    , 350    , 20)
                2 -> MCTSBotDropoff(6 * 2_200  , 2_200  , 20)
                3 -> MCTSBotDropoff(6 * 8_000  , 8_000  , 20)
                4 -> MCTSBotDropoff(6 * 20_000 , 20_000 , 20)
                5 -> MCTSBotDropoff(6 * 50_000 , 50_000 , 20)
                6 -> MCTSBotDropoff(6 * 100_000, 100_000, 20)
                else -> throw Exception("No such difficulty")
            }
        }

        fun sharedPrefStore(sharedPref: SharedPreferences, gs: GameState) {
            sharedPref.edit {
                log(gs.history.toString())
                gs.history.forEachIndexed { i, move -> putInt("MOVE$i", move) }
                putInt("MOVE${gs.history.size}", Int.MAX_VALUE) // end marker
                putInt("X", gs.players.first.ordinal)
                putInt("O", gs.players.second.ordinal)
                putInt("difficulty", gs.extraBotDifficulty)
            }
        }

        fun sharedPrefCreate(sharedPref: SharedPreferences): GameState {
            val gs = GameState()
            val x = sharedPref.getInt("X",-1)
            val o = sharedPref.getInt("O", -1)

            // No previous save game, early return
            if(x == -1 || o == -1)
                return gs
            else return gs.apply {
                // Re-create players
                players = Pair(sourceFromOrdinal(x), sourceFromOrdinal(o))

                // Re-create bot
                val difficulty = sharedPref.getInt("difficulty", 0)
                if (difficulty > 0){
                    extraBotDifficulty = difficulty
                    extraBot = createBot(difficulty)
                }

                // Re-create board
                for (i in 1..81){
                    val move = sharedPref.getInt("MOVE$i", Int.MAX_VALUE)
                    if (move != Int.MAX_VALUE)
                        history.add(move)
                    else break

                }
                board = boardFromHistory(history)
            }
        }
    }
}
