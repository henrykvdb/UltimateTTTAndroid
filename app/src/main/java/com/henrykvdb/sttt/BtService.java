package com.henrykvdb.sttt;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.JSONBoard;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import static com.henrykvdb.sttt.GameService.Source.Local;

public class BtService extends Service
{
	// Unique UUID for this application
	public static final UUID UUID = java.util.UUID.fromString("8158f052-fa77-4d08-8f1a-f598c31e2422");
	private static final String TAG = "BluetoothService";
	public static final String STATE = "STATE";

	// Member fields
	private BluetoothAdapter btAdapter;
	private GameService gameService;
	private Handler handler = null;

	//State stuff
	private State state = State.NONE;
	private boolean blockIncoming = false;
	private boolean connecting = false;

	//Threads
	private ListenThread listenThread;
	private ConnectingThread connectingThread;
	private ConnectedThread connectedThread;

	public enum State
	{
		NONE,
		LISTEN,
		CONNECTING,
		CONNECTED
	}

	public enum Message
	{
		STATE_CHANGE,
		SEND_BOARD_UPDATE,
		SEND_SETUP,
		RECEIVE_UNDO,
		RECEIVE_SETUP,
		TURN_LOCAL,
		TOAST,
		DEVICE_NAME
	}

	// Binder given to clients
	private final IBinder mBinder = new LocalBinder();

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

	public void setBlockIncoming(boolean blockIncoming)
	{
		this.blockIncoming = blockIncoming;

		if (blockIncoming)
		{
			closeConnecting();
			closeListen();
		}
		else
		{
			start();
		}
	}

	public boolean blockIncoming()
	{
		return blockIncoming;
	}

	public String getConnectedDeviceName()
	{
		if (connectedThread != null)
			return connectedThread.socket.getRemoteDevice().getName();

		return null;
	}

	public void setup(GameService gameService, Handler handler)
	{
		Log.d(TAG, "Setup method called");
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		this.gameService = gameService;
		this.handler = handler;
		setState(state);

		if (state != State.CONNECTED)
			start();
		else
			handler.obtainMessage(Message.DEVICE_NAME.ordinal(), -1, -1, connectedThread.socket.getRemoteDevice().getName()).sendToTarget();

	}

	public void setState(State newState)
	{
		Log.d(TAG, "new state: " + newState.name());
		state = newState;

		if (handler != null)
		{
			android.os.Message msg = handler.obtainMessage(Message.STATE_CHANGE.ordinal());
			Bundle bundle = new Bundle();
			bundle.putSerializable(STATE, state);
			msg.setData(bundle);
			handler.sendMessage(msg);
		}
	}

	public synchronized void start()
	{
		Log.d(TAG, "start");

		closeConnecting();
		closeConnected();
		closeListen();

		// Start the thread to listen on a BluetoothServerSocket
		if (listenThread == null)
		{
			listenThread = new ListenThread();
			listenThread.start();
		}
	}

	public synchronized void connect(String address)
	{
		BluetoothDevice device = btAdapter.getRemoteDevice(address);
		Log.d(TAG, "connect to: " + device);

		start();

		// Start the thread to connect with the given device
		connectingThread = new ConnectingThread(device);
		connectingThread.start();
	}

	private synchronized void connected(BluetoothSocket socket, boolean isHost)
	{
		Log.d(TAG, "connected");

		start();

		if (!blockIncoming || !isHost)
		{
			// Start the thread to manage the connection and perform transmissions
			connectedThread = new ConnectedThread(socket);
			connectedThread.start();

			if (!isHost)
				handler.obtainMessage(Message.SEND_SETUP.ordinal(), -1, -1).sendToTarget();
		}
	}

	public synchronized void stop()
	{
		Log.d(TAG, "stop");

		try
		{
			connecting = true;
			closeConnecting();
			closeConnected();
			closeListen();
		}
		catch (Exception e)
		{
			//NOP
		}

		setState(State.NONE);
	}

	private void closeListen()
	{
		if (listenThread != null)
		{
			listenThread.cancel();
			listenThread = null;
		}
	}

	private void closeConnecting()
	{
		if (connectingThread != null)
		{
			connectingThread.cancel();
			connectingThread = null;
		}
	}

	private void closeConnected()
	{
		if (connectedThread != null)
		{
			connectedThread.cancel();
			connectedThread = null;
		}
	}

	public void sendBoard(Board board)
	{
		ConnectedThread r;
		synchronized (this)
		{
			if (state != State.CONNECTED) return;
			r = connectedThread;
		}

		if (board != null)
			r.boardUpdate(board);
	}

	public void sendState(GameState gs, boolean force)
	{
		ConnectedThread r;
		synchronized (this)
		{
			if (state != State.CONNECTED) return;
			r = connectedThread;
		}

		r.setupEnemyGame(gs.board(), gs.players().second.equals(Local), force);
	}

	public void requestUndo(boolean force)
	{
		ConnectedThread r;
		synchronized (this)
		{
			if (state != State.CONNECTED) return;
			r = connectedThread;
		}

		r.requestUndo(force);
	}

	public void updateLocalBoard(Board verifyBoard)
	{
		if (connectedThread != null)
			connectedThread.localBoard = verifyBoard;
	}

	/**
	 * This thread runs while listening for incoming connections. It behaves like a server-side client. It runs until a
	 * connection is accepted (or until cancelled).
	 */
	private class ListenThread extends Thread
	{
		// The local server socket
		private final BluetoothServerSocket serverSocket;

		public ListenThread()
		{
			BluetoothServerSocket tmp = null;

			// Create a new listening server socket
			try
			{
				tmp = btAdapter.listenUsingRfcommWithServiceRecord("BluetoothChatSecure", UUID);
			}
			catch (IOException e)
			{
				Log.e(TAG, "listen() failed", e);
			}
			serverSocket = tmp;
			setState(BtService.State.LISTEN);
		}

		public void run()
		{
			Log.d(TAG, "BEGIN ListenThread" + this);
			setName("ListenThread");

			BluetoothSocket socket;

			// Listen to the server socket if we're not connected
			while (state != BtService.State.CONNECTED)
			{
				try
				{
					// This is a blocking call and will only return on a
					// successful connection or an exception
					socket = serverSocket.accept();
				}
				catch (IOException e)
				{
					Log.e(TAG, "accept() failed", e);
					break;
				}

				// If a connection was accepted
				if (socket != null)
				{
					synchronized (BtService.this)
					{
						switch (state)
						{
							case LISTEN:
							case CONNECTING:
								// Situation normal. Start the connected thread.
								connected(socket, true);
								break;
							case NONE:
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
			}
			Log.i(TAG, "END ListenThread");

		}

		public void cancel()
		{
			Log.d(TAG, "cancel " + this);
			try
			{
				serverSocket.close();
			}
			catch (IOException e)
			{
				Log.e(TAG, "close() of server failed", e);
			}
		}
	}


	/**
	 * This thread runs while attempting to make an outgoing connection with a device. It runs straight through; the
	 * connection either succeeds or fails.
	 */
	private class ConnectingThread extends Thread
	{
		private final BluetoothSocket socket;

		private ConnectingThread(BluetoothDevice device)
		{
			connecting = true;
			BluetoothSocket tmp = null;

			// Get a BluetoothSocket for a connection with the given BluetoothDevice
			try
			{
				tmp = device.createRfcommSocketToServiceRecord(UUID);
			}
			catch (IOException e)
			{
				Log.e(TAG, "create() failed", e);
			}
			socket = tmp;
			setState(BtService.State.CONNECTING);
		}

		public void run()
		{
			Log.i(TAG, "BEGIN connectingThread");
			setName("ConnectingThread");

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
				// Close the socket
				try
				{
					socket.close();
				}
				catch (IOException e2)
				{
					Log.e(TAG, "unable to close() socket during connection failure", e2);
				}

				if (!connecting)
				{
					handler.obtainMessage(Message.TURN_LOCAL.ordinal(), -1, -1, "Unable to connect to device").sendToTarget();
					BtService.this.start();
				}

				setState(BtService.State.NONE);

				//TODO could not connect error toast

				connecting = false;

				return;
			}

			// Reset the ConnectThread because we're done
			synchronized (BtService.this)
			{
				connectingThread = null;
			}

			// Start the connected thread
			connected(socket, false);
		}

		public void cancel()
		{
			try
			{
				socket.close();
			}
			catch (IOException e)
			{
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}

	/**
	 * This thread runs during a connection with a remote device. It handles all incoming and outgoing transmissions.
	 */
	private class ConnectedThread extends Thread
	{
		private final BluetoothSocket socket;
		private final InputStream inStream;
		private final OutputStream outStream;

		private Board localBoard;

		public ConnectedThread(BluetoothSocket socket)
		{
			connecting = false;
			Log.d(TAG, "create ConnectedThread");
			this.socket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the BluetoothSocket input and output streams
			try
			{
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			}
			catch (IOException e)
			{
				Log.e(TAG, "temp sockets not created", e);
			}

			inStream = tmpIn;
			outStream = tmpOut;
			handler.obtainMessage(Message.TOAST.ordinal(), -1, -1, "Connected to " + socket.getRemoteDevice().getName()).sendToTarget();
			handler.obtainMessage(Message.DEVICE_NAME.ordinal(), -1, -1, socket.getRemoteDevice().getName()).sendToTarget();
			setState(BtService.State.CONNECTED);
		}

		public void run()
		{
			Log.i(TAG, "BEGIN connectedThread");
			byte[] buffer = new byte[1024];

			// Keep listening to the InputStream while connected
			while (state == BtService.State.CONNECTED)
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
									Log.d(TAG, "We received a valid board");
									if (gameService != null)
										gameService.play(GameService.Source.Bluetooth, newMove);
									else
										Log.e(TAG, "Error playing move, btBot is null");
								}
								else
								{
									Log.e(TAG, "Invalid move, desync");
									BtService.this.start();
									handler.obtainMessage(Message.TURN_LOCAL.ordinal(), -1, -1, "Games got desynchronized").sendToTarget();
								}
							}
							catch (Throwable t)
							{
								Log.e(TAG, "desync");
								BtService.this.start();
								handler.obtainMessage(Message.TURN_LOCAL.ordinal(), -1, -1, "Games got desynchronized").sendToTarget();
							}
						}

					}
					else if (message == Message.RECEIVE_SETUP.ordinal())
					{
						android.os.Message msg = handler.obtainMessage(message);
						Bundle bundle = new Bundle();

						Board board = JSONBoard.fromJSON(new JSONObject(json.getString("board")));
						localBoard = board;

						bundle.putBoolean("swapped", json.getBoolean("swapped"));
						bundle.putBoolean("force", json.getBoolean("force"));
						bundle.putSerializable("board", board);

						msg.setData(bundle);
						handler.sendMessage(msg);
					}
					else if (message == Message.RECEIVE_UNDO.ordinal())
					{
						handler.obtainMessage(Message.RECEIVE_UNDO.ordinal(), json.getBoolean("force")).sendToTarget();
					}
				}
				catch (IOException e)
				{
					Log.e(TAG, "disconnected", e);

					if (!connecting)
					{
						handler.obtainMessage(Message.TURN_LOCAL.ordinal(), -1, -1, "Bluetooth connection lost").sendToTarget();
						BtService.this.start();
					}

					//TODO add toast

					setState(BtService.State.NONE);
					connecting = false;

					break;
				}
				catch (JSONException e)
				{
					Log.e(TAG, "JSON read parsing failed");
					e.printStackTrace();
				}
			}
		}

		private void boardUpdate(Board board)
		{
			try
			{
				localBoard = board;

				JSONObject json = new JSONObject();
				try
				{
					json.put("message", Message.SEND_BOARD_UPDATE.ordinal());
					json.put("board", JSONBoard.toJSON(board).toString());
				}
				catch (JSONException e)
				{
					e.printStackTrace();
				}

				byte[] data = json.toString().getBytes();

				outStream.write(data);
			}
			catch (IOException e)
			{
				Log.e(TAG, "Exception during boardUpdate", e);
			}
		}

		private void setupEnemyGame(Board board, boolean swapped, boolean force)
		{
			try
			{
				JSONObject json = new JSONObject();
				try
				{
					json.put("message", Message.RECEIVE_SETUP.ordinal());
					json.put("force", force);
					json.put("swapped", swapped);
					json.put("board", JSONBoard.toJSON(board).toString());
				}
				catch (JSONException e)
				{
					e.printStackTrace();
				}

				byte[] data = json.toString().getBytes();

				outStream.write(data);
			}
			catch (IOException e)
			{
				Log.e(TAG, "Exception during boardUpdate", e);
			}
		}

		private void requestUndo(boolean force)
		{
			try
			{
				JSONObject json = new JSONObject();
				try
				{
					json.put("message", Message.RECEIVE_UNDO.ordinal());
					json.put("force", force);
				}
				catch (JSONException e)
				{
					e.printStackTrace();
				}

				byte[] data = json.toString().getBytes();

				outStream.write(data);
			}
			catch (IOException e)
			{
				Log.e(TAG, "Exception during undo", e);
			}
		}

		private void cancel()
		{
			try
			{
				inStream.close();
				outStream.close();
				socket.close();
			}
			catch (IOException e)
			{
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}
}