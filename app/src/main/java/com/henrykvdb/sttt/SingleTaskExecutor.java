package com.henrykvdb.sttt;

import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SingleTaskExecutor
{
	private final ExecutorService executor;
	private InterruptableFuture lastTask;
	private List<Closeable> closeables = new ArrayList<>();

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

		Iterator<Closeable> iter = closeables.iterator();
		while (iter.hasNext())
		{
			Closeable closeable = iter.next();
			closeables.remove(closeable);
			try
			{
				closeable.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
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
