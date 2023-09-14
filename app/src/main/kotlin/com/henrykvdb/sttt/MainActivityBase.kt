/*
 * This file is part of Super Tic Tac Toe.
 * Copyright (C) 2018 Henryk Van der Bruggen <henrykdev@gmail.com>
 *
 * Super Tic Tac Toe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Super Tic Tac Toe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Super Tic Tac Toe.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.henrykvdb.sttt

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.material.navigation.NavigationView
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.henrykvdb.sttt.databinding.ActivityMainBinding
import common.Board
import java.net.URL


fun log(text: String) = if (BuildConfig.DEBUG) Log.e("STTT", text) else 0

const val GAMESTATE_KEY = "GAMESTATE_KEY" // Key for saving to bundle

/** This is the root Activity, which implements the base game and basic UI **/
open class MainActivityBase : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
	//Game fields
	internal lateinit var gs: GameState

	//Misc fields
	private var askDialog: AlertDialog? = null
	private lateinit var binding: ActivityMainBinding

	// Implemented by child class MainActivity
	open fun triggerDialogs() {}
	open fun newAiDialog() {}
	open fun newLocalDialog() {}
	open fun newRemoteDialog() {}
	open fun feedbackSender() {}
	open fun shareDialog() {}
	open fun aboutDialog() {}

	override fun onCreate(savedInstanceState: Bundle?) {
		log("onCreate") // TODO REMOVE
		super.onCreate(savedInstanceState)
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		setSupportActionBar(binding.toolbar)

		//Disable crash reporting and firebase analytics on debug builds
		FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

		//Add listener to open and closeGame drawer
		val toggle = ActionBarDrawerToggle(
			this, binding.drawerLayout, binding.toolbar,
			R.string.navigation_drawer_open,
			R.string.navigation_drawer_close
		)
		binding.drawerLayout.addDrawerListener(toggle)
		toggle.syncState()
		binding.navigationView.setNavigationItemSelectedListener(this)
		supportActionBar?.setHomeButtonEnabled(true)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_toolbar_drawer)

		//Add ads in portrait
		if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT && !BuildConfig.DEBUG) {
			MobileAds.initialize(this) {}
			binding.adView?.loadAd(AdRequest.Builder().build())
		}

		if (savedInstanceState == null) {
			gs = GameState()
			triggerDialogs()
		} else {
			gs = @Suppress("DEPRECATION") if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
				savedInstanceState.getSerializable(GAMESTATE_KEY, GameState::class.java)!!
			else savedInstanceState.getSerializable(GAMESTATE_KEY) as GameState
		}
		updateTitleText() // has to be caled after GameState is created

		//Add listener to the BoardView
		binding.boardView.setup(
			{ coord -> play(Source.LOCAL, coord) }, // TODO should the AI play too?
			binding.nextMoveTextview
		)
		binding.boardView.drawState(gs)
	}

	// TODO remove
	override fun onStart() { log("onStart"); super.onStart() }
	override fun onStop() { log("onStop"); super.onStop() }
	override fun onDestroy() { log("onDestroy"); super.onDestroy() }

	override fun onSaveInstanceState(outState: Bundle) {
		log("onSaveInstanceState")
		outState.putSerializable(GAMESTATE_KEY, gs)
		super.onSaveInstanceState(outState)
	}

	@Synchronized fun play(source: Source, move: Byte) {
		gs.play(source, move)
		runOnUiThread { binding.boardView.drawState(gs) }
	}

	fun updateTitleText(){
		// Set title
		supportActionBar?.title = when(gs.type){
			Source.LOCAL -> getString(R.string.local_game)
			Source.AI -> getString(R.string.computer_game)
			Source.REMOTE -> getString(R.string.online_game)
		}

		// Set subtitle
		supportActionBar?.subtitle = if (gs.type != Source.REMOTE) null
										else getString(R.string.subtitle_remote, gs.remoteId)
	}

	@Synchronized fun newLocal() {
		gs.newLocal()
		runOnUiThread { updateTitleText() }
	}

	@Synchronized fun newAi(swapped: Boolean, difficulty: Int) {
		gs.newAi(swapped, difficulty)
		runOnUiThread { updateTitleText() }
	}

	@Synchronized fun newRemote(swapped: Boolean, board: Board, remoteId: String) {
		gs.newRemote(swapped, board, remoteId)
		runOnUiThread { updateTitleText() }
	}

	/*override fun run() {
			name = "GameThread"
			running = true
			log("$this started")

			while (!gs.board.isDone && running) {
				// Set progress bar visibility
				val nextSource = gs.nextSource()
				runOnUiThread { binding.aiProgressInd.isVisible = nextSource == Source.AI }

				// Fetch move
				val move = if (nextSource == Source.AI) gs.extraBot.move(gs.board) {
					runOnUiThread { if(it < 100) binding.aiProgressInd.progress = it }
				} else waitForMove(nextSource)

				if (running) move?.let {
					val newBoard = gs.board.copy()
					newBoard.play(move)

					// TODO
					//if (gs.players.contains(Source.REMOTE) && gs.board.nextPlayer == Player.PLAYER == (gs.players.first == Source.LOCAL))
					//	remote.sendBoard(newBoard)
					gs.pushBoard(newBoard)
					binding.boardView.drawState(gs)
				}
			}
			log("$this stopped")
		}*/

	private val toast by lazy { Toast.makeText(this, "", Toast.LENGTH_SHORT) }
	internal fun toast(text: String) {
		toast.setText(text)
		toast.show()
	}

	override fun onCreateOptionsMenu(menu: Menu) = true.apply {
		menuInflater.inflate(R.menu.menu_undo, menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		if (item.itemId != R.id.action_undo) return false

		if (gs.boards.size == 1 || (gs.boards.size == 2 && gs.otherSource() == Source.AI)) {
			toast(getString(R.string.no_prev_moves))
			return true
		}

		when(gs.type){
			Source.REMOTE -> TODO()//remote.sendUndo(ask = true)
			Source.AI -> {
				if (gs.nextSource() == Source.LOCAL) undo(count = 2)
				else {
					undo(count = 1)
					gs.extraBot.cancel()
				}
			}
			else -> undo()
		}

		return true
	}

	override fun onNavigationItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.nav_start_ai -> newAiDialog()
			R.id.nav_start_local -> newLocalDialog()
			R.id.nav_start_online -> newRemoteDialog()
			R.id.nav_other_feedback -> feedbackSender()
			R.id.nav_other_tutorial -> startActivity(Intent(this, TutorialActivity::class.java))
			R.id.nav_other_share -> shareDialog()
			R.id.nav_other_about -> aboutDialog()
			else -> return false
		}

		binding.drawerLayout.closeDrawer(GravityCompat.START)
		return true
	}

	private fun undo(ask: Boolean = false, count: Int = 1) {
		if (ask && gs.type == Source.REMOTE) {
			askUser(getString(R.string.undo_request)) { allow ->
				if (allow) {
					undo()
					TODO()//remote.sendUndo(ask = false)
				}
			}
		} else TODO()//newGame(GameStateBase.Builder().gs(gs).build().apply { repeat(count) { popBoard() } })
	}

	private fun askUser(message: String, callBack: (Boolean) -> Unit) {
		val dialogClickListener = DialogInterface.OnClickListener { _, which ->
			if (which == DialogInterface.BUTTON_POSITIVE) callBack.invoke(true)
			else if (which == DialogInterface.BUTTON_NEGATIVE) callBack.invoke(false)
		}

		if (askDialog?.isShowing == true) askDialog!!.dismiss()
		askDialog = AlertDialog.Builder(this).setMessage(message)
			.setPositiveButton(getString(R.string.yes), dialogClickListener)
			.setNegativeButton(getString(R.string.no), dialogClickListener)
			.setOnDismissListener { callBack.invoke(false) }.show()
	}
}
