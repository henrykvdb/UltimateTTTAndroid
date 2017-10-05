package com.henrykvdb.sttt;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.JSONBoard;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.content.ContentValues.TAG;
import static com.henrykvdb.sttt.BtService.State.*;
import static com.henrykvdb.sttt.BtService.State.CONNECTING;
import static com.henrykvdb.sttt.BtService.State.LISTENING;

public class BtService extends Service
{
	private final java.util.UUID UUID = java.util.UUID.fromString("8158f052-fa77-4d08-8f1a-f598c31e2422");

	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
	private final IBinder mBinder = new LocalBinder();

	private boolean allowIncoming;
	private volatile State state = NONE;

	private InputStream inStream = null;
	private OutputStream outStream = null;

	private GameState requestState;
	private Board localBoard = new Board();
	private Toast toast;

	public enum State
	{
		NONE,
		LISTENING,
		CONNECTING,
		CONNECTED
	}


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
	public void onCreate()
	{
		super.onCreate();

		Log.e("BTSERVICE", "CREATED");
		registerReceiver(btStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		unregisterReceiver(btStateReceiver);
	}

	private void toast(String text)
	{
		if (toast == null)
			toast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

		toast.setText(text);
		toast.show();
	}

	public void setAllowIncoming(boolean allowIncoming)
	{
		if (this.allowIncoming == allowIncoming)
			return;

		Log.e("setAllowInc", allowIncoming + "");

		this.allowIncoming = allowIncoming;

		if (allowIncoming)
		{
		}
		else
		{
		}
	}

	public void connect(String address, GameState requestState)
	{
		if (state!= NONE)
			executor.shutdownNow();

		this.requestState = requestState;

		BluetoothDevice device = btAdapter.getRemoteDevice(address);
		Log.d("", "connect to: " + device);

		executor.submit(new ConnectingRunnable(device));
	}

	private class ListenRunnable implements Runnable
	{
		private BluetoothServerSocket serverSocket = null;

		public ListenRunnable()
		{
			state = LISTENING;

			// Create a new listening server socket
			try
			{
				serverSocket = btAdapter.listenUsingRfcommWithServiceRecord("BluetoothChatSecure", UUID);
			}
			catch (IOException e)
			{
				Log.e("", "listen() failed", e);
			}
		}

		@Override
		public void run()
		{
			Log.d("", "BEGIN ListenThread" + this);

			BluetoothSocket socket;

			// Listen to the server socket if we're not connected
			while (state != CONNECTED)
			{
				try
				{
					// This is a blocking call and will only return on a
					// successful connection or an exception
					socket = serverSocket.accept();
				}
				catch (IOException e)
				{
					Log.e("", "accept() failed", e);
					break;
				}
				catch (NullPointerException e)
				{
					throw new RuntimeException(e); //TODO remove
					//break;
				}

				// If a connection was accepted
				if (socket != null)
				{
						switch (state)
						{
							case LISTENING:
							case CONNECTING:
								// Situation normal. Start the connected thread.
								connected(socket, true);
								break;
							case NONE: //TODO remove
								break;
							case CONNECTED:
								// Either not ready or already connected. Terminate new socket.
								try
								{
									socket.close();
								}
								catch (IOException e)
								{
									Log.e(TAG, "Could not close unwanted socket", e);
								}
								break;
						}
				}
			}

			state = State.NONE;
			Log.i(TAG, "END ListenThread");
		}
	}

	private class ConnectingRunnable implements Runnable
	{
		private BluetoothSocket socket;

		public ConnectingRunnable(BluetoothDevice device)
		{
			try
			{
				socket = device.createRfcommSocketToServiceRecord(UUID);
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		@Override
		public void run()
		{
			Log.i("BTSERVICE", "BEGIN connectingThread");

			// Always cancel discovery because it will slow down a connection
			btAdapter.cancelDiscovery();

			// Make a connection to the BluetoothSocket
			try
			{
				// This is a blocking call and will only return on a successful connection or an exception
				socket.connect();
			}
			catch (IOException e)
			{
				state = NONE;
				toast("Unable to connect to device");
				Log.e("lol", "Unable to connect to device", e);

				// Close the socket
				try
				{
					socket.close();
				}
				catch (IOException e2)
				{
					Log.e("lol", "unable to close() socket during connection failure", e2);
				}

				return;
			}

			// Start the connected thread
			connected(socket, false);
		}
	}

	public void sendSetup(GameState gameState, boolean force)
	{
		localBoard = gameState.board();
		//TODO
	}

	public void sendBoard(Board board)
	{
		localBoard = board;
		//TODO
	}

	private void connected(BluetoothSocket socket, boolean isHost)
	{
		Log.d("connected", "Hi, I am the connected method");

		inStream = null;
		outStream = null;

		try
		{
			inStream = socket.getInputStream();
			outStream = socket.getOutputStream();
		}
		catch (IOException e)
		{
			Log.e("lol", "connected sockets not created", e);
			state = NONE;
			return;
		}

		Log.e("CONNECTED", "CONNECTED to " + socket.getRemoteDevice().getName());
		toast("Connected to " + socket.getRemoteDevice().getName());
		//TODO callback enemy to UI
		//setEnemy(socket.getRemoteDevice().getName());
		state = CONNECTED;

		byte[] buffer = new byte[1024];

		if (!isHost)
			sendSetup(requestState, false);

		// Keep listening to the InputStream while connected
		while (state == CONNECTED)
		{
			try
			{
				// Read from the InputStream
				inStream.read(buffer); //TODO improve

				JSONObject json = new JSONObject(new String(buffer));

				int message = json.getInt("message");

				if (message == Message.SEND_BOARD_UPDATE.ordinal())
				{
					Board newBoard = JSONBoard.fromJSON(new JSONObject(json.getString("board")));

					if (!newBoard.equals(localBoard))
					{
						try
						{
							Coord newMove = newBoard.getLastMove();
							Board verifyBoard = localBoard.copy();
							verifyBoard.play(newBoard.getLastMove());

							if (verifyBoard.equals(newBoard))
							{
								Log.d("", "We received a valid board");
								//TODO play
								//gameService.play(GameService.Source.Bluetooth, newMove);
							}
							else
							{
								//TODO turnlocal
								state = NONE;
								//turnLocal();
								toast("Games got desynchronized");
							}
						}
						catch (Throwable t)
						{
							//TODO turnlocal
							state = NONE;
							//turnLocal();
							toast("Games got desynchronized");
						}
					}

				}
				else if (message == Message.RECEIVE_SETUP.ordinal())
				{
					Board board = JSONBoard.fromJSON(new JSONObject(json.getString("board")));
					boolean swapped = json.getBoolean("swapped");

					localBoard = board;

					//TODO receive setup
					//btCallback.receive(
							GameState.builder().bt().swapped(swapped).board(board).build(),
							json.getBoolean("force"))
				}
				else if (message == Message.RECEIVE_UNDO.ordinal())
				{
					//TODO undo
					//btCallback.undo(json.getBoolean("force"));
				}
			}
			catch (IOException e)
			{
				Log.e(TAG, "disconnected", e);

					turnLocal();
				state = State.NONE;

				toast("Connection lost");
				break;
			}
			catch (JSONException e)
			{
				Log.e("", "JSON read parsing failed");
				e.printStackTrace();
			}
		}
	}

	public interface BtCallback extends Runnable
	{
		void btDisabled();
	}

	private final BroadcastReceiver btStateReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED))
			{
				/*if (btAdapter.getState() == BluetoothAdapter.STATE_ON)
					Log.e("btStateReceiver","ON");*/
				if (btAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF)
				{
					//TODO callback to disable allowIncoming switch
					Log.e("btStateReceiver", "TURNING OFF");
				}
				/*if (btAdapter.getState() == BluetoothAdapter.STATE_OFF)
					Log.e("btStateReceiver","OFF");*/
			}
		}
	};
}
