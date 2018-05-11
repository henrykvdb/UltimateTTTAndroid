/*
 * This file is part of Super Tic Tac Toe.
 * Copyright (C) 2018 Henryk Van der Bruggen <henrykdev@gmail.com>
 *
 * Super Tic Tac Toe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Super Tic Tac Toe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Super Tic Tac Toe.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.henrykvdb.sttt.remote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.res.Resources
import com.flaghacker.sttt.common.Board
import com.flaghacker.sttt.common.JSONBoard
import com.flaghacker.sttt.common.toJSON
import com.henrykvdb.sttt.GameState
import com.henrykvdb.sttt.R
import com.henrykvdb.sttt.Source
import com.henrykvdb.sttt.log
import org.json.JSONException
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

private val UUID = java.util.UUID.fromString("8158f052-fa77-4d08-8f1a-f598c31e2422")

@SuppressLint("HardwareIds")
class BtGame(val callback: RemoteCallback, val res: Resources) : RemoteGame {
    //Final fields
    private val btAdapter = BluetoothAdapter.getDefaultAdapter()!!

    //State
    override var state = RemoteState.NONE

    //Other
    private var btThread: CloseableThread? = null
    private var connectedDeviceName: String? = null
    private lateinit var requestState: GameState
    private var boards = LinkedList(listOf(Board()))

    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null


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

        device?.let {
            btThread = ConnectingThread(it)
            btThread?.start()
        }
    }

    override fun close() {
        btThread?.close()
        btThread?.join()
    }

    private abstract class CloseableThread : Thread(), Closeable

    private inner class ListenThread : CloseableThread() {
        private var serverSocket: BluetoothServerSocket? = null
        private var socket: BluetoothSocket? = null

        init {
            try {
                this@BtGame.state = RemoteState.LISTENING
                serverSocket = btAdapter.listenUsingRfcommWithServiceRecord("SuperTTT", UUID)
            } catch (e: IOException) {
                log(e.toString())
                interrupt()
            }
        }

        override fun run() {
            log("BEGIN ListenThread $this")
            while (this@BtGame.state != RemoteState.CONNECTED && !isInterrupted) {
                try {
                    socket = serverSocket?.accept() //Blocking call
                } catch (e: IOException) {
                    log(e.toString())
                    interrupt()
                }

                if (!isInterrupted) socket?.let { connected(it, true) } //Manage connection, blocking call
                else interrupt()
            }

            try {
                this@BtGame.state = RemoteState.NONE
                serverSocket?.close()
                socket?.close()
            } catch (e: IOException) {
                log("Could not close sockets when closing ListenThread")
            }
            log("END ListenThread $this")
        }

        override fun close() {
            interrupt()
            serverSocket?.close()
            socket?.close()
        }
    }

    private inner class ConnectingThread(device: BluetoothDevice) : CloseableThread() {
        private var socket: BluetoothSocket? = null

        init {
            try {
                this@BtGame.state = RemoteState.CONNECTING
                btAdapter.cancelDiscovery()
                socket = device.createRfcommSocketToServiceRecord(UUID)
            } catch (e: IOException) {
                log(e.toString())
                interrupt()
            }
        }

        override fun run() {
            log("BEGIN connectingThread" + this)
            try {
                socket?.connect() //Blocking call
                if (!isInterrupted) socket?.let { connected(it, false) } //Manage connection, blocking call
            } catch (e: IOException) {
                callback.toast(res.getString(R.string.unable_to_connect))
                log(e.toString())
            }

            try {
                this@BtGame.state = RemoteState.NONE
                socket?.close()
            } catch (e2: IOException) {
                log(e2.toString())
            }
            log("END connectingThread" + this)
        }

        override fun close() {
            interrupt()
            socket?.close()
        }
    }

    private fun connected(socket: BluetoothSocket, isHost: Boolean) {
        log("BEGIN connected" + this)

        try {
            inStream = socket.inputStream
            outStream = socket.outputStream

            state = RemoteState.CONNECTED
            connectedDeviceName = socket.remoteDevice.name
            callback.toast(res.getString(R.string.connected_to, connectedDeviceName))

            if (isHost) {
                callback.newGame(requestState)
                boards = LinkedList(listOf(requestState.board()))

                outStream?.write(JSONObject().apply {
                    put("message", RemoteMessageType.SETUP.ordinal)
                    put("start", requestState.players.first == Source.REMOTE)
                    put("board", requestState.board().toJSON().toString())
                }.toString().toByteArray(Charsets.UTF_8))
            }
        } catch (e: IOException) {
            log(e.toString())
            Thread.currentThread().interrupt()
        } catch (e: JSONException) {
            e.printStackTrace()
            Thread.currentThread().interrupt()
        }

        val buffer = ByteArray(1024)
        while (state == RemoteState.CONNECTED && !Thread.interrupted()) {
            try {
                //TODO improve reading of the stream, replace buffer
                inStream?.read(buffer)
                val json = JSONObject(String(buffer, Charsets.UTF_8))

                when (RemoteMessageType.values()[json.getInt("message")]) {
                    RemoteMessageType.BOARD_UPDATE -> {
                        val newBoard = JSONBoard.fromJSON(JSONObject(json.getString("board")))
                        if (isValidBoard(boards.peek(), newBoard)) {
                            log("We received a valid board")
                            boards.push(newBoard)
                            callback.move(newBoard.lastMove()!!)
                        } else {
                            callback.toast(res.getString(R.string.desync_message))
                            Thread.currentThread().interrupt()
                        }
                    }
                    RemoteMessageType.SETUP -> {
                        val board = JSONBoard.fromJSON(JSONObject(json.getString("board")))
                        boards = LinkedList(listOf(board))
                        callback.newGame(GameState.Builder().bt().board(board).swapped(!json.getBoolean("start")).build())
                    }
                    RemoteMessageType.UNDO -> {
                        val ask = json.getBoolean("ask")
                        callback.undo(ask)
                        if (ask) boards.pop()
                    }
                }
            } catch (e: IOException) {
                log(e.toString())
                callback.toast(res.getString(R.string.connection_lost))
                Thread.currentThread().interrupt()
            } catch (e: JSONException) {
                log("JSON read parsing failed")
                callback.toast(res.getString(R.string.json_parsing_failed))
                Thread.currentThread().interrupt()
            }
        }

        state = RemoteState.NONE
        callback.turnLocal()

        try {
            Thread.currentThread().interrupt()
            inStream?.close()
            outStream?.close()
            socket.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        log("END connected" + this)
    }

    override fun sendUndo(ask: Boolean) {
        try {
            if (ask) boards.pop()
            outStream?.write(JSONObject().apply {
                put("message", RemoteMessageType.UNDO.ordinal)
                put("ask", ask)
            }.toString().toByteArray(Charsets.UTF_8))
        } catch (e: IOException) {
            log(e.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override fun sendBoard(board: Board) {
        try {
            boards.push(board)
            outStream?.write(JSONObject().apply {
                put("message", RemoteMessageType.BOARD_UPDATE.ordinal)
                put("board", board.toJSON().toString())
            }.toString().toByteArray(Charsets.UTF_8))
        } catch (e: IOException) {
            log(e.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override val localName get() = listOfNotNull(btAdapter.name, btAdapter.address, "ERROR").first()
    override val remoteName get() = if (state == RemoteState.CONNECTED) connectedDeviceName else null
    override val lastBoard: Board get() = boards.last
}