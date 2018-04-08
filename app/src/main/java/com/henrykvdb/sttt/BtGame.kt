package com.henrykvdb.sttt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.res.Resources
import android.util.Log
import com.flaghacker.sttt.common.Board
import com.flaghacker.sttt.common.JSONBoard
import com.flaghacker.sttt.common.toJSON
import com.henrykvdb.sttt.util.isValidBoard
import org.json.JSONException
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class BtGame(val callback: RemoteCallback) : RemoteGame {
    //Final fields
    private val UUID = java.util.UUID.fromString("8158f052-fa77-4d08-8f1a-f598c31e2422")
    private val btAdapter = BluetoothAdapter.getDefaultAdapter()!!
    private val res = Resources.getSystem()

    //State
    override var state = RemoteState.NONE

    //Other
    private var btThread: CloseableThread? = null
    private var connectedDeviceName: String? = null
    private lateinit var requestState: GameState
    private var outStream: OutputStream? = null
    private var localBoard = Board()

    override fun listen(gs: GameState) {
        requestState = gs

        if (state != RemoteState.LISTENING) {
            close()
            btThread = ListenThread()
            btThread?.start()
        }
    }

    override fun connect(adr: String) {
        val device = btAdapter.getRemoteDevice(adr)
        callback.toast(device?.name?.let { res.getString(R.string.connecting_to, it) }
                ?: res.getString(R.string.connecting))

        close()
        btThread = device?.let { ConnectingThread(it) }
        btThread?.start()
    }

    override fun close() {
        try {
            btThread?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        callback.turnLocal()
    }

    abstract class CloseableThread : Thread(), Closeable

    private inner class ListenThread : CloseableThread() {
        private var serverSocket: BluetoothServerSocket? = null
        private var socket: BluetoothSocket? = null

        init {
            this@BtGame.state = RemoteState.LISTENING

            try {
                serverSocket = btAdapter.listenUsingRfcommWithServiceRecord("SuperTTT", UUID)
            } catch (e: IOException) {
                Log.e(LOG_TAG, "listen() failed", e)
            }

        }

        override fun run() {
            Log.e(LOG_TAG, "BEGIN ListenThread " + this)

            // Listen to the server socket if we're not connected
            while (this@BtGame.state != RemoteState.CONNECTED && !isInterrupted) {
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

            this@BtGame.state = RemoteState.NONE
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
            this@BtGame.state = RemoteState.CONNECTING

            try {
                socket = device.createRfcommSocketToServiceRecord(UUID)
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }

        override fun run() {
            Log.e(LOG_TAG, "BEGIN connectingThread" + this)

            btAdapter.cancelDiscovery()

            try {
                // This is a blocking call and will only return on a successful connection or an exception
                socket?.connect()

                // Manage the connection, returns when the connection is stopped
                socket?.let { connected(it, false) }

                // No longer connected, close the socket
                socket!!.close()
            } catch (e: IOException) {
                this@BtGame.state = RemoteState.NONE
                callback.toast(res.getString(R.string.unable_to_connect))
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

        if (Thread.interrupted()) return

        val inStream: InputStream
        try {
            inStream = socket.inputStream
            outStream = socket.outputStream
            state = RemoteState.CONNECTED
        } catch (e: IOException) {
            Log.e(LOG_TAG, "connected sockets not created", e)
            state = RemoteState.NONE
            return
        }

        connectedDeviceName = socket.remoteDevice.name
        Log.e(LOG_TAG, "CONNECTED to $connectedDeviceName")
        callback.toast(res.getString(R.string.connected_to, connectedDeviceName))

        val buffer = ByteArray(1024)

        if (isHost && !Thread.interrupted()) sendSetup()

        // Keep listening to the InputStream while connected
        while (state == RemoteState.CONNECTED && !Thread.interrupted()) {
            try {
                // Read from the InputStream
                inStream.read(buffer) //TODO improve

                val json = JSONObject(String(buffer))

                val message = json.getInt("message")

                Log.e(LOG_TAG, "RECEIVED BTMESSAGE: $message")
                if (message == RemoteMessage.BOARD_UPDATE.ordinal) {
                    val newBoard = JSONBoard.fromJSON(JSONObject(json.getString("board")))
                    val newMove = newBoard.lastMove()!!

                    if (isValidBoard(localBoard, newBoard)) {
                        Log.e(LOG_TAG, "We received a valid board")
                        localBoard = newBoard
                        callback.move(newMove)
                    } else {
                        callback.toast(res.getString(R.string.desync_message))
                        break
                    }
                } else if (message == RemoteMessage.SETUP.ordinal) {
                    val board = JSONBoard.fromJSON(JSONObject(json.getString("board")))
                    localBoard = board
                    callback.newGame(GameState.Builder().bt().board(board).swapped(!json.getBoolean("start")).build())
                } else if (message == RemoteMessage.UNDO.ordinal) {
                    callback.undo(json.getBoolean("force"))
                }
            } catch (e: IOException) {
                Log.e(LOG_TAG, "disconnected", e)
                callback.toast(res.getString(R.string.connection_lost))
                break
            } catch (e: JSONException) {
                Log.e(LOG_TAG, "JSON read parsing failed")
                break
            }
        }

        state = RemoteState.NONE
        callback.turnLocal()

        try {
            inStream.close()
            outStream?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        Log.e(LOG_TAG, "END connected thread")
    }

    override fun sendUndo(force: Boolean) {
        Log.e(LOG_TAG, "Sending undo")
        if (state != RemoteState.CONNECTED) return

        try {
            val json = JSONObject()
            json.put("message", RemoteMessage.UNDO.ordinal)
            json.put("force", force)
            val data = json.toString().toByteArray()
            outStream?.write(data)
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Exception during undo", e)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override fun sendBoard(board: Board) {
        Log.e(LOG_TAG, "Sending board")
        localBoard = board

        try {
            val json = JSONObject()

            json.put("message", RemoteMessage.BOARD_UPDATE.ordinal)
            json.put("board", board.toJSON().toString())

            val data = json.toString().toByteArray()

            outStream?.write(data)
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Exception during boardUpdate", e)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun sendSetup() { //TODO only called once: inline
        callback.newGame(requestState)
        Log.e(LOG_TAG, "Sending setup, starting: " + requestState.players.first)

        localBoard = requestState.board()

        try {
            val json = JSONObject()
            json.put("message", RemoteMessage.SETUP.ordinal)
            json.put("start", requestState.players.first == Source.REMOTE)
            json.put("board", requestState.board().toJSON().toString())
            val data = json.toString().toByteArray()

            outStream?.write(data)
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Exception during boardUpdate", e)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("HardwareIds")
    override val localName = listOfNotNull(btAdapter.name, btAdapter.address, "ERROR").first()
    override val remoteName = if (state == RemoteState.CONNECTED) connectedDeviceName else null
    override fun lastBoard() = localBoard
}