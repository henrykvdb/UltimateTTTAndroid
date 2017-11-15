package com.henrykvdb.sttt;

import android.app.Service;
import android.bluetooth.*;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import com.flaghacker.uttt.common.*;
import com.henrykvdb.sttt.Util.IntentUtil;
import com.henrykvdb.sttt.Util.Util;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;

public class BtService extends Service {
	private static final java.util.UUID UUID = java.util.UUID.fromString("8158f052-fa77-4d08-8f1a-f598c31e2422");

	private final IBinder mBinder = new LocalBinder();
	private BluetoothAdapter btAdapter;

	private volatile State state = State.NONE;
	private Board localBoard = new Board();
	private OutputStream outStream = null;
	private String connectedDeviceName;
	private CloseableThread btThread;
	private GameState requestState;

	public enum State {
		NONE,
		LISTENING,
		CONNECTING,
		CONNECTED
	}

	public enum Message {
		RECEIVE_UNDO,
		RECEIVE_SETUP,
		SEND_BOARD_UPDATE
	}

	public class LocalBinder extends Binder {
		BtService getService() {
			return BtService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		Log.e(Constants.LOG_TAG, "BTSERVICE CREATED");
		btAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	public void listen() {
		setRequestState(GameState.builder().bt().build());

		closeThread();
		btThread = new ListenThread();
		btThread.start();
	}

	public void connect(String address) {
		closeThread();

		BluetoothDevice device = btAdapter.getRemoteDevice(address);
		Log.e(Constants.LOG_TAG, "connect to: " + device);

		btThread = new ConnectingThread(device);
		btThread.start();
	}

	public void closeThread() {
		if (btThread != null) {
			try {
				btThread.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

		IntentUtil.sendTurnLocal(this);
	}

	public static abstract class CloseableThread extends Thread implements Closeable {
	}

	private class ListenThread extends CloseableThread {
		private BluetoothServerSocket serverSocket;
		private BluetoothSocket socket;

		public ListenThread() {
			state = BtService.State.LISTENING;

			try {
				serverSocket = btAdapter.listenUsingRfcommWithServiceRecord("SuperTTT", UUID);
			}
			catch (IOException e) {
				Log.e(Constants.LOG_TAG, "listen() failed", e);
			}
		}

		@Override
		public void run() {
			Log.e(Constants.LOG_TAG, "BEGIN ListenThread " + this);

			// Listen to the server socket if we're not connected
			while (state != BtService.State.CONNECTED && !isInterrupted()) {
				try {
					// This is a blocking call and will only return on a successful connection or an exception
					socket = serverSocket.accept();
				}
				catch (IOException e) {
					Log.e(Constants.LOG_TAG, "accept() failed", e);
					break;
				}

				if (socket != null && !isInterrupted())
					connected(socket, true);
			}

			state = BtService.State.NONE;
			Log.e(Constants.LOG_TAG, "END ListenThread");
		}

		@Override
		public void close() throws IOException {
			if (serverSocket != null)
				serverSocket.close();
			if (socket != null)
				socket.close();
		}
	}

	private class ConnectingThread extends CloseableThread {
		private BluetoothSocket socket;

		public ConnectingThread(BluetoothDevice device) {
			state = BtService.State.CONNECTING;

			try {
				socket = device.createRfcommSocketToServiceRecord(UUID);
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void run() {
			Log.e(Constants.LOG_TAG, "BEGIN connectingThread" + this);

			btAdapter.cancelDiscovery();

			try {
				// This is a blocking call and will only return on a successful connection or an exception
				socket.connect();

				// Manage the connection, returns when the connection is stopped
				connected(socket, false);

				// No longer connected, close the socket
				socket.close();
			}
			catch (IOException e) {
				state = BtService.State.NONE;
				IntentUtil.sendToast(BtService.this, "Unable to connect to device");
				Log.e(Constants.LOG_TAG, "Unable to connect to device", e);

				try {
					socket.close();
				}
				catch (IOException e2) {
					Log.e(Constants.LOG_TAG, "unable to interrupt() socket during connection failure", e2);
				}
			}

			Log.e(Constants.LOG_TAG, "END connectingThread" + this);
		}

		@Override
		public void close() throws IOException {
			if (socket != null)
				socket.close();
		}
	}

	private void connected(BluetoothSocket socket, boolean isHost) {
		Log.e(Constants.LOG_TAG, "BEGIN connected thread");

		if (Thread.interrupted())
			return;

		InputStream inStream;
		outStream = null;

		try {
			inStream = socket.getInputStream();
			outStream = socket.getOutputStream();

			state = State.CONNECTED;
		}
		catch (IOException e) {
			Log.e(Constants.LOG_TAG, "connected sockets not created", e);
			state = State.NONE;
			return;
		}

		connectedDeviceName = socket.getRemoteDevice().getName();
		Log.e(Constants.LOG_TAG, "CONNECTED to " + socket.getRemoteDevice().getName());
		IntentUtil.sendToast(this, "Connected to " + socket.getRemoteDevice().getName());

		byte[] buffer = new byte[1024];

		if (isHost && !Thread.interrupted())
			sendSetup();

		// Keep listening to the InputStream while connected
		while (state == State.CONNECTED && !Thread.interrupted()) {
			try {
				// Read from the InputStream
				inStream.read(buffer); //TODO improve

				JSONObject json = new JSONObject(new String(buffer));

				int message = json.getInt("message");

				Log.e(Constants.LOG_TAG, "RECEIVED BTMESSAGE: " + message);
				if (message == Message.SEND_BOARD_UPDATE.ordinal()) {
					Board newBoard = JSONBoard.fromJSON(new JSONObject(json.getString("board")));
					Coord newMove = newBoard.getLastMove();

					if (Util.isValidBoard(localBoard, newBoard)) {
						Log.e(Constants.LOG_TAG, "We received a valid board");
						localBoard = newBoard;
						IntentUtil.sendMove(this, MainActivity.Source.Bluetooth, newMove);
					}
					else {
						IntentUtil.sendToast(this, "Games got desynchronized");
						break;
					}
				}
				else if (message == Message.RECEIVE_SETUP.ordinal()) {
					Board board = JSONBoard.fromJSON(new JSONObject(json.getString("board")));
					localBoard = board;

					IntentUtil.sendNewGame(this, GameState.builder().bt().board(board).swapped(!json.getBoolean("start")).build());
				}
				else if (message == Message.RECEIVE_UNDO.ordinal()) {
					IntentUtil.sendUndo(this, json.getBoolean("force"));
				}
			}
			catch (IOException e) {
				Log.e(Constants.LOG_TAG, "disconnected", e);
				IntentUtil.sendToast(this, "Connection lost");
				break;
			}
			catch (JSONException e) {
				Log.e(Constants.LOG_TAG, "JSON read parsing failed");
				break;
			}
		}

		state = State.NONE;
		IntentUtil.sendTurnLocal(this);

		try {
			inStream.close();
			outStream.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		Log.e(Constants.LOG_TAG, "END connected thread");
	}

	public String getConnectedDeviceName() {
		if (state != State.CONNECTED)
			return null;

		return connectedDeviceName;
	}

	public State getState() {
		return state;
	}

	public void setLocalBoard(Board localBoard) {
		this.localBoard = localBoard;
	}

	public void sendUndo(boolean force) {
		Log.e(Constants.LOG_TAG, "Sending undo");

		if (state != State.CONNECTED)
			return;

		try {
			JSONObject json = new JSONObject();
			json.put("message", Message.RECEIVE_UNDO.ordinal());
			json.put("force", force);
			byte[] data = json.toString().getBytes();

			outStream.write(data);
		}
		catch (IOException e) {
			Log.e(Constants.LOG_TAG, "Exception during undo", e);
		}
		catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void sendSetup() {
		IntentUtil.sendNewGame(this, requestState);
		Log.e(Constants.LOG_TAG, "Sending setup, starting: " + requestState.players().first);

		localBoard = requestState.board();

		try {
			JSONObject json = new JSONObject();

			json.put("message", Message.RECEIVE_SETUP.ordinal());
			json.put("start", requestState.players().first.equals(MainActivity.Source.Bluetooth));
			json.put("board", JSONBoard.toJSON(requestState.board()).toString());

			byte[] data = json.toString().getBytes();

			outStream.write(data);
		}
		catch (IOException e) {
			Log.e(Constants.LOG_TAG, "Exception during boardUpdate", e);
		}
		catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void sendBoard(Board board) {
		Log.e(Constants.LOG_TAG, "Sending board");

		localBoard = board;

		try {
			JSONObject json = new JSONObject();

			json.put("message", Message.SEND_BOARD_UPDATE.ordinal());
			json.put("board", JSONBoard.toJSON(board).toString());

			byte[] data = json.toString().getBytes();

			outStream.write(data);
		}
		catch (IOException e) {
			Log.e(Constants.LOG_TAG, "Exception during boardUpdate", e);
		}
		catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void setRequestState(GameState requestState) {
		this.requestState = requestState;
	}

	public String getLocalBluetoothName() {
		return btAdapter.getName() != null ? btAdapter.getName() : btAdapter.getAddress();
	}

	public Board getLocalBoard() {
		return localBoard;
	}
}
