package com.henrykvdb.uttt;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BtService extends Service
{
	// Debugging
	private static final String TAG = "BluetoothService";

	// Unique UUID for this application
	private static final UUID UUID = java.util.UUID.fromString("8158f052-fa77-4d08-8f1a-f598c31e2422");

	// Member fields
	private BluetoothAdapter mAdapter;
	private Handler handler = null;
	private AcceptThread acceptThread;
	private ConnectThread connectThread;
	private ConnectedThread connectedThread;
	private State state = State.NONE;

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
		READ,
		WRITE,
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
	public void onDestroy()
	{
		stop();
		super.onDestroy();
	}

	@Override
	public void onCreate()
	{
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		super.onCreate();
	}

	public void setHandler(Handler handler)
	{
		this.handler = handler;
		setState(State.NONE);
	}

	public void setState(State newState)
	{
		state = newState;
		handler.obtainMessage(Message.STATE_CHANGE.ordinal(), - 1, - 1).sendToTarget();
	}

	public synchronized State getState()
	{
		return state;
	}

	/**
	 * Start the chat service. Specifically start AcceptThread to begin a session in listening (server) mode. Called by
	 * the Activity onResume()
	 */
	public synchronized void start()
	{
		Log.d(TAG, "start");

		// Cancel any thread attempting to make a connection
		if (connectThread != null)
		{
			connectThread.cancel();
			connectThread = null;
		}

		// Cancel any thread currently running a connection
		if (connectedThread != null)
		{
			connectedThread.cancel();
			connectedThread = null;
		}

		// Start the thread to listen on a BluetoothServerSocket
		if (acceptThread == null)
		{
			acceptThread = new AcceptThread();
			acceptThread.start();
		}
	}

	public synchronized void connect(String address)
	{
		BluetoothDevice device = mAdapter.getRemoteDevice(address);
		Log.d(TAG, "connect to: " + device);

		// Cancel any thread attempting to make a connection
		if (state == State.CONNECTING)
		{
			if (connectThread != null)
			{
				connectThread.cancel();
				connectThread = null;
			}
		}

		// Cancel any thread currently running a connection
		if (connectedThread != null)
		{
			connectedThread.cancel();
			connectedThread = null;
		}

		// Start the thread to connect with the given device
		connectThread = new ConnectThread(device);
		connectThread.start();
	}

	/**
	 * Start the ConnectedThread to begin managing a Bluetooth connection
	 *
	 * @param socket The BluetoothSocket on which the connection was made
	 * @param device The BluetoothDevice that has been connected
	 */
	public synchronized void connected(BluetoothSocket socket, BluetoothDevice device)
	{
		Log.d(TAG, "connected");

		// Cancel the thread that completed the connection
		if (connectThread != null)
		{
			connectThread.cancel();
			connectThread = null;
		}

		// Cancel any thread currently running a connection
		if (connectedThread != null)
		{
			connectedThread.cancel();
			connectedThread = null;
		}

		// Cancel the accept thread because we only want to connect to one device
		if (acceptThread != null)
		{
			acceptThread.cancel();
			acceptThread = null;
		}

		// Start the thread to manage the connection and perform transmissions
		connectedThread = new ConnectedThread(socket);
		connectedThread.start();
	}

	/**
	 * Stop all threads
	 */
	public synchronized void stop()
	{
		Log.d(TAG, "stop");

		if (connectThread != null)
		{
			connectThread.cancel();
			connectThread = null;
		}

		if (connectedThread != null)
		{
			connectedThread.cancel();
			connectedThread = null;
		}

		if (acceptThread != null)
		{
			acceptThread.cancel();
			acceptThread = null;
		}

		setState(State.NONE);
	}

	/**
	 * Write to the ConnectedThread in an unsynchronized manner
	 *
	 * @param out The bytes to write
	 * @see ConnectedThread#write(byte[])
	 */
	public void write(byte[] out)
	{
		// Create temporary object
		ConnectedThread r;
		// Synchronize a copy of the ConnectedThread
		synchronized (this)
		{
			if (state != State.CONNECTED) return;
			r = connectedThread;
		}
		// Perform the write unsynchronized
		r.write(out);
	}

	/**
	 * Indicate that the connection attempt failed and notify the UI Activity.
	 */
	private void connectionFailed()
	{
		// Send a failure message back to the UI
		setState(State.NONE);
		handler.obtainMessage(Message.TOAST.ordinal(), - 1, - 1, "Unable to connect device").sendToTarget();

		// Start the service over to restart listening mode
		BtService.this.start();
	}

	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 */
	private void connectionLost()
	{
		// Send a failure message back to the UI
		setState(State.NONE);
		handler.obtainMessage(Message.TOAST.ordinal(), - 1, - 1, "Bluetooth connection lost").sendToTarget();

		// Start the service over to restart listening mode
		start();
	}

	/**
	 * This thread runs while listening for incoming connections. It behaves like a server-side client. It runs until a
	 * connection is accepted (or until cancelled).
	 */
	private class AcceptThread extends Thread
	{
		// The local server socket
		private final BluetoothServerSocket mmServerSocket;

		public AcceptThread()
		{
			BluetoothServerSocket tmp = null;

			// Create a new listening server socket
			try
			{
				tmp = mAdapter.listenUsingRfcommWithServiceRecord("BluetoothChatSecure",
						UUID);
			}
			catch (IOException e)
			{
				Log.e(TAG, "listen() failed", e);
			}
			mmServerSocket = tmp;
			setState(BtService.State.LISTEN);
		}

		public void run()
		{
			Log.d(TAG, "BEGIN mAcceptThread" + this);
			setName("AcceptThread");

			BluetoothSocket socket;

			// Listen to the server socket if we're not connected
			while (state != BtService.State.CONNECTED)
			{
				try
				{
					// This is a blocking call and will only return on a
					// successful connection or an exception
					socket = mmServerSocket.accept();
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
								connected(socket, socket.getRemoteDevice());
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
			Log.i(TAG, "END mAcceptThread");

		}

		public void cancel()
		{
			Log.d(TAG, "cancel " + this);
			try
			{
				mmServerSocket.close();
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
	private class ConnectThread extends Thread
	{
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;

		public ConnectThread(BluetoothDevice device)
		{
			mmDevice = device;
			BluetoothSocket tmp = null;

			// Get a BluetoothSocket for a connection with the
			// given BluetoothDevice
			try
			{
				tmp = device.createRfcommSocketToServiceRecord(
						UUID);

			}
			catch (IOException e)
			{
				Log.e(TAG, "create() failed", e);
			}
			mmSocket = tmp;
			setState(BtService.State.CONNECTING);
		}

		public void run()
		{
			Log.i(TAG, "BEGIN connectThread");
			setName("ConnectThread");

			// Always cancel discovery because it will slow down a connection
			mAdapter.cancelDiscovery();

			// Make a connection to the BluetoothSocket
			try
			{
				// This is a blocking call and will only return on a
				// successful connection or an exception
				mmSocket.connect();
			}
			catch (IOException e)
			{
				// Close the socket
				try
				{
					mmSocket.close();
				}
				catch (IOException e2)
				{
					Log.e(TAG, "unable to close() socket during connection failure", e2);
				}
				connectionFailed();
				return;
			}

			// Reset the ConnectThread because we're done
			synchronized (BtService.this)
			{
				connectThread = null;
			}

			// Start the connected thread
			connected(mmSocket, mmDevice);
		}

		public void cancel()
		{
			try
			{
				mmSocket.close();
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
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket)
		{
			Log.d(TAG, "create ConnectedThread");
			mmSocket = socket;
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

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
			handler.obtainMessage(Message.DEVICE_NAME.ordinal(), - 1, - 1, socket.getRemoteDevice().getName()).sendToTarget();
			setState(BtService.State.CONNECTED);
		}

		public void run()
		{
			Log.i(TAG, "BEGIN connectedThread");
			byte[] buffer = new byte[1024];
			int bytes;

			// Keep listening to the InputStream while connected
			while (state == BtService.State.CONNECTED)
			{
				try
				{
					// Read from the InputStream
					bytes = mmInStream.read(buffer);

					// Send the obtained bytes to the UI Activity
					handler.obtainMessage(BtService.Message.READ.ordinal(), bytes, - 1, buffer).sendToTarget();
				}
				catch (IOException e)
				{
					Log.e(TAG, "disconnected", e);
					connectionLost();
					break;
				}
			}
		}

		/**
		 * Write to the connected OutStream.
		 *
		 * @param buffer The bytes to write
		 */
		public void write(byte[] buffer)
		{
			try
			{
				mmOutStream.write(buffer);

				// Share the sent message back to the UI Activity
				handler.obtainMessage(BtService.Message.WRITE.ordinal(), - 1, - 1, buffer).sendToTarget();
			}
			catch (IOException e)
			{
				Log.e(TAG, "Exception during write", e);
			}
		}

		public void cancel()
		{
			try
			{
				mmSocket.close();
			}
			catch (IOException e)
			{
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}
}