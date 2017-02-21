package com.henrykvdb.uttt;

import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;

import java.io.Serializable;
import java.util.List;
import java.util.Random;

public class GameState implements Serializable
{
	private List<Bot> bots;
	private boolean swapped = new Random().nextBoolean();
	private boolean running = true;
	private Board board = new Board();

	public GameState(List<Bot> bots, boolean shuffle)
	{
		this.bots = bots;
		this.swapped = shuffle && swapped;
	}

	public GameState(List<Bot> bots, boolean swapped, boolean running, Board board)
	{
		this.bots = bots;
		this.swapped = swapped;
		this.running = running;
		this.board = board;
	}

	public List<Bot> bots()
	{
		return bots;
	}

	public void setBots(List<Bot> bots)
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

	public boolean running()
	{
		return running;
	}

	public void setRunning(boolean running)
	{
		this.running = running;
	}

	public Board board()
	{
		return board;
	}

	public void setBoard(Board board)
	{
		this.board = board;
	}
}
