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

	private fun Int.getS() = applicationContext.getString(this)
	private fun Int.getC() = ContextCompat.getColor(applicationContext, this)

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
		addSlide(BoardSlide.newInstance(R.string.tut1_title.getS(), R.string.tut1_desc.getS(), b0))
		addSlide(BoardSlide.newInstance(R.string.tut2_title.getS(),	R.string.tut2_desc.getS(), b1))
		addSlide(BoardSlide.newInstance(R.string.tut3_title.getS(),	R.string.tut3_desc.getS(), b2))
		addSlide(BoardSlide.newInstance(R.string.tut4_title.getS(),	R.string.tut4_desc.getS(), b3))
		addSlide(BoardSlide.newInstance(R.string.tut5_title.getS(),	R.string.tut5_desc.getS(), b4))
		addSlide(BoardSlide.newInstance(R.string.tut6_title.getS(),	R.string.tut6_desc.getS(), b5))

		// Add final slide
		addSlide(ModeSlide())

		//Set bottom navigation bar colors
		setBarColor(R.color.colorNav.getC())
		setSeparatorColor(R.color.colorNavAccent.getC())

		//Set navigation button colors
		setColorDoneText(R.color.colorWhite.getC())
		setColorSkipButton(R.color.colorWhite.getC())
		setNextArrowColor(R.color.colorWhite.getC())
		setBackArrowColor(R.color.colorWhite.getC())

		setIndicatorColor(
			selectedIndicatorColor = R.color.colorWhite.getC(),
			unselectedIndicatorColor = R.color.colorNavDark.getC()
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
				else @Suppress("DEPRECATION") requireArguments().getSerializable("gameState") as GameState?

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
