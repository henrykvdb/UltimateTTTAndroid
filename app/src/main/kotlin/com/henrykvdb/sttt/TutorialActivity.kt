/*
 * This file is part of Ultimate Tic Tac Toe.
 * Copyright (C) 2023 Henryk Van der Bruggen <henrykdev@gmail.com>
 *
 * This work is licensed under a Creative Commons
 * Attribution-NonCommercial-NoDerivatives 4.0 International License.
 *
 * You should have received a copy of the CC NC ND License along
 * with Ultimate Tic Tac Toe.  If not, see <https://creativecommons.org/>.
 */

package com.henrykvdb.sttt

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro

class TutorialActivity : AppIntro() {
	override val layoutId = R.layout.appintro_fixed
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		//Add the main slide
		addSlide(FirstSlide())

		// Create the boards
		fun gameState(history: List<Int>) = GameState().apply { newRemote(false, history, "")}
		val b0 = gameState(listOf(68, 67, 54, 97, 20, 66, 32))
		val b1 = gameState(listOf(68, 72, -124, 71, 116, 70))
		val b2 = gameState(listOf(65, 23, 120, -124, 64, 6, 104, -127, 24, -122, 103, 112, 7, 116, 72, -123, 81, 16, 4, 66, 40, -121, 113, 19, 48, 8, 68, 22, 102, 2, 36, 56, 32))
		val b3 = gameState(listOf(68, 69, 86, 99))
		val b4 = gameState(listOf(68, 69, 86, 99, 50))
		val b5 = gameState(listOf(68, 72, -124, 71, 116, 70, 100))

		//Add explanation
		// TODO extract to strings
		addSlide(BoardSlide.newInstance("The board", "Players take turns playing nine tic tac toe grids, arranged inside a bigger tic tac toe grid", b0))
		addSlide(BoardSlide.newInstance("Win grids", "Win grids by getting three in a row, like you would in regular tic tac toe", b1))
		addSlide(BoardSlide.newInstance("How to win", "Win 3 grids in a row in order to win the game", b2))
		addSlide(BoardSlide.newInstance("The colors", "The allowed moves for the next player are indicated in that player's color. The last move is colored slightly lighter", b3))
		addSlide(BoardSlide.newInstance("Allowed moves", "You decide where the opponent can play next. Example: If you play top right in a grid, the opponent has to play in the top right grid", b4))
		addSlide(BoardSlide.newInstance("Free moves", "If you get sent to a full or won grid you can play anywhere you want", b5))

		addSlide(ModeSlide())

		//Set bottom navigation bar colors
		setBarColor(ContextCompat.getColor(this, R.color.colorPrimary))
		setSeparatorColor(ContextCompat.getColor(this, R.color.colorAccent))

		//Set navigation button colors
		setColorDoneText(ContextCompat.getColor(this, R.color.colorWhite))
		setColorSkipButton(ContextCompat.getColor(this, R.color.colorWhite))
		setNextArrowColor(ContextCompat.getColor(this, R.color.colorWhite))
		setBackArrowColor(ContextCompat.getColor(this, R.color.colorWhite))

		setIndicatorColor(
			selectedIndicatorColor = ContextCompat.getColor(this, R.color.colorWhite),
			unselectedIndicatorColor = ContextCompat.getColor(this, R.color.colorPrimaryDark)
		)
	}

	override fun onSkipPressed(currentFragment: Fragment?) {
		super.onSkipPressed(currentFragment)
		setResult(Activity.RESULT_OK, intent)
		finish()
	}

	override fun onDonePressed(currentFragment: Fragment?) {
		super.onDonePressed(currentFragment)
		setResult(Activity.RESULT_OK, intent)
		finish()
	}
}

// Custom slides
class FirstSlide : Fragment(R.layout.tutorial_body_first)
class ModeSlide : Fragment(R.layout.tutorial_body_modes)
class BoardSlide : Fragment(R.layout.tutorial_body_boardview) {
	private val title get() = requireArguments().getString("title") ?: ""
	private val desc get() = requireArguments().getString("desc") ?: ""
	private val gs: GameState? get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
			requireArguments().getSerializable("gameState", GameState::class.java)
				else requireArguments().getSerializable("gameState") as GameState?

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return super.onCreateView(inflater, container, savedInstanceState)?.apply {
			findViewById<TextView>(R.id.title).text = title
			findViewById<TextView>(R.id.description).text = desc
			findViewById<BoardView>(R.id.boardView).drawState(gs)
		}
	}

	companion object {
		fun newInstance(title: String, desc: String, gameState: GameState): BoardSlide {
			return BoardSlide().apply {
				arguments = Bundle().apply {
					putString("title", title)
					putString("desc", desc)
					putSerializable("gameState", gameState)
				}
			}
		}
	}
}
