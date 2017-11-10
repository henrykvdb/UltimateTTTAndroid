package com.henrykvdb.sttt;

import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SingleTaskExecutor
{
	private final ExecutorService executor;
	private InterruptableFuture lastTask;

	public SingleTaskExecutor()
	{
		this.executor = Executors.newSingleThreadExecutor();
	}

	public void cancel()
	{
		new RuntimeException("cancel task, not a bug").printStackTrace();
		Log.e("Executor","Cancled task");

		if (lastTask != null)
			lastTask.cancel(true);
	}

	public <T extends InterruptableRunnable> void submit(T task)
	{
		new RuntimeException("submit task, not a bug").printStackTrace();
		Log.e("Executor","Submitted task");

		if (lastTask != null)
			lastTask.cancel(true);

		lastTask = new InterruptableFuture(executor.submit(task),task);
	}

}
