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
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.support.design.widget.NavigationView
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.support.v4.view.GravityCompat
import android.support.v4.widget.ViewDragHelper
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.Pair
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.flaghacker.sttt.common.Player
import com.flaghacker.sttt.common.Timer
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.firebase.analytics.FirebaseAnalytics
import com.henrykvdb.sttt.remote.BtPicker
import com.henrykvdb.sttt.remote.RemoteService
import com.henrykvdb.sttt.remote.RemoteState
import com.henrykvdb.sttt.remote.RemoteType
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
        Fabric.with(this, Crashlytics.Builder().core(CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build()).build())
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)

        //Make it easier to open the drawer
        try {
            val draggerObj = drawer_layout.javaClass.getDeclaredField("mLeftDragger")
                    .apply { isAccessible = true }.get(drawer_layout) as ViewDragHelper
            draggerObj.javaClass.getDeclaredField("mEdgeSize").apply {
                isAccessible = true
                setInt(draggerObj, getInt(draggerObj) * 4)
            }
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
            MobileAds.initialize(applicationContext, getString(R.string.banner_ad_unit_id))
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
        registerReceiver(btStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
            addAction(INTENT_STOP_BT_SERVICE)
        })

        if (savedInstanceState == null) {
            keepBtOn = btAdapter != null && btAdapter!!.isEnabled
            gs = GameState.Builder().swapped(false).build()
            rateDialog()
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
        val notificationManager = NotificationManagerCompat.from(this@MainActivity)
        notificationManager.cancel(REMOTE_STILL_RUNNING)

        if (!remoteServiceStarted) {
            startService(Intent(this, RemoteService::class.java))
            remoteServiceStarted = true
        }

        if (!remoteServiceBound) {
            bindService(Intent(this, RemoteService::class.java), btServerConn, Context.BIND_AUTO_CREATE)
        } else throw IllegalStateException("BtService already bound") //TODO remove

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
        if (!killService && remoteConnected) remoteRunningNotification()

        //Unbind remoteService and stop if needed
        unbindRemoteService(killService)
        if (killService || isChangingConfigurations) gameThread.close()

        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(remoteReceiver)
        unregisterReceiver(btStateReceiver)

        if (!isChangingConfigurations) {
            unbindRemoteService(true)
            if (!keepBtOn) btAdapter!!.disable()
        }

        //Close notification
        val notificationManager = NotificationManagerCompat.from(this@MainActivity)
        notificationManager.cancel(REMOTE_STILL_RUNNING)

        super.onDestroy()
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

    private fun remoteRunningNotification() {
        //This intent reopens the app
        val reopenIntent = Intent(this, MainActivity::class.java)
        reopenIntent.action = Intent.ACTION_MAIN
        reopenIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val reopenPendingIntent = PendingIntent.getActivity(this, 0, reopenIntent, PendingIntent.FLAG_ONE_SHOT)

        //This intent shuts down the remoteService
        val type = remoteService?.getType()
        val closeRemoteIntent = PendingIntent.getBroadcast(this, 1, when (type) {
            RemoteType.BLUETOOTH -> Intent(INTENT_STOP_BT_SERVICE)
            RemoteType.NONE, null -> Intent()
        }, PendingIntent.FLAG_ONE_SHOT)

        //Text to be displayed
        val text = when (type) {
            RemoteType.BLUETOOTH -> getString(R.string.bt_running_notification)
            RemoteType.NONE, null -> ""
        }

        //Drawalbe to be displayed
        val drawable = when (type) {
            RemoteType.BLUETOOTH -> R.drawable.ic_menu_bluetooth
            RemoteType.NONE, null -> R.drawable.ic_icon
        }

        val notification = NotificationCompat.Builder(this, "sttt")
                .setSmallIcon(R.drawable.ic_icon)
                .setContentTitle(getString(R.string.app_name_long))
                .setContentIntent(reopenPendingIntent)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .addAction(drawable, getString(R.string.close), closeRemoteIntent)
                .setOngoing(true).build()

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(REMOTE_STILL_RUNNING, notification)
    }

    private val remoteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent): Unit = when (intent.action) {
            INTENT_MOVE -> gameThread.play(Source.REMOTE, intent.getSerializableExtra(INTENT_DATA) as Byte)
            INTENT_UNDO -> undo(intent.getBooleanExtra(INTENT_DATA, false))
            INTENT_TOAST -> toast(intent.getStringExtra(INTENT_DATA))
            INTENT_TURNLOCAL -> turnLocal()
            INTENT_NEWGAME -> {
                btDialog?.apply { setOnDismissListener { };dismiss() }
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
                    if (newBoard != gs.board()) newBoard.lastMove()?.let { gameThread.play(Source.REMOTE, it) }

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
                    val notificationManager = NotificationManagerCompat.from(this@MainActivity)
                    notificationManager.cancel(REMOTE_STILL_RUNNING)
                }
            }
        }
    }

    private fun newGame(gs: GameState) {
        gameThread.close()
        boardView.drawState(gs)
        this.gs = gs

        setTitle(when (gs.type) {
            Source.LOCAL -> getString(R.string.human_game)
            Source.REMOTE -> getString(R.string.bt_game)
            Source.AI -> getString(R.string.ai_game)
        })

        if (gs.type == Source.REMOTE) {
            val remoteName = remote?.remoteName
            if (remoteName != null) setSubTitle(getString(R.string.connected_to, remoteName))
            else setSubTitle(getString(R.string.connected))
        } else setSubTitle(null)

        gameThread = GameThread()
        gameThread.start()
    }

    private fun turnLocal() {
        if (gs.type == Source.REMOTE) {
            newGame(GameState.Builder().boards(gs.boards).build())
            NotificationManagerCompat.from(this@MainActivity).cancel(REMOTE_STILL_RUNNING)
        }
    }

    private inner class GameThread : Thread(), Closeable {
        private val playerLock = java.lang.Object()
        private val playerMove = AtomicReference<Pair<Byte, Source>>()

        @Volatile
        private var running: Boolean = false
        private var timer = Timer(0)

        override fun run() {
            name = "GameThread"
            running = true

            while (!gs.board().isDone() && running) {
                timer = Timer(5000)
                timer.start()
                val next = gs.nextSource()
                val move = if (next == Source.AI) gs.extraBot.move(gs.board(), timer) else {
                    waitForMove(next)
                }
                if (running) move?.let {
                    val newBoard = gs.board().copy()
                    newBoard.play(move)

                    if (gs.players.contains(Source.REMOTE) && gs.board().nextPlayer() == Player.PLAYER == (gs.players.first == Source.LOCAL))
                        remote?.sendBoard(newBoard)
                    gs.pushBoard(newBoard)
                    boardView!!.drawState(gs)
                }
            }
        }

        private fun waitForMove(player: Source): Byte? {
            playerMove.set(Pair<Byte, Source>(null, null))
            while ((!gs.board().availableMoves().contains(playerMove.get().first)         //Impossible move
                            || player != playerMove.get().second                            //Wrong player
                            || playerMove.get() == null                                     //No Pair
                            || playerMove.get().first == null                               //No move
                            || playerMove.get().second == null)                             //No source
                    && !Thread.interrupted()) {
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
                    timer.interrupt()
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
            remote?.sendUndo(false)
        else if (gs.otherSource() == Source.AI)
            repeat(2) { undo(true) }
        else undo(true)

        return true
    }

    fun undo(force: Boolean) {
        if (!force && remoteConnected && gs.type == Source.REMOTE) {
            askUser(getString(R.string.undo_request, remote?.remoteName), { allow ->
                if (allow) {
                    undo(true)
                    remote?.sendUndo(true)
                }
            })
        } else newGame(GameState.Builder().gs(gs).build().apply { popBoard() })
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_local_human -> newLocalDialog()
            R.id.nav_local_ai -> newAiDialog()
            R.id.nav_bt_host -> hostBt()
            R.id.nav_bt_join -> joinBt()
            R.id.nav_other_feedback -> feedbackSender()
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

        if (btAdapter!!.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            remote?.listen(GameState.Builder().bt().build())

            val layout = View.inflate(this, R.layout.dialog_bt_host, null)
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
                val swapped = if (newBoard) !start else start xor (gs.board().nextPlayer() == Player.PLAYER)
                val gsBuilder = GameState.Builder().bt().swapped(swapped)
                if (!newBoard) gsBuilder.board(gs.board())

                remote?.listen(gsBuilder.build())
            }

            (layout.findViewById<View>(R.id.start_radio_group) as RadioGroup).setOnCheckedChangeListener(onCheckedChangeListener)
            (layout.findViewById<View>(R.id.board_radio_group) as RadioGroup).setOnCheckedChangeListener(onCheckedChangeListener)

            btDialog?.dismiss()
            btDialog = keepDialog(AlertDialog.Builder(this)
                    .setView(layout)
                    .setCustomTitle(newTitle(getString(R.string.host_bluetooth_game)))
                    .setNegativeButton(getString(R.string.close)) { _, _ -> btDialog?.dismiss() }
                    .setOnDismissListener({ remote?.close() }) //TODO fix, onconnect dialog gets dismissed lmao
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
        if (!btAdapter!!.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
        } else {
            //If we don't have the COARSE LOCATION permission, request it
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_COARSE_LOC)
            else btDialog = BtPicker(this, btAdapter!!, { adr -> remote?.connect(adr) }).alertDialog
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
        if (askDialog?.isShowing == true) askDialog!!.dismiss()

        val dialogClickListener = DialogInterface.OnClickListener { _, which ->
            if (which == DialogInterface.BUTTON_POSITIVE) callBack.invoke(true)
            else if (which == DialogInterface.BUTTON_NEGATIVE) callBack.invoke(false)
        }

        askDialog = AlertDialog.Builder(this).setMessage(message)
                .setPositiveButton(getString(R.string.yes), dialogClickListener)
                .setNegativeButton(getString(R.string.no), dialogClickListener)
                .setOnDismissListener { callBack.invoke(false) }.show()

        keepDialog(askDialog!!)
    }
}
