package com.henrykvdb.uttt;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.Timer;

import java.util.concurrent.atomic.AtomicReference;

public class WaitBot implements Bot
{
	private final Object playerLock = new Object[0];
	private AtomicReference<Coord> move = new AtomicReference<>();

	private Handler handler = null;

	public WaitBot(Handler handler)
	{
		this.handler = handler;
	}

	public WaitBot()
	{
	}

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

		Log.d("ab","HERe");
		if (handler != null)
		{
			android.os.Message msg = handler.obtainMessage(BtService.Message.SEND_BOARD_UPDATE.ordinal());

			//Make output board and put it in a Bundle
			Bundle bundle = new Bundle();
			Board output = board.copy();
			output.play(move.get());
			bundle.putSerializable("myBoard", output);

			//Return the bundle
			msg.setData(bundle);
			handler.sendMessage(msg);
			Log.d("ab","Called");
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
