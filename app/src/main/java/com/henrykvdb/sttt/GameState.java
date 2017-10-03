package com.henrykvdb.sttt;

import com.flaghacker.uttt.bots.RandomBot;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import static com.henrykvdb.sttt.MainActivity.Source.AI;
import static com.henrykvdb.sttt.MainActivity.Source.Bluetooth;
import static com.henrykvdb.sttt.MainActivity.Source.Local;

public class GameState implements Serializable
{
	private static final long serialVersionUID = -3051602110955747927L;

	private final Players players;
	private final LinkedList<Board> boards;
	private final Bot extraBot;

	private GameState(Players players, LinkedList<Board> boards, Bot extraBot)
	{
		this.players = players;
		this.boards = boards;
		this.extraBot = extraBot;
	}

	static class Players implements Serializable
	{
		public final MainActivity.Source first;
		public final MainActivity.Source second;

		public Players(MainActivity.Source first, MainActivity.Source second)
		{
			this.first = first;
			this.second = second;
		}

		public Players swap()
		{
			return new Players(second, first);
		}

		public boolean contains(MainActivity.Source source)
		{
			return first == source || second == source;
		}
	}

	public static Builder builder()
	{
		return new Builder();
	}

	public static class Builder
	{
		private Players players = new Players(Local, Local);
		private LinkedList<Board> boards = new LinkedList<>(Collections.singletonList(new Board()));
		private boolean swapped = new Random().nextBoolean();
		private Bot extraBot = new RandomBot();

		public GameState build()
		{
			return new GameState(swapped ? players.swap() : players, boards, extraBot);
		}

		public Builder boards(List<Board> boards)
		{
			this.boards = new LinkedList<>(boards);
			return this;
		}

		public Builder board(Board board)
		{
			return this.boards(Collections.singletonList(board));
		}

		public Builder swapped(boolean swapped)
		{
			this.swapped = swapped;
			return this;
		}

		public Builder ai(Bot extraBot)
		{
			this.extraBot = extraBot;
			players = new Players(Local, AI);
			return this;
		}

		public Builder players(Players players)
		{
			this.players = players;
			return this;
		}

		public Builder bt()
		{
			players = new Players(Local, Bluetooth);
			return this;
		}

		public Builder gs(GameState gs)
		{
			this.players = gs.players();
			this.boards = gs.boards();
			this.swapped = false;
			this.extraBot = gs.extraBot();
			return this;
		}
	}

	public Players players()
	{
		return players;
	}

	public LinkedList<Board> boards()
	{
		return boards;
	}

	public void pushBoard(Board board)
	{
		this.boards.push(board);
	}

	public void popBoard()
	{
		this.boards.pop();
	}

	public Board board()
	{
		return boards.peek();
	}

	public Bot extraBot()
	{
		return extraBot;
	}

	public boolean isBluetooth()
	{
		return players.contains(Bluetooth);
	}

	public boolean isAi()
	{
		return players.contains(AI);
	}

	public boolean isHuman()
	{
		return players.first == Local && players.second == Local;
	}
}
