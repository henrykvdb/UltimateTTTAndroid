package com.henrykvdb.sttt

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.flaghacker.sttt.common.Board
import com.flaghacker.sttt.common.Coord
import com.github.paolorotolo.appintro.AppIntro
import com.github.paolorotolo.appintro.AppIntroFragment
import com.github.paolorotolo.appintro.model.SliderPage


class TutorialActivity : AppIntro() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		//Add the main slide
		addSlide(AppIntroFragment.newInstance(SliderPage().apply {
			bgColor = ContextCompat.getColor(this@TutorialActivity, R.color.tutorialBackground)
			titleColor = ContextCompat.getColor(this@TutorialActivity, R.color.textDark)
			descColor = ContextCompat.getColor(this@TutorialActivity, R.color.textLight)
			title = "Tutorial"
			description = "This will go over the basic rules"
			imageDrawable = R.drawable.ic_icon_color
		}))

		//Class used for custom slides with a BoardView in them
		class BoardSlide(val title: String, val description: String, val gameState: GameState) : Fragment() {
			override fun onCreate(savedInstanceState: Bundle?) {
				super.onCreate(savedInstanceState)
				retainInstance = true
			}

			override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
				return inflater.inflate(R.layout.tutorial_body_boardview, container, false).apply {
					findViewById<TextView>(R.id.title).text = title
					findViewById<TextView>(R.id.description).text = description
					findViewById<BoardView>(R.id.boardView).drawState(gameState)
				}
			}
		}

		//TODO add to api itself
		fun Board.playAll(vararg moves: Coord) {
			for (move in moves)
				play(move)
		}

		fun playedBoard(vararg moves: Coord) = Board().apply { playAll(*moves) }

		//Add explanation
		val board = GameState.Builder().build()
		val colors = GameState.Builder().board(playedBoard(40, 41, 51, 57)).build()
		val win = GameState.Builder().board(playedBoard(37, 16, 71, 76, 36, 6, 62, 73, 17, 78, 61, 63, 7, 67, 44, 77,
				46, 9, 4, 38, 26, 79, 64, 12, 27, 8, 40, 15, 60, 2, 22, 35, 18)).build()
		val available = GameState.Builder().board(playedBoard(40, 41, 51, 57, 29)).build()
		val freemoves = GameState.Builder().board(playedBoard(40, 44, 76, 43, 67, 42, 58)).build()

		addSlide(BoardSlide("How to win", "Win 3 small tic tac toe grids in a row in order to win the game", win))
		addSlide(BoardSlide("The board", "The board consists of 9 small tic tac toe grids, arranged inside a tic tac toe grid", board))
		addSlide(BoardSlide("The colors", "The available moves for the next player are indicated in that player's color. The last move is indicated by a lighter color", colors))
		addSlide(BoardSlide("Available moves", "You decide where the opponent can play next. Example: If you play top right in a small grid, the opponent plays in the top right grid", available))
		addSlide(BoardSlide("Free moves", "If you send the opponent to a grid that's already full or won he can play anywhere he wants", freemoves))

		class ModeSlide : Fragment() {
			override fun onCreate(savedInstanceState: Bundle?) {
				super.onCreate(savedInstanceState)
				retainInstance = true
			}

			override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
				return inflater.inflate(R.layout.tutorial_body_modes, container, false)
			}
		}

		addSlide(ModeSlide())

		//Set bottom navigation bar colors
		setBarColor(ContextCompat.getColor(this, R.color.colorPrimary))
		setSeparatorColor(ContextCompat.getColor(this, R.color.colorAccent))
	}

	override fun onSkipPressed(currentFragment: Fragment) {
		super.onSkipPressed(currentFragment)
		onDonePressed(currentFragment)
	}

	override fun onDonePressed(currentFragment: Fragment) {
		super.onDonePressed(currentFragment)
		finish()
	}
}