package sttt

import android.os.Bundle
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.flaghacker.sttt.common.Board
import com.flaghacker.sttt.common.Coord
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import com.github.appintro.model.SliderPage
import com.henrykvdb.sttt.GameState
import com.henrykvdb.sttt.R
import java.util.*

class TutorialActivity : AppIntro() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		//Add the main slide
		addSlide(AppIntroFragment.createInstance(SliderPage().apply {
			backgroundColorRes = R.color.tutorialBackground
			titleColorRes = R.color.textDark
			descriptionColorRes = R.color.textLight
			title = "Tutorial"
			description = "This will go over the basic rules"
			imageDrawable = R.drawable.ic_icon_color
		}))

		//Class used for custom slides with a BoardView in them
		class BoardSlide(val title: String, val description: String, val gameState: GameState) : Fragment() {
			override fun onCreate(savedInstanceState: Bundle?) {
				super.onCreate(savedInstanceState)
			}

			override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
				return inflater.inflate(R.layout.tutorial_body_boardview, container, false).apply {
					findViewById<TextView>(R.id.title).text = title
					findViewById<TextView>(R.id.description).text = description
					findViewById<BoardView>(R.id.boardView).drawState(gameState)
				}
			}
		}

		val rand = Random()
		fun Board.randomMoves(count: Int) = apply { repeat(count) { play(randomAvailableMove(rand)) } }
		fun Board.toGameState() = GameState.Builder().board(this).build()
		fun playedBoard(vararg moves: Coord) = Board().apply {
			for (move in moves)
				play(move)
		}

		//Add explanation
		addSlide(BoardSlide("The board", "Players take turns playing nine tic tac toe grids, arranged inside a bigger tic tac toe grid", playedBoard(40).randomMoves(6).toGameState()))
		addSlide(BoardSlide("Win grids", "Win grids by getting three in a row, like you would in regular tic tac toe", Board("XXx".padStart(45).padEnd(81)).toGameState()))
		addSlide(BoardSlide("How to win", "Win 3 grids in a row in order to win the game", playedBoard(37, 16, 71, 76, 36, 6, 62, 73, 17, 78, 61, 63, 7, 67, 44, 77, 46, 9, 4, 38, 26, 79, 64, 12, 27, 8, 40, 15, 60, 2, 22, 35, 18).toGameState()))
		addSlide(BoardSlide("The colors", "The allowed moves for the next player are indicated in that player's color. The last move is colored slightly lighter", playedBoard(40, 41, 51, 57).toGameState()))
		addSlide(BoardSlide("Allowed moves", "You decide where the opponent can play next. Example: If you play top right in a grid, the opponent has to play in the top right grid", playedBoard(40, 41, 51, 57, 29).toGameState()))
		addSlide(BoardSlide("Free moves", "If you get sent to a full or won grid you can play anywhere you want", playedBoard(40, 44, 76, 43, 67, 42, 58).toGameState()))

		class ModeSlide : Fragment() {
			override fun onCreate(savedInstanceState: Bundle?) {
				super.onCreate(savedInstanceState)
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

	override fun onSkipPressed(currentFragment: Fragment?) {
		super.onSkipPressed(currentFragment)
		finish()
	}

	override fun onDonePressed(currentFragment: Fragment?) {
		super.onDonePressed(currentFragment)
		finish()
	}
}