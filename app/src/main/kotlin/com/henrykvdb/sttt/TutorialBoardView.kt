package com.henrykvdb.sttt

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.widget.TextView
import common.Player

/** Special BoardView to indicate the next move rule
 *
 *  explainNextMove set => it will draw an arrow from the
 *  current move to the allowed next moves, which are encircled
 *
 *  explainNextMove not set => it will act as a regular BoardView
 *
 *  The tutorial could be further clarified by adding extra features to this class
 *  */
class TutorialBoardView(context: Context, attrs: AttributeSet?) : BoardView(context, attrs) {
    private val path = Path()
    var explainNextMove = true

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!explainNextMove) return

        // Set the paint settings
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tileSymbolStroke.toFloat() / 2
        paint.color = Color.YELLOW

        // Draw the rectangle indicating the next macro
        canvas.translateToStart()
        canvas.translateToMacro(2, 0)
        canvas.drawRect(0f, 0f, macroSizeSmall, macroSizeSmall, paint)
        canvas.translateToMacro(-2, 0)
        canvas.translateToStart(invert = true)

        // Draw arrow pointing from the last move to the next macro
        val xArrowStart = whiteSpace + 2.5f * tileSize
        val yArrowStart = macroSizeFull/2
        val xArrowLen = 2*macroSizeFull - xArrowStart + whiteSpace/3
        val yArrowLen = macroSizeFull/2 + whiteSpace/3
        val xArrowEnd = xArrowStart + xArrowLen
        val yArrowEnd = yArrowStart + yArrowLen
        val xArcEnd = xArrowEnd + xArrowLen
        val yArcEnd = yArrowEnd + yArrowLen
        canvas.drawArc(xArrowStart, yArrowStart, xArcEnd, yArcEnd, 180f, 90f, false, paint)

        // Draw arrow head
        val head = tileSize / 5
        path.reset()
        path.moveTo(xArrowEnd - head, yArrowStart - head)
        path.lineTo(xArrowEnd, yArrowStart)
        path.lineTo(xArrowEnd - head, yArrowStart + head)
        canvas.drawPath(path, paint)
    }
}