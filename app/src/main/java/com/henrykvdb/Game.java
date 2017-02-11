package com.henrykvdb;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.Util;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static com.flaghacker.uttt.common.Board.ENEMY;
import static com.flaghacker.uttt.common.Board.PLAYER;

public class Game implements Closeable, Parcelable
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
		swapped = random.nextBoolean();
	}

	protected Game(Parcel in)
	{
		timePerMove = in.readInt();
		swapped = in.readByte() != 0;
		running = in.readByte() != 0;

		Bot p1 = (Bot) in.readSerializable();
		Bot p2 = (Bot) in.readSerializable();
		bots = Collections.unmodifiableList(Arrays.asList(p1, p2));

		board = (Board) in.readSerializable();
	}

	public void setupGame(BoardView boardView, AndroidBot androidBot)
	{
		this.boardView = boardView;

		//Replace bots if necessary
		Bot p1 = Objects.equals(bots.get(0).toString(), "AndroidBot") ? androidBot : bots.get(0);
		Bot p2 = Objects.equals(bots.get(1).toString(), "AndroidBot") ? androidBot : bots.get(1);
		bots = Collections.unmodifiableList(Arrays.asList(p1, p2));

		redraw();
		run();
	}

	public void run()
	{
		thread = new Thread(() -> {
			if (running)
			{
				if (board == null)
					board = new Board();

				Bot p1 = bots.get(swapped ? 1 : 0);
				Bot p2 = bots.get(swapped ? 0 : 1);

				int nextRound = 0;
				Log.d("DANKEST", "TEST");
				while (! board.isDone() && running)
				{
					prints("Round #" + nextRound++);

					Coord m1 = Util.moveBotWithTimeOut(p1, board.copy(), timePerMove);
					if (running)
					{
						prints(p1 + " played: " + m1);
						board.play(m1, PLAYER);
						boardView.setBoard(board);
					}

					if (board.isDone() || ! running)
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

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(Parcel parcel, int i)
	{
		parcel.writeInt(timePerMove);
		parcel.writeByte((byte) (swapped ? 1 : 0));
		parcel.writeByte((byte) (running ? 1 : 0));

		parcel.writeSerializable(bots.get(0));
		parcel.writeSerializable(bots.get(1));

		parcel.writeSerializable(board);
	}
}
