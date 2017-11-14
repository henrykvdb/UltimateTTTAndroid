package com.henrykvdb.sttt;

import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.ViewDragHelper;
import android.support.v7.app.*;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Pair;
import android.view.*;
import android.widget.*;
import com.flaghacker.uttt.common.*;
import com.google.android.gms.ads.*;
import com.henrykvdb.sttt.Util.DialogUtil;
import com.henrykvdb.sttt.Util.Util;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener
{
	//Fields that get saved to bundle
	private GameState gs;
	private boolean keepBtOn;

	//Game fields
	private AtomicReference<Pair<Coord, Source>> playerMove = new AtomicReference<>();
	private final Object playerLock = new Object[0];
	private BoardView boardView;
	private GameThread gameThread;

	//Bluetooth
	private BtService btService;
	private boolean btServiceBound;
	private BluetoothAdapter btAdapter;

	//Other
	private Toast toast;
	private AlertDialog askDialog;
	private AlertDialog btDialog;

	public enum Source
	{
		Local,
		AI,
		Bluetooth
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		//Create some variables used later
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

		//Make it easier to open the drawer
		try
		{
			Field mDragger = drawer.getClass().getDeclaredField("mLeftDragger");
			mDragger.setAccessible(true);
			ViewDragHelper draggerObj = (ViewDragHelper) mDragger.get(drawer);
			Field mEdgeSize = draggerObj.getClass().getDeclaredField("mEdgeSize");
			mEdgeSize.setAccessible(true);
			mEdgeSize.setInt(draggerObj, mEdgeSize.getInt(draggerObj) * 4);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		//Add listener to open and closeGame drawer
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
				this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
		drawer.addDrawerListener(toggle);
		toggle.syncState();

		//Add listener to the items
		NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);

		//Add ads in portrait
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
		{
			MobileAds.initialize(getApplicationContext(), getString(R.string.banner_ad_unit_id));
			((AdView) findViewById(R.id.adView)).loadAd(new AdRequest.Builder().build());
		}

		//Register receiver to close bt service intent
		IntentFilter filter = new IntentFilter();
		filter.addAction(Constants.INTENT_STOP_BT_SERVICE);
		filter.addAction(Constants.INTENT_TOAST);
		filter.addAction(Constants.INTENT_NEWGAME);
		filter.addAction(Constants.INTENT_MOVE);
		filter.addAction(Constants.INTENT_UNDO);
		filter.addAction(Constants.INTENT_STOP_BT_SERVICE);
		filter.addAction(Constants.INTENT_TURNLOCAL);
		registerReceiver(intentReceiver, filter);

		//Prepare fields
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		boardView = (BoardView) findViewById(R.id.boardView);
		boardView.setNextPlayerView((TextView) findViewById(R.id.next_move_view));
		boardView.setMoveCallback(coord -> play(Source.Local, coord));

		if (savedInstanceState == null)
		{
			//New game
			keepBtOn = btAdapter != null && btAdapter.isEnabled();
			newLocal();
		}
		else
		{
			//Restore game
			keepBtOn = savedInstanceState.getBoolean(Constants.STARTED_WITH_BT_KEY, false);
			newGame((GameState) savedInstanceState.getSerializable(Constants.GAMESTATE_KEY));
		}

		//Ask the user to rateDialog
		if (savedInstanceState == null)
			DialogUtil.rateDialog(this);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		outState.putBoolean(Constants.STARTED_WITH_BT_KEY, keepBtOn);
		outState.putSerializable(Constants.GAMESTATE_KEY, gs);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onStart()
	{
		super.onStart();

		registerReceiver(btStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

		NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MainActivity.this);
		notificationManager.cancel(Constants.BT_STILL_RUNNING);

		if (!btServiceBound)
			bindBtService();
	}

	@Override
	protected void onStop()
	{
		super.onStop();

		unregisterReceiver(btStateReceiver);

		//Notification telling the user that BtService is still open
		if (btService.getState() == BtService.State.CONNECTED)
			btRunningNotification();

		boolean kill = !isChangingConfigurations() && btService.getState() != BtService.State.CONNECTED;
		unbindBtService(kill);
	}

	public void btRunningNotification()
	{
		//This intent reopens the app
		Intent reopenIntent = new Intent(this, MainActivity.class);
		reopenIntent.setAction(Intent.ACTION_MAIN);
		reopenIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		PendingIntent reopenPendingIntent = PendingIntent.getActivity(this, 0, reopenIntent, PendingIntent.FLAG_ONE_SHOT);

		//This intent shuts down the btService
		Intent intentAction = new Intent(Constants.INTENT_STOP_BT_SERVICE);
		PendingIntent pendingCloseIntent = PendingIntent.getBroadcast(this, 1, intentAction, PendingIntent.FLAG_ONE_SHOT);

		Notification notification = new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_icon)
				.setContentTitle("Super Tic Tac Toe")
				.setContentIntent(reopenPendingIntent)
				.setContentText("A Bluetooth game is still running.\nTouch to open STTT")
				.setStyle(new NotificationCompat.BigTextStyle().bigText("A Bluetooth game is still running.\nTouch to open STTT"))
				.addAction(R.drawable.ic_menu_bluetooth, "close", pendingCloseIntent)
				.setOngoing(true).build();

		NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
		notificationManager.notify(Constants.BT_STILL_RUNNING, notification);
	}

	private BroadcastReceiver intentReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			Log.e("INTENT", "RECEIVED INTENT: " + action);


			switch (action)
			{
				case Constants.INTENT_MOVE:
					Source src = (Source) intent.getSerializableExtra(Constants.INTENT_DATA_FIRST);
					Coord move = (Coord) intent.getSerializableExtra(Constants.INTENT_DATA_SECOND);
					play(src, move);
					break;
				case Constants.INTENT_NEWGAME:
					newGame((GameState) intent.getSerializableExtra(Constants.INTENT_DATA_FIRST));
					break;
				case Constants.INTENT_STOP_BT_SERVICE:
					unbindBtService(true);
					NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MainActivity.this);
					notificationManager.cancel(Constants.BT_STILL_RUNNING);
					break;
				case Constants.INTENT_TOAST:
					toast(intent.getStringExtra(Constants.INTENT_DATA_FIRST));
					break;
				case Constants.INTENT_TURNLOCAL:
					turnLocal();
					break;
				case Constants.INTENT_UNDO:
					Boolean forced = intent.getBooleanExtra(Constants.INTENT_DATA_FIRST, false);

					if (forced)
						undo();
					else
					{
						runOnUiThread(() -> askUser(btService.getConnectedDeviceName() + " requests to undo the last move, do you accept?", allow ->
						{
							if (allow)
							{
								undo();
								btService.sendUndo(true);
							}
						}));
					}
					break;
			}
		}
	};

	@Override
	protected void onDestroy()
	{
		unregisterReceiver(intentReceiver);

		if (!keepBtOn)
			btAdapter.disable();

		NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MainActivity.this);
		notificationManager.cancel(Constants.BT_STILL_RUNNING);

		super.onDestroy();
	}

	private void bindBtService()
	{
		if (btServiceBound)
			throw new RuntimeException("BtService already bound");

		if (!Util.isServiceRunning(BtService.class, this))
			startService(new Intent(this, BtService.class));

		bindService(new Intent(this, BtService.class), btServerConn, Context.BIND_AUTO_CREATE);
	}

	private void unbindBtService(boolean stop)
	{
		if (btServiceBound)
		{
			dismissBtDialog();
			unbindService(btServerConn);
			btServiceBound = false;
		}

		if (stop)
		{
			stopService(new Intent(this, BtService.class));
			turnLocal();
		}
	}

	private ServiceConnection btServerConn = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			Log.e("BTS", "btService Connected");

			btService = ((BtService.LocalBinder) service).getService();
			btServiceBound = true;

			if (gs.isBluetooth())
			{
				if (btService.getState() == BtService.State.CONNECTED)
				{
					//Fetch latest board
					Board newBoard = btService.getLocalBoard();
					if (newBoard != gs.board())
						play(Source.Bluetooth, newBoard.getLastMove());

					//Update subtitle
					setSubTitle("Connected to " + btService.getConnectedDeviceName());
				}
				else turnLocal();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			Log.e("BTS", "btService Disconnected");
			btServiceBound = false;
		}
	};

	private final BroadcastReceiver btStateReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)
					&& btAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF)
			{
				dismissBtDialog();
				btService.closeThread();
				keepBtOn = false;
				Log.e("btStateReceiver", "TURNING OFF");
			}
		}
	};

	private void dismissBtDialog()
	{
		if (btDialog != null)
		{
			Log.e(Constants.LOG_TAG, "Closing bt dialog");
			btDialog.dismiss();
			btDialog = null;
		}
	}

	private void newGame(GameState gs)
	{
		Log.e("NEWGAME", "NEWGAME");
		closeGame();

		this.gs = gs;
		boardView.drawState(gs);

		if (this.gs.isBluetooth())
			setTitle("Bluetooth Game");
		else if (this.gs.isAi())
			setTitle("AI Game");
		else if (this.gs.isHuman()) //Normal local game
			setTitle("Human Game");
		else throw new IllegalStateException();

		if (!gs.isBluetooth())
		{
			setSubTitle(null);

			if (btService != null)
				btService.closeThread();
		}
		else if (btService != null)
			setSubTitle("Connected to " + btService.getConnectedDeviceName());

		dismissBtDialog();

		gameThread = new GameThread();
		gameThread.start();
	}

	private void turnLocal()
	{
		if (!gs.isAi() && !gs.isHuman())
			newGame(GameState.builder().boards(gs.boards()).build());
	}

	private void newLocal()
	{
		newGame(GameState.builder().swapped(false).build());
	}

	private class GameThread extends Thread implements Closeable
	{
		private volatile boolean running;
		private Timer timer;

		@Override
		public void run()
		{
			Log.e("GAMETHREAD RAN", "yea");
			setName("GameThread");
			running = true;

			Source p1 = gs.players().first;
			Source p2 = gs.players().second;

			while (!gs.board().isDone() && running)
			{
				timer = new Timer(5000);

				if (gs.board().nextPlayer() == Player.PLAYER && running)
					playAndUpdateBoard((p1 != Source.AI) ? getMove(p1) : gs.extraBot().move(gs.board(), timer));

				if (gs.board().isDone() || !running)
					continue;

				if (gs.board().nextPlayer() == Player.ENEMY && running)
					playAndUpdateBoard((p2 != Source.AI) ? getMove(p2) : gs.extraBot().move(gs.board(), timer));
			}
		}

		@Override
		public void close() throws IOException
		{
			running = false;

			if (timer != null)
				timer.interrupt();

			interrupt();
		}
	}

	private void playAndUpdateBoard(Coord move)
	{
		if (move != null)
		{
			Board newBoard = gs.board().copy();
			newBoard.play(move);

			if (gs.players().contains(Source.Bluetooth)
					&& ((gs.board().nextPlayer() == Player.PLAYER) == (gs.players().first == Source.Local)))
				btService.sendBoard(newBoard);

			gs.pushBoard(newBoard);
		}

		boardView.drawState(gs);
	}

	private void play(Source source, Coord move)
	{
		synchronized (playerLock)
		{
			playerMove.set(new Pair<>(move, source));
			playerLock.notify();
		}
	}

	private Coord getMove(Source player)
	{
		playerMove.set(new Pair<>(null, null));
		while (!gs.board().availableMoves().contains(playerMove.get().first)    //Impossible move
				|| !player.equals(playerMove.get().second)                      //Wrong player
				|| playerMove.get() == null                                     //No Pair
				|| playerMove.get().first == null                               //No move
				|| playerMove.get().second == null)                             //No source
		{
			synchronized (playerLock)
			{
				try
				{
					playerLock.wait();
				}
				catch (InterruptedException e)
				{
					return null;
				}
			}
		}
		//TODO play sound
		return playerMove.getAndSet(null).first;
	}

	private void closeGame()
	{
		try
		{
			if (gameThread != null)
				gameThread.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	private void setTitle(String title)
	{
		final ActionBar actionBar = getSupportActionBar();

		if (actionBar != null)
			actionBar.setTitle(title);
	}

	private void setSubTitle(String subTitle)
	{
		final ActionBar actionBar = getSupportActionBar();

		if (actionBar != null)
			actionBar.setSubtitle(subTitle);
	}

	private void toast(String text)
	{
		if (toast == null)
			toast = Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT);

		toast.setText(text);
		toast.show();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() != R.id.action_undo)
			return false;

		undo();
		return true;
	}

	public void undo()
	{
		if (gs.boards().size() > 1)
		{
			GameState newState = GameState.builder().gs(gs).build();
			newState.popBoard();
			if (Source.AI == (gs.board().nextPlayer() == Player.PLAYER ? gs.players().first : gs.players().second)
					&& newState.boards().size() > 1)
				newState.popBoard();

			newGame(newState);

			if (btService != null && btService.getState() == BtService.State.CONNECTED)
				btService.setLocalBoard(gs.board());
		}
		else toast("No previous moves");
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item)
	{
		int id = item.getItemId();

		if (id == R.id.nav_local_human)
			DialogUtil.newLocal(accept -> newLocal(), this);
		else if (id == R.id.nav_local_ai)
			DialogUtil.newAi(this::newGame, this);
		else if (id == R.id.nav_bt_host)
			hostBt();
		else if (id == R.id.nav_bt_join)
			joinBt();
		else if (id == R.id.nav_other_feedback)
			DialogUtil.feedbackSender(this);
		else if (id == R.id.nav_other_share)
			DialogUtil.shareDialog(this);
		else if (id == R.id.nav_other_about)
			DialogUtil.aboutDialog(this);
		else return false;

		((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawer(GravityCompat.START);
		return true;
	}

	private void hostBt()
	{
		if (btAdapter == null)
		{
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
			return;
		}

		boolean discoverable = btAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;

		if (discoverable)
		{
			btService.listen();

			View layout = View.inflate(this, R.layout.dialog_bt_host, null);
			((TextView) layout.findViewById(R.id.bt_host_desc)).setText(getString(R.string.host_desc, btService.getLocalBluetoothName()));

			RadioGroup.OnCheckedChangeListener onCheckedChangeListener = (group, checkedId) ->
			{
				//Get board type
				boolean newBoard = ((RadioButton) layout.findViewById(R.id.board_new)).isChecked();

				//Get the beginning player
				int beginner = ((RadioGroup) layout.findViewById(R.id.start_radio_group)).getCheckedRadioButtonId();
				boolean start = new Random().nextBoolean();
				if (beginner == R.id.start_you) start = true;
				else if (beginner == R.id.start_other) start = false;

				//Create the actual requested gamestate
				boolean swapped = newBoard ? (!start) : (start ^ (gs.board().nextPlayer() == Player.PLAYER));
				GameState.Builder gsBuilder = GameState.builder().bt().swapped(swapped);
				if (!newBoard)
					gsBuilder.board(gs.board());

				btService.setRequestState(gsBuilder.build());
			};

			((RadioGroup) layout.findViewById(R.id.start_radio_group)).setOnCheckedChangeListener(onCheckedChangeListener);
			((RadioGroup) layout.findViewById(R.id.board_radio_group)).setOnCheckedChangeListener(onCheckedChangeListener);

			btDialog = DialogUtil.keepDialog(new AlertDialog.Builder(this)
					.setView(layout)
					.setCustomTitle(DialogUtil.newTitle(this, Util.getString(this, R.string.host_bluetooth_game)))
					.setOnCancelListener(dialog -> btService.closeThread())
					.setNegativeButton("close", (dialog, which) ->
					{
						dismissBtDialog();
						btService.closeThread();
					}).show());
		}
		else
		{
			Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
			startActivityForResult(discoverableIntent, Constants.REQUEST_ENABLE_DSC);
		}
	}

	private void joinBt()
	{
		// If the adapter is null, then Bluetooth is not supported
		if (btAdapter == null)
		{
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
			return;
		}

		// If BT is not on, request that it be enabled first.
		if (!btAdapter.isEnabled())
		{
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, Constants.REQUEST_ENABLE_BT);
		}
		else
		{
			//If we don't have the COARSE LOCATION permission, request it
			if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION))
				ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, Constants.REQUEST_COARSE_LOCATION);
			else btDialog = BtPicker.createDialog(this, btAdapter, adr -> btService.connect(adr));
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (requestCode == Constants.REQUEST_ENABLE_BT && resultCode == RESULT_OK)
			joinBt();
		else if (requestCode == Constants.REQUEST_ENABLE_DSC && resultCode != RESULT_CANCELED)
			hostBt();

		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		if (requestCode == Constants.REQUEST_COARSE_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
			joinBt();

		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	private void askUser(String message, Callback<Boolean> callBack)
	{
		if (askDialog != null && askDialog.isShowing())
			askDialog.dismiss();

		DialogInterface.OnClickListener dialogClickListener = (dialog, which) ->
		{
			if (which == DialogInterface.BUTTON_POSITIVE)
				callBack.callback(true);
			else if (which == DialogInterface.BUTTON_NEGATIVE)
				callBack.callback(false);
		};

		askDialog = new AlertDialog.Builder(this).setMessage(message)
				.setPositiveButton("Yes", dialogClickListener)
				.setNegativeButton("No", dialogClickListener)
				.setOnDismissListener(dialogInterface -> callBack.callback(false))
				.show();

		DialogUtil.keepDialog(askDialog);
	}
}
