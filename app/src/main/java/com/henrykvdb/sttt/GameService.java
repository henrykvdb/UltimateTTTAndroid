package com.henrykvdb.sttt;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Pair;
import android.widget.Toast;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.Timer;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static com.flaghacker.uttt.common.Player.ENEMY;
import static com.flaghacker.uttt.common.Player.PLAYER;

public class GameService extends Service implements Closeable
{
	private final IBinder mBinder = new GameService.LocalBinder();

	private BoardView boardView;
	private GameThread thread;
	private GameState gs;
	private BtService btService;

	Toast toast;
	private LocalBroadcastManager gameBroadcaster;

	public void setBtService(BtService btService)
	{
		this.btService = btService;
	}

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
	public void onCreate()
	{
		super.onCreate();
		gameBroadcaster = LocalBroadcastManager.getInstance(this);
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

	public void sendToUi(String type, String title)
	{
		Intent intent = new Intent(Constants.EVENT_UI);
		intent.putExtra(Constants.EVENT_TYPE, type);
		intent.putExtra(Constants.DATA_STRING, title);
		gameBroadcaster.sendBroadcast(intent);
	}

	public void setBoardView(BoardView boardView)
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
			sendToUi(Constants.TYPE_TITLE, "Bluetooth Game");
		else if (gs.isAi())
			sendToUi(Constants.TYPE_TITLE, "AI Game");
		else if (gs.isHuman()) //Normal local game
			sendToUi(Constants.TYPE_TITLE, "Human Game");
		else throw new IllegalStateException();
	}

	public GameState getState()
	{
		return gs;
	}

	public void undo(boolean force)
	{
		if (!force && gs.isBluetooth())
			btService.sendUndo();
		else
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
				if (toast == null)
					toast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

				toast.setText("Could not undo further");
				toast.show();
			}
		}
	}

	public void newGame(GameState gs)
	{
		close();

		this.gs = gs;
		boardView.drawState(gs);
		updateTitle();

		if (!gs.isBluetooth())
		{
			sendToUi(Constants.TYPE_SUBTITLE, null);

			if (btService != null)
				btService.restart();
		}
		else
		{
			Intent intent = new Intent(Constants.EVENT_UI);
			intent.putExtra(Constants.EVENT_TYPE, Constants.TYPE_ALLOW_INCOMING_BT);
			intent.putExtra(Constants.DATA_BOOLEAN_ALLOW, false); //Allow
			intent.putExtra(Constants.DATA_BOOLEAN_SILENT, true); //Silent
			gameBroadcaster.sendBroadcast(intent);
		}

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
		private Timer timer;

		@Override
		public void run()
		{
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

			if (gs.players().contains(GameService.Source.Bluetooth))
				btService.sendBoard(newBoard);

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
