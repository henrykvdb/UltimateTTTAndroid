package com.henrykvdb.sttt;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.flaghacker.uttt.common.Board;

public class BtService extends Service
{
	//Fields //TODO sort
	private LocalBroadcastManager btBroadcaster;
	private final IBinder mBinder = new LocalBinder();
	private BluetoothAdapter btAdapter;
	private GameService gameService;
	Bluetooth bt;
	private Dialogs dialogs;

	public class LocalBinder extends Binder
	{
		BtService getService()
		{
			return BtService.this;
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

	@Override
	public void onCreate()
	{
		super.onCreate();

		btBroadcaster = LocalBroadcastManager.getInstance(this);
		registerReceiver(btStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

		btAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		unregisterReceiver(btStateReceiver);
	}

	public void setup(GameService gameService, Dialogs dialogs)
	{
		this.gameService = gameService;
		this.dialogs = dialogs;

		if (bt == null)
		{
			if (btAdapter != null && btAdapter.isEnabled())
				create();
		}
		else
		{
			if (bt.state() == Bluetooth.State.CONNECTED)
				bt.setEnemy(bt.getConnectedDeviceName());
		}

	}

	public void setAllowIncoming(boolean allow)
	{
		Intent intent = new Intent(Constants.EVENT_UI);
		intent.putExtra(Constants.EVENT_TYPE, Constants.TYPE_ALLOW_INCOMING_BT);
		intent.putExtra(Constants.DATA_BOOLEAN_ALLOW, allow);
		btBroadcaster.sendBroadcast(intent);
	}

	public void connect(String address, GameState requestState)
	{
		if (!requestState.board().isDone())
			bt.connect(address, requestState);
		else
			Log.d("GameService", "You can't send a finished board to the bt opponent");
	}

	public void setBlockIncoming(boolean blockIncoming)
	{
		if (bt != null)
			bt.setBlockIncoming(blockIncoming);
	}

	public boolean blockIncoming()
	{
		return bt == null || bt.blockIncoming();
	}

	private Bluetooth.BtCallback btCallback = new Bluetooth.BtCallback()
	{
		@Override
		public void receive(GameState gs, boolean force)
		{
			receiveSetup(GameState.builder().gs(gs).swapped(true).build(), force);
		}

		@Override
		public void undo(boolean force)
		{
			receiveUndo(force);
		}
	};

	private void create()
	{
		stop();
		if (gameService != null)
			bt = new Bluetooth(this, gameService, btCallback);
	}

	private void stop()
	{
		try
		{
			if (bt != null)
				bt.stop();
			bt = null;
		}
		catch (Exception e)
		{
			//NOP
		}
	}

	public void restart()
	{
		if (bt != null)
			bt.start();
	}

	public void sendBoard(Board board)
	{
		if (bt != null)
			bt.sendBoard(board);
	}

	private void receiveSetup(GameState requestState, boolean force)
	{
		bt.setRequestState(null);

		if (!force)
		{
			if (bt != null)
			{
				if (!bt.blockIncoming())
				{
					final boolean[] allowed = {false};
					dialogs.askUser(bt.getConnectedDeviceName() + " challenges you for a duel, do you accept?", allow ->
					{
						if (bt != null)
						{
							if (allow)
							{
								allowed[0] = true;
								setAllowIncoming(false);
								bt.updateLocalBoard(requestState.board());
								bt.sendSetup(requestState, true);
								gameService.newGame(requestState);
							}
							//If you press yes it will still callback false when dismissed
							else if (!allowed[0])
							{
								if (bt.state().equals(Bluetooth.State.CONNECTED))
									bt.closeConnected();
							}
						}
					});
				}
				else
				{
					bt.start();
				}
			}
		}
		else
		{
			bt.updateLocalBoard(requestState.board());
			gameService.newGame(requestState);
		}
	}

	public void sendUndo()
	{
		if (bt != null)
			bt.sendUndo(false);
	}

	private void receiveUndo(boolean force)
	{
		if (bt != null)
		{
			if (!force)
			{
				dialogs.askUser(bt.getConnectedDeviceName() + " requests to undo the last move, do you accept?", allow ->
				{
					if (allow && bt != null)
					{
						gameService.undo(true);
						bt.updateLocalBoard(gameService.getState().board());
						bt.sendUndo(true);
					}
				});
			}
			else
			{
				gameService.undo(true);
				bt.updateLocalBoard(gameService.getState().board());
			}
		}
	}

	private final BroadcastReceiver btStateReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED))
			{
				if (btAdapter.getState() == BluetoothAdapter.STATE_ON)
				{
					create();
				}
				if (btAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF)
				{
					if (bt != null)
						stop();

					btBroadcaster.sendBroadcast(new Intent(Constants.EVENT_UI).putExtra(Constants.EVENT_TYPE, Constants.TYPE_SUBTITLE));
					gameService.turnLocal();
				}
				if (btAdapter.getState() == BluetoothAdapter.STATE_OFF)
				{
					btBroadcaster.sendBroadcast(new Intent(Constants.EVENT_UI).putExtra(Constants.EVENT_TYPE, Constants.TYPE_SUBTITLE));
					setAllowIncoming(false);
				}
			}
		}
	};
}