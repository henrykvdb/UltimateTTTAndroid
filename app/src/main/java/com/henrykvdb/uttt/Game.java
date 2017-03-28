package com.henrykvdb.uttt;

import android.util.Log;
import com.flaghacker.uttt.common.Bot;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.Util;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.flaghacker.uttt.common.Player.ENEMY;
import static com.flaghacker.uttt.common.Player.PLAYER;

public class Game implements Closeable
{
	private BoardView boardView;
	private GameState state;

	private Thread thread;
	private ExecutorService es;

	public static Game newGame(BoardView boardView, Bot p1, Bot p2)
	{
		GameState state = new GameState(Collections.unmodifiableList(Arrays.asList(p1, p2)), true);

		return new Game(state, boardView, new WaitBot());
	}

	public Game(GameState state, BoardView boardView, WaitBot waitBot)
	{
		this.boardView = boardView;
		this.state = state;

		//Replace androidBots
		Bot p1 = (state.bots().get(0).getClass().equals(WaitBot.class)) ? waitBot : state.bots().get(0);
		Bot p2 = (state.bots().get(1).getClass().equals(WaitBot.class)) ? waitBot : state.bots().get(1);
		state.setBots(Collections.unmodifiableList(Arrays.asList(p1, p2)));

		//Set up BoardView
		boardView.setAndroidBot(waitBot);
		boardView.setBoard(state.board());

		//Start the tread
		es = Executors.newSingleThreadExecutor();
		run();
	}

	public Game(GameState state, BoardView boardView)
	{
		this.state = state;
		this.boardView = boardView;
		boardView.setBoard(state.board());

		//Start the tread
		es = Executors.newSingleThreadExecutor();
		run();
	}

	public GameState getState()
	{
		return state;
	}

	public void run()
	{
		state.setRunning(true);
		thread = new Thread(() -> {
			if (state.running())
			{
				Bot p1 = state.bots().get(state.swapped() ? 1 : 0);
				Bot p2 = state.bots().get(state.swapped() ? 0 : 1);

				while (!state.board().isDone() && state.running())
				{
					if (state.board().nextPlayer() == PLAYER && state.running())
						play(p1);

					if (state.board().isDone() || !state.running())
						continue;

					if (state.board().nextPlayer() == ENEMY && state.running())
						play(p2);
				}
			}
		});
		thread.start();
	}

	private void play(Bot bot)
	{
		if (es == null)
			es = Executors.newSingleThreadExecutor();

		Future<Coord> fMove = Util.moveBotWithTimeOutAsync(es, bot, state.board().copy(), 500);
		Coord move;

		try
		{
			move = fMove.get();
			state.board().play(move);
			boardView.setBoard(state.board());
			prints(bot + " played: " + move);
		}
		catch (InterruptedException e)
		{
			fMove.cancel(true);
		}
		catch (ExecutionException e)
		{
			throw new RuntimeException(e);
		}
	}

	private void prints(String s)
	{
		Log.d("GAMELOG", s);
	}

	@Override
	public void close()
	{
		state.setRunning(false);
		thread.interrupt();

		if (es != null)
		{
			es.shutdown();
			es = null;
		}
	}

	public String getType()
	{
		if (state.bots().get(0).getClass().equals(WaitBot.class)
				&& state.bots().get(1).getClass().equals(WaitBot.class))
			return "1v1";
		else
			return "against AI";
	}
}
