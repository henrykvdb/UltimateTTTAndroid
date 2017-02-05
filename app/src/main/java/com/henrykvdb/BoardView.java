package com.henrykvdb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Coord;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.BLUE;
import static android.graphics.Color.GRAY;
import static android.graphics.Color.RED;
import static com.flaghacker.uttt.common.Board.ENEMY;
import static com.flaghacker.uttt.common.Board.NEUTRAL;
import static com.flaghacker.uttt.common.Board.PLAYER;

public class BoardView extends View
{
	private com.flaghacker.uttt.common.Board board;
	private AndroidBot ab;

	private Paint paint;
	private float macroSizeFull;
	private float whiteSpace;
	private float macroSizeSmall;
	private float tileSize;
	private float xBorder;
	private float oBorder;
	private float fieldSize;

	public BoardView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		paint = new Paint();
		setVars();

		setBoard(new Board());
		setAndroidBot(new AndroidBot());
	}

	private void setVars()
	{
		fieldSize = Math.min(getWidth(), getHeight());

		macroSizeFull = fieldSize / 3;
		whiteSpace = fieldSize * 0.02f;

		macroSizeSmall = macroSizeFull - 2 * whiteSpace;
		tileSize = macroSizeSmall / 3;

		xBorder = fieldSize / 9 * 0.10f;
		oBorder = fieldSize / 9 * 0.15f;
	}

	public void setBoard(Board board)
	{
		this.board = board;
		postInvalidate();
	}

	public void setAndroidBot(AndroidBot ab)
	{
		this.ab = ab;
	}

	protected void onDraw(Canvas canvas)
	{
		//Make available moves yellow
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(Color.rgb(255, 255, 100));
		for (Coord coord : board.availableMoves())
		{
			float x = coord.xm() * macroSizeFull + coord.xs() * tileSize + whiteSpace;
			float y = coord.ym() * macroSizeFull + coord.ys() * tileSize + whiteSpace;

			canvas.translate(x, y);
			canvas.drawRect(0, 0, tileSize, tileSize, paint);
			canvas.translate(-x, -y);
		}

		for (int om = 0; om < 9; om++)
			drawMacro(canvas, om);

		//Bigger macro separate lines
		drawGridBarriers(canvas, fieldSize, BLACK, 8);
	}

	private void drawMacro(Canvas canvas, int om)
	{
		int xm = om % 3;
		int ym = om / 3;

		//Check if macro is finished
		byte mPlayer = board.macro(xm, ym);
		boolean mNeutral = mPlayer == NEUTRAL;

		//Translate to macro
		float xmt = macroSizeFull * xm + whiteSpace;
		float ymt = macroSizeFull * ym + whiteSpace;
		canvas.translate(xmt, ymt);

		//Draw macro lines
		drawGridBarriers(canvas, macroSizeSmall, BLACK, 0);

		//Loop through macro tiles
		for (Coord tile : Coord.macro(xm, ym))
		{
			byte player = board.tile(tile);

			//Translate to tile
			float xt = tile.xs() * tileSize;
			float yt = tile.ys() * tileSize;
			canvas.translate(xt, yt);

			if (player == PLAYER) //x
			{
				if (!mNeutral)
					drawTile(canvas, true, xBorder, tileSize, Color.rgb(0, 0, 230), 16);
				else
					drawTile(canvas, true, xBorder, tileSize, BLUE, 16);
			}
			else if (player == ENEMY) //o
			{
				if (!mNeutral)
					drawTile(canvas, false, oBorder, tileSize, Color.rgb(230, 0, 0), 16);
				else
					drawTile(canvas, false, oBorder, tileSize, RED, 16);
			}

			canvas.translate(-xt, -yt);
		}

		//Make macro gray
		if (!mNeutral)
		{
			paint.setColor(GRAY);
			paint.setStyle(Paint.Style.FILL);
			paint.setAlpha(50);
			canvas.drawRect(0, 0, macroSizeFull - 2 * whiteSpace, macroSizeFull - 2 * whiteSpace, paint);
			paint.setAlpha(100);
		}

		//Draw x and y over macros
		if (mPlayer == PLAYER) //X
			drawTile(canvas, true, xBorder, macroSizeSmall, BLUE, 40);
		else if (mPlayer == ENEMY) //O
			drawTile(canvas, false, oBorder, macroSizeSmall, RED, 40);

		canvas.translate(-xmt, -ymt);
	}

	private void drawTile(Canvas canvas, boolean isX, float border, float size, int color, int strokeWidth)
	{
		float realSize = size - 2 * border;
		canvas.translate(border, border);

		paint.setStrokeWidth(strokeWidth);
		paint.setColor(color);

		if (isX)
		{
			paint.setStyle(Paint.Style.FILL);
			canvas.drawLine(0, 0, realSize, realSize, paint);
			canvas.drawLine(0, realSize, realSize, 0, paint);
		}
		else
		{
			paint.setStyle(Paint.Style.STROKE);
			canvas.drawOval(0, 0, realSize, realSize, paint);
		}

		canvas.translate(-border, -border);
	}

	private void drawGridBarriers(Canvas canvas, float size, int color, int strokeWidth)
	{
		paint.setColor(color);
		paint.setStrokeWidth(strokeWidth);

		for (int i = 1; i < 3; i++)
		{
			canvas.drawLine(i * size / 3, 0, i * size / 3, size, paint);
			canvas.drawLine(0, i * size / 3, size, i * size / 3, paint);
		}
	}

	private boolean pointInSquare(float pointX, float pointY, float startX, float startY, float squareSize)
	{
		return pointX > startX && pointY > startY && pointX < startX + squareSize && pointY < startY + squareSize;
	}

	private Pair<Integer, Integer> findLocation(float x, float y, float startX, float startY, float step)
	{
		int x_ = (int) ((x - startX) / step);
		int y_ = (int) ((y - startY) / step);

		return Pair.create(x_, y_);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		setVars();
	}

	@Override
	public boolean onTouchEvent(MotionEvent e)
	{
		float x = e.getX();
		float y = e.getY();

		if (e.getAction() == MotionEvent.ACTION_UP)
		{
			Log.d("Pressed on", "x: " + x + " y: " + y);

			//If event is in the board
			if (pointInSquare(x, y, 0, 0, fieldSize))
			{

				//Get event's macro
				Pair<Integer, Integer> macro = findLocation(x, y, 0, 0, macroSizeFull);
				int xm = macro.first;
				int ym = macro.second;

				//Check if event is not in whitespace area
				if (pointInSquare(x, y, xm * macroSizeFull + whiteSpace, ym * macroSizeFull + whiteSpace, macroSizeFull - 2 * whiteSpace))
				{
					Pair<Integer, Integer> tile = findLocation(x, y, xm * macroSizeFull + whiteSpace, ym * macroSizeFull + whiteSpace, tileSize);
					int xs = tile.first;
					int ys = tile.second;

					ab.play(Coord.coord(xm, ym, xs, ys));
					Log.d("ClickEvent", "Clicked xm:" + xm + " ym:" + ym + " xs:" + xs + " ys:" + ys);
				}
				else
				{
					Log.d("ClickEvent", "Clicked in whitespace");
				}
			}
			else
			{
				Log.d("ClickEvent", "Clicked outside of board");
			}
		}

		return true;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
		int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
		int fieldSize = Math.min(parentWidth, parentHeight);

		this.setMeasuredDimension(fieldSize, fieldSize);
	}
}