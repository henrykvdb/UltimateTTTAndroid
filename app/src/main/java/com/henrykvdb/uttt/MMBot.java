package com.henrykvdb.uttt;

import android.util.Log;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.Player;
import com.flaghacker.uttt.common.Timer;
import com.flaghacker.uttt.common.Util;

import java.util.Random;

public class MMBot implements Bot
{
	private Random random = Util.loggedRandom();

	private static final double TILE_VALUE = 1;
	private static final double MACRO_VALUE = 100;

	private static final double CENTER_FACTOR = 3;
	private static final double CORNER_FACTOR = 2;
	private static final double EDGE_FACTOR = 1;

	private final int levels = 3;
	private final int endLevels = 6;

	private Player player;
	private int difficulty = 100;
	private Timer timer;

	public MMBot(int difficulty)
	{
		this.difficulty = difficulty;
	}

	@Override
	public Coord move(Board board, Timer timer)
	{
		this.timer = timer;
		player = board.nextPlayer();

		int bestScore = Integer.MIN_VALUE;
		Coord bestMove = board.availableMoves().get(new Random().nextInt(board.availableMoves().size()));

		if (random.nextInt(100) > difficulty)
		{
			Log.d("MMBot", "Played random");
			return bestMove;
		}

		if (board.freeTiles().size() == 81)
			return Coord.coord(4, 4);

		for (Coord move : board.availableMoves())
		{
			if (timer.running())
			{
				Board testBoard = board.copy();
				testBoard.play(move);

				int minMax = miniMax(testBoard, board.freeTiles().size() < 30 ? endLevels : levels, false);

				if (minMax > bestScore)
				{
					bestScore = minMax;
					bestMove = move;
				}
			}
			else break;
		}

		return bestMove;
	}

	private int miniMax(Board board, int depth, boolean maximizingPlayer)
	{
		if (depth == 0 || board.isDone() || timer.running())
			return rateBoard(board);

		int bestScore = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
		for (Coord move : board.availableMoves())
		{
			Board deepBoard = board.copy();
			deepBoard.play(move);

			bestScore = (maximizingPlayer)
					? Math.max(bestScore, miniMax(deepBoard, depth - 1, false))
					: Math.min(bestScore, miniMax(deepBoard, depth - 1, true));
		}
		return bestScore;
	}

	private int rateBoard(Board board)
	{
		int score = 0;

		//Gives score for tiles
		for (Coord coord : Coord.list())
			score += TILE_VALUE * tileFactor(coord.os()) * tileFactor(coord.om()) * playerSign(board.tile(coord));

		//Gives score for macros
		for (int om = 0; om < 9; om++)
		{
			Player owner = board.macro(om);

			if (owner != Player.NEUTRAL)
				score += tileFactor(om) * MACRO_VALUE * playerSign(owner);
		}

		//Winning games is good, losing is bad
		if (board.isDone())
		{
			if (board.wonBy() == player)
				return Integer.MAX_VALUE;
			else if (board.wonBy() == player.other())
				return Integer.MIN_VALUE + 1;
			else
				return Integer.MIN_VALUE;
		}

		return score;
	}

	private double tileFactor(int o)
	{
		int x = o % 3;
		int y = o / 3;

		if (x == 1 && y == 1)
			return CENTER_FACTOR;
		if (x == 1 || y == 1)
			return EDGE_FACTOR;
		return CORNER_FACTOR;
	}

	private int playerSign(Player p)
	{
		if (p == player)
			return 1;
		else if (p == player.other())
			return -1;
		else
			return 0;
	}

	@Override
	public String toString()
	{
		return "MMBot";
	}
}
