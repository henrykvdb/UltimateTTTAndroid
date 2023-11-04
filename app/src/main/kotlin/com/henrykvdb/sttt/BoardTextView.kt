package com.henrykvdb.sttt

import android.content.Context
import android.util.AttributeSet
import common.Player

class BoardTextView(context: Context, attrs: AttributeSet?) : androidx.appcompat.widget.AppCompatTextView(context, attrs) {
    fun updateText(gs: GameState) {
        // Set the textview
        val board = gs.board
        text = null

        if (!board.isDone) {
            if (gs.type != Source.LOCAL) {
                // Set text color
                setTextColor(
                    if (board.nextPlayX) getColor(R.color.xColor)
                    else getColor(R.color.oColor)
                )

                // Set text string
                val yourTurn = gs.nextSource() == Source.LOCAL
                if (yourTurn) text = resources.getString(R.string.turn_yours)
                else text = resources.getString(R.string.turn_enemy)
            }
        } else {
            if (board.wonBy == Player.NEUTRAL) {
                setTextColor(getColor(R.color.colorText))
                text = resources.getText(R.string.winner_tie)
            } else {
                // Set the text color
                val playerWin = board.wonBy == Player.PLAYER
                setTextColor(
                    if (playerWin) getColor(R.color.xColor) else getColor(R.color.oColor)
                )

                // Set the text string
                if (gs.type == Source.LOCAL) {
                    val winner = if (playerWin) "X" else "O"
                    text = resources.getString(R.string.winner_generic, winner)
                } else {
                    val youWon =
                        if (playerWin) gs.players.first == Source.LOCAL
                        else gs.players.second == Source.LOCAL
                    if (youWon) text = resources.getString(R.string.winner_you)
                    else text = resources.getString(R.string.winner_other)
                }
            }
        }
    }
}