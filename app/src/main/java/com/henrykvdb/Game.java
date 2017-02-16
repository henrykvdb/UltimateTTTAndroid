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
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.flaghacker.uttt.common.Player.ENEMY;
import static com.flaghacker.uttt.common.Player.PLAYER;

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
	private ExecutorService es;

	public Game(BoardView boardView, Bot p1, Bot p2)
	{
		this.boardView = boardView;
		bots = Collections.unmodifiableList(Arrays.asList(p1, p2));
		board = new Board();
		swapped = random.nextBoolean();
		es = Executors.newSingleThreadExecutor();
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
		es = Executors.newSingleThreadExecutor();
	}

	public void setupGame(BoardView boardView, AndroidBot androidBot)
	{
		this.boardView = boardView;

		//Replace bots if necessary
		Bot p1 = (bots.get(0).getClass().equals(AndroidBot.class)) ? androidBot : bots.get(0);
		Bot p2 = (bots.get(1).getClass().equals(AndroidBot.class)) ? androidBot : bots.get(1);
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

					if (board.nextPlayer() == PLAYER && running)
						aplay(p1);

					if (board.isDone() || ! running)
						continue;

					if (board.nextPlayer() == ENEMY && running)
						aplay(p2);
				}
			}
		});
		thread.start();
	}

	private void play(Bot bot)
	{
		Coord move = Util.moveBotWithTimeOut(bot, board.copy(), timePerMove);
		if (running)
		{
			prints(bot + " played: " + move);
			board.play(move);
			boardView.setBoard(board);
		}
	}

	private void aplay(Bot bot)
	{
		Future<Coord> fMove = Util.moveBotWithTimeOutAsync(es, bot, board.copy(), timePerMove);

		while (! fMove.isDone() && running)
		{
			try
			{
				Thread.sleep(10);
			}
			catch (InterruptedException e)
			{
				close();
				//e.printStackTrace();
			}
		}

		if (running)
		{
			Coord move = null;
			try
			{
				move = fMove.get();
			}
			catch (InterruptedException | ExecutionException e)
			{
				close();
				e.printStackTrace(); //NOP?
			}

			prints(bot + " played: " + move + " on instance " + this.hashCode());
			board.play(move);
			boardView.setBoard(board);
		}
		else
		{
			fMove.cancel(true);
		}
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
