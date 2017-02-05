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
import com.flaghacker.uttt.common.Bot;
import com.flaghacker.uttt.common.Coord;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.BLUE;
import static android.graphics.Color.GRAY;
import static android.graphics.Color.RED;

public class BoardView extends View
{
	private com.flaghacker.uttt.common.Board board;
	private AndroidBot androidBot;
	private Game game;

	private Paint paint;
	private float macroSize;
	private float whiteSpace;
	private float microLineLength;
	private float tileSize;
	private float xBorder;
	private float oBorder;
	private float fieldSize;

	public BoardView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		androidBot = new AndroidBot();
		paint = new Paint();

		newGame();

		setVars();
		setBoard(new Board());
	}

	private void setVars()
	{
		fieldSize = Math.min(getWidth(), getHeight());

		macroSize = fieldSize / 3;
		whiteSpace = fieldSize * 0.02f;
		microLineLength = macroSize - 2 * whiteSpace;
		tileSize = microLineLength / 3;

		xBorder = fieldSize / 9 * 0.10f;
		oBorder = fieldSize / 9 * 0.15f;
		measure(getMeasuredWidth(), getHeight());
	}

	public void setBoard(Board board)
	{
		this.board = board;
		postInvalidate();
	}

	public void newGame()
	{
		setBoard(new Board());

		if (game != null)
			game.close();

		androidBot = new AndroidBot();
		game = new Game(this, androidBot, androidBot);
		game.run();
	}

	public void newGame(Bot bot)
	{
		setBoard(new Board());

		if (game != null)
			game.close();

		androidBot = new AndroidBot();
		game = new Game(this, androidBot, bot);
		game.run();
	}

	protected void onDraw(Canvas canvas)
	{
		canvas.clipRect(0, 0, fieldSize, fieldSize);

		//Make available moves yellow
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(Color.rgb(255, 255, 100));
		for (Coord coord : board.availableMoves())
		{
			float x = coord.xm() * macroSize + coord.xs() * tileSize + whiteSpace;
			float y = coord.ym() * macroSize + coord.ys() * tileSize + whiteSpace;

			canvas.translate(x, y);

			canvas.drawRect(0, 0, tileSize, tileSize, paint);

			canvas.translate(-x, -y);
		}

		paint.setColor(BLACK);
		paint.setStrokeWidth(0);
		canvas.translate(whiteSpace, whiteSpace);
		//Small blue lines (not actually blue)
		for (int x = 0; x < 9; x++)
		{
			for (int y = 0; y < 3; y++)
			{
				if (x % 3 == 0)
					x++;

				canvas.drawLine(x % 3 * tileSize + x / 3 * macroSize,
						macroSize * y,
						x % 3 * tileSize + x / 3 * macroSize,
						microLineLength + macroSize * y, paint);

				canvas.drawLine(macroSize * y,
						x % 3 * tileSize + x / 3 * macroSize,
						microLineLength + macroSize * y,
						x % 3 * tileSize + x / 3 * macroSize, paint);
			}
		}
		canvas.translate(-whiteSpace, -whiteSpace);

		//Bigger macro separate lines
		paint.setStrokeWidth(8);
		paint.setColor(BLACK);
		for (int xy = 1; xy < 3; xy++)
		{
			canvas.drawLine(xy * macroSize, 0, xy * macroSize, fieldSize, paint);
			canvas.drawLine(0, xy * macroSize, fieldSize, xy * macroSize, paint);
		}

		//O's and X's
		paint.setStrokeWidth(16);
		for (int x = 0; x < 9; x++)
		{
			for (int y = 0; y < 9; y++)
			{
				if (board.tile(x, y) != Board.NEUTRAL)
				{
					float xt = whiteSpace + x % 3 * tileSize + x / 3 * macroSize;
					float yt = whiteSpace + y % 3 * tileSize + y / 3 * macroSize;

					canvas.translate(xt, yt);

					//Draw X and O
					if (board.tile(x, y) == Board.PLAYER)
					{
						paint.setColor(BLUE);
						paint.setStyle(Paint.Style.FILL);
						if (board.macro(x / 3, y / 3) != Board.NEUTRAL)
						{
							paint.setColor(Color.rgb(0, 0, 230));
						}

						//Draw X
						canvas.drawLine(xBorder, xBorder, tileSize - xBorder, tileSize - xBorder, paint);
						canvas.drawLine(xBorder, tileSize - xBorder, tileSize - xBorder, xBorder, paint);
					}
					else if (board.tile(x, y) == Board.ENEMY)
					{ //O
						paint.setColor(RED);
						paint.setStyle(Paint.Style.STROKE);
						if (board.macro(x / 3, y / 3) != Board.NEUTRAL)
						{
							paint.setColor(Color.rgb(230, 0, 0));
						}

						//Draw O
						canvas.drawOval(oBorder, oBorder, tileSize - oBorder, tileSize - oBorder, paint);
					}

					canvas.translate(-xt, -yt);
				}
			}
		}

		paint.setStrokeWidth(40);
		for (int xm = 0; xm < 3; xm++)
		{
			for (int ym = 0; ym < 3; ym++)
			{
				if (board.macro(xm, ym) != Board.NEUTRAL)
				{
					float xt = whiteSpace + xm * macroSize;
					float yt = whiteSpace + ym * macroSize;

					canvas.translate(xt, yt);

					//Make macro gray
					paint.setColor(GRAY);
					paint.setStyle(Paint.Style.FILL);
					paint.setAlpha(50);
					canvas.drawRect(0, 0, macroSize - 2 * whiteSpace, macroSize - 2 * whiteSpace, paint);
					paint.setAlpha(100);

					//Draw x and y over macros
					if (board.macro(xm, ym) == Board.PLAYER)
					{ //X
						paint.setColor(BLUE);
						paint.setStyle(Paint.Style.FILL);
						canvas.drawLine(xBorder, xBorder, macroSize - 2 * whiteSpace - xBorder, macroSize - 2 * whiteSpace - xBorder, paint);
						canvas.drawLine(xBorder, macroSize - 2 * whiteSpace - xBorder, macroSize - 2 * whiteSpace - xBorder, xBorder, paint);
					}
					else if (board.macro(xm, ym) == Board.ENEMY)
					{ //O
						paint.setColor(RED);
						paint.setStyle(Paint.Style.STROKE);
						canvas.drawOval(oBorder, oBorder, macroSize - 2 * whiteSpace - oBorder, macroSize - 2 * whiteSpace - oBorder, paint);
					}

					canvas.translate(-xt, -yt);
				}
			}
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
				Pair<Integer, Integer> macro = findLocation(x, y, 0, 0, macroSize);
				int xm = macro.first;
				int ym = macro.second;

				//Check if event is not in whitespace area
				if (pointInSquare(x, y, xm * macroSize + whiteSpace, ym * macroSize + whiteSpace, macroSize - 2 * whiteSpace))
				{
					Pair<Integer, Integer> tile = findLocation(x, y, xm * macroSize + whiteSpace, ym * macroSize + whiteSpace, tileSize);
					int xs = tile.first;
					int ys = tile.second;

					androidBot.play(Coord.coord(xm, ym, xs, ys));
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