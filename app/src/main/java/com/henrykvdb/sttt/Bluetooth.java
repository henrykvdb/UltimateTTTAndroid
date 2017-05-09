package com.henrykvdb.sttt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.JSONBoard;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.henrykvdb.sttt.Bluetooth.State.CONNECTED;
import static com.henrykvdb.sttt.Bluetooth.State.CONNECTING;
import static com.henrykvdb.sttt.Bluetooth.State.LISTEN;
import static com.henrykvdb.sttt.Bluetooth.State.NONE;
import static com.henrykvdb.sttt.GameService.Source.Local;

public class Bluetooth
{
	//Unique UUID for this application
	public static final java.util.UUID UUID = java.util.UUID.fromString("8158f052-fa77-4d08-8f1a-f598c31e2422");
	private static final String TAG = "BluetoothService";
	private final GameService gameService;
	private final BtCallback btCallback;

	//Other fields
	private LocalBroadcastManager btBroadcaster;
	private BluetoothAdapter btAdapter;

	//State stuff
	private AtomicReference<State> state = new AtomicReference<>(NONE);
	private boolean blockIncoming = false;
	private boolean connecting = false;

	//Threads
	private ListenThread listenThread;
	private ConnectingThread connectingThread;
	private ConnectedThread connectedThread;

	//Locks
	Lock listenLock = new ReentrantLock();
	Lock connectingLock = new ReentrantLock();
	Lock connectedLock = new ReentrantLock();

	private GameState requestState;

	public void setRequestState(GameState requestState)
	{
		this.requestState = requestState;
	}

	public enum State
	{
		NONE,
		LISTEN,
		CONNECTING,
		CONNECTED
	}

	public State state()
	{
		return state.get();
	}

	public enum Message
	{
		RECEIVE_UNDO,
		RECEIVE_SETUP,
		SEND_BOARD_UPDATE
	}

	public interface BtCallback
	{
		void receive(GameState gs, boolean force);

		void undo(boolean force);
	}

	public Bluetooth(Context context, GameService gameService, BtCallback btCallback)
	{
		this.btCallback = btCallback;
		btBroadcaster = LocalBroadcastManager.getInstance(context);
		this.gameService = gameService;
		setup();
	}

	public void setEnemy(String name)
	{
		Intent intent = new Intent(Constants.EVENT_UI);
		intent.putExtra(Constants.EVENT_TYPE, Constants.TYPE_SUBTITLE);
		intent.putExtra(Constants.DATA_STRING, "against " + name);
		btBroadcaster.sendBroadcast(intent);
	}

	public void toast(String toast)
	{
		Intent intent = new Intent(Constants.EVENT_UI);
		intent.putExtra(Constants.EVENT_TYPE, Constants.TYPE_TOAST);
		intent.putExtra(Constants.DATA_STRING, toast);
		btBroadcaster.sendBroadcast(intent);
	}

	public void setBlockIncoming(boolean blockIncoming)
	{
		this.blockIncoming = blockIncoming;

		if (!blockIncoming)
			start();
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

	public void setup()
	{
		Log.d(TAG, "Setup method called");
		btAdapter = BluetoothAdapter.getDefaultAdapter();

		if (state.get() != CONNECTED)
			start();
		else
			setEnemy(connectedThread.socket.getRemoteDevice().getName());

	}

	public void start()
	{
		//In order to see who called start()
		new RuntimeException("start").printStackTrace();

		closeConnecting();
		closeConnected();
		closeListen();

		// Start the thread to listen on a BluetoothServerSocket
		listenLock.lock();
		if (listenThread == null)
		{
			listenThread = new ListenThread();
			listenThread.start();
		}
		listenLock.unlock();
	}

	public void connect(String address, GameState requestState)
	{
		this.requestState = requestState;

		closeConnecting();
		closeConnected();

		BluetoothDevice device = btAdapter.getRemoteDevice(address);
		Log.d(TAG, "connect to: " + device);

		// Start the thread to connect with the given device
		connectingLock.lock();
		connectingThread = new ConnectingThread(device);
		connectingThread.start();
		connectingLock.unlock();
	}

	private void connected(BluetoothSocket socket, boolean isHost)
	{
		closeConnecting();

		if (state.equals(CONNECTED))
			closeConnected();

		Log.d(TAG, "connected");

		if (!blockIncoming || !isHost)
		{
			// Start the thread to manage the connection and perform transmissions
			connectedLock.lock();
			connectedThread = new ConnectedThread(socket, isHost);
			connectedThread.start();
			connectedLock.unlock();
		}
		else
		{
			try
			{
				socket.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	public void stop()
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

		state.set(NONE);
	}

	private void closeListen()
	{
		listenLock.lock();
		if (listenThread != null)
			listenThread.cancel();
		listenLock.unlock();
	}

	private void closeConnecting()
	{
		connectingLock.lock();
		if (connectingThread != null)
		{
			connectingThread.cancel();
			connectingThread = null;
		}
		connectingLock.unlock();
	}

	public void closeConnected()
	{
		connectedLock.lock();
		if (connectedThread != null)
		{
			connectedThread.cancel();
			connectedThread = null;
		}
		connectedLock.unlock();
	}

	public void sendBoard(Board board)
	{
		if (state.get() != CONNECTED) return;

		connectedLock.lock();
		ConnectedThread r = connectedThread;
		connectedLock.unlock();

		if (board != null)
			r.boardUpdate(board);
	}

	public void sendSetup(GameState gs, boolean force)
	{
		if (state.get() != CONNECTED) return;

		connectedLock.lock();
		ConnectedThread r = connectedThread;
		connectedLock.unlock();

		r.setupEnemyGame(gs.board(), gs.players().second.equals(Local), force);
	}

	public void sendUndo(boolean force)
	{
		if (state.get() != CONNECTED) return;

		connectedLock.lock();
		ConnectedThread r = connectedThread;
		connectedLock.unlock();


		r.requestUndo(force);
	}

	public void updateLocalBoard(Board verifyBoard)
	{
		connectedLock.lock();
		if (connectedThread != null)
			connectedThread.localBoard = verifyBoard;
		connectedLock.unlock();
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
			state.set(LISTEN);
		}

		public void run()
		{
			setName("ListenThread");
			Log.d(TAG, "BEGIN ListenThread" + this);

			BluetoothSocket socket;

			// Listen to the server socket if we're not connected
			while (state.get() != CONNECTED)
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
				catch (NullPointerException e)
				{
					break;
				}

				// If a connection was accepted
				if (socket != null)
				{
					synchronized (Bluetooth.this)
					{
						switch (state.get())
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

			cancel();
			Log.i(TAG, "END ListenThread");
		}

		public void cancel()
		{
			Log.d(TAG, "cancel " + this);
			try
			{
				listenLock.lock();
				serverSocket.close();
				listenThread = null;
				listenLock.unlock();
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
			state.set(CONNECTING);
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
					gameService.turnLocal();
					Bluetooth.this.start();
				}

				toast("Unable to connect to device");
				state.set(NONE);

				connecting = false;

				return;
			}

			// Reset the ConnectThread because we're done
			connectingLock.lock();
			connectingThread = null;
			connectingLock.unlock();

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
		private boolean isHost;
		private boolean cancelled = false;

		public ConnectedThread(BluetoothSocket socket, boolean isHost)
		{
			connecting = false;
			this.isHost = isHost;

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
			toast("Connected to " + socket.getRemoteDevice().getName());
			setEnemy(socket.getRemoteDevice().getName());
			state.set(CONNECTED);
		}

		public void run()
		{
			Log.i(TAG, "BEGIN connectedThread");
			byte[] buffer = new byte[1024];

			if (!isHost) sendSetup(requestState, false);

			// Keep listening to the InputStream while connected
			while (state.get() == CONNECTED)
			{
				try
				{
					// Read from the InputStream
					inStream.read(buffer); //TODO improve

					JSONObject json = new JSONObject(new String(buffer));

					int message = json.getInt("message");

					if (message == Bluetooth.Message.SEND_BOARD_UPDATE.ordinal())
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
									Bluetooth.this.start();
									if (gameService != null)
										gameService.turnLocal();
									toast("Games got desynchronized");
								}
							}
							catch (Throwable t)
							{
								Bluetooth.this.start();
								if (gameService != null)
									gameService.turnLocal();
								toast("Games got desynchronized");
							}
						}

					}
					else if (message == Bluetooth.Message.RECEIVE_SETUP.ordinal())
					{
						Board board = JSONBoard.fromJSON(new JSONObject(json.getString("board")));
						boolean swapped = json.getBoolean("swapped");

						localBoard = board;

						btCallback.receive(
								GameState.builder().bt().swapped(swapped).board(board).build(),
								json.getBoolean("force"));
					}
					else if (message == Bluetooth.Message.RECEIVE_UNDO.ordinal())
					{
						btCallback.undo(json.getBoolean("force"));
					}
				}
				catch (IOException e)
				{
					Log.e(TAG, "disconnected", e);

					if (!connecting)
					{
						if (gameService != null)
							gameService.turnLocal();
						Bluetooth.this.start();
					}

					toast("Connection lost");

					if (!cancelled)
						state.set(NONE);

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
					json.put("message", Bluetooth.Message.SEND_BOARD_UPDATE.ordinal());
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
					json.put("message", Bluetooth.Message.RECEIVE_SETUP.ordinal());
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
					json.put("message", Bluetooth.Message.RECEIVE_UNDO.ordinal());
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
				cancelled = true;
			}
			catch (IOException e)
			{
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}
}
