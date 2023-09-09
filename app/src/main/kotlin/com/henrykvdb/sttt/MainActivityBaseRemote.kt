package com.henrykvdb.sttt

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.ktx.Firebase

internal fun String.getDbRef() = Firebase.database.getReference(this)

@IgnoreExtraProperties
data class RemoteDbEntry(
    var board: String = "",
    var fidHost: String = "",
    var fidRemote: String = "",
    var startHost: Boolean = true,
    var undoHost: Boolean = false,
    var undoRemote: Boolean = false
)

/** This class implements remote (internet) game functionality on the top MainActivityBase **/
open class MainActivityBaseRemote : MainActivityBase() {
    private var remoteListener: ValueEventListener? = null

    /** Host side of game creation handshakeing **/
    fun createOnlineGame(afterSuccess: (id: String) -> Unit,
                         afterFail: (msg: String) -> Unit, attempts:Int) {
        // Stop trying if attempts run out
        if (attempts == 0) return
        log("ATTEMPT CREATE GAME (left=$attempts)")

        // Pick game id
        val validChars: List<Char> = ('A'..'Z') + ('0'..'9')
        val gameId: String = List(6) { validChars.random() }.joinToString("")
        val remoteGameRef = gameId.getDbRef()

        FirebaseInstallations.getInstance().id.addOnSuccessListener { fid -> // hostID
            remoteGameRef.get().addOnSuccessListener {
                if (it.exists()) {
                    log("Remote game with gameId=$gameId already exists, retrying")
                    createOnlineGame(afterSuccess, afterFail, attempts - 1)
                } else {
                    log("Create remote game with gameId=$gameId")
                    //remoteGameRef.child("board").setValue(gs.board.toCompactString())
                    remoteGameRef.setValue(RemoteDbEntry(fidHost = fid))
                    afterSuccess(gameId)
                }
            }.addOnFailureListener{
                afterFail("Check internet connection (gid)")
            }
        }.addOnFailureListener{
            afterFail("Check internet connection (fid)")
        }
    }

    /** Client side of game creation handshakeing **/
    fun joinOnlineGame(gameId: String, afterSuccess: () -> Unit, afterFail: (msg: String) -> Unit) {
        log("ATTEMPT JOIN GAME ($gameId)")
        val remoteGameRef = gameId.getDbRef()

        FirebaseInstallations.getInstance().id.addOnSuccessListener { fidLocal -> // hostID
            remoteGameRef.get().addOnSuccessListener {
                if (it.exists()) {
                    val child = remoteGameRef.child("fidRemote")
                    child.get().addOnSuccessListener { data ->
                        val fidExisting: String? = data.getValue(String::class.java)
                        if (fidExisting == "" || fidExisting == fidLocal){
                            remoteGameRef.child("fidRemote").setValue(fidLocal)
                            afterSuccess()
                        } else{
                            afterFail("Game not joinable")
                        }
                    }.addOnFailureListener {
                            afterFail("Check internet connection")
                    }
                } else {
                    afterFail("Game does not exist")
                }
            }.addOnFailureListener{
                afterFail("Check internet connection (gid)")
            }
        }.addOnFailureListener{
            afterFail("Check internet connection (fid)")
        }
    }

    fun createListener(gameId: String, onChange: (data: RemoteDbEntry) -> Unit){
        log("Start listening to gameId=$gameId")

        // Cancel listener if one exists
        remoteListener?.let { destroyListener(gameId) }

        // Create the new listener
        remoteListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val newDbEntry = dataSnapshot.getValue(RemoteDbEntry::class.java)
                log("Update (gid=$gameId): $newDbEntry")
                newDbEntry?.let { onChange(it) }
            }

            override fun onCancelled(error: DatabaseError) {
                TODO("This should be handled")
            }
        }

        // Add the new listener
        remoteListener?.let { gameId.getDbRef().addValueEventListener(it) }
    }

    fun destroyListener(gameId: String){
        log("Stop listening to gameId=$gameId")
        remoteListener?.let { gameId.getDbRef().removeEventListener(it) }
        remoteListener = null
    }

    /** Remove database entry related to game and destory listener **/
    fun removeOnlineGame(gameId: String) {
        log("Remove remote game with gameId=$gameId")
        gameId.getDbRef().removeValue()
        destroyListener(gameId)
    }
}