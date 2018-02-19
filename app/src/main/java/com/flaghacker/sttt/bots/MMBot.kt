package com.flaghacker.sttt.bots

import com.flaghacker.sttt.common.Board
import com.flaghacker.sttt.common.Bot
import com.flaghacker.sttt.common.Player
import com.flaghacker.sttt.common.Timer
import java.lang.Double.NEGATIVE_INFINITY
import java.lang.Double.POSITIVE_INFINITY
import java.lang.Math.max
import java.util.*

class MMBot(private val depth: Int) : Bot {
	override fun move(board: Board, timer: Timer): Byte? {
        if(depth==0) throw IllegalArgumentException("Minimum MMBot depth is 1")
		return negaMax(board, depth, NEGATIVE_INFINITY, POSITIVE_INFINITY, playerSign(board.nextPlayer())).move
	}

	private class ValuedMove(val move: Byte, val value: Double)

	private fun negaMax(board: Board, depth: Int, a: Double, b: Double, player: Int): ValuedMove {
		if (depth == 0 || board.isDone())
			return ValuedMove(board.lastMove()!!, player * value(board))

		val children = children(board)

		var bestValue = NEGATIVE_INFINITY
		var bestMove: Byte? = null

		var newA = a
		for (child in children) {
			val value = -negaMax(child, depth - 1, -b, -newA, -player).value

			if (value > bestValue || bestMove == null) {
				bestValue = value
				bestMove = child.lastMove()
			}
			newA = max(newA, value)
			if (newA >= b)
				break
		}

		return ValuedMove(bestMove!!, bestValue)
	}

	private fun children(board: Board): List<Board> {
		val moves = board.availableMoves()
		val children = ArrayList<Board>(moves.size)

		for (move in moves) {
			val child = board.copy()
			child.play(move)
			children.add(child)
		}

		return children
	}

	private fun value(board: Board) = when (board.isDone()) {
		true -> Double.POSITIVE_INFINITY * playerSign(board.wonBy())
		false -> {
			(0..80).sumByDouble {
				TILE_VALUE * tileFactor(it % 9) * tileFactor(it / 9) * playerSign(board.tile(it.toByte())).toDouble()
			} + (0..8).sumByDouble {
				MACRO_VALUE * tileFactor(it) * playerSign(board.macro(it.toByte())).toDouble()
			}
		}
	}

	private fun playerSign(player: Player) = when (player) {
		Player.NEUTRAL -> 0
		Player.PLAYER -> 1
		Player.ENEMY -> -1
	}

	private fun tileFactor(os: Int) = when {
		os == 4 -> CENTER_FACTOR
		os % 2 == 0 -> CORNER_FACTOR
		else -> EDGE_FACTOR
	}

	override fun toString() = "MMBotJava"

	companion object {
		private const val TILE_VALUE = 1.0
		private const val MACRO_VALUE = 10e9

		private const val CENTER_FACTOR = 4.0
		private const val CORNER_FACTOR = 3.0
		private const val EDGE_FACTOR = 1.0
	}
}
