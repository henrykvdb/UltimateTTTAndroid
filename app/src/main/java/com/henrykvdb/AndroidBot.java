package com.henrykvdb;

import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.Timer;

public class AndroidBot implements Bot
{
	@Override
	public Coord move(Board board, Timer timer)
	{
		Coord result;

		while (move == null || ! board.availableMoves().contains(move))
		{
			synchronized (playerLock)
			{
				try
				{
					playerLock.wait();
				}
				catch (InterruptedException e)
				{
					//NOP
				}
			}
		}

		result = move;
		move = null;

		return result;
	}

	private final Object playerLock = new Object[0];
	private Coord move;

	public void play(Coord coord)
	{
		synchronized (playerLock)
		{
			this.move = coord;
			playerLock.notifyAll();
		}
	}

	@Override
	public String toString()
	{
		return "AndroidBot";
	}
}
