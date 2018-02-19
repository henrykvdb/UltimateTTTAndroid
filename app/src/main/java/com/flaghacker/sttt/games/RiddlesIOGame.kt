package com.flaghacker.sttt.games

import com.flaghacker.sttt.common.Board
import com.flaghacker.sttt.common.Bot
import com.flaghacker.sttt.common.Player
import com.flaghacker.sttt.common.Timer
import java.util.*

class RiddlesIOGame(private val bot: Bot) {
	private val scan: Scanner = Scanner(System.`in`)
	private var timePerMove: Int = 0
	private var maxTimebank: Int = 0
	private var timeBank: Int = 0
	private var roundNumber: Int = 0
	private var myName: String? = null
	private var myId = 0

	fun board() = Board(board, macroMask, null)
	private var board = Array(9, { Array(9, { Player.NEUTRAL }) })
	private var macroMask = 0b111111111

	fun run() {
		while (scan.hasNextLine()) {
			val line = scan.nextLine()

			if (line.isEmpty()) continue

			val parts = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
			when (parts[0]) {
				"settings" -> parseSettings(parts[1], parts[2])
				"update" -> if (parts[1] == "game") {
					parseGameData(parts[2], parts[3])
				}
				"action" -> if (parts[1] == "move") { /* move requested */
					timeBank = Integer.parseInt(parts[2])
					val move = bot.move(Board(board, macroMask, null), Timer(80))!!.toInt()

					val os = move % 9
					val om = move / 9

					val x = (om % 3) * 3 + os % 3
					val y = (om / 3) * 3 + os / 3

					println("place_move $x $y")
				}
				else -> println("unknown command")
			}
		}
	}

	private fun parseSettings(key: String, value: String) {
		when (key) {
			"timebank" -> {
				val time = Integer.parseInt(value)
				maxTimebank = time
				timeBank = time
			}
			"player_names" -> { }
			"time_per_move" -> timePerMove = Integer.parseInt(value)
			"your_bot" -> myName = value
			"your_botid" -> myId = Integer.parseInt(value)
			else -> throw RuntimeException("Cannot parse game data input with key $key")
		}

	}

	private fun parseGameData(key: String, value: String) {
		when (key) {
			"round" -> roundNumber = Integer.parseInt(value)
			"field" -> {
				val parsed = value.replace(",", "")
				if (parsed.length != 81 || parsed.replace("X", "").replace("O", "").isBlank())
					throw IllegalArgumentException("board string formatted incorrectly (input: $parsed)")

				board = Array(9, { Array(9, { Player.NEUTRAL }) })
				(0 until 81).filter { parsed[it] != '.' }.forEach {
					board[it % 9][it / 9] = if (Character.getNumericValue(parsed[it]) == myId) Player.PLAYER else Player.ENEMY
				}
			}
			"macroboard" -> {
				val parsed = value.split(',')
				if (parsed.size != 9)
					throw IllegalArgumentException("macro mask formatted incorrectly (input: $parsed)")

				macroMask = 0
				for (i in 0 until 9) {
					val macro = parsed[i]
					if (macro == "-1") macroMask += 1 shl i
				}
			}
			else -> throw RuntimeException("Cannot parse game data input with key $key")
		}

	}
}
