package com.henrykvdb.sttt

import com.flaghacker.sttt.common.Board
import com.flaghacker.sttt.common.Bot
import com.flaghacker.sttt.common.Player
import com.flaghacker.sttt.games.BotGame
import java.util.*

// TODO remove after API upstep benchmarks are complete
class BotGameTiming(private val p1: Bot, private val p2: Bot) {
    private var count = 1
    private var logLevel = BotGame.LogLevel.ALL
    private var shuffling: Boolean = false
    private var random = Random()

    fun setLogLevel(logLevel: BotGame.LogLevel) = apply { this.logLevel = logLevel }
    fun setShuffling(shuffling: Boolean) = apply { this.shuffling = shuffling }
    fun setRandomSeed(seed: Long) = apply { this.random = Random(seed) }
    fun setCount(count: Int) = apply {
        this.count = count
        if (count >= 100) this.logLevel = BotGame.LogLevel.BASIC
    }

    fun run(): IntArray {
        val results = IntArray(3)
        val times = LongArray(2)
        val moves = LongArray(2)

        for (i in 0 until count) {
            if (count <= 100 || i % (count / 100) == 0)
                printImportant("starting game $i; ${i.toDouble() / count}")

            val swapped = shuffling && random.nextBoolean()
            val p1 = if (swapped) this.p2 else this.p1
            val p2 = if (swapped) this.p1 else this.p2

            val board = Board()
            var board_copy = board.copy()

            var nextRound = 0
            while (!board.isDone) {
                printDetail("Round #" + nextRound++)

                board_copy = board.copy()
                var dt: Long = -System.nanoTime()
                val pMove = p1.move(board_copy)
                dt += System.nanoTime()
                times[if (swapped) 1 else 0] += dt
                moves[if (swapped) 1 else 0]++
                printDetail("p1 move: " + pMove!!)
                board.play(pMove)

                printDetail(board.toString())

                if (board.isDone)
                    continue

                board_copy = board.copy()
                dt = -System.nanoTime()
                val rMove = p2.move(board_copy)
                dt += System.nanoTime()
                times[if (swapped) 0 else 1] += dt
                moves[if (swapped) 0 else 1]++
                printDetail("p2 move: " + rMove!!)
                board.play(rMove)

                printDetail(board.toString())
            }

            val wonBy = if (!swapped) board.wonBy else board.wonBy?.otherWithNeutral()

            printDetail("done, won by: ${board.wonBy} swapped: $swapped")
            results[if (wonBy == Player.PLAYER) 0 else if (wonBy == Player.ENEMY) 2 else 1]++
        }

        printImportant("Results:")
        printImportant("$p1 Win:\t\t${results[0].toDouble() / count} TPM:\t${times[0] / 1e6 / moves[0]}")
        printImportant("Tie:\t\t\t${results[1].toDouble() / count}")
        printImportant("$p2 Win:\t${results[2].toDouble() / count} TPM:\t${times[1] / 1e6 / moves[1]}")

        return results
    }

    private fun printDetail(message: String) {
        if (logLevel == BotGame.LogLevel.ALL)
            println(message)
    }

    private fun printImportant(message: String) {
        if (logLevel.ordinal > BotGame.LogLevel.NONE.ordinal)
            println(message)
    }
}
