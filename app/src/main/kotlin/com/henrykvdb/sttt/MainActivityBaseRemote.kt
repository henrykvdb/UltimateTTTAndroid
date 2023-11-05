package com.henrykvdb.sttt

import android.os.Bundle
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

internal fun String.getDbRef() = Firebase.database.getReference(this)

@IgnoreExtraProperties
data class RemoteDbEntry(
    var history: List<Int> = listOf(),
    var idHost: String = "",
    var idRemote: String = "",
    var hostIsX: Boolean = true,
    var undoHost: Boolean = false,
    var undoRemote: Boolean = false,
    var timestamp: Long = System.currentTimeMillis()
)

/** This class implements remote (internet) game functionality on the top MainActivityBase **/
open class MainActivityBaseRemote : MainActivityBase() {
    private var dataBaseReference: DatabaseReference? = null
    private var remoteListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore the connection
        if (gs.type == Source.REMOTE){
            val gameId = gs.remoteId
            if (gameId.isNotEmpty())
                createListener(gameId) { onDbEntryChange(it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyListener()
    }

    override fun updateRemote(history: List<Int>) {
        super.updateRemote(history)
        dataBaseReference?.updateChildren(hashMapOf<String, Any>(
                "history" to history,
                "undoHost" to false,
                "undoRemote" to false,
            )
        )
    }

    override fun requestRemoteUndo() {
        super.requestRemoteUndo()
        val gameId = gs.remoteId
        if (gs.type == Source.REMOTE && gameId.isNotEmpty()){
            runWithUid ({ localUid ->
                val dbRef = gameId.getDbRef()
                dbRef.child("idHost").get().addOnSuccessListener {
                    val hostUid = it.getValue(String::class.java)
                    val tag = if (localUid == hostUid) "undoHost" else "undoRemote"
                    dbRef.child(tag).setValue(true)
                }
            }, afterFail = {toast(it)}) // Consume error instead of passing it on
        }
    }

    fun onDbEntryChange(newEntry: RemoteDbEntry){
        if (gs.type == Source.REMOTE){
            // Update game with latest data
            newRemote(gs.swapped, newEntry.history, gs.remoteId)

            // Ask device if undo is accepted
            runWithUid ({ localUid ->
                val isHost = localUid == newEntry.idHost
                if ((isHost && newEntry.undoRemote) || (!isHost && newEntry.undoHost))
                    undo(ask = true)
            }, afterFail = { toast(it) }) // Consume error instead of passing on
        }
        else turnLocal()
    }

    /** Host side of game creation handshakeing **/
    fun createOnlineGame(
        afterSuccess: (id: String) -> Unit,
        afterFail: (errStringRes: Int) -> Unit,
        attempts:Int
    ) {
        // Stop trying if attempts run out
        if (attempts == 0) return
        log("ATTEMPT CREATE GAME (left=$attempts)")

        // Pick game id
        val validChars: List<Char> = ('A'..'Z') + ('0'..'9')
        val gameId: String = List(6) { validChars.random() }.joinToString("")
        val remoteGameRef = gameId.getDbRef()

        runWithUid ({ idHost ->
            remoteGameRef.get().addOnSuccessListener(this) {
                if (it.exists()) {
                    log("Remote game with gameId=$gameId already exists, retrying")
                    createOnlineGame(afterSuccess, afterFail,attempts - 1)
                } else {
                    log("Create remote game with gameId=$gameId")
                    remoteGameRef.setValue(RemoteDbEntry(idHost = idHost))
                    afterSuccess(gameId)
                }
            }.addOnFailureListener{ afterFail(R.string.toast_internet_1) }
        }, afterFail={afterFail(it)}) // Pass error on to caller
    }

    /** Client side of game creation handshakeing **/
    fun joinOnlineGame(gameId: String,
                       afterSuccess: (host: Boolean) -> Unit,
                       afterFail: (errStringRes: Int) -> Unit
    ) {
        log("ATTEMPT JOIN GAME ($gameId)")
        val remoteGameRef = gameId.getDbRef()

        runWithUid(afterSuccess = { idLocal ->
            remoteGameRef.get().addOnSuccessListener(this) {
                if (it.exists()) {
                    val child = it.getValue(RemoteDbEntry::class.java)
                    val idRemote = child?.idRemote ?: ""
                    val idHost = child?.idHost ?: ""

                    if (idHost == idLocal)
                        afterSuccess(true)
                    else if (idRemote == "" || idRemote == idLocal) {
                        remoteGameRef.child("idRemote").setValue(idLocal)
                        afterSuccess(false)
                    } else afterFail(R.string.toast_online_not_joinable)
                } else afterFail(R.string.toast_online_not_exist)
            }.addOnFailureListener{ afterFail(R.string.toast_internet_2) }
        }, afterFail = { afterFail(it) }) // Pass message on to caller
    }

    fun createListener(gameId: String, onChange: (data: RemoteDbEntry) -> Unit){
        log("Start listening to gameId=$gameId")

        // Cancel listener if one exists
        remoteListener?.let { destroyListener() }

        // Create the new listener
        dataBaseReference = gameId.getDbRef()
        remoteListener = object : ValueEventListener {
            override fun onCancelled(error: DatabaseError) = turnLocal()
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val newDbEntry = dataSnapshot.getValue(RemoteDbEntry::class.java)
                log("Update (gid=$gameId): $newDbEntry")
                newDbEntry?.let { onChange(it) }
            }
        }

        // Add the new listener
        remoteListener?.let { dataBaseReference?.addValueEventListener(it) }
    }

    fun destroyListener(){
        log("REMOTE Stop listening")
        remoteListener?.let { dataBaseReference?.removeEventListener(it) }
        dataBaseReference = null
        remoteListener = null
    }

    /** Remove database entry related to game and destory listener **/
    fun removeOnlineGame(gameId: String) {
        log("Remove remote game with gameId=$gameId")
        gameId.getDbRef().removeValue()
        destroyListener()
        turnLocal()
    }

    private fun runWithUid(
        afterSuccess: (id: String) -> Unit,
        afterFail: (errStringRes: Int) -> Unit
    ) {
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null)
            afterSuccess(currentUser.uid)
        else Firebase.auth.signInAnonymously()
            .addOnSuccessListener(this) { runWithUid(afterSuccess, afterFail) }
            .addOnFailureListener { afterFail(R.string.toast_internet_3) }
    }
}