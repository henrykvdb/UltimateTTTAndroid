package com.henrykvdb.sttt

import com.google.firebase.database.ktx.database
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.ktx.Firebase

/** This class implements remote (internet) game functionality on the top MainActivityBase **/
open class MainActivityBaseRemote : MainActivityBase() {
    fun createOnlineGame(afterCreate: (id: String) -> Unit,
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

    fun removeOnlineGame(gameId: String) {
        log("Remote game with gameId=$gameId removed")
        val remoteGameRef = Firebase.database.getReference(gameId)
        remoteGameRef.removeValue()
    }
}