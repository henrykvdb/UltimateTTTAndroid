package com.flaghacker.sttt.common

fun fromNiceString(string: String): Player {
	Player.values().filter { it.toNiceString() == string }.forEach { return it }
	throw IllegalArgumentException(string + " is not a valid com.flaghacker.sttt.common.KotlinPlayer")
}

enum class Player(val niceString: String) {
	PLAYER("X"),
	ENEMY("O"),
	NEUTRAL(" ");

	fun other(): Player = when {
		this == PLAYER -> ENEMY
		this == ENEMY -> PLAYER
		else -> throw IllegalArgumentException("player should be one of [PLAYER, ENEMY]; was " + this)
	}

	fun otherWithNeutral(): Player = if (this == NEUTRAL) NEUTRAL else this.other()
	fun toNiceString(): String = niceString
}
