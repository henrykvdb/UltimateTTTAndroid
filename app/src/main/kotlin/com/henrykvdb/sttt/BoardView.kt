/*
 * This file is part of Ultimate Tic Tac Toe.
 * Copyright (C) 2023 Henryk Van der Bruggen <henrykdev@gmail.com>
 *
 * This work is licensed under a Creative Commons
 * Attribution-NonCommercial-NoDerivatives 4.0 International License.
 *
 * You should have received a copy of the CC NC ND License along
 * with Ultimate Tic Tac Toe.  If not, see <https://creativecommons.org/>.
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
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import common.Board
import common.Player
import common.toCoord
import kotlin.math.min
import kotlin.math.sqrt

@Suppress("com/henrykvdb/sttt/unused")
typealias ds = DrawSettings

object DrawSettings {
    //Availability color settings; 0(=full) -> 255(=transparant)
    const val alphaOverlayFront = 200 // alpha symbol over won macros (symbols)
    const val alphaOverlayBack = 100  // alpha background won macros  (bg fill & symbols)

    //Symbol stroke width
    const val tileSymbolStrokeRel = 16f / 984
    const val macroSymbolStrokeRel = 40f / 984
    const val wonSymbolStrokeRel = 120f / 984

    //Grid-line settings
    const val bigGridStrokeRel = 8f / 984
    const val smallGridStrokeRel = 1f / 984

    //Other settings
    const val whiteSpaceRel = 0.02f
    const val borderXRel = 0.10f / 9
    const val borderORel = 0.15f / 9
}

class BoardView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint().apply { isAntiAlias = true }
    private val path = Path()

    private var boardGs: GameState? = null
    private var nextPlayerView: TextView? = null
    private var moveCallback: (Byte) -> Unit = {}

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

    fun drawState(gameState: GameState?) {
        this.boardGs = gameState
        postInvalidate()
    }

    private fun setVars() {
        fieldSize = min(width, height).toFloat()

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
        boardGs?.let { gs ->
            val board = gs.board

            // Set the helper text
            // TODO nice to have: move the code for this text view out of the boardview
            nextPlayerView?.let {
                it.text = null
                if (!board.isDone) {
                    if (gs.type != Source.LOCAL) {
                        it.setTextColor(if (board.nextPlayX) Color.BLUE else Color.RED)
                        val yourTurn = gs.nextSource() == Source.LOCAL
                        it.text =
                            resources.getString(if (yourTurn) R.string.turn_yours else R.string.turn_enemy)
                    }
                } else {
                    if (board.wonBy == Player.NEUTRAL) {
                        it.setTextColor(Color.BLACK)
                        it.text = resources.getText(R.string.winner_tie)
                    } else {
                        if (gs.type == Source.LOCAL) {
                            it.setTextColor(if (board.wonBy == Player.PLAYER) Color.BLUE else Color.RED)
                            it.text = resources.getString(
                                R.string.winner_generic,
                                if (board.wonBy == Player.PLAYER) "X" else "O"
                            )
                        } else {
                            it.setTextColor(if (board.wonBy == Player.PLAYER) Color.BLUE else Color.RED)
                            val youWon =
                                if (board.wonBy == Player.PLAYER) gs.players.first == Source.LOCAL
                                else gs.players.second == Source.LOCAL
                            it.text =
                                resources.getString(if (youWon) R.string.winner_you else R.string.winner_other)
                        }
                    }
                }
            }

            //Draw the macros
            for (om in 0 until 9) drawMacro(canvas, board, om)

            //Bigger macro separate lines
            drawGridBarriers(canvas, fieldSize, getColor(R.color.colorTextSmall), bigGridStroke)
        }
    }

    private fun drawMacro(canvas: Canvas, board: Board, om: Int) {
        val xm = om % 3
        val ym = om / 3

        //Translate to macro
        val xmt = macroSizeFull * xm + whiteSpace
        val ymt = macroSizeFull * ym + whiteSpace
        canvas.translate(xmt, ymt)

        //Draw macro lines
        drawGridBarriers(canvas, macroSizeSmall, getColor(R.color.colorText), smallGridStroke)

        // Draw individual tiles
        for (os in 0 until 9) {
            val coord = ((om.toInt() shl 4) + os).toByte()
            val player = board.tile(coord)

            //Translate to tile
            val xt = os % 3 * tileSize
            val yt = os / 3 * tileSize
            canvas.translate(xt, yt)

            //Color tile if available
            paint.style = Paint.Style.FILL
            paint.color =
                if (board.nextPlayX) getColor(R.color.xColor) else getColor(R.color.oColor)
            paint.alpha = 50

            board.availableMoves.forEach {
                if (it == coord) canvas.drawRect(0f, 0f, tileSize, tileSize, paint)
            }

            //Draw the correct symbol on the tile
            val darkTiles = (board.wonBy != Player.NEUTRAL || (board.macro(om) != Player.NEUTRAL) || (board.macroTied(om)))
            if (player == Player.PLAYER) {
                drawTile(
                    canvas, isX = true, size = tileSize, color = when {
                        coord == board.lastMove -> getColor(R.color.xColorLight)
                        darkTiles -> getColorAlpha(R.color.xColorDark, ds.alphaOverlayBack)
                        else -> getColor(R.color.xColor)
                    }, strokeWidth = tileSymbolStroke, border = xBorder
                )
            } else if (player == Player.ENEMY) {
                drawTile(
                    canvas, isX = false, size = tileSize, color = when {
                        coord == board.lastMove -> getColor(R.color.oColorLight)
                        darkTiles -> getColorAlpha(R.color.oColorDark, ds.alphaOverlayBack)
                        else -> getColor(R.color.oColor)
                    }, strokeWidth = tileSymbolStroke, border = oBorder
                )
            }

            canvas.translate(-xt, -yt)
        }

        // Set the background color for the macro
        paint.style = Paint.Style.FILL
        val macroPartOfWin = board.macroPartOfWin(om)
        if (board.isDone) {
            if (macroPartOfWin) {
                val xMacro = board.macro(om) == Player.PLAYER
                paint.color = getColorAlpha(
                    if (xMacro) R.color.xColor else R.color.oColor, ds.alphaOverlayBack
                )
                canvas.drawRect(0f, 0f, macroSizeSmall, macroSizeSmall, paint)
            }
        } else if (board.macro(om) != Player.NEUTRAL || board.macroTied(om)) {
            paint.color = getColor(R.color.colorBoardUnavailable)
            canvas.drawRect(0f, 0f, macroSizeSmall, macroSizeSmall, paint)
        }

        //Draw big x and y over macros
        if (board.macro(om) == Player.PLAYER) {
            drawTile(
                canvas, true, macroSizeSmall,
                getColorAlpha(R.color.xColor, ds.alphaOverlayFront),
                macroSymbolStroke, xBorder
            )
        } else if (board.macro(om) == Player.ENEMY) {
            drawTile(
                canvas, false, macroSizeSmall,
                getColorAlpha(R.color.oColor, ds.alphaOverlayFront),
                macroSymbolStroke, oBorder
            )
        }

        canvas.translate(-xmt, -ymt)
    }

    private fun drawTile(canvas: Canvas, isX: Boolean, size: Float, color: Int, strokeWidth: Int, border: Float) {
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
        val distanceInPx = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        return distanceInPx / resources.displayMetrics.density
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        val fieldSize = min(parentWidth, parentHeight)

        this.setMeasuredDimension(fieldSize, fieldSize)
    }

    private fun getColor(colorRes: Int): Int = ContextCompat.getColor(context, colorRes)
    private fun getColorAlpha(colorRes: Int, a: Int): Int {
        return ColorUtils.setAlphaComponent(getColor(colorRes), a)
    }
}
