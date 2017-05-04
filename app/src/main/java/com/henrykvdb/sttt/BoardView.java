package com.henrykvdb.sttt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
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
	private GameState gameState;
	private TextView nextPlayerView;
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
	private GameService gameService;

	public BoardView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		ds = new DrawSettings();
		paint = new Paint();
		path = new Path();

		setVars();
		board = new Board();

		postInvalidate();
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

	public void drawState(GameState gameState)
	{
		this.gameState = gameState;
		postInvalidate();
	}

	public void setNextPlayerView(TextView nextPlayerView)
	{
		this.nextPlayerView = nextPlayerView;
	}

	public void setDrawSettings(DrawSettings drawSettings)
	{
		this.ds = drawSettings;
		setVars();
		postInvalidate();
	}

	public void setGameService(GameService gameService)
	{
		this.gameService = gameService;
	}

	protected void onDraw(Canvas canvas)
	{
		if (gameState != null)
		{
			board = gameState.board();

			//Some helper vars
			boolean xNext = board.nextPlayer() == PLAYER;
			GameState.Players players = gameState.players();
			boolean local = gameState.isLocal();

			//Only if not local game
			boolean yourTurn = board.nextPlayer() == PLAYER
					? players.first == GameService.Source.Local
					: players.second == GameService.Source.Local;

			nextPlayerView.setTextColor(xNext ? Color.BLUE : Color.RED);

			if (!board.isDone())
			{
				if (!local)
					nextPlayerView.setText(yourTurn ? "It is your turn!" : "Waiting on the enemy!");
			}
			else
			{
				if (board.wonBy() == NEUTRAL)
				{
					try
					{
						nextPlayerView.setTextColor(getResources().getColor(android.R.color.primary_text_dark));
					}
					catch (Exception e)
					{
						nextPlayerView.setTextColor(Color.BLACK);
					}

					nextPlayerView.setText("It is a tie!");
				}
				else
				{
					if (local)
					{
						nextPlayerView.setTextColor(board.wonBy() == PLAYER ? Color.BLUE : Color.RED);
						nextPlayerView.setText((board.wonBy() == PLAYER ? "X" : "O") + " won the game!");
					}
					else
					{
						boolean youWon = board.wonBy() == PLAYER
								? players.first == GameService.Source.Local
								: players.second == GameService.Source.Local;
						nextPlayerView.setTextColor(board.wonBy() == PLAYER ? Color.BLUE : Color.RED);
						nextPlayerView.setText(youWon ? "You won!" : "You lost!");
					}
				}
			}
		}

		//Make available moves the correct color
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(board.nextPlayer() == PLAYER ? ds.xColor() : ds.oColor());
		paint.setAlpha(50);
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

		Player mPlayer = board.macro(xm, ym);
		boolean mNeutral = mPlayer == NEUTRAL;
		boolean finished = board.wonBy() != NEUTRAL;
		Coord lastMove = board.getLastMove();

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
				drawTile(canvas, true, false, tileSize, (tile == lastMove) ? ds.xColorLight()
								: (finished ? ds.xColorDarkest() : (mNeutral ? ds.xColor() : ds.xColorDarker())),
						tileSymbolStroke, xBorder);
			else if (player == ENEMY) //o
				drawTile(canvas, false, false, tileSize, (tile == lastMove) ? ds.oColorLight()
								: (finished ? ds.oColorDarkest() : (mNeutral ? ds.oColor() : ds.oColorDarker())),
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
			canvas.drawOval(new RectF(0, 0, realSize, realSize), paint);
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
			if (x > (float) 0 && y > (float) 0 && x < (float) 0 + fieldSize && y < (float) 0 + fieldSize)
			{
				//Get event's macro
				int xm = (int) (x / macroSizeFull);
				int ym = (int) (y / macroSizeFull);

				//Get event's tile
				int xs = (int) ((x - xm * macroSizeFull) / (macroSizeSmall / 3));
				int ys = (int) ((y - ym * macroSizeFull) / (macroSizeSmall / 3));

				//Fix coordinates being too big due to whitespace
				xs = xs > 2 ? --xs : xs;
				ys = ys > 2 ? --ys : ys;

				if (gameService != null)
				{
					gameService.play(GameService.Source.Local, Coord.coord(xm, ym, xs, ys));
					Log.d("ClickEvent", "Clicked: (" + (xm * 3 + xs) + "," + (ym * 3 + ys) + ")");
				}
				else
				{
					Log.d("ERROR", "Clicked without gameService");
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