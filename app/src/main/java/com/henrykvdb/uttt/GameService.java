package com.henrykvdb.uttt;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.util.Pair;
import android.widget.Toast;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.Util;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static com.flaghacker.uttt.common.Player.ENEMY;
import static com.flaghacker.uttt.common.Player.PLAYER;

public class GameService extends Service implements Closeable
{
	// Binder given to clients
	private final IBinder mBinder = new GameService.LocalBinder();

	private BoardView boardView;

	private GameThread thread;
	private GameState gs;

	public enum Source
	{
		Local,
		AI,
		Bluetooth
	}

	public class LocalBinder extends Binder
	{
		GameService getService()
		{
			return GameService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return mBinder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		return START_STICKY;
	}

	public void setBoardView(BoardView boardView)
	{
		boardView.setGameService(this);
		this.boardView = boardView;

		if (thread == null || !thread.running)
			newLocal();

		boardView.drawState(gs);
	}

	public void undo()
	{
		if (gs.boards().size() > 1)
		{
			GameState newState = GameState.builder().gs(gs).build();
			newState.popBoard();
			if (gs.players().contains(Source.AI)
					&& Source.Local == gs.players().get(gs.board().nextPlayer() == PLAYER ? 1 : 0)
					&& newState.boards().size() > 1)
				newState.popBoard();

			newGame(newState);
		}
		else
		{
			Toast.makeText(this,"Could not undo further",Toast.LENGTH_SHORT).show();
		}
	}

	public void newGame(GameState gs)
	{
		close();

		this.gs = gs;

		boardView.drawState(gs);

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
		private boolean running;

		@Override
		public void run()
		{
			running = true;

			Source p1 = gs.players().get(0);
			Source p2 = gs.players().get(1);

			while (!gs.board().isDone() && running)
			{
				if (gs.board().nextPlayer() == PLAYER && running)
					playAndUpdateBoard((p1 != Source.AI) ? getMove(p1) : Util.moveBotWithTimeOut(gs.extraBot(), gs.board(), 500));

				if (gs.board().isDone() || !running)
					continue;

				if (gs.board().nextPlayer() == ENEMY && running)
					playAndUpdateBoard((p2 != Source.AI) ? getMove(p2) : Util.moveBotWithTimeOut(gs.extraBot(), gs.board(), 500));
			}
		}

		@Override
		public void close() throws IOException
		{
			running = false;
			interrupt();
		}
	}

	private void playAndUpdateBoard(Coord move)
	{
		if (move != null)
		{
			Board newBoard = gs.board().copy();
			newBoard.play(move);

			if (gs.players().contains(GameService.Source.Bluetooth))
			{
				Message msg = gs.btHandler().obtainMessage(BtService.Message.SEND_BOARD_UPDATE.ordinal());

				Bundle bundle = new Bundle();
				bundle.putSerializable("myBoard", newBoard);
				msg.setData(bundle);

				gs.btHandler().sendMessage(msg);
			}

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

	public GameState getState()
	{
		return gs;
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
