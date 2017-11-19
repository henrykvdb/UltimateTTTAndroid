package com.henrykvdb.sttt.Util;

import com.flaghacker.uttt.common.Board;

public class Util {
	public static boolean isValidBoard(Board cBoard, Board newBoard) {
		if (!cBoard.availableMoves().contains(newBoard.getLastMove()))
			return false;

		Board verifyBoard = cBoard.copy();
		verifyBoard.play(newBoard.getLastMove());

		return verifyBoard.equals(newBoard);
	}
}
