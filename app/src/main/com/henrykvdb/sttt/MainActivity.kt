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

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.util.Pair
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.flaghacker.sttt.common.Player
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.material.navigation.NavigationView
import com.henrykvdb.sttt.remote.*
import io.fabric.sdk.android.Fabric
import kotlinx.android.synthetic.main.activity_main.*
import java.io.Closeable
import java.util.*
import java.util.concurrent.atomic.AtomicReference

fun log(text: String) = if (BuildConfig.DEBUG) Log.e("STTT", text) else 0

@SuppressLint("ShowToast")
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
	//Game fields
	private var gameThread = GameThread()
	private lateinit var gs: GameState

	//RemoteService fields
	private var remoteServiceBound = false
	private var remoteServiceStarted = false
	private var remoteService: RemoteService? = null
	private val remote get() = remoteService?.remoteGame()
	private val remoteConnected get() = remote?.state == RemoteState.CONNECTED

	//Bluetooth fields
	private var btAdapter = BluetoothAdapter.getDefaultAdapter()
	private var btDialog: AlertDialog? = null
	private var keepBtOn = false

	//Misc fields
	private var askDialog: AlertDialog? = null
	private var isInBackground = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		setSupportActionBar(toolbar)

		//Disable crash reporting and firebase analytics on debug builds
		val crashlyticsCore = CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build()
		Fabric.with(this, Crashlytics.Builder().core(crashlyticsCore).build())

		//Make it easier to open the drawer
		try {
			val draggerObj = drawer_layout.javaClass.getDeclaredField("mLeftDragger")
					.apply { isAccessible = true }.get(drawer_layout) as androidx.customview.widget.ViewDragHelper
			val edgeSize = draggerObj.javaClass.getDeclaredField("mEdgeSize")
			edgeSize.isAccessible = true
			edgeSize.setInt(draggerObj, edgeSize.getInt(draggerObj) * 4)
		} catch (e: Exception) {
			e.printStackTrace()
		}

		//Add listener to open and closeGame drawer
		val toggle = ActionBarDrawerToggle(
				this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
		drawer_layout.addDrawerListener(toggle)
		toggle.syncState()
		navigation_view.setNavigationItemSelectedListener(this)

		//Add ads in portrait
		if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT && !BuildConfig.DEBUG) {
			MobileAds.initialize(applicationContext, getString(R.string.admob_banner_id))
			adView?.loadAd(AdRequest.Builder().build())
		}

		//Register receiver to get updates from the remote game
		registerReceiver(remoteReceiver, IntentFilter().apply {
			addAction(INTENT_TURNLOCAL)
			addAction(INTENT_NEWGAME)
			addAction(INTENT_TOAST)
			addAction(INTENT_MOVE)
			addAction(INTENT_UNDO)
		})

		//Register the reciever that handles bt state changes
		registerReceiver(btStateReceiver, IntentFilter().apply {
			addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
			addAction(INTENT_STOP_BT_SERVICE)
		})

		if (savedInstanceState == null) {
			keepBtOn = btAdapter?.isEnabled ?: false
			gs = GameState.Builder().swapped(false).build()
			triggerDialogs()
		} else {
			remoteServiceStarted = savedInstanceState.getBoolean(BTSERVICE_STARTED_KEY)
			keepBtOn = savedInstanceState.getBoolean(KEEP_BT_ON_KEY)
			gs = savedInstanceState.getSerializable(GAMESTATE_KEY) as GameState
		}

		//Add listener to the BoardView
		boardView.setup({ coord -> gameThread.play(Source.LOCAL, coord) }, next_move_textview)
	}

	override fun onStart() {
		super.onStart()
		isInBackground = false

		//Cancel notification saying that the RemoteService is still running in the background
		closeBtNotification(this@MainActivity)

		if (!remoteServiceStarted) {
			try {
				startService(Intent(this, RemoteService::class.java))
				remoteServiceStarted = true
			} catch (e: IllegalStateException) {
				//App tried to start a service while in the background, ignore
				log(e.toString())
			}
		}

		bindService(Intent(this, RemoteService::class.java), btServerConn, Context.BIND_AUTO_CREATE)
		newGame(gs)
	}

	private var killService = false
	override fun onSaveInstanceState(outState: Bundle) {
		killService = !isChangingConfigurations && !remoteConnected
		outState.putBoolean(BTSERVICE_STARTED_KEY, remoteServiceStarted && !killService)
		outState.putBoolean(KEEP_BT_ON_KEY, keepBtOn)
		outState.putSerializable(GAMESTATE_KEY, gs)
		super.onSaveInstanceState(outState)
	}

	override fun onStop() {
		isInBackground = true

		//Notification telling the user that BtService is still open
		if (!killService && remoteConnected) openBtNotification(this)

		//Unbind remoteService and stop if needed
		unbindRemoteService(killService)
		if (killService || isChangingConfigurations) gameThread.close()

		super.onStop()
	}

	override fun onDestroy() {
		super.onDestroy()
		unregisterReceiver(remoteReceiver)
		unregisterReceiver(btStateReceiver)

		if (!isChangingConfigurations) {
			unbindRemoteService(true)
			if (!keepBtOn) btAdapter?.disable()
		}

		//Close notification
		closeBtNotification(this)
	}

	private fun unbindRemoteService(stop: Boolean) {
		if (remoteServiceBound) {
			log("Unbinding")
			btDialog?.dismiss()
			remoteServiceBound = false
			unbindService(btServerConn)
		}

		if (stop && remoteServiceStarted) {
			log("Stopping")
			remoteServiceStarted = false
			stopService(Intent(this, RemoteService::class.java))
			turnLocal()
		}
	}

	private val remoteReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent): Unit = when (intent.action) {
			INTENT_MOVE -> gameThread.play(Source.REMOTE, intent.getSerializableExtra(INTENT_DATA) as Byte)
			INTENT_UNDO -> undo(intent.getBooleanExtra(INTENT_DATA, true))
			INTENT_TOAST -> toast(intent.getStringExtra(INTENT_DATA)!!)
			INTENT_TURNLOCAL -> turnLocal()
			INTENT_NEWGAME -> {
				btDialog?.setOnDismissListener { }
				btDialog?.dismiss()
				newGame(intent.getSerializableExtra(INTENT_DATA) as GameState)
			}
			else -> throw IllegalStateException(intent.action)
		}
	}

	private val btServerConn = object : ServiceConnection {
		override fun onServiceConnected(name: ComponentName, service: IBinder) {
			log("remoteService Connected")

			remoteService = (service as RemoteService.LocalBinder).getService()
			remoteServiceBound = true

			if (isInBackground) unbindRemoteService(killService)
			else if (gs.type == Source.REMOTE) {
				if (remoteConnected) {
					//Fetch latest board
					val newBoard = remote!!.lastBoard
					if (newBoard != gs.board()) newBoard.lastMove?.let { gameThread.play(Source.REMOTE, it) }

					//Update subtitle
					setSubTitle(getString(R.string.connected_to, remote?.remoteName)) //TODO fix null after onpause
				} else turnLocal()
			}
		}

		override fun onServiceDisconnected(name: ComponentName) {
			log("remoteService Disconnected")
			remoteServiceBound = false
		}
	}

	private val btStateReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			when (intent.action) {
				BluetoothAdapter.ACTION_STATE_CHANGED -> {
					if (btAdapter?.state == BluetoothAdapter.STATE_TURNING_OFF) {
						btDialog?.dismiss()
						if (remoteService?.getType() == RemoteType.BLUETOOTH) remote?.close()
						keepBtOn = false
					}
				}
				INTENT_STOP_BT_SERVICE -> {
					unbindRemoteService(true)
					closeBtNotification(this@MainActivity)
				}
			}
		}
	}

	private fun newGame(gs: GameState) {
		if (gs.type != Source.REMOTE && btDialog?.isShowing == false)
			remote?.close()
		gameThread.close()

		boardView.drawState(gs)
		this.gs = gs

		setTitle(when (gs.type) {
			Source.LOCAL -> getString(R.string.human_game)
			Source.REMOTE -> getString(R.string.bt_game)
			Source.AI -> getString(R.string.ai_game)
		})

		val remoteName = remote?.remoteName
		setSubTitle(when {
			gs.type != Source.REMOTE -> null
			remoteName != null -> getString(R.string.connected_to, remoteName)
			else -> getString(R.string.connected)
		})

		gameThread = GameThread()
		gameThread.start()
	}

	private fun turnLocal() {
		if (gs.type == Source.REMOTE) {
			newGame(GameState.Builder().boards(gs.boards).build())
			closeBtNotification(this@MainActivity)
		}
	}

	private inner class GameThread : Thread(), Closeable {
		private val playerLock = Object()
		private val playerMove = AtomicReference<Pair<Byte, Source>>()

		@Volatile
		private var running: Boolean = false

		override fun run() {
			name = "GameThread"
			running = true
			log("$this started")

			while (!gs.board().isDone && running) {
				val nextSource = gs.nextSource()
				val move = if (nextSource == Source.AI) gs.extraBot.move(gs.board()) else waitForMove(nextSource)

				if (running) move?.let {
					val newBoard = gs.board().copy()
					newBoard.play(move)

					if (gs.players.contains(Source.REMOTE) && gs.board().nextPlayer == Player.PLAYER == (gs.players.first == Source.LOCAL))
						remote?.sendBoard(newBoard)
					gs.pushBoard(newBoard)
					boardView.drawState(gs)
				}
			}
			log("$this stopped")
		}

		private fun waitForMove(player: Source): Byte? {
			playerMove.set(Pair(-1, Source.LOCAL))
			while ((!gs.board().availableMoves.contains(playerMove.get().first)             //Impossible move
							|| player != playerMove.get().second                            //Wrong player
							|| playerMove.get() == null                                     //No Pair
							|| playerMove.get().first == null                               //No move
							|| playerMove.get().second == null)                             //No source
					&& !interrupted() && running) {
				synchronized(playerLock) {
					try {
						playerLock.wait() //TODO fix #2
					} catch (e: InterruptedException) {
						return null
					}
				}
			}
			return playerMove.getAndSet(null).first
		}

		fun play(source: Source, move: Byte) {
			synchronized(playerLock) {
				playerMove.set(Pair(move, source))
				playerLock.notify() //TODO fix #1
			}
		}

		override fun close() {
			try {
				synchronized(playerLock) {
					running = false
					interrupt()
					playerLock.notify() //TODO fix #0
				}
			} catch (t: Throwable) {
				log("Error closing thread $t")
			}
		}
	}

	private fun setTitle(title: String) {
		supportActionBar?.title = title
	}

	private fun setSubTitle(subTitle: String?) {
		supportActionBar?.subtitle = subTitle
	}

	private val toast by lazy { Toast.makeText(this, "", Toast.LENGTH_SHORT) }
	private fun toast(text: String) {
		toast.setText(text)
		toast.show()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.menu, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		if (item.itemId != R.id.action_undo) return false

		if (gs.boards.size == 1 || (gs.boards.size == 2 && gs.otherSource() == Source.AI)) {
			toast(getString(R.string.no_prev_moves))
			return true
		}

		if (gs.type == Source.REMOTE && remoteConnected)
			remote?.sendUndo(ask = true)
		else if (gs.otherSource() == Source.AI)
			undo(count = 2)
		else undo()

		return true
	}

	fun undo(ask: Boolean = false, count: Int = 1) {
		if (ask && remoteConnected && gs.type == Source.REMOTE) {
			askUser(getString(R.string.undo_request, remote?.remoteName)) { allow ->
				if (allow) {
					undo()
					remote?.sendUndo(ask = false)
				}
			}
		} else newGame(GameState.Builder().gs(gs).build().apply { repeat(count) { popBoard() } })
	}

	override fun onNavigationItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.nav_local_human -> newLocalDialog()
			R.id.nav_local_ai -> newAiDialog()
			R.id.nav_bt_host -> hostBt()
			R.id.nav_bt_join -> joinBt()
			R.id.nav_other_feedback -> feedbackSender()
			R.id.nav_other_tutorial -> startActivity(Intent(this, TutorialActivity::class.java))
			R.id.nav_other_share -> shareDialog()
			R.id.nav_other_about -> aboutDialog()
			else -> return false
		}

		drawer_layout.closeDrawer(GravityCompat.START)
		return true
	}

	private fun hostBt() {
		if (btAdapter == null) {
			Toast.makeText(this, getString(R.string.bt_not_available), Toast.LENGTH_LONG).show()
			return
		} else remoteService?.setType(RemoteType.BLUETOOTH)

		if (btAdapter.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			remote?.listen(GameState.Builder().bt().build())

			val layout = View.inflate(this, R.layout.dialog_body_bt_host, null)
			(layout.findViewById<View>(R.id.bt_host_desc) as TextView).text = getString(R.string.host_desc, remote?.localName)

			val onCheckedChangeListener = RadioGroup.OnCheckedChangeListener { _, _ ->
				//Get board type
				val newBoard = (layout.findViewById<View>(R.id.board_new) as RadioButton).isChecked

				//Get the beginning player
				val beginner = (layout.findViewById<View>(R.id.start_radio_group) as RadioGroup).checkedRadioButtonId
				var start = Random().nextBoolean()
				if (beginner == R.id.start_you)
					start = true
				else if (beginner == R.id.start_other) start = false

				//Create the actual requested gamestate
				val swapped = if (newBoard) !start else start xor (gs.board().nextPlayer == Player.PLAYER)
				val gsBuilder = GameState.Builder().bt().swapped(swapped)
				if (!newBoard) gsBuilder.boards(gs.boards)

				remote?.listen(gsBuilder.build())
			}

			(layout.findViewById<View>(R.id.start_radio_group) as RadioGroup).setOnCheckedChangeListener(onCheckedChangeListener)
			(layout.findViewById<View>(R.id.board_radio_group) as RadioGroup).setOnCheckedChangeListener(onCheckedChangeListener)

			btDialog?.dismiss()
			btDialog = keepDialog(AlertDialog.Builder(this)
					.setView(layout)
					.setCustomTitle(newTitle(getString(R.string.host_bluetooth_game)))
					.setNegativeButton(getString(R.string.close)) { _, _ -> btDialog?.dismiss() }
					.setOnDismissListener { remote?.close() }
					.show())
		} else {
			val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
			discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0)
			startActivityForResult(discoverableIntent, REQUEST_ENABLE_DSC)
		}
	}

	private fun joinBt() {
		if (btAdapter == null) {
			Toast.makeText(this, getString(R.string.bt_not_available), Toast.LENGTH_LONG).show()
			return
		} else remoteService?.setType(RemoteType.BLUETOOTH)

		// If BT is not on, request that it be enabled first.
		if (!btAdapter.isEnabled) {
			val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
		} else {
			//If we don't have the COARSE LOCATION permission, request it
			if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION))
				ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_COARSE_LOC)
			else btDialog = BtPicker(this, btAdapter) { adr -> remote?.connect(adr) }.alertDialog
		}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) joinBt()
		else if (requestCode == REQUEST_ENABLE_DSC && resultCode != Activity.RESULT_CANCELED) hostBt()
		super.onActivityResult(requestCode, resultCode, data)
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		if (requestCode == REQUEST_COARSE_LOC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) joinBt()
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
	}

	private fun askUser(message: String, callBack: (Boolean) -> Unit) {
		val dialogClickListener = DialogInterface.OnClickListener { _, which ->
			if (which == DialogInterface.BUTTON_POSITIVE) callBack.invoke(true)
			else if (which == DialogInterface.BUTTON_NEGATIVE) callBack.invoke(false)
		}

		if (askDialog?.isShowing == true) askDialog!!.dismiss()
		askDialog = keepDialog(AlertDialog.Builder(this).setMessage(message)
				.setPositiveButton(getString(R.string.yes), dialogClickListener)
				.setNegativeButton(getString(R.string.no), dialogClickListener)
				.setOnDismissListener { callBack.invoke(false) }.show())
	}
}
