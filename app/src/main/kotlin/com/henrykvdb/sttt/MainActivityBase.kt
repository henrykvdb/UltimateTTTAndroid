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
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import com.henrykvdb.sttt.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


fun log(text: String) = if (BuildConfig.DEBUG) Log.e("STTT", text) else 0

const val GAMESTATE_KEY = "GAMESTATE_KEY" // Key for saving to bundle

/** This is the root Activity, which implements the base game and basic UI **/
open class MainActivityBase : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
	//Game fields
	internal lateinit var gs: GameState

	// Automaticly closed (lifecycleScope)
	private var aiJob: Job? = null

	// Closables
	private lateinit var drawerToggle: ActionBarDrawerToggle
	private var remoteDialog: AlertDialog? = null
	private var onBackCallback = object: OnBackPressedCallback(true) {
		override fun handleOnBackPressed() {
			moveTaskToBack(true);
		}
	}

	//Misc fields
	private lateinit var binding: ActivityMainBinding

	// Implemented by child class MainActivity
	open fun triggerDialogs() {}
	open fun newAiDialog() {}
	open fun newLocalDialog() {}
	open fun newRemoteDialog() {}
	open fun feedbackSender() {}
	open fun shareDialog() {}
	open fun aboutDialog() {}

	// Implemented by child class MainActivityBaseRemote
	open fun updateRemote(history: List<Int>) {}
	open fun requestRemoteUndo() {}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		setSupportActionBar(binding.toolbar)

		// App integrity check
		Firebase.initialize(this)
		Firebase.appCheck.installAppCheckProviderFactory(
			if(BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
			else PlayIntegrityAppCheckProviderFactory.getInstance()
		)

		//Disable crash reporting and firebase analytics on debug builds
		FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

		//Add listener to open and closeGame drawer
		drawerToggle = ActionBarDrawerToggle(
			this, binding.drawerLayout, binding.toolbar,
			R.string.navigation_drawer_open,
			R.string.navigation_drawer_close
		)
		binding.drawerLayout.addDrawerListener(drawerToggle)
		drawerToggle.syncState()
		binding.navigationView.setNavigationItemSelectedListener(this)
		supportActionBar?.setHomeButtonEnabled(true)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_toolbar_drawer)

		// Make back button not finish() application
		onBackPressedDispatcher.addCallback(this, onBackCallback)

		// Restore gamestate if rotate / re-create
		gs = if (savedInstanceState == null) GameState()
		else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
				savedInstanceState.getSerializable(GAMESTATE_KEY, GameState::class.java)!!
		else @Suppress("DEPRECATION") savedInstanceState.getSerializable(GAMESTATE_KEY) as GameState

		// Set up board and draw
		binding.boardView.setup({ coord -> play(Source.LOCAL, coord) },	binding.nextMoveTextview)
		redraw(launchAI = false)

		//Add ads in portrait
		if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT && !BuildConfig.DEBUG) {
			MobileAds.initialize(this)
			binding.adView?.loadAd(AdRequest.Builder().build())
		}

		// Trigger the rate / tutorial dialog
		if (savedInstanceState == null) triggerDialogs()
	}

	override fun onDestroy() {
		super.onDestroy()

		// Remove listeners
		binding.drawerLayout.addDrawerListener(drawerToggle)
		onBackCallback.remove()

		// Dismiss ask dialog
		remoteDialog?.setOnDismissListener(null)
		remoteDialog?.dismiss()
	}

	override fun onPause() {
		super.onPause()
		aiJob?.cancel()
	}

	override fun onStart() {
		super.onStart()
		if (gs.nextSource() == Source.AI)
			launchAI()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		outState.putSerializable(GAMESTATE_KEY, gs)
		super.onSaveInstanceState(outState)
	}

	private fun launchAI(){
		aiJob = lifecycleScope.launch {
			var move: Byte = -1
			try {
				binding.aiProgressInd.isVisible = true
				withContext(Dispatchers.IO) {
					val bot = gs.extraBot
					bot.reset() // the bot might have been cancelled before
					move = bot.move(gs.board) {
						if (isActive) runOnUiThread {
							if(it < 100) binding.aiProgressInd.progress = it
						} else bot.cancel()
					}
				}
			} finally {
				// (conditionally) play move
				var playMove = true
				playMove = playMove and (move != (-1).toByte())
				playMove = playMove and (lifecycle.currentState >= Lifecycle.State.STARTED)
				playMove = playMove and isActive
				if (playMove) play(Source.AI, move)
			}
		}
	}

	private fun redraw(launchAI: Boolean = true){
		// Disable progress bar
		binding.aiProgressInd.isVisible = false

		// Set title
		supportActionBar?.title = when(gs.type){
			Source.LOCAL -> getString(R.string.local_game)
			Source.AI -> getString(R.string.computer_game)
			Source.REMOTE -> getString(R.string.online_game)
		}

		// Set subtitle
		supportActionBar?.subtitle = if (gs.type != Source.REMOTE) null
										else getString(R.string.subtitle_remote, gs.remoteId)

		// Redraw board
		binding.boardView.drawState(gs)

		// Generate AI move if necessary
		if (launchAI && gs.nextSource() == Source.AI) launchAI()
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

	@Synchronized fun play(source: Source, move: Byte) {
		val remoteGame = gs.type == Source.REMOTE
		if (gs.play(source, move)){
			if (remoteGame) updateRemote(gs.history)
			runOnUiThread { redraw() }
		}
	}

	@Synchronized fun newLocal() {
		gs.newLocal()
		runOnUiThread { redraw() }
	}

	@Synchronized fun turnLocal() {
		gs.turnLocal()
		runOnUiThread { redraw() }
	}

	@Synchronized fun newAi(swapped: Boolean, difficulty: Int) {
		gs.newAi(swapped, difficulty)
		runOnUiThread { redraw() }
	}

	@Synchronized fun newRemote(swapped: Boolean, history: List<Int>, remoteId: String) {
		gs.newRemote(swapped, history, remoteId)
		runOnUiThread { redraw() }
	}

	@Synchronized fun undo(ask: Boolean = false, count: Int = 1) {
		if (!ask) gs.undo(count)
		else {
			val dialogClickListener = DialogInterface.OnClickListener { _, which ->
				if (which == DialogInterface.BUTTON_POSITIVE)
					undo(ask = false)
				updateRemote(history = gs.history) // cancels undo requests
			}

			if (remoteDialog?.isShowing == true) remoteDialog?.dismiss()
			remoteDialog = MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialogTheme)
				.setMessage(getString(R.string.undo_request_msg))
				.setPositiveButton(getString(R.string.yes), dialogClickListener)
				.setNegativeButton(getString(R.string.no), dialogClickListener).show().autoDismiss(this)
		}
	}

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

		var success = true
		aiJob?.cancel()
		when(gs.type){
			Source.REMOTE -> requestRemoteUndo() // don't update success
			Source.AI -> success = if (gs.nextSource() == Source.LOCAL) gs.undo(2)
									else gs.undo()
			Source.LOCAL -> success = gs.undo()
		}

		if (!success) toast("Not enough moves")
		else runOnUiThread { redraw() }
		return success
	}
}
