package com.henrykvdb.sttt

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
import android.support.v4.widget.DrawerLayout
import android.support.v4.widget.ViewDragHelper
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.util.Pair
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.flaghacker.sttt.common.Coord
import com.flaghacker.sttt.common.Player
import com.flaghacker.sttt.common.Timer
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.henrykvdb.sttt.util.*
import java.io.Closeable
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    //Game fields
    private var gameThread = GameThread()
    private var boardView: BoardView? = null

    //Bluetooth
    private var btServiceStarted: Boolean = false
    private var btServiceBound: Boolean = false
    private var killService: Boolean = false
    private var keepBtOn: Boolean = false
    private var btAdapter: BluetoothAdapter? = null
    private var btService: BtService? = null

    //Other
    private var askDialog: AlertDialog? = null
    private var btDialog: AlertDialog? = null
    private var toast: Toast? = null
    private var isInBackground = false
    private var gs: GameState? = null

    enum class Source {
        Local,
        AI,
        Bluetooth
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Create some variables used later
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)

        //Make it easier to open the drawer
        try {
            val dragger = drawer.javaClass.getDeclaredField("mLeftDragger")
            dragger.isAccessible = true
            val draggerObj = dragger.get(drawer) as ViewDragHelper
            val mEdgeSize = draggerObj.javaClass.getDeclaredField("mEdgeSize")
            mEdgeSize.isAccessible = true
            mEdgeSize.setInt(draggerObj, mEdgeSize.getInt(draggerObj) * 4)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        //Add listener to open and closeGame drawer
        val toggle = ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        //Add listener to the items
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        //Add ads in portrait
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            MobileAds.initialize(applicationContext, getString(R.string.banner_ad_unit_id))
            (findViewById<View>(R.id.adView) as AdView).loadAd(AdRequest.Builder().build())
        }

        //Register receiver to close bt service intent
        val filter = IntentFilter()
        filter.addAction(INTENT_STOP_BT_SERVICE)
        filter.addAction(INTENT_TURNLOCAL)
        filter.addAction(INTENT_NEWGAME)
        filter.addAction(INTENT_TOAST)
        filter.addAction(INTENT_MOVE)
        filter.addAction(INTENT_UNDO)
        registerReceiver(intentReceiver, filter)

        //Prepare fields
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        boardView = findViewById(R.id.boardView)
        boardView?.setup(object : Callback<Coord> {
            override fun invoke(coord: Coord) {
                gameThread.play(Source.Local, coord)
            }
        }, findViewById(R.id.next_move_view))

        if (savedInstanceState == null) {
            //New game
            keepBtOn = btAdapter != null && btAdapter!!.isEnabled
            gs = GameState.Builder().swapped(false).build()
        } else {
            //Restore game
            btServiceStarted = savedInstanceState.getBoolean(BTSERVICE_STARTED_KEY)
            keepBtOn = savedInstanceState.getBoolean(KEEP_BT_ON_KEY)
            gs = savedInstanceState.getSerializable(GAMESTATE_KEY) as GameState
        }

        //Ask the user to rateDialog
        if (savedInstanceState == null)
            rateDialog(this)
    }

    override fun onStart() {
        super.onStart()
        isInBackground = false

        //Register receiver
        registerReceiver(btStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        //Cancel notification
        val notificationManager = NotificationManagerCompat.from(this@MainActivity)
        notificationManager.cancel(BT_STILL_RUNNING)

        if (!btServiceStarted) {
            startService(Intent(this, BtService::class.java))
            btServiceStarted = true
        }

        if (!btServiceBound) {
            bindService(Intent(this, BtService::class.java), btServerConn, Context.BIND_AUTO_CREATE)
        } else throw RuntimeException("BtService already bound") //TODO remove

        if (gs == null) throw RuntimeException()
        else newGame(gs!!)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        killService = !isChangingConfigurations && btService != null && btService?.getState() != BtService.State.CONNECTED
        outState.putBoolean(BTSERVICE_STARTED_KEY, btServiceStarted && !killService)
        outState.putBoolean(KEEP_BT_ON_KEY, keepBtOn)
        outState.putSerializable(GAMESTATE_KEY, gs)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        super.onPause()
        isInBackground = true
    }

    override fun onStop() {
        //Notification telling the user that BtService is still open
        if (!killService && btService?.getState() === BtService.State.CONNECTED) btRunningNotification()

        //Unbind btService and stop if needed
        unbindBtService(killService)
        if (killService || isChangingConfigurations) gameThread.close()


        unregisterReceiver(btStateReceiver)
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(intentReceiver)

        if (!isChangingConfigurations) {
            unbindBtService(true)
            if (!keepBtOn) btAdapter!!.disable()
        }

        //Close notification
        val notificationManager = NotificationManagerCompat.from(this@MainActivity)
        notificationManager.cancel(BT_STILL_RUNNING)

        super.onDestroy()
    }

    private fun unbindBtService(stop: Boolean) {
        RuntimeException("unbind").printStackTrace()

        if (btServiceBound) {
            Log.e("BTS", "Unbinding")
            dismissBtDialog()
            btServiceBound = false
            unbindService(btServerConn)
        }

        if (stop) {
            Log.e("BTS", "Stopping")
            btServiceStarted = false
            stopService(Intent(this, BtService::class.java))
            turnLocal()
        }
    }

    private fun btRunningNotification() {
        //This intent reopens the app
        val reopenIntent = Intent(this, MainActivity::class.java)
        reopenIntent.action = Intent.ACTION_MAIN
        reopenIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val reopenPendingIntent = PendingIntent.getActivity(this, 0, reopenIntent, PendingIntent.FLAG_ONE_SHOT)

        //This intent shuts down the btService
        val intentAction = Intent(INTENT_STOP_BT_SERVICE)
        val pendingCloseIntent = PendingIntent.getBroadcast(this, 1, intentAction, PendingIntent.FLAG_ONE_SHOT)

        val notification = NotificationCompat.Builder(this, "sttt")
                .setSmallIcon(R.drawable.ic_icon)
                .setContentTitle(getString(R.string.app_name_long))
                .setContentIntent(reopenPendingIntent)
                .setContentText(getString(R.string.bt_running_notification))
                .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.bt_running_notification)))
                .addAction(R.drawable.ic_menu_bluetooth, getString(R.string.close), pendingCloseIntent)
                .setOngoing(true).build()

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(BT_STILL_RUNNING, notification)
    }

    private val intentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            when (action) {
                INTENT_MOVE -> {
                    val src = intent.getSerializableExtra(INTENT_DATA_FIRST) as Source
                    val move = intent.getSerializableExtra(INTENT_DATA_SECOND) as Coord
                    gameThread.play(src, move)
                }
                INTENT_NEWGAME -> newGame(intent.getSerializableExtra(INTENT_DATA_FIRST) as GameState)
                INTENT_STOP_BT_SERVICE -> {
                    unbindBtService(true)
                    val notificationManager = NotificationManagerCompat.from(this@MainActivity)
                    notificationManager.cancel(BT_STILL_RUNNING)
                }
                INTENT_TOAST -> toast(intent.getStringExtra(INTENT_DATA_FIRST))
                INTENT_TURNLOCAL -> turnLocal()
                INTENT_UNDO -> undo(intent.getBooleanExtra(INTENT_DATA_FIRST, false))
            }
        }
    }

    private val btServerConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.e(LOG_TAG, "btService Connected")

            btService = (service as BtService.LocalBinder).getService()
            btServiceBound = true

            if (isInBackground) unbindBtService(killService)
            else if (gs!!.isBluetooth()) {
                if (btService!!.getState() === BtService.State.CONNECTED) {
                    //Fetch latest board
                    val newBoard = btService!!.getLocalBoard()
                    if (newBoard !== gs!!.board()) newBoard.lastMove()?.let { gameThread.play(Source.Bluetooth, it) }

                    //Update subtitle
                    setSubTitle(getString(R.string.connected_to, btService!!.getConnectedDeviceName()))
                } else turnLocal()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.e(LOG_TAG, "btService Disconnected")
            btServiceBound = false
        }
    }

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED && btAdapter!!.state == BluetoothAdapter.STATE_TURNING_OFF) {
                dismissBtDialog()
                btService!!.closeThread()
                keepBtOn = false
                Log.e("btStateReceiver", "TURNING OFF")
            }
        }
    }

    private fun dismissBtDialog() {
        if (btDialog != null) {
            Log.e(LOG_TAG, "Closing bt dialog")
            btDialog!!.dismiss()
            btDialog = null
        }
    }

    private fun newGame(gs: GameState) {
        gameThread.close()
        dismissBtDialog()

        this.gs = gs
        boardView!!.drawState(gs)

        when {
            this.gs!!.isBluetooth() -> setTitle(getString(R.string.bt_game))
            this.gs!!.isAi() -> setTitle(getString(R.string.ai_game))
            this.gs!!.isHuman() -> setTitle(getString(R.string.human_game))
            else -> throw IllegalStateException()
        }

        if (!gs.isBluetooth()) {
            setSubTitle(null)
            if (btService != null) btService!!.closeThread()
        } else if (btService != null) setSubTitle(getString(R.string.connected_to, btService!!.getConnectedDeviceName()))

        gameThread = GameThread()
        gameThread.start()
    }

    private fun turnLocal() {
        if (!gs!!.isAi() && !gs!!.isHuman()) {
            newGame(GameState.Builder().boards(gs!!.boards).build())
            NotificationManagerCompat.from(this@MainActivity).cancel(BT_STILL_RUNNING)
        }
    }

    private fun newLocal() {
        newGame(GameState.Builder().swapped(false).build())
    }

    private inner class GameThread : Thread(), Closeable {
        private val playerLock = java.lang.Object()
        private val playerMove = AtomicReference<Pair<Coord, Source>>()

        @Volatile
        private var running: Boolean = false
        private var timer = Timer(0)

        override fun run() {
            name = "GameThread"
            running = true

            while (!gs!!.board().isDone() && running) {
                timer = Timer(5000)
                timer.start()
                val next = gs!!.nextSource()
                val move = if (next == Source.AI) gs!!.extraBot.move(gs!!.board(), timer) else {
                    waitForMove(next)
                }
                if (running) move?.let {
                    val newBoard = gs!!.board().copy()
                    newBoard.play(move)

                    if (gs!!.players.contains(Source.Bluetooth) && gs!!.board().nextPlayer() == Player.PLAYER == (gs!!.players.first === Source.Local))
                        btService!!.sendBoard(newBoard)
                    gs!!.pushBoard(newBoard)
                    boardView!!.drawState(gs!!)
                }
            }
        }

        private fun waitForMove(player: Source): Coord? {
            playerMove.set(Pair<Coord, Source>(null, null))
            while ((!gs!!.board().availableMoves().contains(playerMove.get().first) //Impossible move
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
            //TODO play sound
            return playerMove.getAndSet(null).first
        }

        fun play(source: Source, move: Coord) {
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
                    playerLock.notify()
                }
            } catch (t: Throwable) {
                Log.e("RUN", "Error closing thread $t")
            }
        }
    }

    private fun setTitle(title: String) {
        supportActionBar?.title = title
    }

    private fun setSubTitle(subTitle: String?) {
        supportActionBar?.subtitle = subTitle
    }

    private fun toast(text: String) {
        if (toast == null)
            toast = Toast.makeText(this@MainActivity, "", Toast.LENGTH_SHORT)

        toast!!.setText(text)
        toast!!.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_undo)
            return false

        if (gs!!.boards.size == 1) {
            toast(getString(R.string.no_prev_moves))
            return true
        }

        if (btService != null && btService!!.getState() === BtService.State.CONNECTED && gs!!.isBluetooth())
            btService!!.sendUndo(false)
        else
            undo(false)
        return true
    }

    fun undo(force: Boolean) {
        if (!force && btService != null && btService!!.getState() === BtService.State.CONNECTED && gs!!.isBluetooth()) {
            askUser(getString(R.string.undo_request, btService!!.getConnectedDeviceName()), object : Callback<Boolean> {
                override fun invoke(allow: Boolean) { //TODO fix #3
                    if (allow) {
                        undo(true)
                        btService!!.sendUndo(true)
                    }
                }
            })
        } else {
            val newState = GameState.Builder().gs(gs!!).build()
            newState.popBoard()
            if (Source.AI == gs!!.otherSource() && newState.boards.size > 1)
                newState.popBoard()

            newGame(newState)

            if (btService != null && btService!!.getState() === BtService.State.CONNECTED)
                btService!!.setLocalBoard(gs!!.board())
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_local_human -> com.henrykvdb.sttt.util.newLocal(object : Callback<Boolean> {
                override fun invoke(t: Boolean) {
                    if (t) newLocal()
                }
            }, this)
            R.id.nav_local_ai -> newAi(object : Callback<GameState> {
                override fun invoke(t: GameState) {
                    newGame(t)
                }
            }, this)
            R.id.nav_bt_host -> hostBt()
            R.id.nav_bt_join -> joinBt()
            R.id.nav_other_feedback -> feedbackSender(this)
            R.id.nav_other_share -> shareDialog(this)
            R.id.nav_other_about -> aboutDialog(this)
            else -> return false
        }

        (findViewById<View>(R.id.drawer_layout) as DrawerLayout).closeDrawer(GravityCompat.START)
        return true
    }

    private fun hostBt() {
        if (btAdapter == null) {
            Toast.makeText(this, getString(R.string.bt_not_available), Toast.LENGTH_LONG).show()
            return
        }

        val discoverable = btAdapter!!.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE

        if (discoverable) {
            btService!!.listen()

            val layout = View.inflate(this, R.layout.dialog_bt_host, null)
            (layout.findViewById<View>(R.id.bt_host_desc) as TextView).text = getString(R.string.host_desc, btService!!.getLocalBluetoothName())

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
                val swapped = if (newBoard) !start else start xor (gs!!.board().nextPlayer() == Player.PLAYER)
                val gsBuilder = GameState.Builder().bt().swapped(swapped)
                if (!newBoard)
                    gsBuilder.board(gs!!.board())

                btService!!.setRequestState(gsBuilder.build())
            }

            (layout.findViewById<View>(R.id.start_radio_group) as RadioGroup).setOnCheckedChangeListener(onCheckedChangeListener)
            (layout.findViewById<View>(R.id.board_radio_group) as RadioGroup).setOnCheckedChangeListener(onCheckedChangeListener)

            btDialog = keepDialog(AlertDialog.Builder(this)
                    .setView(layout)
                    .setCustomTitle(newTitle(this, getString(R.string.host_bluetooth_game)))
                    .setOnCancelListener { btService!!.closeThread() }
                    .setNegativeButton(getString(R.string.close)) { _, _ ->
                        dismissBtDialog()
                        btService!!.closeThread()
                    }.show())
        } else {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0)
            startActivityForResult(discoverableIntent, REQUEST_ENABLE_DSC)
        }
    }

    private fun joinBt() {
        // If the adapter is null, then Bluetooth is not supported
        if (btAdapter == null) {
            Toast.makeText(this, getString(R.string.bt_not_available), Toast.LENGTH_LONG).show()
            return
        }

        // If BT is not on, request that it be enabled first.
        if (!btAdapter!!.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
        } else {
            //If we don't have the COARSE LOCATION permission, request it
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_COARSE_LOCATION)
            else
                btDialog = BtPicker(this, btAdapter!!, object : Callback<String> {
                    override fun invoke(t: String) {
                        btService?.connect(t)
                    }
                }).alertDialog
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK)
            joinBt()
        else if (requestCode == REQUEST_ENABLE_DSC && resultCode != Activity.RESULT_CANCELED)
            hostBt()

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_COARSE_LOCATION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            joinBt()
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun askUser(message: String, callBack: Callback<Boolean>) {
        if (askDialog != null && askDialog!!.isShowing)
            askDialog!!.dismiss()

        val dialogClickListener = DialogInterface.OnClickListener { _, which ->
            if (which == DialogInterface.BUTTON_POSITIVE)
                callBack.invoke(true)
            else if (which == DialogInterface.BUTTON_NEGATIVE)
                callBack.invoke(false)
        }

        askDialog = AlertDialog.Builder(this).setMessage(message)
                .setPositiveButton(getString(R.string.yes), dialogClickListener)
                .setNegativeButton(getString(R.string.no), dialogClickListener)
                .setOnDismissListener { callBack.invoke(false) }.show()

        keepDialog(askDialog!!)
    }
} 