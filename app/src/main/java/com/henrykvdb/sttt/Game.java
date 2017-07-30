package com.henrykvdb.sttt;

import android.util.Log;
import android.util.Pair;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.Timer;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static com.flaghacker.uttt.common.Player.ENEMY;
import static com.flaghacker.uttt.common.Player.PLAYER;

public class Game implements Closeable
{
	private GameCallback callback;

	private BoardView boardView;
	private GameThread thread;
	private GameState gs;

	public enum Source
	{
		Local,
		AI,
		Bluetooth
	}

	public interface GameCallback
	{
		void setTitle(String title);

		void setSubTitle(String subTitle);

		void sendToast(String text);
	}

	public Game(GameCallback callback, BoardView boardView)
	{
		this.callback = callback;
		setBoardView(boardView);
	}

	private void setBoardView(BoardView boardView)
	{
		boardView.setGameService(this);
		this.boardView = boardView;

		if (thread == null || !thread.running)
			newLocal();

		updateTitle();
		boardView.drawState(gs);
	}

	private void updateTitle()
	{
		if (gs.isBluetooth())
			callback.setTitle("Bluetooth Game");
		else if (gs.isAi())
			callback.setTitle("AI Game");
		else if (gs.isHuman()) //Normal local game
			callback.setTitle("Human Game");
		else throw new IllegalStateException();
	}

	public GameState getState()
	{
		return gs;
	}

	public void undo()
	{
		if (gs.boards().size() > 1)
		{
			GameState newState = GameState.builder().gs(gs).build();
			newState.popBoard();
			if (Source.AI == (gs.board().nextPlayer() == PLAYER ? gs.players().first : gs.players().second)
					&& newState.boards().size() > 1)
				newState.popBoard();

			newGame(newState);
		}
		else
		{
			callback.sendToast("No previous moves");
		}
	}

	public void newGame(GameState gs)
	{
		Log.d("NEWGAME","NEWGAME");
		close();

		this.gs = gs;
		boardView.drawState(gs);
		updateTitle();

		if (!gs.isBluetooth())
			callback.setSubTitle(null);

		thread = new GameThread();
		thread.start();
	}

	public void newLocal()
	{
		newGame(GameState.builder().swapped(false).build());
	}

	public void turnLocal()
	{
		newGame(GameState.builder().boards(gs.boards()).build());
	}

	private class GameThread extends Thread implements Closeable
	{
		public GameThread()
		{
			Log.d("GAMETHREAD CREATED","yea");
		}

		private boolean running;
		private Timer timer;

		@Override
		public void run()
		{
			Log.d("GAMETHREAD RAN","yea");
			setName("GameThread");
			running = true;

			Source p1 = gs.players().first;
			Source p2 = gs.players().second;

			while (!gs.board().isDone() && running)
			{
				timer = new Timer(5000);

				if (gs.board().nextPlayer() == PLAYER && running)
					playAndUpdateBoard((p1 != Source.AI) ? getMove(p1) : gs.extraBot().move(gs.board(), timer));

				if (gs.board().isDone() || !running)
					continue;

				if (gs.board().nextPlayer() == ENEMY && running)
					playAndUpdateBoard((p2 != Source.AI) ? getMove(p2) : gs.extraBot().move(gs.board(), timer));
			}
		}

		@Override
		public void close() throws IOException
		{
			running = false;

			if (timer != null)
				timer.interrupt();

			interrupt();
		}

	}

	private void playAndUpdateBoard(Coord move)
	{
		if (move != null)
		{
			Board newBoard = gs.board().copy();
			newBoard.play(move);

			//if (gs.players().contains(Game.Source.Bluetooth))
			//	btService.sendBoard(newBoard);

			gs.pushBoard(newBoard);
		}

		boardView.drawState(gs);
	}

	public void play(Source source, Coord move)
	{
		synchronized (playerLock)
		{
			playerMove.set(new Pair<>(move, source));
			playerLock.notify();
		}
	}

	private final Object playerLock = new Object[0];
	private AtomicReference<Pair<Coord, Source>> playerMove = new AtomicReference<>();

	private Coord getMove(Source player)
	{
		playerMove.set(new Pair<>(null, null));
		while (playerMove.get().first == null
				|| !gs.board().availableMoves().contains(playerMove.get().first)
				|| !player.equals(playerMove.get().second))
		{
			synchronized (playerLock)
			{
				try
				{
					playerLock.wait();
				}
				catch (InterruptedException e)
				{
					return null;
				}
			}
		}
		//TODO play sound
		return playerMove.getAndSet(null).first;
	}

	@Override
	public void close()
	{
		try
		{
			if (thread != null)
				thread.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}
