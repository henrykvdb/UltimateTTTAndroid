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
	private final int levels = 3;
	private final int endLevels = 6;

	private int difficulty = 50;

	private Player player;
	private Random random = Util.loggedRandom();

	public MMBot(int difficulty)
	{
		this.difficulty = difficulty;
	}

	@Override
	public Coord move(Board board, Timer timer)
	{
		player = board.nextPlayer();

		int bestScore = Integer.MIN_VALUE;
		Coord bestMove = board.availableMoves().get(new Random().nextInt(board.availableMoves().size()));

		if (random.nextInt(100)>difficulty)
		{
			Log.d("MMBot","Played random");
			return bestMove;
		}

		for (Coord move : board.availableMoves())
		{
			Board testBoard = board.copy();
			testBoard.play(move);

			int minMax = minMax(testBoard, board.freeTiles().size() < 30 ? endLevels : levels, false);

			if (minMax > bestScore)
			{
				bestScore = minMax;
				bestMove = move;
			}
		}

		return bestMove;
	}

	private int minMax(Board board, int depth, boolean maximizingPlayer)
	{
		if (depth == 0 || board.isDone())
			return rateBoard(board);

		int bestScore = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
		for (Coord move : board.availableMoves())
		{
			Board deepBoard = board.copy();
			deepBoard.play(move);

			bestScore = (maximizingPlayer)
					? Math.max(bestScore, minMax(deepBoard, depth - 1, false))
					: Math.min(bestScore, minMax(deepBoard, depth - 1, true));
		}
		return bestScore;
	}

	private int rateBoard(Board board)
	{
		//boolean ourTurn = board.nextPlayer() == player;
		int score = 0;

		//Taking macros is good, losing them is bad
		for (Coord c : Coord.macro(0, 0))
		{
			Player owner = board.macro(c.x(), c.y());

			if (owner != Player.NEUTRAL)
				score += (owner == player) ? 50 : - 50;
		}

		//Winning games is good, losing is bad
		if (board.isDone())
			score += (board.wonBy() == player)?9999:-9999;

		return score;
	}

	@Override
	public String toString()
	{
		return "MMBot";
	}
}
