package com.flaghacker.sttt.common

class Timer(private val time: Long) {

	private var start = -1L
	var isInterrupted: Boolean = false

	fun started() = start != -1L
	fun start() {
		start = System.currentTimeMillis()
	}

	fun interrupt() {
		this.isInterrupted = true
	}

	fun running() = timeLeft() > 0
	fun timeLeft(): Long {
		if (!started())
			throw IllegalStateException("this Timer has not been started yet")

		val left = time - (System.currentTimeMillis() - start)
		return if (left > 0 && !isInterrupted) left else 0
	}
}
