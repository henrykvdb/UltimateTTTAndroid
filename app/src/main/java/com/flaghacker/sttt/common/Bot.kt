package com.flaghacker.sttt.common

import java.io.Serializable

interface Bot : Serializable {
	fun move(board: Board, timer: Timer): Byte?
}
