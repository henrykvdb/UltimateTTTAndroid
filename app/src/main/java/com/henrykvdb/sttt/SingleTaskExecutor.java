package com.henrykvdb.sttt;

import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SingleTaskExecutor
{
	private final ExecutorService executor;
	private InterruptableFuture lastTask;
	private List<Closeable> closeables = Collections.synchronizedList(new ArrayList<>());

	public SingleTaskExecutor()
	{
		this.executor = Executors.newSingleThreadExecutor();
	}

	public void cancel()
	{
		new RuntimeException("cancel task, not a bug").printStackTrace();
		Log.e("Executor", "Cancled task");

		if (lastTask != null)
			lastTask.cancel(true);

		synchronized (this)
		{
			for (Closeable c:closeables)
			{
				try
				{
					c.close();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
			closeables.clear();
		}
	}

	public void submit(InterruptableRunnable task)
	{
		cancel();

		new RuntimeException("submit task, not a bug").printStackTrace();
		Log.e("Executor", "Submitted task");

		lastTask = new InterruptableFuture(executor.submit(task), task);
	}

	public void addCloseable(Closeable... closeables)
	{
		Collections.addAll(this.closeables, closeables);
	}

}
