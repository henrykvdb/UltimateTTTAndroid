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
import java.util.LinkedList

enum class Source { LOCAL, AI, REMOTE }

open class GameState(
    players: Pair<Source, Source> = Pair(Source.LOCAL, Source.LOCAL),
    boards: LinkedList<Board> = LinkedList(listOf(Board())),
    extraBot: Bot = RandomBot(),
    remoteId: String = ""
) : Serializable {
    var players: Pair<Source, Source> = players; private set
    var boards: LinkedList<Board> = boards; private set
    var extraBot: Bot = extraBot; private set
    var remoteId: String = remoteId; private set

    /** Access methods (not extendable) **/

    // Player access methods
    @Synchronized fun nextSource() = if (board.nextPlayX) players.first else players.second
    @Synchronized fun otherSource() = if (board.nextPlayX) players.second else players.first
    @get:Synchronized val type : Source get() = when {
        players.first == Source.REMOTE || players.second == Source.REMOTE -> Source.REMOTE
        players.first == Source.AI || players.second == Source.AI -> Source.AI
        else -> Source.LOCAL
    }

    // Board access methods
    @Synchronized fun pushBoard(board: Board) = boards.push(board)
    @Synchronized fun popBoard() = boards.pop() ?: null
    @get:Synchronized val board get() = boards.peek()!!

    /** Play method **/

    @Synchronized open fun play(source: Source, move: Byte) {
        if(board.availableMoves.contains(move) && source == nextSource())
            board.play(move)
    }

    /** Methods to start new games **/

    @Synchronized open fun newLocal(){
        this.players = Pair(Source.LOCAL, Source.LOCAL)
        this.boards = LinkedList(listOf(Board()))
        this.remoteId = ""
    }

    @Synchronized open fun newAi(swapped: Boolean, difficulty: Int){
        this.players = if (swapped) Pair(Source.AI, Source.LOCAL) else Pair(Source.LOCAL, Source.AI)
        this.boards = LinkedList(listOf(Board()))
        this.extraBot = MCTSBot(100*25_000) // TODO based on difficulty
        this.remoteId = ""
    }

    @Synchronized open fun newRemote(swapped: Boolean, board: Board, remoteId: String){
        this.players = if (swapped) Pair(Source.REMOTE, Source.LOCAL) else Pair(Source.LOCAL, Source.REMOTE)
        this.boards = LinkedList(listOf(board))
        this.remoteId = remoteId
    }

    companion object {
        private const val serialVersionUID: Long = 5744699802048496982L
    }
}
