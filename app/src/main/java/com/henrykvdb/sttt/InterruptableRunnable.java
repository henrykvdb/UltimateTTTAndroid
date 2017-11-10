package com.henrykvdb.sttt;

abstract class InterruptableRunnable implements Runnable
{
	private volatile boolean interrupted = false;

	public void interrupt()
	{
		interrupted = true;
	}

	public boolean isInterrupted()
	{
		return interrupted;
	}
}
