package com.henrykvdb.sttt;

import android.support.annotation.NonNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class InterruptableFuture implements Future
{
	private final Future<?> lastTask;
	private final InterruptableRunnable runnable;

	public InterruptableFuture(Future<?> lastTask, InterruptableRunnable runnable)
	{
		this.lastTask = lastTask;
		this.runnable = runnable;
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning)
	{
		if (mayInterruptIfRunning)
			runnable.interrupt();

		return lastTask.cancel(true);
	}

	@Override
	public boolean isCancelled()
	{
		return runnable.isInterrupted() && lastTask.isCancelled();
	}

	@Override
	public boolean isDone()
	{
		return lastTask.isDone();
	}

	@Override
	public Object get() throws InterruptedException, ExecutionException
	{
		return lastTask.get();
	}

	@Override
	public Object get(long timeout, @NonNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
	{
		return lastTask.get(timeout,unit);
	}
}
