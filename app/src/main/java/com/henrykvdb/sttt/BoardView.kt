package com.henrykvdb.sttt

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.flaghacker.uttt.common.Board
import com.flaghacker.uttt.common.Coord
import com.flaghacker.uttt.common.Player

@Suppress("unused") typealias ds = DrawSettings

class BoardView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private val path: Path = Path()
    private val paint: Paint = Paint()

    private lateinit var moveCallback: Callback<Coord>
    private var gameState: GameState = GameState.Builder().build()
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
    private var pressedX: Float = 0.0f
    private var pressedY: Float = 0.0f

    init {
        setVars()
        postInvalidate()
    }

    fun setup(moveCallback: Callback<Coord>, nextPlayerView: TextView) {
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
        whiteSpace = fieldSize * ds.whiteSpace

        macroSizeSmall = macroSizeFull - 2 * whiteSpace
        tileSize = macroSizeSmall / 3

        xBorder = fieldSize * ds.borderX
        oBorder = fieldSize * ds.borderO

        bigGridStroke = (fieldSize * ds.bigGridStroke).toInt()
        smallGridStroke = (fieldSize * ds.smallGridStroke).toInt()
        tileSymbolStroke = (fieldSize * ds.tileSymbolStroke).toInt()
        macroSymbolStroke = (fieldSize * ds.macroSymbolStroke).toInt()
        wonSymbolStroke = (fieldSize * ds.wonSymbolStroke).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        //Get the board
        val board = gameState.board()

        nextPlayerView?.let {
            //Set the helper text
            it.setTextColor(if (board.nextPlayer() == Player.PLAYER) Color.BLUE else Color.RED)
            it.text = null
            if (!board.isDone) {
                if (!gameState.isHuman()){
                    val yourTurn = gameState.nextSource()== MainActivity.Source.Local
                    it.text = resources.getString(if (yourTurn) R.string.your_turn else R.string.enemy_turn)
                }
            } else {
                if (board.wonBy() == Player.NEUTRAL) {
                    it.setTextColor(Color.BLACK)
                    it.text = resources.getText(R.string.tie_message)
                } else {
                    if (gameState.isHuman()) {
                        it.setTextColor(if (board.wonBy() == Player.PLAYER) Color.BLUE else Color.RED)
                        it.text = resources.getString(R.string.game_winner, if (board.wonBy() == Player.PLAYER) "X" else "O")
                    } else {
                        val youWon =
                                if (board.wonBy() == Player.PLAYER) gameState.players.first == MainActivity.Source.Local
                                else gameState.players.second == MainActivity.Source.Local
                        it.setTextColor(if (board.wonBy() == Player.PLAYER) Color.BLUE else Color.RED)
                        it.text = resources.getString(if (youWon) R.string.you_won else R.string.you_lost)
                    }
                }
            }
        }

        //Make available moves the correct color
        paint.style = Paint.Style.FILL
        paint.color = if (board.nextPlayer() == Player.PLAYER) ds.xColor else ds.oColor
        paint.alpha = 50
        for (coord in board.availableMoves()) {
            val x = coord.xm() * macroSizeFull + coord.xs() * tileSize + whiteSpace
            val y = coord.ym() * macroSizeFull + coord.ys() * tileSize + whiteSpace

            canvas.translate(x, y)
            canvas.drawRect(0f, 0f, tileSize, tileSize, paint)
            canvas.translate(-x, -y)
        }

        for (om in 0..8)
            drawMacro(canvas, board, om)

        //Bigger macro separate lines
        drawGridBarriers(canvas, fieldSize, ds.gridColor, bigGridStroke)

        if (board.isDone) {
            when (board.wonBy()) {
                Player.PLAYER -> drawTile(canvas, true, true, fieldSize, ds.xColor - ds.symbolTransparency, wonSymbolStroke, tileSize)
                Player.ENEMY -> drawTile(canvas, false, true, fieldSize, ds.oColor - ds.symbolTransparency, wonSymbolStroke, tileSize * oBorder / xBorder)
                Player.NEUTRAL -> Unit //Nobody won, so no need to draw anything
            }
        }
    }

    private fun drawMacro(canvas: Canvas, board: Board, om: Int) {
        val xm = om % 3
        val ym = om / 3

        val mPlayer = board.macro(xm, ym)
        val mNeutral = mPlayer == Player.NEUTRAL
        val finished = board.wonBy() != Player.NEUTRAL
        val lastMove = board.lastMove

        //Translate to macro
        val xmt = macroSizeFull * xm + whiteSpace
        val ymt = macroSizeFull * ym + whiteSpace
        canvas.translate(xmt, ymt)

        //Draw macro lines
        drawGridBarriers(canvas, macroSizeSmall, ds.gridColor, smallGridStroke)

        //Loop through tiles of the macro
        for (tile in Coord.macro(xm, ym)) {
            val player = board.tile(tile)

            //Translate to tile
            val xt = tile.xs() * tileSize
            val yt = tile.ys() * tileSize
            canvas.translate(xt, yt)

            if (player == Player.PLAYER)//x
                drawTile(canvas, true, false, tileSize, when {
                    tile == lastMove -> ds.xColorLight
                    finished -> ds.xColorDarkest
                    mNeutral -> ds.xColor
                    else -> ds.xColorDarker
                }, tileSymbolStroke, xBorder)
            else if (player == Player.ENEMY)//o
                drawTile(canvas, false, false, tileSize, when {
                    tile == lastMove -> ds.oColorLight
                    finished -> ds.oColorDarkest
                    mNeutral -> ds.oColor
                    else -> ds.oColorDarker
                }, tileSymbolStroke, oBorder)

            canvas.translate(-xt, -yt)
        }

        //Draw x and y over macros
        if (mPlayer == Player.PLAYER)//X
            drawTile(canvas, true, !finished, macroSizeSmall,
                    if (finished) ds.xColorDarker - ds.symbolTransparency else ds.xColor - ds.symbolTransparency,
                    macroSymbolStroke, xBorder)
        else if (mPlayer == Player.ENEMY)//O
            drawTile(canvas, false, !finished, macroSizeSmall,
                    if (finished) ds.oColorDarker - ds.symbolTransparency else ds.oColor - ds.symbolTransparency,
                    macroSymbolStroke, oBorder)

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

        for (i in 1..2) {
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
            moveCallback.invoke(Coord.coord(xm, ym, xs, ys))
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
