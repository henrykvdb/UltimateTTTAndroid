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
import android.view.MotionEvent
import android.view.View
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
    const val alphaOverlayFront = 190 // alpha symbol over won macros (symbols)
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

open class BoardView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    protected val paint = Paint().apply { isAntiAlias = true }
    private val path = Path()

    protected var boardGs: GameState? = null
    protected var moveCallback: (Byte) -> Unit = {}

    protected var macroSizeSmall = 0.0f
    protected var macroSizeFull = 0.0f
    protected var whiteSpace = 0.0f
    private var fieldSize = 0.0f
    protected var tileSize = 0.0f
    private var xBorder = 0.0f
    private var oBorder = 0.0f

    private var macroSymbolStroke = 0
    protected var tileSymbolStroke = 0
    private var smallGridStroke = 0
    private var wonSymbolStroke = 0
    private var bigGridStroke = 0

    //Fields for distinguishing click and drag events
    private var downX = -1
    private var downY = -1

    init {
        setVars()
        postInvalidate()
    }

    fun setup(moveCallback: (Byte) -> Unit) {
        this.moveCallback = moveCallback
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

            //Draw the macros
            canvas.translateToStart()
            for (om in 0 until 9){
                val xm = om % 3
                val ym = om / 3
                canvas.translateToMacro(xm, ym)
                drawMacro(canvas, board, om)
                canvas.translateToMacro(-xm, -ym)
            }
            canvas.translateToStart(true)

            //Bigger macro separate lines
            drawGridBarriers(canvas, fieldSize, getColor(R.color.colorTextSmall), bigGridStroke)
        }
    }

    // Translate the x and y whitespace away
    protected fun Canvas.translateToStart(invert: Boolean = false){
        if (invert) translate(-whiteSpace, -whiteSpace) else translate(whiteSpace, whiteSpace)
    }

    // Translate to chosen macro from end of whitespace ( after translateToStart() )
    protected fun Canvas.translateToMacro(xm: Int, ym: Int){
        val xmt = macroSizeFull * xm
        val ymt = macroSizeFull * ym
        translate(xmt, ymt)
    }

    // Translate to chosen tile from the base coord of any macro ( after translateToMacro() )
    private fun Canvas.translateToTile(xs: Int, ys: Int){
        val xst = tileSize * xs
        val yst = tileSize * ys
        translate(xst, yst)
    }

    // Note: can only be called after translated to correct macro ( with translateToMacro() )
    private fun drawMacro(canvas: Canvas, board: Board, om: Int) {
        //Draw macro lines
        drawGridBarriers(canvas, macroSizeSmall, getColor(R.color.colorText), smallGridStroke)

        // Draw individual tiles
        for (os in 0 until 9) {
            val xs = os % 3
            val ys = os / 3
            canvas.translateToTile(xs, ys)
            drawTile(canvas, board, om, os)
            canvas.translateToTile(-xs, -ys)
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
            drawSymbol(
                canvas, true, macroSizeSmall,
                getColorAlpha(R.color.xColor, ds.alphaOverlayFront),
                macroSymbolStroke, xBorder
            )
        } else if (board.macro(om) == Player.ENEMY) {
            drawSymbol(
                canvas, false, macroSizeSmall,
                getColorAlpha(R.color.oColor, ds.alphaOverlayFront),
                macroSymbolStroke, oBorder
            )
        }
    }

    // Note: can only be called after translated to correct tile ( with translateToTile() )
    private fun drawTile(canvas: Canvas, board: Board, om: Int, os: Int) {
        // Extract details from board
        val coord = ((om shl 4) + os).toByte()
        val player = board.tile(coord)

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
            drawSymbol(
                canvas, isX = true, size = tileSize, color = when {
                    coord == board.lastMove -> getColor(R.color.xColorLight)
                    darkTiles -> getColorAlpha(R.color.xColorDark, ds.alphaOverlayBack)
                    else -> getColor(R.color.xColor)
                }, strokeWidth = tileSymbolStroke, border = xBorder
            )
        } else if (player == Player.ENEMY) {
            drawSymbol(
                canvas, isX = false, size = tileSize, color = when {
                    coord == board.lastMove -> getColor(R.color.oColorLight)
                    darkTiles -> getColorAlpha(R.color.oColorDark, ds.alphaOverlayBack)
                    else -> getColor(R.color.oColor)
                }, strokeWidth = tileSymbolStroke, border = oBorder
            )
        }
    }

    private fun drawSymbol(canvas: Canvas, isX: Boolean, size: Float, color: Int, strokeWidth: Int, border: Float) {
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
        // Avoid events outside board
        if (e.x < 0 || e.y < 0 || e.x > fieldSize || e.y > fieldSize) {
            log("ClickEvent: outside of board (ignored)")
            downX = -1; downY = -1
            return true
        }

        if (e.action == MotionEvent.ACTION_DOWN) {
            downX = getCoordXY(e.x)
            downY = getCoordXY(e.y)
        } else if (e.action == MotionEvent.ACTION_MOVE || e.action == MotionEvent.ACTION_UP) {
            // Avoid events that drag outside of tile
            val x = getCoordXY(e.x)
            val y = getCoordXY(e.y)
            if (x != downX || y != downY){
                log("ClickEvent: dragged outside of tile (ignored)")
                downX = -1; downY = -1
                return true
            }

            if (e.action == MotionEvent.ACTION_UP) {
                performClick()
                moveCallback.invoke(toCoord(downX, downY))
                log("ClickEvent: ($x, $y)")
            }
        }

        return true
    }

    private fun getCoordXY(pixels: Float): Int {
        val macros = (pixels / macroSizeFull).toInt()
        var tiles = ((pixels - macros * macroSizeFull) / tileSize).toInt()

        //Fix coordinates being too big due to whitespace
        tiles = if (tiles > 2) --tiles else tiles

        return macros * 3 + tiles
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        val fieldSize = min(parentWidth, parentHeight)

        this.setMeasuredDimension(fieldSize, fieldSize)
    }
}

fun View.getColor(colorRes: Int): Int = ContextCompat.getColor(context, colorRes)
fun View.getColorAlpha(colorRes: Int, a: Int): Int {
    return ColorUtils.setAlphaComponent(getColor(colorRes), a)
}
