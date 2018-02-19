package com.flaghacker.sttt.common

import java.io.Serializable
import java.util.*

typealias Coord = Byte

fun toCoord(x: Int, y: Int) = (((x / 3) + (y / 3) * 3) * 9 + ((x % 3) + (y % 3) * 3)).toByte()
fun Int.toPair() = toByte().toPair()
fun Coord.toPair(): Pair<Int, Int> {
	val om = this / 9
	val os = this % 9
	return Pair((om % 3) * 3 + (os % 3), (om / 3) * 3 + (os / 3))
}

class Board : Serializable {
	/*
	Each element represents a row of macros (3 rows of 9 tiles)
	The first 3 Ints hold the macros for Player
	The next 3 Ints hold the macros for Enemy

	In each Int the bit representation is as follows:
	aaaaaaaaabbbbbbbbbcccccccccABC with:
	a/b/c: bit enabled if the player is the owner of the tile
	A/B/C: bit enabled if the player won the macro
	*/
	private var rows: Array<Int> = Array(6) { 0 }
	private var macroMask = 0b111111111
	private var nextPlayer: Player = Player.PLAYER
	private var lastMove: Coord? = null
	private var wonBy = Player.NEUTRAL

	constructor()

	fun copy() = Board(this)

	constructor(board: Board) {
		rows = board.rows.copyOf()
		wonBy = board.wonBy
		nextPlayer = board.nextPlayer
		macroMask = board.macroMask
		lastMove = board.lastMove
	}

	constructor(board: Array<Array<Player>>, macroMask: Int, lastMove: Coord?) {
		if (board.size != 9 && board.all { it.size != 9 })
			throw IllegalArgumentException("Input board is the wrong size (input: $board)")
		else if (macroMask < 0 || macroMask > 0b111111111)
			throw IllegalArgumentException("Incorrect input macro mask (input: $macroMask)")

		val xCount1 = board.sumBy { it.filterNot { it == Player.NEUTRAL }.sumBy { if (it == Player.PLAYER) 1 else -1 } }
		var xCount = 0
		for (i in 0 until 81) {
			val macroShift = (i / 9) % 3 * 9
			val coords = i.toPair()
			val owner = board[coords.first][coords.second]

			if (owner != Player.NEUTRAL) {
				xCount += if (owner == Player.PLAYER) 1 else -1
				rows[i / 27 + owner.ordinal * 3] += 1 shl i % 27
				if (wonGrid((rows[i / 27 + owner.ordinal * 3] shr macroShift) and 0b111111111, i % 9)) {
					rows[i / 27 + owner.ordinal * 3] += (1 shl (27 + macroShift / 9)) //27 + macro number
					if (wonGrid(winGrid(owner), i / 9)) wonBy = nextPlayer
				}
			}
		}

		println(xCount1 == xCount)
		println("$xCount  $xCount1")

		this.lastMove = lastMove
		this.macroMask = macroMask
		nextPlayer = when (xCount) {
			-1, 0 -> Player.PLAYER
			1 -> Player.ENEMY
			else -> throw IllegalArgumentException("Input board is invalid (input: $board)")
		}
	}

	fun macroMask() = macroMask
	fun isDone() = wonBy != Player.NEUTRAL || availableMoves().isEmpty()
	fun nextPlayer() = nextPlayer
	fun lastMove() = lastMove
	fun wonBy() = wonBy

	fun flip(): Board {
		val board = copy()

		val newRows = Array(6) { 0 }
		for (i in 0..2) newRows[i] = board.rows[i + 3]
		for (i in 3..5) newRows[i] = board.rows[i - 3]
		board.rows = newRows
		board.wonBy = board.wonBy.otherWithNeutral()
		board.nextPlayer = board.nextPlayer.otherWithNeutral()

		return board
	}

	fun availableMoves(): List<Coord> {
		val output = ArrayList<Coord>()

		for (macro in 0 until 9) {
			if (macroMask.getBit(macro)) {
				val row = rows[macro / 3] or rows[macro / 3 + 3]
				(0 until 9).map { it + macro * 9 }.filter { !row.getBit(it % 27) }.mapTo(output) { it.toByte() }
			}
		}

		return output
	}

	fun macro(index: Byte): Player = when {
		rows[index / 3].getBit(27 + index % 3) -> Player.PLAYER
		rows[3 + index / 3].getBit(27 + index % 3) -> Player.ENEMY
		else -> Player.NEUTRAL
	}

	fun tile(index: Coord): Player = when {
		rows[index / 27].getBit(index % 27) -> Player.PLAYER
		rows[3 + index / 27].getBit(index % 27) -> Player.ENEMY
		else -> Player.NEUTRAL
	}

	fun play(index: Coord): Boolean {
		val row = index / 27                     //Row (0,1,2)
		val macroShift = (index / 9) % 3 * 9     //Shift to go to the right micro (9om)
		val moveShift = index % 9                //Shift required for index within matrix (os)
		val shift = moveShift + macroShift       //Total move offset in the row entry
		val pRow = nextPlayer.ordinal * 3 + row  //Index of the row entry in the rows array

		//If the move is not available throw exception
		if ((rows[row] or rows[row + 3]).getBit(shift) || !macroMask.getBit((index / 27) * 3 + (macroShift / 9)))
			throw RuntimeException("Position $index not available")
		else if (wonBy != Player.NEUTRAL)
			throw RuntimeException("Can't play; game already over")

		//Write move to board & check for macro win
		rows[pRow] += (1 shl shift)
		val macroWin = wonGrid((rows[pRow] shr macroShift) and 0b111111111, moveShift)

		//Check if the current player won
		if (macroWin) {
			rows[pRow] += (1 shl (27 + macroShift / 9))
			if (wonGrid(winGrid(nextPlayer), index / 9)) wonBy = nextPlayer
		}

		//Prepare the board for the next player
		val winGrid = winGrid(Player.PLAYER) or winGrid(Player.ENEMY)
		val freeMove = winGrid.getBit(moveShift) || macroFull(moveShift)
		macroMask = if (freeMove) (0b111111111 and winGrid.inv()) else (1 shl moveShift)
		lastMove = index
		nextPlayer = nextPlayer.other()

		return macroWin
	}

	private fun Int.getBit(index: Int) = ((this shr index) and 1) == 1
	private fun Int.isMaskSet(mask: Int) = this and mask == mask
	private fun macroFull(om: Int) = (rows[om / 3] or rows[3 + om / 3]).shr((om % 3) * 9).isMaskSet(0b111111111)
	private fun winGrid(player: Player) = (rows[0 + 3 * player.ordinal] shr 27)
			.or((rows[1 + 3 * player.ordinal] shr 27) shl 3)
			.or((rows[2 + 3 * player.ordinal] shr 27) shl 6)

	private fun wonGrid(grid: Int, index: Int) = when (index) {
		4 -> grid.getBit(1) && grid.getBit(7)        //Center: line |
				|| grid.getBit(3) && grid.getBit(5)  //Center: line -
				|| grid.getBit(0) && grid.getBit(8)  //Center: line \
				|| grid.getBit(6) && grid.getBit(2)  //Center: line /
		3, 5 -> grid.getBit(index - 3) && grid.getBit(index + 3) //Horizontal side: line |
				|| grid.getBit(4) && grid.getBit(8 - index)      //Horizontal side: line -
		1, 7 -> grid.getBit(index - 1) && grid.getBit(index + 1) //Vertical side: line |
				|| grid.getBit(4) && grid.getBit(8 - index)      //Vertical side: line -
		else -> { //Corners
			val x = index % 3
			val y = index / 3
			grid.getBit(4) && grid.getBit(8 - index)                                            //line \ or /
					|| grid.getBit(3 * y + (x + 1) % 2) && grid.getBit(3 * y + (x + 2) % 4)     //line -
					|| grid.getBit(x + ((y + 1) % 2) * 3) && grid.getBit(x + ((y + 2) % 4) * 3) //line |
		}
	}

	override fun toString() = (0 until 81).map { it to toCoord(it % 9, it / 9) }.joinToString("") {
		when {
			(it.first == 0 || it.first == 80) -> ""
			(it.first % 27 == 0) -> "\n---+---+---\n"
			(it.first % 9 == 0) -> "\n"
			(it.first % 3 == 0 || it.first % 6 == 0) -> "|"
			else -> ""
		} + when {
			rows[it.second / 27].getBit(it.second % 27) -> "X"
			rows[(it.second / 27) + 3].getBit(it.second % 27) -> "O"
			else -> " "
		}
	}

	override fun hashCode() = 31 * Arrays.hashCode(rows) + macroMask
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as Board

		if (!Arrays.equals(rows, other.rows)) return false
		if (macroMask != other.macroMask) return false

		return true
	}
}
