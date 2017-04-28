package com.henrykvdb.sttt;

import android.os.Handler;
import com.flaghacker.uttt.bots.RandomBot;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import static com.henrykvdb.sttt.GameService.Source.AI;
import static com.henrykvdb.sttt.GameService.Source.Bluetooth;
import static com.henrykvdb.sttt.GameService.Source.Local;

public class GameState implements Serializable
{
	private static final long serialVersionUID = -3051602110955747927L;
	private List<GameService.Source> players = Arrays.asList(Local, Local);
	private LinkedList<Board> boards = new LinkedList<>(Collections.singletonList(new Board()));
	private Bot extraBot = new RandomBot();
	private Handler btHandler;

	private GameState(List<GameService.Source> players, LinkedList<Board> boards, Bot extraBot, Handler btHandler)
	{
		this.players = players;
		this.boards = boards;
		this.extraBot = extraBot;
		this.btHandler = btHandler;
	}

	public static Builder builder()
	{
		return new Builder();
	}

	public static class Builder
	{
		private List<GameService.Source> players = Arrays.asList(Local, Local);
		private LinkedList<Board> boards = new LinkedList<>(Collections.singletonList(new Board()));
		private boolean swapped = new Random().nextBoolean();
		private Bot extraBot = new RandomBot();
		private Handler btHandler;

		public GameState build()
		{
			return new GameState(swapped ? Arrays.asList(players.get(1), players.get(0)) : players, boards, extraBot, btHandler);
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
			players = Arrays.asList(Local, AI);
			return this;
		}

		public Builder bt(Handler btHandler)
		{
			this.btHandler = btHandler;
			players = Arrays.asList(Local, Bluetooth);
			return this;
		}

		public Builder gs(GameState gs)
		{
			this.players = gs.players();
			this.boards = gs.boards();
			this.swapped = false;
			this.extraBot = gs.extraBot();
			this.btHandler = gs.btHandler();
			return this;
		}
	}

	public List<GameService.Source> players()
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

	public Handler btHandler()
	{
		return btHandler;
	}

	public boolean isBluetooth()
	{
		return players.contains(GameService.Source.Bluetooth);
	}
}
