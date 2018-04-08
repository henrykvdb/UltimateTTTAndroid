package com.henrykvdb.sttt.util

import com.flaghacker.sttt.common.Board

fun isValidBoard(cBoard: Board, newBoard: Board): Boolean {
    if (!cBoard.availableMoves().contains(newBoard.lastMove()!!)) return false

    val verifyBoard = cBoard.copy()
    verifyBoard.play(newBoard.lastMove()!!)

    return verifyBoard == newBoard
}