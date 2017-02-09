package com.henrykvdb;

import android.util.Log;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.Util;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.flaghacker.uttt.common.Board.ENEMY;
import static com.flaghacker.uttt.common.Board.PLAYER;

public class Game implements Closeable
{
	private Random random = Util.loggedRandom();

	private BoardView boardView;
	private List<Bot> bots;

	private int timePerMove = 500;

	private boolean swapped;
	private boolean running = true;

	private Board board;
	private Thread thread;

	public Game(BoardView boardView, Bot p1, Bot p2)
	{
		this.boardView = boardView;
		bots = Collections.unmodifiableList(Arrays.asList(p1, p2));
		board = new Board();
	}

	public void run()
	{
		thread = new Thread(() -> {
			if (running)
			{
				if (board == null)
					board = new Board();

				swapped = random.nextBoolean();
				Bot p1 = bots.get(swapped ? 1 : 0);
				Bot p2 = bots.get(swapped ? 0 : 1);

				int nextRound = 0;
				Log.d("DANKEST", "TEST");
				while (!board.isDone() && running)
				{
					prints("Round #" + nextRound++);

					Coord m1 = Util.moveBotWithTimeOut(p1, board.copy(), timePerMove);
					if (running)
					{
						prints(p1 + " played: " + m1);
						board.play(m1, PLAYER);
						boardView.setBoard(board);
					}

					if (board.isDone() || !running)
						continue;

					Coord m2 = Util.moveBotWithTimeOut(p2, board.copy(), timePerMove);
					if (running)
					{
						prints(p2 + " played: " + m2);
						board.play(m2, ENEMY);
						boardView.setBoard(board);
					}
				}
			}
		});
		thread.start();
	}

	private void prints(String s)
	{
		Log.d("GAMELOG", s);
	}

	@Override
	public void close()
	{
		running = false;
		thread.interrupt();
	}

	public void redraw()
	{
		if (board != null)
			boardView.setBoard(board);
	}

	public void setBoardView(BoardView boardView)
	{
		this.boardView = boardView;
	}
}
