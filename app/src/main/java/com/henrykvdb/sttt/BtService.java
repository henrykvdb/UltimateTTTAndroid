package com.henrykvdb.sttt;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.JSONBoard;
import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static android.content.ContentValues.TAG;
import static com.henrykvdb.sttt.BtService.State.CONNECTED;
import static com.henrykvdb.sttt.BtService.State.LISTENING;
import static com.henrykvdb.sttt.BtService.State.NONE;

public class BtService extends Service
{
	private final java.util.UUID UUID = java.util.UUID.fromString("8158f052-fa77-4d08-8f1a-f598c31e2422");

	private SingleTaskExecutor executor;
	private BluetoothAdapter btAdapter;
	private final IBinder mBinder = new LocalBinder();

	private boolean allowIncoming;
	private volatile State state = NONE;

	private InputStream inStream = null;
	private OutputStream outStream = null;

	private Board localBoard = new Board();
	private GameState requestState;
	private String connectedDeviceName;

	private Toast toast;

	public enum State
	{
		NONE,
		LISTENING,
		CONNECTING,
		CONNECTED
	}

	public enum Message
	{
		RECEIVE_UNDO,
		RECEIVE_SETUP,
		SEND_BOARD_UPDATE
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

		executor = new SingleTaskExecutor();
		btAdapter = BluetoothAdapter.getDefaultAdapter();

		Log.e("BTSERVICE", "CREATED");
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
			if (state != LISTENING)
			{
				executor.submit(new ListenRunnable());
			}
		}
		else
		{
			if (state != State.CONNECTING && state != State.CONNECTED)
				executor.cancel();
		}
	}

	public void connect(String address, GameState requestState)
	{
		if (state != NONE)
			executor.cancel();

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
			while (state != CONNECTED && !Thread.interrupted())
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
				}//TODO catch interrupted exception

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
			btAdapter.cancelDiscovery();

			// Make a connection to the BluetoothSocket
			try
			{
				// Blocking call; only returns on a successful connection or an exception
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

	private void connected(BluetoothSocket socket, boolean isHost)
	{
		if (Thread.interrupted())
			return;

		Log.d("connected", "Hi, I am the connected method");

		inStream = null;
		outStream = null;

		try
		{
			inStream = socket.getInputStream();
			outStream = socket.getOutputStream();
			state = CONNECTED;
		}
		catch (IOException e)
		{
			Log.e("lol", "connected sockets not created", e);
			state = NONE;
			return;
		}

		connectedDeviceName = socket.getRemoteDevice().getName();
		Log.e("CONNECTED", "CONNECTED to " + socket.getRemoteDevice().getName());
		toast("Connected to " + socket.getRemoteDevice().getName());

		//TODO callback enemy to UI
		//setEnemy(socket.getRemoteDevice().getName());

		byte[] buffer = new byte[1024];

		if (!isHost)
			sendSetup(requestState, false);

		// Keep listening to the InputStream while connected
		while (state == CONNECTED && !Thread.interrupted())
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
					Coord newMove = newBoard.getLastMove();

					if (newBoard.equals(localBoard)) //TODO is this even needed?
						return;

					if (!localBoard.availableMoves().contains(newMove))
					{
						toast("Games got desynchronized");
						closeConnection();
					}
					else
					{
						Board verifyBoard = localBoard.copy();
						verifyBoard.play(newMove);

						if (verifyBoard.equals(newBoard))
						{
							Log.d("", "We received a valid board");

							EventBus.getDefault().post(new Events.NewMove(MainActivity.Source.Bluetooth, newMove));
						}
						else
						{
							toast("Games got desynchronized");
							closeConnection();
						}
					}
				}
				else if (message == Message.RECEIVE_SETUP.ordinal())
				{
					Board board = JSONBoard.fromJSON(new JSONObject(json.getString("board")));
					boolean swapped = json.getBoolean("swapped");

					localBoard = board;

					GameState requestState = GameState.builder().bt().swapped(swapped).board(board).build();
					EventBus.getDefault().post(new Events.Setup(requestState,json.getBoolean("force")));
				}
				else if (message == Message.RECEIVE_UNDO.ordinal())
				{
					EventBus.getDefault().post(new Events.Undo(json.getBoolean("force")));
				}
			}
			catch (IOException e)
			{
				Log.e(TAG, "disconnected", e);

				closeConnection();
				state = State.NONE; //TODO not needed?

				toast("Connection lost");
				break;
			}
			catch (JSONException e)
			{
				Log.e("", "JSON read parsing failed");
				e.printStackTrace();
			}//TODO catch interrupted exception
		}
	}

	public void closeConnection()
	{
		executor.cancel();
		EventBus.getDefault().post(new Events.TurnLocal());
	}

	public String getConnectedDeviceName()
	{
		if (state != CONNECTED)
			return null;

		return connectedDeviceName;
	}

	public State getState()
	{
		return state;
	}

	public void setLocalBoard(Board localBoard)
	{
		this.localBoard = localBoard;
	}

	public void sendUndo(boolean force)
	{
		if (state != CONNECTED)
			return;

		try
		{
			JSONObject json = new JSONObject();
			json.put("message", Message.RECEIVE_UNDO.ordinal());
			json.put("force", force);
			byte[] data = json.toString().getBytes();

			outStream.write(data);
		}
		catch (IOException e)
		{
			Log.e(TAG, "Exception during undo", e);
		}
		catch (JSONException e)
		{
			e.printStackTrace();
		}
	}

	public void sendSetup(GameState gs, boolean force)
	{
		//localBoard = gs.board();
		//TODO

		try
		{
			JSONObject json = new JSONObject();

			json.put("message", Message.RECEIVE_SETUP.ordinal());
			json.put("force", force);
			json.put("swapped", gs.players().first.equals(MainActivity.Source.Bluetooth));
			json.put("board", JSONBoard.toJSON(gs.board()).toString());

			byte[] data = json.toString().getBytes();

			outStream.write(data);
		}
		catch (IOException e)
		{
			Log.e(TAG, "Exception during boardUpdate", e);
		}
		catch (JSONException e)
		{
			e.printStackTrace();
		}
	}

	public void sendBoard(Board board)
	{
		localBoard = board;

		try
		{

			JSONObject json = new JSONObject();

			json.put("message", Message.SEND_BOARD_UPDATE.ordinal());
			json.put("board", JSONBoard.toJSON(board).toString());

			byte[] data = json.toString().getBytes();

			outStream.write(data);
		}
		catch (IOException e)
		{
			Log.e(TAG, "Exception during boardUpdate", e);
		}
		catch (JSONException e)
		{
			e.printStackTrace();
		}
	}
}
