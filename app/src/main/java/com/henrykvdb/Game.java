package com.henrykvdb;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.Util;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.flaghacker.uttt.common.Board.ENEMY;
import static com.flaghacker.uttt.common.Board.PLAYER;

public class Game implements Parcelable, Closeable
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

	private Game(Parcel in)
	{
		bots = new ArrayList<>();
		in.readList(bots, this.getClass().getClassLoader());
		swapped = in.readInt() != 0;
		board = (Board) in.readSerializable();
	}

	public static final Creator<Game> CREATOR = new Creator<Game>()
	{
		@Override
		public Game createFromParcel(Parcel in)
		{
			return new Game(in);
		}

		@Override
		public Game[] newArray(int size)
		{
			return new Game[size];
		}
	};

	public void run()
	{
		thread = new Thread(() -> {
			if (running)
			{
				Board board = new Board();

				swapped = random.nextBoolean();
				Bot p1 = bots.get(swapped ? 1 : 0);
				Bot p2 = bots.get(swapped ? 0 : 1);

				int nextRound = 0;
				while (!board.isDone() && running)
				{
					prints("Round #" + nextRound++);

					Coord m1 = Util.moveBotWithTimeOut(p1, board.copy(), timePerMove);
					prints(p1 + " played: " + m1);
					board.play(m1, PLAYER);
					boardView.setBoard(board);

					if (board.isDone() || !running)
						continue;

					Coord m2 = Util.moveBotWithTimeOut(p2, board.copy(), timePerMove);
					prints(p2 + " played: " + m2);
					board.play(m2, ENEMY);
					boardView.setBoard(board);
				}
			}
		});
		thread.start();
	}

	private void prints(String s)
	{
		Log.d("PRINTER", s);
	}

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags)
	{
		dest.writeList(bots);
		dest.writeInt(swapped ? 1 : 0);
		dest.writeSerializable(board);
	}

	@Override
	public void close()
	{
		running = false;
		thread.interrupt();
	}
}
