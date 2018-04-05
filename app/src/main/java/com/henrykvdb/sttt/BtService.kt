package com.henrykvdb.sttt

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.flaghacker.sttt.common.Board
import com.flaghacker.sttt.common.JSONBoard
import com.flaghacker.sttt.common.toJSON
import com.henrykvdb.sttt.util.*
import org.json.JSONException
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class BtService : Service() {
    private val UUID = java.util.UUID.fromString("8158f052-fa77-4d08-8f1a-f598c31e2422")
    private val mBinder = LocalBinder()

    private var btAdapter: BluetoothAdapter? =  BluetoothAdapter.getDefaultAdapter()
    @Volatile private var state = State.NONE
    private var localBoard = Board()

    private lateinit var requestState: GameState
    private var connectedDeviceName: String? = null
    private var btThread: CloseableThread? = null
    private var outStream: OutputStream? = null

    enum class State {
        NONE,
        LISTENING,
        CONNECTING,
        CONNECTED
    }

    enum class Message {
        RECEIVE_UNDO,
        RECEIVE_SETUP,
        SEND_BOARD_UPDATE
    }

    override fun onBind(intent: Intent): IBinder? = mBinder
    inner class LocalBinder : Binder() {
        fun getService() = this@BtService
    }

    fun listen() {
        setRequestState(GameState.Builder().bt().build())

        closeThread()
        btThread = ListenThread()
        btThread?.start()
    }

    fun connect(address: String) {
        closeThread()

        val device = btAdapter?.getRemoteDevice(address)
        sendToast(this, device?.name?.let { getString(R.string.connecting_to, it) } ?: getString(R.string.connecting))
        Log.e(LOG_TAG, "connecting to: $device")

        btThread = device?.let { ConnectingThread(it) }
        btThread?.start()
    }

    fun closeThread() {
        try {
            btThread?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        sendTurnLocal(this)
    }

    abstract class CloseableThread : Thread(), Closeable

    private inner class ListenThread : CloseableThread() {
        private var serverSocket: BluetoothServerSocket? = null
        private var socket: BluetoothSocket? = null

        init {
            this@BtService.state = BtService.State.LISTENING

            try {
                serverSocket = btAdapter?.listenUsingRfcommWithServiceRecord("SuperTTT", UUID)
            } catch (e: IOException) {
                Log.e(LOG_TAG, "listen() failed", e)
            }

        }

        override fun run() {
            Log.e(LOG_TAG, "BEGIN ListenThread " + this)

            // Listen to the server socket if we're not connected
            while (this@BtService.state != BtService.State.CONNECTED && !isInterrupted) {
                try {
                    // This is a blocking call and will only return on a successful connection or an exception
                    socket = serverSocket?.accept()
                } catch (e: IOException) {
                    Log.e(LOG_TAG, "accept() failed", e)
                    break
                }

                if (!isInterrupted)
                    socket?.let { connected(it, true) }
            }

            this@BtService.state = BtService.State.NONE
            Log.e(LOG_TAG, "END ListenThread")
        }

        @Throws(IOException::class)
        override fun close() {
            serverSocket?.close()
            socket?.close()
        }
    }

    private inner class ConnectingThread(device: BluetoothDevice) : CloseableThread() {
        private var socket: BluetoothSocket? = null

        init {
            this@BtService.state = BtService.State.CONNECTING

            try {
                socket = device.createRfcommSocketToServiceRecord(UUID)
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }

        override fun run() {
            Log.e(LOG_TAG, "BEGIN connectingThread" + this)

            btAdapter?.cancelDiscovery()

            try {
                // This is a blocking call and will only return on a successful connection or an exception
                socket?.connect()

                // Manage the connection, returns when the connection is stopped
                socket?.let { connected(it, false) }

                // No longer connected, close the socket
                socket!!.close()
            } catch (e: IOException) {
                this@BtService.state = BtService.State.NONE
                sendToast(this@BtService, getString(R.string.unable_to_connect))
                Log.e(LOG_TAG, "Unable to connect to device", e)

                try {
                    socket!!.close()
                } catch (e2: IOException) {
                    Log.e(LOG_TAG, "unable to interrupt() socket during connection failure", e2)
                }

            }

            Log.e(LOG_TAG, "END connectingThread" + this)
        }

        @Throws(IOException::class)
        override fun close() {
            socket?.close()
        }
    }


    private fun connected(socket: BluetoothSocket, isHost: Boolean) {
        Log.e(LOG_TAG, "BEGIN connected thread")

        if (Thread.interrupted())
            return

        val inStream: InputStream
        try {
            inStream = socket.inputStream
            outStream = socket.outputStream

            state = State.CONNECTED
        } catch (e: IOException) {
            Log.e(LOG_TAG, "connected sockets not created", e)
            state = State.NONE
            return
        }

        connectedDeviceName = socket.remoteDevice.name
        Log.e(LOG_TAG, "CONNECTED to $connectedDeviceName")
        sendToast(this, getString(R.string.connected_to, connectedDeviceName))

        val buffer = ByteArray(1024)

        if (isHost && !Thread.interrupted()) sendSetup()

        // Keep listening to the InputStream while connected
        while (state == State.CONNECTED && !Thread.interrupted()) {
            try {
                // Read from the InputStream
                inStream.read(buffer) //TODO improve

                val json = JSONObject(String(buffer))

                val message = json.getInt("message")

                Log.e(LOG_TAG, "RECEIVED BTMESSAGE: $message")
                if (message == Message.SEND_BOARD_UPDATE.ordinal) {
                    val newBoard = JSONBoard.fromJSON(JSONObject(json.getString("board")))
                    val newMove = newBoard.lastMove()!!

                    if (isValidBoard(localBoard, newBoard)) {
                        Log.e(LOG_TAG, "We received a valid board")
                        localBoard = newBoard
                        sendMove(this, MainActivity.Source.Bluetooth, newMove)
                    } else {
                        sendToast(this, getString(R.string.desync_message))
                        break
                    }
                } else if (message == Message.RECEIVE_SETUP.ordinal) {
                    val board = JSONBoard.fromJSON(JSONObject(json.getString("board")))
                    localBoard = board
                    sendNewGame(this, GameState.Builder().bt().board(board).swapped(!json.getBoolean("start")).build())
                } else if (message == Message.RECEIVE_UNDO.ordinal) {
                    sendUndo(this, json.getBoolean("force"))
                }
            } catch (e: IOException) {
                Log.e(LOG_TAG, "disconnected", e)
                sendToast(this, getString(R.string.connection_lost))
                break
            } catch (e: JSONException) {
                Log.e(LOG_TAG, "JSON read parsing failed")
                break
            }

        }

        state = State.NONE
        sendTurnLocal(this)

        try {
            inStream.close()
            outStream?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        Log.e(LOG_TAG, "END connected thread")
    }

    fun sendUndo(force: Boolean) {
        Log.e(LOG_TAG, "Sending undo")

        if (state != State.CONNECTED)
            return

        try {
            val json = JSONObject()
            json.put("message", Message.RECEIVE_UNDO.ordinal)
            json.put("force", force)
            val data = json.toString().toByteArray()

            outStream?.write(data)
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Exception during undo", e)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

    }

    private fun sendSetup() { //TODO only called once: inline
        sendNewGame(this, requestState)
        Log.e(LOG_TAG, "Sending setup, starting: " + requestState.players.first)

        localBoard = requestState.board()

        try {
            val json = JSONObject()
            json.put("message", Message.RECEIVE_SETUP.ordinal)
            json.put("start", requestState.players.first == MainActivity.Source.Bluetooth)
            json.put("board", requestState.board().toJSON().toString())
            val data = json.toString().toByteArray()

            outStream?.write(data)
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Exception during boardUpdate", e)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun sendBoard(board: Board) {
        Log.e(LOG_TAG, "Sending board")
        localBoard = board

        try {
            val json = JSONObject()

            json.put("message", Message.SEND_BOARD_UPDATE.ordinal)
            json.put("board", board.toJSON().toString())

            val data = json.toString().toByteArray()

            outStream?.write(data)
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Exception during boardUpdate", e)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

    }

    fun setRequestState(requestState: GameState) {
        this.requestState = requestState
    }

    fun setLocalBoard(localBoard: Board) {
        this.localBoard = localBoard
    }

    @SuppressLint("HardwareIds")
    fun getLocalBluetoothName(): String = if (btAdapter!!.name != null) btAdapter!!.name else btAdapter!!.address
    fun getConnectedDeviceName(): String? = if (state != State.CONNECTED) null else connectedDeviceName
    fun getLocalBoard() = localBoard
    fun getState() = state
}