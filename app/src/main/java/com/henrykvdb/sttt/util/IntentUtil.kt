package com.henrykvdb.sttt.util

import android.content.Context
import android.content.Intent
import com.henrykvdb.sttt.*

fun sendMove(context: Context, src: MainActivity.Source, move: Byte) {
    val i = Intent(INTENT_MOVE)
    i.putExtra(INTENT_DATA_FIRST, src)
    i.putExtra(INTENT_DATA_SECOND, move)
    context.sendBroadcast(i)
}

fun sendNewGame(context: Context, gs: GameState) {
    val i = Intent(INTENT_NEWGAME)
    i.putExtra(INTENT_DATA_FIRST, gs)
    context.sendBroadcast(i)
}

fun sendToast(context: Context, text: String) {
    val i = Intent(INTENT_TOAST)
    i.putExtra(INTENT_DATA_FIRST, text)
    context.sendBroadcast(i)
}

fun sendUndo(context: Context, force: Boolean?) {
    val i = Intent(INTENT_UNDO)
    i.putExtra(INTENT_DATA_FIRST, force)
    context.sendBroadcast(i)
}

fun sendTurnLocal(context: Context) {
    context.sendBroadcast(Intent(INTENT_TURNLOCAL))
}