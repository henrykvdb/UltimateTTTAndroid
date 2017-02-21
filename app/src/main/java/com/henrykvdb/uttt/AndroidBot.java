package com.henrykvdb.uttt;

import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.Timer;

import java.util.concurrent.atomic.AtomicReference;

public class AndroidBot implements Bot
{
	private final Object playerLock = new Object[0];
	private AtomicReference<Coord> move = new AtomicReference<>();

	@Override
	public Coord move(Board board, Timer timer)
	{
		while (move.get() == null || ! board.availableMoves().contains(move.get()))
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

			if (timer.isInterrupted())
				return null;
		}

		return move.getAndSet(null);
	}

	public void play(Coord coord)
	{
		synchronized (playerLock)
		{
			move.set(coord);
			playerLock.notify();
		}
	}

	@Override
	public String toString()
	{
		return "AndroidBot";
	}
}
