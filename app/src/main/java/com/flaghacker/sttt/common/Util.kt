package com.flaghacker.sttt.common

import java.util.concurrent.*

fun moveBotWithTimeOut(bot: Bot, board: Board, time: Long): Byte? {
	val timer = Timer(time)
	timer.start()
	return bot.move(board, timer)
}

fun moveBotWithTimeOutAsync(
		executor: ExecutorService, bot: Bot, board: Board, time: Long): Future<Byte> {
	val timer = Timer(time)
	timer.start()

	val future = executor.submit(Callable<Byte> { bot.move(board, timer) })

	return object : Future<Byte> {
		private var cancelled = false

		override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
			if (mayInterruptIfRunning) {
				future.cancel(true)
				timer.interrupt()
				cancelled = true
			}

			return cancelled
		}

		override fun isCancelled() = cancelled
		override fun isDone() = future.isDone

		@Throws(InterruptedException::class, ExecutionException::class)
		override fun get() = future.get()

		@Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
		override fun get(timeout: Long, unit: TimeUnit) = future.get(timeout, unit)
	}
}
