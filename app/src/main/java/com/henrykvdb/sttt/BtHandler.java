package com.henrykvdb.sttt;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;
import com.flaghacker.uttt.common.Board;

class BtHandler extends Handler
{
	private static final String TAG = "BtHandler";

	private MainActivity main;
	private GameService gameService;
	private BtService btService;

	private String connectedDeviceName;
	private AlertDialog btAskDialog;
	private GameState requestState;

	public void setMain(MainActivity main)
	{
		this.main = main;
	}

	public void setBtService(BtService btService)
	{
		this.btService = btService;

		if (btService != null)
			btService.setup(gameService, this);

		Log.d(TAG, "set btService");
	}

	public void setGameService(GameService gameService)
	{
		this.gameService = gameService;
		Log.d(TAG, "set gameService");
	}

	public void connect(String address, GameState requestState)
	{
		this.requestState = requestState;
		btService.connect(address);
		Log.d(TAG, "connect");
	}

	public void setBlockIncoming(boolean blockIncoming)
	{
		if (btService != null)
			btService.setBlockIncoming(blockIncoming);
	}

	public void requestUndo()
	{
		if (btService != null)
			btService.requestUndo(false);
		else
			gameService.turnLocal();
	}

	public void resetBluetooth()
	{
		if (btService != null)
			btService.start();
	}

	@Override
	public void handleMessage(Message msg)
	{
		if (msg.what == BtService.Message.STATE_CHANGE.ordinal())
		{
			BtService.State state = (BtService.State) msg.getData().getSerializable(BtService.STATE);

			if (state == BtService.State.CONNECTED)
				main.setBtStatusMessage("connected to " + connectedDeviceName);
			else
				main.setBtStatusMessage(null);
		}
		else if (msg.what == BtService.Message.SEND_BOARD_UPDATE.ordinal())
		{
			Board board = (Board) msg.getData().getSerializable("myBoard");

			if (btService != null)
				btService.sendBoard(board);

			if (board != null)
				Log.d("BTHANDLER", "boardUpdate: " + board.getLastMove());
		}
		else if (msg.what == BtService.Message.RECEIVE_UNDO.ordinal())
		{
			boolean force = (boolean) msg.obj;

			if (!force)
			{
				if (btAskDialog == null || !btAskDialog.isShowing())
				{
					askUser(connectedDeviceName + " requests to undo the last move, do you accept?", allow ->
					{
						if (allow && btService != null)
						{
							gameService.undo(true);
							btService.updateLocalBoard(gameService.getState().board());
							btService.requestUndo(true);
						}
					});
				}
			}
			else
			{
				gameService.undo(true);
				btService.updateLocalBoard(gameService.getState().board());
			}
		}
		else if (msg.what == BtService.Message.SEND_SETUP.ordinal())
		{
			if (btService != null)
				btService.sendState(requestState, false);
		}
		else if (msg.what == BtService.Message.RECEIVE_SETUP.ordinal())
		{
			Bundle data = msg.getData();
			boolean swapped = !data.getBoolean("swapped");
			Board board = (Board) data.getSerializable("board");
			requestState = null;

			if (!data.getBoolean("force"))
			{
				if (btService != null)
				{
					if (!btService.blockIncoming())
					{
						final boolean[] allowed = {false};
						askUser(connectedDeviceName + " challenges you for a duel, do you accept?", allow ->
						{
							if (btService != null)
							{
								if (allow)
								{
									allowed[0] = true;
									//btHostSwitch.setChecked(false);
									requestState = GameState.builder().bt(this).swapped(swapped).board(board).build();
									btService.updateLocalBoard(requestState.board());
									btService.sendState(requestState, true);
									gameService.newGame(requestState);
								}
								//If you press yes it runs once with allow = true and once with allow = false
								else if (!allowed[0])
								{
									btService.start();
								}
							}
						});
					}
					else
					{
						btService.start();
					}
				}
			}
			else
			{
				requestState = GameState.builder().bt(this).swapped(swapped).board(board).build();
				btService.updateLocalBoard(requestState.board());
				gameService.newGame(requestState);
			}
		}
		else if (msg.what == BtService.Message.DEVICE_NAME.ordinal())
		{
			connectedDeviceName = (String) msg.obj;
		}
		else if (msg.what == BtService.Message.TOAST.ordinal() && main != null)
		{
			Toast.makeText(main, (String) msg.obj, Toast.LENGTH_SHORT).show();
		}
		else if (msg.what == BtService.Message.ERROR_TOAST.ordinal() && main != null)
		{
			gameService.turnLocal();

			Toast.makeText(main, (String) msg.obj, Toast.LENGTH_SHORT).show();
		}
	}

	private void askUser(String message, CallBack<Boolean> callBack)
	{
		DialogInterface.OnClickListener dialogClickListener = (dialog, which) ->
		{
			switch (which)
			{
				case DialogInterface.BUTTON_POSITIVE:
					callBack.callback(true);
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					callBack.callback(false);
					break;
			}
		};

		btAskDialog = new AlertDialog.Builder(main).setMessage(message)
				.setPositiveButton("Yes", dialogClickListener)
				.setNegativeButton("No", dialogClickListener)
				.setOnDismissListener(dialogInterface -> callBack.callback(false))
				.show();

		MainActivity.doKeepDialog(btAskDialog);
	}

	private interface CallBack<T>
	{
		void callback(T t);
	}
}
