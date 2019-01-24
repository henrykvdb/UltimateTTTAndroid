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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.flaghacker.sttt.common.Board
import com.flaghacker.sttt.common.Player
import com.flaghacker.sttt.common.toCoord

@Suppress("unused")
typealias ds = DrawSettings

object DrawSettings {
    //PLAYER color settings (X)
    const val xColor = Color.BLUE
    val xColorDarker = Color.rgb(0, 0, 230)
    val xColorDarkest = Color.rgb(0, 0, 200)
    val xColorLight = Color.rgb(75, 155, 255)

    //ENEMY color settings (O)
    const val oColor = Color.RED
    val oColorDarker = Color.rgb(230, 0, 0)
    val oColorDarkest = Color.rgb(200, 0, 0)
    val oColorLight = Color.rgb(255, 155, 75)

    //Availability color settings
    val unavailableColor = Color.argb(50, 136, 136, 136)
    const val symbolTransparency = 60 and 0xff shl 24

    //Symbol stroke width
    const val tileSymbolStrokeRel = 16f / 984
    const val macroSymbolStrokeRel = 40f / 984
    const val wonSymbolStrokeRel = 120f / 984

    //Grid-line settings
    const val gridColor = Color.BLACK
    const val bigGridStrokeRel = 8f / 984
    const val smallGridStrokeRel = 1f / 984

    //Other settings
    const val whiteSpaceRel = 0.02f
    const val borderXRel = 0.10f / 9
    const val borderORel = 0.15f / 9
}

class BoardView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private val path = Path()
    private val paint = Paint()

    private lateinit var moveCallback: (Byte) -> Unit
    private var gameState = GameState.Builder().build()
    private var nextPlayerView: TextView? = null

    private var macroSizeSmall = 0.0f
    private var macroSizeFull = 0.0f
    private var whiteSpace = 0.0f
    private var fieldSize = 0.0f
    private var tileSize = 0.0f
    private var xBorder = 0.0f
    private var oBorder = 0.0f

    private var macroSymbolStroke = 0
    private var tileSymbolStroke = 0
    private var smallGridStroke = 0
    private var wonSymbolStroke = 0
    private var bigGridStroke = 0

    //Fields for distinguishing click and drag events
    private var pressedX = 0.0f
    private var pressedY = 0.0f

    init {
        setVars()
        postInvalidate()
    }

    fun setup(moveCallback: (Byte) -> Unit, nextPlayerView: TextView) {
        this.moveCallback = moveCallback
        this.nextPlayerView = nextPlayerView
    }

    fun drawState(gameState: GameState) {
        this.gameState = gameState
        postInvalidate()
    }

    private fun setVars() {
        fieldSize = Math.min(width, height).toFloat()

        macroSizeFull = fieldSize / 3
        whiteSpace = fieldSize * ds.whiteSpaceRel

        macroSizeSmall = macroSizeFull - 2 * whiteSpace
        tileSize = macroSizeSmall / 3

        xBorder = fieldSize * ds.borderXRel
        oBorder = fieldSize * ds.borderORel

        bigGridStroke = (fieldSize * ds.bigGridStrokeRel).toInt()
        smallGridStroke = (fieldSize * ds.smallGridStrokeRel).toInt()
        tileSymbolStroke = (fieldSize * ds.tileSymbolStrokeRel).toInt()
        macroSymbolStroke = (fieldSize * ds.macroSymbolStrokeRel).toInt()
        wonSymbolStroke = (fieldSize * ds.wonSymbolStrokeRel).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val board = gameState.board()

        //Set the helper text
        nextPlayerView?.let {
            it.text = null
            if (!board.isDone) {
                if (gameState.type != Source.LOCAL) {
                    it.setTextColor(if (board.nextPlayer == Player.PLAYER) Color.BLUE else Color.RED)
                    val yourTurn = gameState.nextSource() == Source.LOCAL
                    it.text = resources.getString(if (yourTurn) R.string.your_turn else R.string.enemy_turn)
                }
            } else {
                if (board.wonBy == Player.NEUTRAL) {
                    it.setTextColor(Color.BLACK)
                    it.text = resources.getText(R.string.tie_message)
                } else {
                    if (gameState.type == Source.LOCAL) {
                        it.setTextColor(if (board.wonBy == Player.PLAYER) Color.BLUE else Color.RED)
                        it.text = resources.getString(R.string.game_winner, if (board.wonBy == Player.PLAYER) "X" else "O")
                    } else {
                        it.setTextColor(if (board.wonBy == Player.PLAYER) Color.BLUE else Color.RED)
                        val youWon =
                                if (board.wonBy == Player.PLAYER) gameState.players.first == Source.LOCAL
                                else gameState.players.second == Source.LOCAL
                        it.text = resources.getString(if (youWon) R.string.you_won else R.string.you_lost)
                    }
                }
            }
        }

        //Draw the macros
        for (om in 0 until 9) drawMacro(canvas, board, om.toByte())

        //Bigger macro separate lines
        drawGridBarriers(canvas, fieldSize, ds.gridColor, bigGridStroke)

        if (board.isDone) {
            when (board.wonBy) {
                Player.PLAYER -> drawTile(canvas, true, true, fieldSize, ds.xColor - ds.symbolTransparency, wonSymbolStroke, tileSize)
                Player.ENEMY -> drawTile(canvas, false, true, fieldSize, ds.oColor - ds.symbolTransparency, wonSymbolStroke, tileSize * oBorder / xBorder)
                Player.NEUTRAL -> Unit //Nobody won, so no need to draw anything
            }
        }
    }

    private fun drawMacro(canvas: Canvas, board: Board, om: Byte) {
        val xm = om % 3
        val ym = om / 3

        val macroOwner = board.macro(om)
        val won = board.wonBy != Player.NEUTRAL

        //Translate to macro
        val xmt = macroSizeFull * xm + whiteSpace
        val ymt = macroSizeFull * ym + whiteSpace
        canvas.translate(xmt, ymt)

        //Draw macro lines
        drawGridBarriers(canvas, macroSizeSmall, ds.gridColor, smallGridStroke)

        //Loop through tiles of the macro
        for (tile in 9 * om until 9 * om + 9) {
            val player = board.tile(tile.toByte())

            //Translate to tile
            val xt = (tile % 9) % 3 * tileSize
            val yt = (tile % 9) / 3 * tileSize
            canvas.translate(xt, yt)

            //Color tile if available
            paint.style = Paint.Style.FILL
            paint.color = if (board.nextPlayer == Player.PLAYER) ds.xColor else ds.oColor
            paint.alpha = 50
            if (board.availableMoves.contains(tile.toByte())) {
                canvas.drawRect(0f, 0f, tileSize, tileSize, paint)
            }

            //Draw the correct symbol on the tile
            if (player == Player.PLAYER) {
                drawTile(canvas, true, false, tileSize, when {
                    tile.toByte() == board.lastMove -> ds.xColorLight
                    won -> ds.xColorDarkest
                    macroOwner == Player.NEUTRAL -> ds.xColor
                    else -> ds.xColorDarker
                }, tileSymbolStroke, xBorder)
            } else if (player == Player.ENEMY) {
                drawTile(canvas, false, false, tileSize, when {
                    tile.toByte() == board.lastMove -> ds.oColorLight
                    won -> ds.oColorDarkest
                    macroOwner == Player.NEUTRAL -> ds.oColor
                    else -> ds.oColorDarker
                }, tileSymbolStroke, oBorder)
            }

            canvas.translate(-xt, -yt)
        }

        //Draw x and y over macros
        if (macroOwner == Player.PLAYER) {
            drawTile(canvas, true, !won, macroSizeSmall,
                    if (won) ds.xColorDarker - ds.symbolTransparency else ds.xColor - ds.symbolTransparency,
                    macroSymbolStroke, xBorder)
        } else if (macroOwner == Player.ENEMY) {
            drawTile(canvas, false, !won, macroSizeSmall,
                    if (won) ds.oColorDarker - ds.symbolTransparency else ds.oColor - ds.symbolTransparency,
                    macroSymbolStroke, oBorder)
        }

        canvas.translate(-xmt, -ymt)
    }

    private fun drawTile(canvas: Canvas, isX: Boolean, grayBack: Boolean, size: Float, color: Int, strokeWidth: Int, border: Float) {
        if (grayBack) {
            paint.style = Paint.Style.FILL
            paint.color = ds.unavailableColor
            canvas.drawRect(0f, 0f, size, size, paint)
        }

        val realSize = size - 2 * border
        canvas.translate(border, border)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth.toFloat()
        paint.color = color
        if (isX) {
            path.moveTo(0f, 0f)
            path.lineTo(realSize, realSize)
            path.moveTo(0f, realSize)
            path.lineTo(realSize, 0f)

            canvas.drawPath(path, paint)
            path.reset()
        } else {
            canvas.drawOval(RectF(0f, 0f, realSize, realSize), paint)
        }

        canvas.translate(-border, -border)
    }

    private fun drawGridBarriers(canvas: Canvas, size: Float, color: Int, strokeWidth: Int) {
        paint.color = color
        paint.strokeWidth = strokeWidth.toFloat()

        for (i in 1 until 3) {
            canvas.drawLine(i * size / 3, 0f, i * size / 3, size, paint)
            canvas.drawLine(0f, i * size / 3, size, i * size / 3, paint)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        setVars()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x
        val y = e.y

        if (e.action == MotionEvent.ACTION_DOWN) {
            pressedX = e.x
            pressedY = e.y
        } else if (e.action == MotionEvent.ACTION_UP && distance(pressedX, pressedY, e.x, e.y) < 30) {
            if (x < 0 || y < 0 || x > fieldSize || y > fieldSize) {
                Log.d("ClickEvent", "Clicked outside of board")
                return true
            }

            //Get event's macro
            val xm = (x / macroSizeFull).toInt()
            val ym = (y / macroSizeFull).toInt()

            //Get event's tile
            var xs = ((x - xm * macroSizeFull) / (macroSizeSmall / 3)).toInt()
            var ys = ((y - ym * macroSizeFull) / (macroSizeSmall / 3)).toInt()

            //Fix coordinates being too big due to whitespace
            xs = if (xs > 2) --xs else xs
            ys = if (ys > 2) --ys else ys

            performClick()
            moveCallback.invoke(toCoord(xm * 3 + xs, ym * 3 + ys))
            Log.d("ClickEvent", "Clicked: (" + (xm * 3 + xs) + "," + (ym * 3 + ys) + ")")
        }

        return true
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        val distanceInPx = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        return distanceInPx / resources.displayMetrics.density
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = View.MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = View.MeasureSpec.getSize(heightMeasureSpec)
        val fieldSize = Math.min(parentWidth, parentHeight)

        this.setMeasuredDimension(fieldSize, fieldSize)
    }
}
