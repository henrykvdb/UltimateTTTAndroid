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
import com.flaghacker.uttt.common.Coord;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService extends Service
{
	// Debugging
	private static final String TAG = "BluetoothService";

	// Unique UUID for this application
	private static final UUID UUID = java.util.UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

	// Member fields
	private BluetoothAdapter mAdapter;
	private Handler mHandler = null;
	private AcceptThread mSecureAcceptThread;
	private AcceptThread mInsecureAcceptThread;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;
	private int mState;

	// Constants that indicate the current connection state
	public static final int STATE_NONE = 0;       // we're doing nothing
	public static final int STATE_LISTEN = 1;     // now listening for incoming connections
	public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
	public static final int STATE_CONNECTED = 3;  // now connected to a remote device

	// Message types sent from the BluetoothService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;
	
	// Binder given to clients
	private final IBinder mBinder = new LocalBinder();

	public class LocalBinder extends Binder
	{
		BluetoothService getService()
		{
			return BluetoothService.this;
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
		mState = STATE_NONE;
		super.onCreate();
	}

	public void setHandler(Handler handler)
	{
		mHandler = handler;
		mHandler.obtainMessage(MESSAGE_STATE_CHANGE, mState, -1).sendToTarget();
	}
	
	public synchronized int getState()
	{
		return mState;
	}

	/**
	 * Start the chat service. Specifically start AcceptThread to begin a session in listening (server) mode. Called by
	 * the Activity onResume()
	 */
	public synchronized void start()
	{
		Log.d(TAG, "start");

		// Cancel any thread attempting to make a connection
		if (mConnectThread != null)
		{
			mConnectThread.cancel();
			mConnectThread = null;
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null)
		{
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		// Start the thread to listen on a BluetoothServerSocket
		if (mSecureAcceptThread == null)
		{
			mSecureAcceptThread = new AcceptThread();
			mSecureAcceptThread.start();
		}
		if (mInsecureAcceptThread == null)
		{
			mInsecureAcceptThread = new AcceptThread();
			mInsecureAcceptThread.start();
		}
		mHandler.obtainMessage(MESSAGE_STATE_CHANGE, mState, -1).sendToTarget();
	}

	public synchronized void connect(String address)
	{
		BluetoothDevice device = mAdapter.getRemoteDevice(address);
		Log.d(TAG, "connect to: " + device);

		// Cancel any thread attempting to make a connection
		if (mState == STATE_CONNECTING)
		{
			if (mConnectThread != null)
			{
				mConnectThread.cancel();
				mConnectThread = null;
			}
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null)
		{
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		// Start the thread to connect with the given device
		mConnectThread = new ConnectThread(device);
		mConnectThread.start();
		mHandler.obtainMessage(MESSAGE_STATE_CHANGE, mState, -1).sendToTarget();
	}

	/**
	 * Start the ConnectedThread to begin managing a Bluetooth connection
	 *
	 * @param socket The BluetoothSocket on which the connection was made
	 * @param device The BluetoothDevice that has been connected
	 */
	public synchronized void connected(BluetoothSocket socket, BluetoothDevice
			device, final String socketType)
	{
		Log.d(TAG, "connected, Socket Type:" + socketType);

		// Cancel the thread that completed the connection
		if (mConnectThread != null)
		{
			mConnectThread.cancel();
			mConnectThread = null;
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null)
		{
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		// Cancel the accept thread because we only want to connect to one device
		if (mSecureAcceptThread != null)
		{
			mSecureAcceptThread.cancel();
			mSecureAcceptThread = null;
		}
		if (mInsecureAcceptThread != null)
		{
			mInsecureAcceptThread.cancel();
			mInsecureAcceptThread = null;
		}

		// Start the thread to manage the connection and perform transmissions
		mConnectedThread = new ConnectedThread(socket, socketType);
		mConnectedThread.start();

		mHandler.obtainMessage(MESSAGE_DEVICE_NAME, - 1, - 1, device.getName()).sendToTarget();
		mHandler.obtainMessage(MESSAGE_STATE_CHANGE, mState, -1).sendToTarget();
		// Send the name of the connected device back to the UI Activity
	}

	/**
	 * Stop all threads
	 */
	public synchronized void stop()
	{
		Log.d(TAG, "stop");

		if (mConnectThread != null)
		{
			mConnectThread.cancel();
			mConnectThread = null;
		}

		if (mConnectedThread != null)
		{
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		if (mSecureAcceptThread != null)
		{
			mSecureAcceptThread.cancel();
			mSecureAcceptThread = null;
		}

		if (mInsecureAcceptThread != null)
		{
			mInsecureAcceptThread.cancel();
			mInsecureAcceptThread = null;
		}
		mState = STATE_NONE;
		mHandler.obtainMessage(MESSAGE_STATE_CHANGE, mState, -1).sendToTarget();
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
			if (mState != STATE_CONNECTED) return;
			r = mConnectedThread;
		}
		// Perform the write unsynchronized
		r.write(out);
	}

	/**
	 * Indicate that the connection attempt failed and notify the UI Activity.
	 */
	private void connectionFailed()
	{
		// Send a failure message back to the Activity
		mHandler.obtainMessage(MESSAGE_TOAST, - 1, - 1, "Unable to connect device").sendToTarget();

		mState = STATE_NONE;
		mHandler.obtainMessage(MESSAGE_STATE_CHANGE, mState, -1).sendToTarget();

		// Start the service over to restart listening mode
		BluetoothService.this.start();
	}

	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 */
	private void connectionLost()
	{
		mState = STATE_NONE;
		mHandler.obtainMessage(MESSAGE_STATE_CHANGE, mState, -1).sendToTarget();
		mHandler.obtainMessage(MESSAGE_TOAST, - 1, - 1, "Unable to connect device").sendToTarget();

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
		private String mSocketType = "Secure";

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
				Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
			}
			mmServerSocket = tmp;
			mState = STATE_LISTEN;
		}

		public void run()
		{
			Log.d(TAG, "Socket Type: " + mSocketType +
					"BEGIN mAcceptThread" + this);
			setName("AcceptThread" + mSocketType);

			BluetoothSocket socket = null;

			// Listen to the server socket if we're not connected
			while (mState != STATE_CONNECTED)
			{
				try
				{
					// This is a blocking call and will only return on a
					// successful connection or an exception
					socket = mmServerSocket.accept();
				}
				catch (IOException e)
				{
					Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
					break;
				}

				// If a connection was accepted
				if (socket != null)
				{
					synchronized (BluetoothService.this)
					{
						switch (mState)
						{
							case STATE_LISTEN:
							case STATE_CONNECTING:
								// Situation normal. Start the connected thread.
								connected(socket, socket.getRemoteDevice(),
										mSocketType);
								break;
							case STATE_NONE:
							case STATE_CONNECTED:
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
			Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

		}

		public void cancel()
		{
			Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
			try
			{
				mmServerSocket.close();
			}
			catch (IOException e)
			{
				Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
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
		private String mSocketType;

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
			mState = STATE_CONNECTING;
		}

		public void run()
		{
			Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
			setName("ConnectThread" + mSocketType);

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
					Log.e(TAG, "unable to close() " + mSocketType +
							" socket during connection failure", e2);
				}
				connectionFailed();
				return;
			}

			// Reset the ConnectThread because we're done
			synchronized (BluetoothService.this)
			{
				mConnectThread = null;
			}

			// Start the connected thread
			connected(mmSocket, mmDevice, mSocketType);
		}

		public void cancel()
		{
			try
			{
				mmSocket.close();
			}
			catch (IOException e)
			{
				Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
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

		public ConnectedThread(BluetoothSocket socket, String socketType)
		{
			Log.d(TAG, "create ConnectedThread: " + socketType);
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;
			mHandler.obtainMessage(MESSAGE_STATE_CHANGE, mState, -1).sendToTarget();

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
			mState = STATE_CONNECTED;
		}

		public void run()
		{
			Log.i(TAG, "BEGIN mConnectedThread");
			byte[] buffer = new byte[1024];
			int bytes;

			// Keep listening to the InputStream while connected
			while (mState == STATE_CONNECTED)
			{
				try
				{
					// Read from the InputStream
					bytes = mmInStream.read(buffer);

					// Send the obtained bytes to the UI Activity
					mHandler.obtainMessage(MESSAGE_READ, bytes, - 1, buffer).sendToTarget();
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
				mHandler.obtainMessage(MESSAGE_WRITE, - 1, - 1, buffer).sendToTarget();
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

	/**
	 * method for clients
	 */
	public Coord getEnemyMove(GameState gameState)
	{
		return null;
	}
}