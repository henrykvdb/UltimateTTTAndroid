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
import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;

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

	private SingleTaskExecutor executor;
	private BluetoothAdapter btAdapter;
	private final IBinder mBinder = new LocalBinder();

	private boolean allowIncoming;
	private volatile State state = NONE;

	private OutputStream outStream = null;

	private Board localBoard = new Board();
	private GameState requestState;
	private String connectedDeviceName;

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

		Log.e(MainActivity.debuglog, "BTSERVICE CREATED");
	}

	public void setAllowIncoming(boolean allowIncoming) //TODO SHOULD ONLY BE CALLED FROM MAIN ACTIVITY
	{
		if (this.allowIncoming == allowIncoming)
			return;

		Log.e(MainActivity.debuglog, "allow incoming? " + allowIncoming);

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
				cancelRunnable();
		}
	}

	public void listen()
	{
		executor.submit(new ListenRunnable());
	}

	public void connect(String address)
	{
		if (state != NONE)
			cancelRunnable();

		BluetoothDevice device = btAdapter.getRemoteDevice(address);
		Log.e(MainActivity.debuglog, "connect to: " + device);

		executor.submit(new ConnectingRunnable(device));
	}

	private class ListenRunnable extends InterruptableRunnable
	{
		private BluetoothServerSocket serverSocket = null;

		public ListenRunnable()
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

			BluetoothSocket socket;

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
					connected(socket, true, this);
			}

			state = State.NONE;
			Log.e(MainActivity.debuglog, "END ListenThread");
		}
	}

	private class ConnectingRunnable extends InterruptableRunnable
	{
		private BluetoothSocket socket;

		public ConnectingRunnable(BluetoothDevice device)
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

			// Make a connection to the BluetoothSocket
			try
			{
				// This is a blocking call and will only return on a successful connection or an exception
				socket.connect();

				// Start the actual connection
				connected(socket, false, this);
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
	}

	private void connected(BluetoothSocket socket, boolean isHost, InterruptableRunnable runnable)
	{
		if (runnable.isInterrupted())
			return;

		Log.e(MainActivity.debuglog, "Connected method");

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

		//TODO callback enemy to UI
		//setEnemy(socket.getRemoteDevice().getName());

		byte[] buffer = new byte[1024];

		if (isHost)
			sendSetup();

		// Keep listening to the InputStream while connected
		while (state == CONNECTED && !runnable.isInterrupted())
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

					if (isValidBoard(newBoard))
					{
						Log.e(MainActivity.debuglog, "We received a valid board");
						EventBus.getDefault().post(new Events.NewMove(MainActivity.Source.Bluetooth, newMove));
					}
					else
					{
						EventBus.getDefault().post(new Events.Toast("Games got desynchronized"));
						cancelRunnable();
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
				state = State.NONE;
				Log.e(MainActivity.debuglog, "disconnected", e);
				cancelRunnable();
				EventBus.getDefault().post(new Events.Toast("Connection lost"));
			}
			catch (JSONException e)
			{
				state = State.NONE;
				Log.e(MainActivity.debuglog, "JSON read parsing failed");
				e.printStackTrace();
			}
		}
	}

	private boolean isValidBoard(Board board)
	{
		if (board.equals(localBoard)) //TODO is this even needed?
			throw new RuntimeException("It's lit fam not a valid board fix ur code");

		if (!localBoard.availableMoves().contains(board.getLastMove()))
			return false;

		Board verifyBoard = localBoard.copy();
		verifyBoard.play(board.getLastMove());

		return verifyBoard.equals(board);
	}

	public void cancelRunnable()
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
		Log.e(MainActivity.debuglog, "Sending setup");

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
}
