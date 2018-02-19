package com.flaghacker.sttt.games

import com.flaghacker.sttt.common.Board
import com.flaghacker.sttt.common.Bot
import com.flaghacker.sttt.common.Player
import com.flaghacker.sttt.common.moveBotWithTimeOut
import java.util.*

class BotGame(private val p1: Bot, private val p2: Bot) {
	private var count = 1
	private var logLevel = LogLevel.ALL
	private var timePerMove = 500
	private var shuffling: Boolean = false
	private var random = Random()

	fun setLogLevel(logLevel: LogLevel) = apply { this.logLevel = logLevel }
	fun setTimePerMove(time: Int) = apply { this.timePerMove = time }
	fun setShuffling(shuffling: Boolean) = apply { this.shuffling = shuffling }
	fun setRandomSeed(seed: Long) = apply { this.random = Random(seed) }
	fun setCount(count: Int) = apply {
		this.count = count
		if (count >= 100) this.logLevel = LogLevel.BASIC
	}

	enum class LogLevel {
		NONE,
		BASIC,
		ALL
	}

	fun run(): IntArray {
		val results = IntArray(3)

		for (i in 0 until count) {
			if (count <= 100 || i % (count / 100) == 0)
				printImportant("starting game $i; ${i.toDouble() / count}")

			val swapped = shuffling && random.nextBoolean()
			val p1 = if (swapped) this.p2 else this.p1
			val p2 = if (swapped) this.p1 else this.p2

			val board = Board()

			var nextRound = 0
			while (!board.isDone()) {
				printDetail("Round #" + nextRound++)

				val pMove = moveBotWithTimeOut(p1, board.copy(), timePerMove.toLong())
				printDetail("p1 move: " + pMove!!)
				board.play(pMove)

				printDetail(board.toString())

				if (board.isDone())
					continue

				val rMove = moveBotWithTimeOut(p2, board.copy(), timePerMove.toLong())
				printDetail("p2 move: " + rMove!!)
				board.play(rMove)

				printDetail(board.toString())
			}

			val wonBy = if (!swapped) board.wonBy() else board.wonBy().otherWithNeutral()

			printDetail("done, won by: ${board.wonBy()} swapped: $swapped")
			results[if (wonBy == Player.PLAYER) 0 else if (wonBy == Player.ENEMY) 2 else 1]++
		}

		printImportant("Results:")
		printImportant("$p1 Win:\t${results[0].toDouble() / count}")
		printImportant("Tie:\t\t\t${results[1].toDouble() / count}")
		printImportant("$p2 Win:\t${results[2].toDouble() / count}")

		return results
	}

	private fun printDetail(message: String) {
		if (logLevel == LogLevel.ALL) println(message)
	}

	private fun printImportant(message: String) {
		if (logLevel.ordinal > LogLevel.NONE.ordinal)
			println(message)
	}
}
