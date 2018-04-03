package com.flaghacker.sttt.bots

import com.flaghacker.sttt.common.Board
import com.flaghacker.sttt.common.Bot
import com.flaghacker.sttt.common.Player
import com.flaghacker.sttt.common.Timer
import java.lang.Double.NEGATIVE_INFINITY
import java.lang.Double.POSITIVE_INFINITY
import java.lang.Math.max

class MMBot(private val depth: Int) : Bot {
	override fun move(board: Board, timer: Timer): Byte? {
		return negaMax(board, value(board), depth,
                NEGATIVE_INFINITY, POSITIVE_INFINITY, playerSign(board.nextPlayer())).move
	}

	private class ValuedMove(val move: Byte, val value: Double)

	private fun negaMax(board: Board, cValue: Double, depth: Int, a: Double, b: Double, player: Int): ValuedMove {
		if (depth == 0 || board.isDone()) return ValuedMove(board.lastMove()!!, player * cValue)

		var bestValue = NEGATIVE_INFINITY
		var bestMove: Byte? = null
		var newA = a

		for (move in board.availableMoves()) {
			val child = board.copy()

			//Calculate the new score
			var childValue = cValue + TILE_VALUE * factor(move % 9) * factor(move / 9) * player
			if (child.play(move)) {
				if (child.isDone()) childValue = POSITIVE_INFINITY * player
				else childValue += MACRO_VALUE * factor(move / 9) * player
			}

			//Check if the (global) value of this child is better then the previous best child
			val value = -negaMax(child, childValue, depth - 1, -b, -newA, -player).value
			if (value > bestValue || bestMove == null) {
				bestValue = value
				bestMove = child.lastMove()
			}
			newA = max(newA, value)
			if (newA >= b) break
		}

		return ValuedMove(bestMove!!, bestValue)
	}

	private fun value(board: Board) = if (board.isDone()) POSITIVE_INFINITY * playerSign(board.wonBy())
	else {
		(0 until 81).sumByDouble {
			TILE_VALUE * factor(it % 9) * factor(it / 9) * playerSign(board.tile(it.toByte())).toDouble()
		} + (0 until 9).sumByDouble {
			MACRO_VALUE * factor(it) * playerSign(board.macro(it.toByte())).toDouble()
		}
	}

	private fun playerSign(player: Player) = when (player) {
		Player.NEUTRAL -> 0
		Player.PLAYER -> 1
		Player.ENEMY -> -1
	}

	private fun factor(os: Int) = when {
		os == 4 -> CENTER_FACTOR
		os % 2 == 0 -> CORNER_FACTOR
		else -> EDGE_FACTOR
	}

	override fun toString() = "MMBot"

	companion object {
		private const val TILE_VALUE = 1.0
		private const val MACRO_VALUE = 10e9

		private const val CENTER_FACTOR = 4.0
		private const val CORNER_FACTOR = 3.0
		private const val EDGE_FACTOR = 1.0
	}
}
