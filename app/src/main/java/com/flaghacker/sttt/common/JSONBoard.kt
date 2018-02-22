package com.flaghacker.sttt.common

import org.json.JSONArray
import org.json.JSONObject

fun Board.toJSON(): JSONObject {
	val json = JSONObject()
	val jsonBoard = JSONArray()
	for (i in 0 until 81) jsonBoard.put(tile(i.toByte()).toNiceString())

	json.put("board", jsonBoard)
	json.put("macroMask", macroMask())
	json.put("lastMove", lastMove()?.toInt())

	return json
}

class JSONBoard {
	companion object {
		fun fromJSON(json: JSONObject): com.flaghacker.sttt.common.Board {
			val board = Array(9, { Array(9, { Player.NEUTRAL }) })
			for (i in 0 until 81)
				board[i.toPair().first][i.toPair().second] = fromNiceString(json.getJSONArray("board").getString(i))
			val macroMask = json.getInt("macroMask")
			val lastMove = if (json.length() == 3) json.getInt("lastMove") else null

			return Board(board, macroMask, lastMove?.toByte())
		}
	}
}
