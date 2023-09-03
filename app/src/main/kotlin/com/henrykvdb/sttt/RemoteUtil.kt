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

package com.henrykvdb.sttt

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.database.ktx.database
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.ktx.Firebase
import java.util.concurrent.Callable

fun Context.createOnlineGame(afterCreate: (id: String) -> Unit,
							 afterFail: (msg: String) -> Unit, attempts:Int) {
	// Stop trying if attempts run out
	if (attempts == 0) return
	log("ATTEMPT CREATE GAME (left=$attempts)")

	// Pick game id
	val validChars: List<Char> = ('A'..'Z') + ('0'..'9')
	val gameId: String = List(6) { validChars.random() }.joinToString("")

	// Fetch id
	val remoteGameRef = Firebase.database.getReference(gameId)

	FirebaseInstallations.getInstance().id.addOnSuccessListener { fid: String -> // hostID
		remoteGameRef.get().addOnSuccessListener {
			if (it.exists()) {
				log("Remote game with gameId=$gameId already exists, retrying")
				createOnlineGame(afterCreate, afterFail, attempts - 1)
			} else {
				log("Remote game with gameId=$gameId created")
				//remoteGameRef.child("board").setValue(gs.board.toCompactString())
				remoteGameRef.child("startHost").setValue(false)
				remoteGameRef.child("idHost").setValue(fid)
				remoteGameRef.child("idRemote").setValue("")
				remoteGameRef.child("undoHost").setValue(false)
				remoteGameRef.child("undoRemote").setValue(false)
				afterCreate(gameId)
			}
		}.addOnFailureListener{
			afterFail("Check internet connection ()")
		}
	}.addOnFailureListener{
		afterFail("Check internet connection (id)")
	}
}

fun Context.removeOnlineGame(gameId: String) {
	log("Remote game with gameId=$gameId removed")
	val remoteGameRef = Firebase.database.getReference(gameId)
	remoteGameRef.removeValue()
}