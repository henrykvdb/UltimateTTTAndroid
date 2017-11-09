package com.henrykvdb.sttt;

import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SingleTaskExecutor
{
	private final ExecutorService executor;
	private Future<?> lastTask;

	public SingleTaskExecutor()
	{
		this.executor = Executors.newSingleThreadExecutor();
	}

	public void cancel()
	{
		Log.e("Executor","Cancled task");

		if (lastTask != null)
			lastTask.cancel(true);
	}

	public void submit(Runnable task)
	{
		Log.e("Executor","Submitted task");

		if (lastTask != null)
			lastTask.cancel(true);

		lastTask = executor.submit(task);
	}
}
