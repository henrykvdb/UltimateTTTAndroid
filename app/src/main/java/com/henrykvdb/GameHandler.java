package com.henrykvdb;

import com.flaghacker.uttt.bots.RandomBot;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;

import java.io.Serializable;

public class GameHandler implements Serializable
{
	private static final long serialVersionUID = 2401679081831269036L;

	private AndroidBot androidBot;
	private BoardView boardView;
	private Game game;

	public GameHandler(BoardView boardView)
	{
		this.boardView = boardView;
		this.androidBot = new AndroidBot();

		boardView.setAndroidBot(androidBot);
	}

	public void setBoardView(BoardView boardView)
	{
		this.boardView = boardView;
		boardView.setAndroidBot(androidBot);

		game.setBoardView(boardView);
		game.redraw();
	}

	public void redraw()
	{
		game.redraw();
	}

	private void newGame(Bot p1, Bot p2)
	{
		boardView.setBoard(new Board());

		if (game != null)
			game.close();

		game = new Game(boardView, p1, p2);
		game.run();
	}

	public void newGame()
	{
		newGame(androidBot, androidBot);
	}

	public void botGame()
	{
		newGame(androidBot, new RandomBot());
	}
}
