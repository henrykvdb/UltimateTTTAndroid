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

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.henrykvdb.sttt.*

enum class RemoteType {
	NONE,
	BLUETOOTH
}

class RemoteService : Service() {
	private var remoteGame: RemoteGame = DummyRemoteGame
	private var type = RemoteType.NONE

	private val binder = LocalBinder()
	override fun onBind(intent: Intent): IBinder? = binder
	inner class LocalBinder : Binder() {
		fun getService() = this@RemoteService
	}

	fun remoteGame() = remoteGame
	fun getType() = type
	fun setType(type: RemoteType) {
		remoteGame.close()
		this.type = type

		remoteGame = when (type) {
			RemoteType.BLUETOOTH -> BtGame(callback, resources)
			RemoteType.NONE -> DummyRemoteGame
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		remoteGame.close()
	}

	private val callback = object : RemoteCallback {
		override fun newGame(gs: GameState) = this@RemoteService.sendBroadcast(Intent(INTENT_NEWGAME).putExtra(INTENT_DATA, gs))
		override fun undo(force: Boolean) = this@RemoteService.sendBroadcast(Intent(INTENT_UNDO).putExtra(INTENT_DATA, force))
		override fun toast(text: String) = this@RemoteService.sendBroadcast(Intent(INTENT_TOAST).putExtra(INTENT_DATA, text))
		override fun move(move: Byte) = this@RemoteService.sendBroadcast(Intent(INTENT_MOVE).putExtra(INTENT_DATA, move))
		override fun turnLocal() = this@RemoteService.sendBroadcast(Intent(INTENT_TURNLOCAL))
	}
}