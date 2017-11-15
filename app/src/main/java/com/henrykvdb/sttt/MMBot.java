package com.henrykvdb.sttt;

import com.flaghacker.uttt.common.*;
import com.flaghacker.uttt.common.Timer;

import java.util.*;

import static com.flaghacker.uttt.common.Player.ENEMY;
import static com.flaghacker.uttt.common.Player.PLAYER;
import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.POSITIVE_INFINITY;

public class MMBot implements Bot {
	private static final long serialVersionUID = 5157670414972977360L;

	private static final double TILE_VALUE = 1;
	private static final double MACRO_VALUE = 100;

	private static final double CENTER_FACTOR = 4;
	private static final double CORNER_FACTOR = 3;
	private static final double EDGE_FACTOR = 1;

	private transient Random random = Util.loggedRandom();
	private transient Timer timer;
	private int levels;

	public MMBot(int levels) {
		this.levels = levels;
	}

	@Override
	public Coord move(Board board, Timer timer) {
		this.timer = timer;

		if (levels == 0)
			return board.availableMoves().get(random.nextInt(board.availableMoves().size()));

		if (board.freeTiles().size() == 81) //First move
			return Coord.coord(4, 4);

		if (board.nextPlayer() != PLAYER)
			board = board.flip();

		Coord move = negaMax(board, levels, NEGATIVE_INFINITY, POSITIVE_INFINITY, 1).move;
		return timer.isInterrupted() ? null : move;
	}

	private ScoreMove negaMax(Board board, int depth, double a, double b, int player) {
		if (depth == 0 || board.isDone() || timer.isInterrupted())
			return new ScoreMove(board.getLastMove(), rateBoard(board) * player);

		double bestScore = NEGATIVE_INFINITY;
		Coord bestMove = null;

		for (Board child : children(board)) {
			if (timer.isInterrupted())
				break;

			double score = -negaMax(child, depth - 1, -b, -a, -player).score;

			if (bestMove == null || score > bestScore) {
				bestScore = score;
				bestMove = child.getLastMove();
			}

			a = Math.max(a, score);
			if (a >= b)
				break;
		}

		return new ScoreMove(bestMove, bestScore);
	}

	private class ScoreMove {
		private Coord move;
		private double score;

		public ScoreMove(Coord move, double score) {
			this.move = move;
			this.score = score;
		}
	}

	private List<Board> children(Board board) {
		List<Board> children = new ArrayList<>();

		for (Coord move : board.availableMoves()) {
			Board deepBoard = board.copy();
			deepBoard.play(move);

			children.add(deepBoard);
		}

		return children;
	}

	private double rateBoard(Board board) {
		//Winning games is good, losing is bad
		if (board.isDone()) {
			if (board.wonBy() == PLAYER)
				return POSITIVE_INFINITY;
			else if (board.wonBy() == ENEMY)
				return NEGATIVE_INFINITY + 1;
			else
				return NEGATIVE_INFINITY;
		}

		double score = 0;

		//Gives score for tiles
		for (Coord coord : Coord.list())
			score += TILE_VALUE * tileFactor(coord.os()) * tileFactor(coord.om()) * sign(board.tile(coord));

		//Winning macros may be good or and losing them could be bad
		for (int om = 0; om < 9; om++) {
			Player owner = board.macro(om);
			if (owner != Player.NEUTRAL)
				score += tileFactor(om) * MACRO_VALUE * sign(owner);
		}

		return score;
	}

	private double tileFactor(int o) {
		int x = o % 3;
		int y = o / 3;

		if (x == 1 && y == 1)
			return CENTER_FACTOR;
		if (x == 1 || y == 1)
			return EDGE_FACTOR;
		return CORNER_FACTOR;
	}

	private int sign(Player p) {
		if (p == PLAYER)
			return 1;
		else if (p == ENEMY)
			return -1;
		else
			return 0;
	}

	@Override
	public String toString() {
		return "MMBot";
	}
}
