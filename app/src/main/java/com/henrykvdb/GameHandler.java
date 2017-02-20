package com.henrykvdb;

import android.os.Parcel;
import android.os.Parcelable;
import com.flaghacker.uttt.bots.RandomBot;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;

public class GameHandler implements Parcelable
{
	private AndroidBot androidBot;
	private BoardView boardView;

	private Game game;

	public GameHandler(BoardView boardView)
	{
		setupGh(boardView, new AndroidBot());
	}

	protected GameHandler(Parcel in)
	{
		this.game = in.readParcelable(ClassLoader.getSystemClassLoader());
	}

	public void setupGh(BoardView boardView, AndroidBot androidBot)
	{
		this.boardView = boardView;
		this.androidBot = androidBot;

		boardView.setAndroidBot(androidBot);

		if (game != null)
			game.setupGame(boardView, androidBot);
	}

	private void newGame(Bot p1, Bot p2)
	{
		boardView.setBoard(new Board());

		if (game != null)
			game.close();

		game = new Game(boardView, p1, p2);
		game.run();
		/*Parcel parcel = Parcel.obtain();
		parcel.writeValue(game);
		parcel.setDataPosition(0);
		Game gameCopy =(Game) parcel.readValue(Game.class.getClassLoader()); //Game.CREATOR.createFromParcel(parcel);
		parcel.recycle();
		gameCopy.setupGame(boardView,androidBot);*/
	}

	public void closeGame()
	{
		game.close();
	}

	public void newGame()
	{
		newGame(androidBot, androidBot);
	}

	public void botGame()
	{
		newGame(androidBot, new RandomBot());
	}

	public static final Creator<GameHandler> CREATOR = new Creator<GameHandler>()
	{
		@Override
		public GameHandler createFromParcel(Parcel in)
		{
			return new GameHandler(in);
		}

		@Override
		public GameHandler[] newArray(int size)
		{
			return new GameHandler[size];
		}
	};

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(Parcel parcel, int i)
	{
		parcel.writeParcelable(game, 0);
	}
}
