package com.henrykvdb.uttt;

import android.os.Handler;
import com.flaghacker.uttt.bots.RandomBot;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import static com.henrykvdb.uttt.GameService.Source.AI;
import static com.henrykvdb.uttt.GameService.Source.Bluetooth;
import static com.henrykvdb.uttt.GameService.Source.Local;

public class GameState implements Serializable
{
	private List<GameService.Source> players = Arrays.asList(Local, Local);
	private boolean swapped = new Random().nextBoolean();
	private LinkedList<Board> boards = new LinkedList<>(Collections.singletonList(new Board()));

	//AI
	private Bot extraBot = new RandomBot();

	//Bluetooth
	private boolean btGame;
	private Handler btHandler;

	//Local-game constructor
	public GameState()
	{
		this.swapped = false;
	}

	public GameState(boolean swapped)
	{
		this.swapped = swapped;
	}

	public GameState(boolean swapped, List<Board> boards)
	{
		this.swapped = swapped;
		this.boards = new LinkedList<>(boards);
	}

	//AI-game constructor
	public GameState(Bot ai, boolean swapped)
	{
		this.extraBot = ai;
		this.players = Arrays.asList(Local, AI);
		this.swapped = swapped;
	}

	//Bluetooth-game constructor
	public GameState(Handler btHandler, boolean swapped)
	{
		this.btHandler = btHandler;
		this.swapped = swapped;
		this.players = Arrays.asList(Local, Bluetooth);
		btGame = true;
	}

	public GameState(GameState gameState, Handler btHandler)
	{
		players = gameState.players();
		swapped = gameState.swapped();
		boards = gameState.boards();

		this.btHandler = btHandler;
		this.players = Arrays.asList(Local, Bluetooth);
		btGame = true;
	}

	public GameState(GameState gs)
	{
		this.players = gs.players();
		this.swapped = gs.swapped();
		this.boards = gs.boards();

		List<Board> boardList = new ArrayList<>();
		for (Board board : gs.boards())
			boardList.add(board.copy());

		boards = new LinkedList<>(boardList);

		this.extraBot = gs.extraBot();
		this.btGame = gs.btGame();
		this.btHandler = gs.btHandler();
	}

	public List<GameService.Source> players()
	{
		return players;
	}

	public void setPlayers(List<GameService.Source> players)
	{
		this.players = players;
	}

	public boolean swapped()
	{
		return swapped;
	}

	public void setSwapped(boolean swapped)
	{
		this.swapped = swapped;
	}

	public LinkedList<Board> boards()
	{
		return boards;
	}

	public void setBoards(List<Board> boards)
	{
		this.boards = new LinkedList<>(boards);
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

	public void setExtraBot(Bot extraBot)
	{
		this.extraBot = extraBot;
	}

	public boolean btGame()
	{
		return btGame;
	}

	public void setBtGame(boolean btGame)
	{
		this.btGame = btGame;
	}

	public Handler btHandler()
	{
		return btHandler;
	}

	public void setBtHandler(Handler btHandler)
	{
		this.btHandler = btHandler;
	}
}
