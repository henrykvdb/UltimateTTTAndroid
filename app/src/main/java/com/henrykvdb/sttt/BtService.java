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
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.JSONBoard;
import com.henrykvdb.sttt.Util.Util;
import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.henrykvdb.sttt.BtService.State.CONNECTED;
import static com.henrykvdb.sttt.BtService.State.CONNECTING;
import static com.henrykvdb.sttt.BtService.State.LISTENING;
import static com.henrykvdb.sttt.BtService.State.NONE;

public class BtService extends Service
{
	private final java.util.UUID UUID = java.util.UUID.fromString("8158f052-fa77-4d08-8f1a-f598c31e2422");

	private BluetoothAdapter btAdapter;
	private final IBinder mBinder = new LocalBinder();

	private volatile State state = NONE;

	private OutputStream outStream = null;

	private Board localBoard = new Board();
	private GameState requestState;
	private String connectedDeviceName;

	private CloseableThread btThread;

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

		btAdapter = BluetoothAdapter.getDefaultAdapter();

		Log.e(MainActivity.debuglog, "BTSERVICE CREATED");
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}

	public void listen()
	{
		setRequestState(GameState.builder().bt().build());

		closeThread();
		btThread = new ListenThread();
		btThread.start();
	}

	public void connect(String address)
	{
		closeThread();

		BluetoothDevice device = btAdapter.getRemoteDevice(address);
		Log.e(MainActivity.debuglog, "connect to: " + device);

		btThread = new ConnectingThread(device);
		btThread.start();
	}

	public void closeThread()
	{
		if (btThread != null)
		{
			try
			{
				btThread.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		EventBus.getDefault().post(new Events.TurnLocal());
	}

	public static abstract class CloseableThread extends Thread implements Closeable
	{
	}

	private class ListenThread extends CloseableThread
	{
		private BluetoothServerSocket serverSocket;
		private BluetoothSocket socket;

		public ListenThread()
		{
			state = LISTENING;

			try
			{
				serverSocket = btAdapter.listenUsingRfcommWithServiceRecord("BluetoothChatSecure", UUID);
			}
			catch (IOException e)
			{
				Log.e(MainActivity.debuglog, "listen() failed", e);
			}
		}

		@Override
		public void run()
		{
			Log.e(MainActivity.debuglog, "BEGIN ListenThread " + this);

			// Listen to the server socket if we're not connected
			while (state != CONNECTED && !isInterrupted())
			{
				try
				{
					// This is a blocking call and will only return on a successful connection or an exception
					socket = serverSocket.accept();
				}
				catch (IOException e)
				{
					Log.e(MainActivity.debuglog, "accept() failed", e);
					break;
				}

				if (socket != null && !isInterrupted())
					connected(socket, true);
			}

			state = BtService.State.NONE;
			Log.e(MainActivity.debuglog, "END ListenThread");
		}


		@Override
		public void close() throws IOException
		{
			if (serverSocket != null)
				serverSocket.close();
			if (socket != null)
				socket.close();
		}
	}

	private class ConnectingThread extends CloseableThread
	{
		private BluetoothSocket socket;

		public ConnectingThread(BluetoothDevice device)
		{
			state = CONNECTING;

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
			Log.e(MainActivity.debuglog, "BEGIN connectingThread" + this);

			btAdapter.cancelDiscovery();

			try
			{
				// This is a blocking call and will only return on a successful connection or an exception
				socket.connect();

				// Manage the connection, returns when the connection is stopped
				connected(socket, false);

				// No longer connected, close the socket
				socket.close();
			}
			catch (IOException e)
			{
				state = NONE;
				EventBus.getDefault().post(new Events.Toast("Unable to connect to device"));
				Log.e(MainActivity.debuglog, "Unable to connect to device", e);

				try
				{
					socket.close();
				}
				catch (IOException e2)
				{
					Log.e(MainActivity.debuglog, "unable to interrupt() socket during connection failure", e2);
				}
			}

			Log.e(MainActivity.debuglog, "END connectingThread" + this);
		}

		@Override
		public void close() throws IOException
		{
			if (socket != null)
				socket.close();
		}
	}

	private void connected(BluetoothSocket socket, boolean isHost)
	{
		Log.e(MainActivity.debuglog, "BEGIN connected thread");

		if (Thread.interrupted())
			return;

		InputStream inStream;
		outStream = null;

		try
		{
			inStream = socket.getInputStream();
			outStream = socket.getOutputStream();

			state = CONNECTED;
		}
		catch (IOException e)
		{
			Log.e(MainActivity.debuglog, "connected sockets not created", e);
			state = NONE;
			return;
		}

		connectedDeviceName = socket.getRemoteDevice().getName();
		Log.e(MainActivity.debuglog, "CONNECTED to " + socket.getRemoteDevice().getName());
		EventBus.getDefault().post(new Events.Toast("Connected to " + socket.getRemoteDevice().getName()));

		byte[] buffer = new byte[1024];

		if (isHost && !Thread.interrupted())
			sendSetup();

		// Keep listening to the InputStream while connected
		while (state == CONNECTED && !Thread.interrupted())
		{
			try
			{
				// Read from the InputStream
				inStream.read(buffer); //TODO improve

				JSONObject json = new JSONObject(new String(buffer));

				int message = json.getInt("message");

				Log.e(MainActivity.debuglog, "RECEIVED BTMESSAGE: " + message);
				if (message == Message.SEND_BOARD_UPDATE.ordinal())
				{
					Board newBoard = JSONBoard.fromJSON(new JSONObject(json.getString("board")));
					Coord newMove = newBoard.getLastMove();

					if (Util.isValidBoard(localBoard, newBoard))
					{
						Log.e(MainActivity.debuglog, "We received a valid board");
						localBoard = newBoard;
						EventBus.getDefault().post(new Events.NewMove(MainActivity.Source.Bluetooth, newMove));
					}
					else
					{
						EventBus.getDefault().post(new Events.Toast("Games got desynchronized"));
						break;
					}
				}
				else if (message == Message.RECEIVE_SETUP.ordinal())
				{
					Board board = JSONBoard.fromJSON(new JSONObject(json.getString("board")));
					localBoard = board;

					GameState requestState = GameState.builder().bt().board(board).swapped(!json.getBoolean("start")).build();
					EventBus.getDefault().post(new Events.NewGame(requestState));
				}
				else if (message == Message.RECEIVE_UNDO.ordinal())
				{
					EventBus.getDefault().post(new Events.Undo(json.getBoolean("force")));
				}
			}
			catch (IOException e)
			{
				Log.e(MainActivity.debuglog, "disconnected", e);
				EventBus.getDefault().post(new Events.Toast("Connection lost"));
				break;
			}
			catch (JSONException e)
			{
				Log.e(MainActivity.debuglog, "JSON read parsing failed");
				break;
			}
		}

		state = State.NONE;
		EventBus.getDefault().post(new Events.TurnLocal());

		try
		{
			inStream.close();
			outStream.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		Log.e(MainActivity.debuglog, "END connected thread");
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
		Log.e(MainActivity.debuglog, "Sending undo");

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
			Log.e(MainActivity.debuglog, "Exception during undo", e);
		}
		catch (JSONException e)
		{
			e.printStackTrace();
		}
	}

	private void sendSetup()
	{
		EventBus.getDefault().post(new Events.NewGame(requestState));
		Log.e(MainActivity.debuglog, "Sending setup, starting: " + requestState.players().first);

		localBoard = requestState.board();

		try
		{
			JSONObject json = new JSONObject();

			json.put("message", Message.RECEIVE_SETUP.ordinal());
			json.put("start", requestState.players().first.equals(MainActivity.Source.Bluetooth));
			json.put("board", JSONBoard.toJSON(requestState.board()).toString());

			byte[] data = json.toString().getBytes();

			outStream.write(data);
		}
		catch (IOException e)
		{
			Log.e(MainActivity.debuglog, "Exception during boardUpdate", e);
		}
		catch (JSONException e)
		{
			e.printStackTrace();
		}
	}

	public void sendBoard(Board board)
	{
		Log.e(MainActivity.debuglog, "Sending board");

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
			Log.e(MainActivity.debuglog, "Exception during boardUpdate", e);
		}
		catch (JSONException e)
		{
			e.printStackTrace();
		}
	}

	public void setRequestState(GameState requestState)
	{
		this.requestState = requestState;
	}

	public String getLocalBluetoothName()
	{
		return btAdapter.getName() != null ? btAdapter.getName() : btAdapter.getAddress();
	}

	public Board getLocalBoard()
	{
		return localBoard;
	}
}
