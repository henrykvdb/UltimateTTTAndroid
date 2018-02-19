package com.flaghacker.sttt.bots

import com.flaghacker.sttt.common.Board
import com.flaghacker.sttt.common.Bot
import com.flaghacker.sttt.common.Timer
import java.util.*

class RandomBot : Bot {
    private val random: Random

    constructor() {
        random = Random()
    }

    constructor(seed: Int) {
        random = Random(seed.toLong())
    }

    override fun move(board: Board, timer: Timer): Byte? {
        val moves = board.availableMoves()
        return moves[random.nextInt(moves.size)]
    }

    override fun toString(): String {
        return "RandomBot"
    }
}
