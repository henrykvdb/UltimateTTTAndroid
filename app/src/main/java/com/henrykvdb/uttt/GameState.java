package com.henrykvdb.uttt;

import android.os.Handler;
import com.flaghacker.uttt.bots.RandomBot;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.henrykvdb.uttt.GameService.Source.AI;
import static com.henrykvdb.uttt.GameService.Source.Local;
import static com.henrykvdb.uttt.GameService.Source.Bluetooth;

public class GameState implements Serializable
{
	private List<GameService.Source> bots = Arrays.asList(Local, Local);
	private boolean swapped = new Random().nextBoolean();
	private Board board = new Board();

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
	public GameState(boolean swapped, Board board)
	{
		this.swapped = swapped;
		this.board = board;
	}

	//AI-game constructor
	public GameState(Bot ai, boolean swapped)
	{
		this.extraBot = ai;
		this.bots = Arrays.asList(Local, AI);
		this.swapped = swapped;
	}

	//Bluetooth-game constructor
	public GameState(Handler btHandler, boolean swapped)
	{
		this.btHandler = btHandler;
		this.swapped=swapped;
		this.bots = Arrays.asList(Local, Bluetooth);
		btGame = true;
	}
	public GameState(GameState gameState, Handler btHandler)
	{
		bots = gameState.bots();
		swapped = gameState.swapped();
		board = gameState.board();

		this.btHandler = btHandler;
		this.bots = Arrays.asList(Local, Bluetooth);
		btGame = true;
	}

	public Bot extraBot()
	{
		return extraBot;
	}

	public void setExtraBot(Bot extraBot)
	{
		this.extraBot = extraBot;
	}

	public List<GameService.Source> bots()
	{
		return bots;
	}

	public void setBots(List<GameService.Source> bots)
	{
		this.bots = bots;
	}

	public boolean swapped()
	{
		return swapped;
	}

	public void setSwapped(boolean swapped)
	{
		this.swapped = swapped;
	}

	public Board board()
	{
		return board;
	}

	public void setBoard(Board board)
	{
		this.board = board;
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
