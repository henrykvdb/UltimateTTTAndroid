package com.henrykvdb.sttt;

import com.flaghacker.uttt.common.Coord;

public class Events
{
	public static class Toast
	{
		public final String text;

		public Toast(String text)
		{
			this.text = text;
		}
	}

	public static class TurnLocal
	{
	}

	public static class NewGame
	{
		public final GameState requestState;

		public NewGame(GameState requestState)
		{
			this.requestState = requestState;
		}
	}

	public static class Undo
	{
		public final boolean forced;

		public Undo(boolean forced)
		{
			this.forced = forced;
		}
	}

	public static class NewMove
	{
		public final MainActivity.Source source;
		public final Coord move;

		public NewMove(MainActivity.Source source, Coord move)
		{
			this.source = source;
			this.move = move;
		}
	}
}
