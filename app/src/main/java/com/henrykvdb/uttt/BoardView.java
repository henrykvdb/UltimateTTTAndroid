package com.henrykvdb.uttt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.Player;

import java.io.Serializable;

import static com.flaghacker.uttt.common.Player.ENEMY;
import static com.flaghacker.uttt.common.Player.NEUTRAL;
import static com.flaghacker.uttt.common.Player.PLAYER;

public class BoardView extends View implements Serializable
{
	private static final long serialVersionUID = -6067519139638476047L;
	private final Path path;
	private Board board;
	private DrawSettings ds;
	private AndroidBot ab;
	private Paint paint;

	private float macroSizeFull;
	private float whiteSpace;
	private float macroSizeSmall;
	private float tileSize;
	private float xBorder;
	private float oBorder;
	private float fieldSize;

	private int bigGridStroke;
	private int smallGridStroke;
	private int tileSymbolStroke;
	private int macroSymbolStroke;
	private int wonSymbolStroke;

	public BoardView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		ds = new DrawSettings();
		paint = new Paint();
		path = new Path();

		setVars();
		setBoard(new Board());
	}

	private void setVars()
	{
		fieldSize = Math.min(getWidth(), getHeight());

		macroSizeFull = fieldSize / 3;
		whiteSpace = fieldSize * ds.whiteSpace();

		macroSizeSmall = macroSizeFull - 2 * whiteSpace;
		tileSize = macroSizeSmall / 3;

		xBorder = fieldSize * ds.borderX();
		oBorder = fieldSize * ds.borderO();

		bigGridStroke = (int) (fieldSize * ds.bigGridStroke());
		smallGridStroke = (int) (fieldSize * ds.smallGridStroke());
		tileSymbolStroke = (int) (fieldSize * ds.tileSymbolStroke());
		macroSymbolStroke = (int) (fieldSize * ds.macroSymbolStroke());
		wonSymbolStroke = (int) (fieldSize * ds.wonSymbolStroke());
	}

	public void setBoard(Board board)
	{
		this.board = board;
		postInvalidate();
	}

	public void setDrawSettings(DrawSettings drawSettings)
	{
		this.ds = drawSettings;
		setVars();
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
		paint.setColor(ds.availableColor());
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
		drawGridBarriers(canvas, fieldSize, ds.gridColor(), bigGridStroke);

		if (board.isDone())
		{
			switch (board.wonBy())
			{
				case PLAYER:
					drawTile(canvas, true, true, fieldSize, ds.xColor() - ds.symbolTransparency(), wonSymbolStroke, tileSize);
					break;
				case ENEMY:
					drawTile(canvas, false, true, fieldSize, ds.oColor() - ds.symbolTransparency(), wonSymbolStroke, tileSize * oBorder / xBorder);
					break;
				default:
					//Nobody won, so no need to draw anything
					break;
			}
		}
	}

	private void drawMacro(Canvas canvas, int om)
	{
		int xm = om % 3;
		int ym = om / 3;

		//Check if macro is finished
		Player mPlayer = board.macro(xm, ym);
		boolean mNeutral = mPlayer == NEUTRAL;
		boolean finished = board.isDone();

		//Translate to macro
		float xmt = macroSizeFull * xm + whiteSpace;
		float ymt = macroSizeFull * ym + whiteSpace;
		canvas.translate(xmt, ymt);

		//Draw macro lines
		drawGridBarriers(canvas, macroSizeSmall, ds.gridColor(), smallGridStroke);

		//Loop through tiles of the macro
		for (Coord tile : Coord.macro(xm, ym))
		{
			Player player = board.tile(tile);

			//Translate to tile
			float xt = tile.xs() * tileSize;
			float yt = tile.ys() * tileSize;
			canvas.translate(xt, yt);

			if (player == PLAYER) //x
				drawTile(canvas, true, false, tileSize,
						finished ? ds.xColorDarkest() : (mNeutral ? ds.xColor() : ds.xColorDarker()),
						tileSymbolStroke, xBorder);
			else if (player == ENEMY) //o
				drawTile(canvas, false, false, tileSize,
						finished ? ds.oColorDarkest() : (mNeutral ? ds.oColor() : ds.oColorDarker()),
						tileSymbolStroke, oBorder);

			canvas.translate(-xt, -yt);
		}

		//Draw x and y over macros
		if (mPlayer == PLAYER) //X
			drawTile(canvas, true, !finished, macroSizeSmall,
					finished ? (ds.xColorDarker() - ds.symbolTransparency()) : (ds.xColor() - ds.symbolTransparency()),
					macroSymbolStroke, xBorder);
		else if (mPlayer == ENEMY) //O
			drawTile(canvas, false, !finished, macroSizeSmall,
					finished ? (ds.oColorDarker() - ds.symbolTransparency()) : (ds.oColor() - ds.symbolTransparency()),
					macroSymbolStroke, oBorder);

		canvas.translate(-xmt, -ymt);
	}

	private void drawTile(Canvas canvas, boolean isX, boolean grayBack, float size, int color, int strokeWidth, float border)
	{
		if (grayBack)
		{
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(ds.unavailableColor());
			canvas.drawRect(0, 0, size, size, paint);
		}


		float realSize = size - 2 * border;
		canvas.translate(border, border);

		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(strokeWidth);
		paint.setColor(color);
		if (isX)
		{
			path.moveTo(0, 0);
			path.lineTo(realSize, realSize);
			path.moveTo(0, realSize);
			path.lineTo(realSize, 0);

			canvas.drawPath(path, paint);
			path.reset();
		}
		else
		{
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

					if (ab != null)
					{
						ab.play(Coord.coord(xm, ym, xs, ys));
						Log.d("ClickEvent", "Clicked: (" + (xm * 3 + xs) + "," + (ym * 3 + ys) + ")");
					}
					else
					{
						Log.d("ERROR", "Clickevent called without ab");
					}
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