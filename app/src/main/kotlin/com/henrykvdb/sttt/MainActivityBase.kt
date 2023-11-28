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
import android.view.View
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
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import com.henrykvdb.sttt.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min


fun log(text: String) = if (BuildConfig.DEBUG) Log.e("STTT", text) else 0

const val GAMESTATE_KEY = "GAMESTATE_KEY" // Key for saving to bundle

/** This is the root Activity, which implements the base game and basic UI **/
open class MainActivityBase : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
	//Game fields
	internal lateinit var gs: GameState

	// Automaticly closed (lifecycleScope)
	private var aiJob: Job? = null

	// Closables
	var openDialog: AlertDialog? = null
	private lateinit var drawerToggle: ActionBarDrawerToggle
	private var onBackCallback = object: OnBackPressedCallback(true) {
		override fun handleOnBackPressed() {
			moveTaskToBack(true);
		}
	}

	//Misc fields
	private lateinit var binding: ActivityMainBinding
	private lateinit var consentInformation: ConsentInformation
	private lateinit var bds : BillingDataSource

	// Admob mobile ads SDK
	private var initAdsEnabled = true // set to false when MobileAds.init() called
	private var initAdsReady = false  // set to true when MobileAds.init() finished

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

		// Create consent information
		consentInformation = UserMessagingPlatform.getConsentInformation(this)
		requestConsent {}

		// Add the billing data source
		bds = (application as StttApplication).appContainer.billingDataSource
		bds.stateLiveData.observe(this) { showAdChecked() }

		// App integrity check
		Firebase.initialize(this)
		Firebase.appCheck.installAppCheckProviderFactory(
			//if(BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance() else
			PlayIntegrityAppCheckProviderFactory.getInstance()
		)

		//Disable crash reporting and firebase analytics on debug builds
		FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

		//Add listener to open and closeGame drawer
		drawerToggle = ActionBarDrawerToggle(
			this, binding.drawerLayout, binding.toolbar,
			R.string.hov_nav_open,
			R.string.hov_nav_close
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
		if (savedInstanceState == null){
			val sharedPref = getSharedPreferences(GAMESTATE_KEY, 0)
			gs = GameState.sharedPrefCreate(sharedPref).apply { turnLocal() }
		} else @Suppress("DEPRECATION") {
			gs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
				savedInstanceState.getSerializable(GAMESTATE_KEY, GameState::class.java)!!
			else savedInstanceState.getSerializable(GAMESTATE_KEY) as GameState
		}

		// Set up board and draw
		binding.boardView.setup { coord -> play(Source.LOCAL, coord) }
		redraw(shouldLaunchAI = false)

		//Add ads in portrait
		if (initAdsEnabled){ // Init SDK just once
			initAdsEnabled = false
			MobileAds.initialize(this) {
				initAdsReady = true
				showAdChecked()
			}
		}
		else showAdChecked()

		// Trigger the rate / tutorial dialog
		if (savedInstanceState == null)
			triggerDialogs()
	}

	private var consentRequestOngoing = false
	private fun requestConsent(callback: () -> Unit){
		// Avoid double triggers
		if (consentRequestOngoing) return
		consentRequestOngoing = true

		// Launch consent requester
		val params = ConsentRequestParameters.Builder().build()
		consentInformation.requestConsentInfoUpdate(this, params, {
			UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
				consentRequestOngoing = false
				if (formError != null) { // Consent not obtained in current session.
					log("Error ${formError.errorCode}: ${formError.message}")
				} else if (consentInformation.canRequestAds()){
					callback()
				}
			}
		}, { log("Error ${it.errorCode}: ${it.message}") })
	}

	private fun showAdChecked(){
		if (consentInformation.canRequestAds()) showAd()
		else requestConsent { showAd() }
	}

	private fun showAd(){
		val shouldShowAd = shouldShowAd()

		val navItem = binding.navigationView.menu.findItem(R.id.nav_remove_ads)
		navItem.isVisible = shouldShowAd

		val adView = binding.adView
		adView?.visibility = if (shouldShowAd) View.VISIBLE else View.GONE

		if (shouldShowAd){
			binding.adView?.apply {
				visibility = View.VISIBLE
				loadAd(AdRequest.Builder().build())
			}
		}
	}

	private fun shouldShowAd(): Boolean {
		val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
		val showAds = bds.billingLoaded && bds.showAds
		return portrait && showAds && initAdsReady
	}

	override fun onDestroy() {
		// Remove listeners
		binding.drawerLayout.addDrawerListener(drawerToggle)
		onBackCallback.remove()

		// Dismiss ask dialog
		openDialog?.setOnDismissListener(null)
		openDialog?.dismiss()

		super.onDestroy()
	}

	override fun onPause() {
		aiJob?.cancel()

		super.onPause()
	}

	override fun onStart() {
		super.onStart()
		launchAI()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		val sharedPref = getSharedPreferences(GAMESTATE_KEY, 0)
		GameState.sharedPrefStore(sharedPref, gs)
		outState.putSerializable(GAMESTATE_KEY, gs)
		super.onSaveInstanceState(outState)
	}

	val AI_DURATION = 500 // The AI is artificially slowed down to 500ms
	private fun launchAI(){
		// Only generate move if needed
		if (gs.nextSource() != Source.AI) return
		if (gs.board.isDone) return

		// Update progress bar and return if should delay more (to reach AI_DURATION)
		fun updateProgressAI(start: Long, aiProg: Int): Boolean{
			val runtime = System.currentTimeMillis() - start
			val progress = min((runtime.toInt() * 100) / AI_DURATION, aiProg)
			runOnUiThread { binding.aiProgressInd.progress = min(progress, 100) }
			return runtime < AI_DURATION
		}

		aiJob?.cancel() // Cancel prior job
		aiJob = lifecycleScope.launch {
			var move: Byte = -1
			try {
				binding.aiProgressInd.isVisible = true
				withContext(Dispatchers.IO) {
					val bot = gs.extraBot
					bot.reset() // the bot might have been cancelled before
					val start = System.currentTimeMillis()
					move = bot.move(gs.board) { aiProgress ->
						if (isActive)
							updateProgressAI(start, aiProgress)
						else bot.cancel()
					}

					var running = true
					while (isActive && running) {
						delay(1)
						running = updateProgressAI(start, 100)
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

	private fun redraw(shouldLaunchAI: Boolean = true){
		// Disable progress bar
		binding.aiProgressInd.isVisible = false

		// Set title
		supportActionBar?.title = when(gs.type){
			Source.LOCAL -> getString(R.string.title_local)
			Source.AI -> getString(R.string.title_computer)
			Source.REMOTE -> getString(R.string.title_online)
		}

		// Set subtitle
		supportActionBar?.subtitle = if (gs.type != Source.REMOTE) null else " ${gs.remoteId}"

		// Redraw board and update text
		binding.boardView.drawState(gs)
		binding.nextMoveTextview.updateText(gs)

		// Generate AI move if necessary
		if (shouldLaunchAI) launchAI()
	}

	override fun onNavigationItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.nav_start_ai -> newAiDialog()
			R.id.nav_start_local -> newLocalDialog()
			R.id.nav_start_online -> newRemoteDialog()
			R.id.nav_remove_ads -> bds.purchaseAdUnlock(this)
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
		if (gs.type == Source.REMOTE) toast(R.string.toast_online_connection_ost)
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

			openDialog?.dismiss()
			openDialog = MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialogTheme)
				.setMessage(getString(R.string.undo_request_msg))
				.setPositiveButton(getString(R.string.yes), dialogClickListener)
				.setNegativeButton(getString(R.string.no), dialogClickListener).show()
		}
	}

	private val toast by lazy { Toast.makeText(this, "", Toast.LENGTH_SHORT) }
	internal fun toast(textResId: Int) {
		toast.cancel()
		toast.setText(textResId)
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

		if (!success){
			launchAI()
			toast(R.string.toast_cant_undo)
		} else runOnUiThread { redraw() }
		return success
	}
}
