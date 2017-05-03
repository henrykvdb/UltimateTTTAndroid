package com.henrykvdb.sttt;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
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
	// Binder given to clients
	private final IBinder mBinder = new GameService.LocalBinder();

	private BoardView boardView;
	private GameThread thread;
	private BtHandler btHandler;
	private GameState gs;

	Toast toast;
	private MainActivity main;
	private LocalBroadcastManager uiBroadcaster;

	public void setBlockIncomingBt(boolean blockIncomingBt)
	{
		btHandler.setBlockIncoming(blockIncomingBt);
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
		uiBroadcaster = LocalBroadcastManager.getInstance(this);
		super.onCreate();
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return mBinder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		btHandler = new BtHandler();
		btHandler.setGameService(this);

		return START_STICKY;
	}

	public void sendToUi(String type, String title)
	{
		Intent intent = new Intent(Constants.UI_RESULT);
		intent.putExtra("type", type);
		intent.putExtra("message", title);
		uiBroadcaster.sendBroadcast(intent);
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
			sendToUi(Constants.TYPE_TITLE,"Bluetooth Game");
		else if (gs.isAi())
			sendToUi(Constants.TYPE_TITLE,"AI Game");
		else if (gs.isLocal()) //Normal local game
			sendToUi(Constants.TYPE_TITLE,"Human Game");
		else throw new IllegalStateException();
	}

	public void setBtServiceAndMain(BtService btService, MainActivity mainActivity)//TODO Remove need for mainActivity
	{
		this.main = mainActivity;
		btHandler.setBtService(btService);
		btHandler.setMain(mainActivity);
	}

	public GameState getState()
	{
		return gs;
	}

	public void undo(boolean force)
	{
		if (!force && gs.isBluetooth())
			btHandler.requestUndo();
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
			sendToUi(Constants.TYPE_SUBTITLE,null);
			btHandler.resetBluetooth();
		}
		else
			main.disableHost();

		thread = new GameThread();
		thread.start();
	}

	public void startBtGame(String address, GameState requestState)
	{
		if (!requestState.board().isDone() && btHandler != null)
			btHandler.connect(address, requestState);
		else
			Log.d("GameService", "You can't send a finished board to the bt opponent");
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
