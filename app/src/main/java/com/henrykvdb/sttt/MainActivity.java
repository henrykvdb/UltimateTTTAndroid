package com.henrykvdb.sttt;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.ViewDragHelper;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.Player;
import com.flaghacker.uttt.common.Timer;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.henrykvdb.sttt.DialogUtil.BasicDialogs;
import com.henrykvdb.sttt.DialogUtil.NewGameDialogs;
import com.henrykvdb.sttt.Util.Callback;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static com.flaghacker.uttt.common.Player.ENEMY;
import static com.flaghacker.uttt.common.Player.PLAYER;
import static com.henrykvdb.sttt.MainActivity.Source.Bluetooth;
import static com.henrykvdb.sttt.MainActivity.Source.Local;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener
{
	//Keys
	private static final String GAMESTATE_KEY = "GAMESTATE_KEY";
	private static final String ALLOW_INCOMING_KEY = "ALLOW_INCOMING_KEY";
	private static final String STARTED_WITH_BT_KEY = "STARTED_WITH_BT_KEY";

	//Fields
	private GameState gs;
	private boolean allowIncoming;
	private boolean keepBtOn; //If the app started with bluetooth the app won't disable bluetooth

	//Request codes
	public static final int REQUEST_START_BTPICKER = 100;   //BtPickerActivity
	public static final int REQUEST_ENABLE_BT = 200;        //Permission required to enable Bluetooth
	public static final int REQUEST_ENABLE_DSC = 201;       //Permission required to enable discoverability
	public static final int REQUEST_COARSE_LOCATION = 202;  //Permission required to search nearby devices

	//Bluetooth Service
	private BtService btService;
	private boolean btServiceBound;

	//Bluetooth fields
	private BluetoothAdapter btAdapter;
	private Switch btHostSwitch;

	//Game fields
	private AtomicReference<Pair<Coord, Source>> playerMove = new AtomicReference<>();
	private final Object playerLock = new Object[0];
	private BoardView boardView;
	private GameThread thread;

	private Toast toast;
	private AlertDialog askDialog;
	public static String debuglog = "DEBUGLOG";

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

		//Set some fields used for bluetooth
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		btHostSwitch = (Switch) MenuItemCompat.getActionView(navigationView.getMenu().findItem(R.id.nav_bt_host_switch));
		keepBtOn = savedInstanceState == null
				? btAdapter != null && btAdapter.isEnabled()
				: savedInstanceState.getBoolean(STARTED_WITH_BT_KEY, false);

		//Add a btHostSwitch listener and set the switch to the correct state
		btHostSwitch.setOnCheckedChangeListener(incomingSwitchListener);
		btHostSwitch.setChecked(btAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);

		//Add ads in portrait
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
		{
			MobileAds.initialize(getApplicationContext(), getString(R.string.banner_ad_unit_id));
			((AdView) findViewById(R.id.adView)).loadAd(new AdRequest.Builder().build());
		}

		//Prepare the BoardView and the game object
		boardView = (BoardView) findViewById(R.id.boardView);
		boardView.setNextPlayerView((TextView) findViewById(R.id.next_move_view));

		//Start an actual game
		if (savedInstanceState != null)
			newGame((GameState) savedInstanceState.getSerializable(GAMESTATE_KEY));
		else
			newLocal();

		//Ask the user to rate the app
		if (savedInstanceState == null)
			BasicDialogs.rate(this);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		outState.putBoolean(ALLOW_INCOMING_KEY, allowIncoming);
		outState.putBoolean(STARTED_WITH_BT_KEY, keepBtOn);
		outState.putSerializable(GAMESTATE_KEY, gs);
		super.onSaveInstanceState(outState);
	}

	@Subscribe
	public void onMessageEvent(Events.Setup setupEvent)
	{
		boolean force = setupEvent.force;
		GameState requestState = setupEvent.requestState;

		if (!force)
		{
			final boolean[] allowed = {false};
			runOnUiThread(() -> askUser(btService.getConnectedDeviceName() + " challenges you for a duel, do you accept?", allow ->
			{
				if (allow)
				{
					allowed[0] = true;

					btService.setLocalBoard(requestState.board());
					btService.sendSetup(requestState, true);
					newGame(requestState);
				}
				//If you press yes it will still callback false when dismissed
				else if (!allowed[0])
				{
					btService.cancelRunnable();
				}
			}));
		}
		else
		{
			btService.setLocalBoard(requestState.board());
			runOnUiThread(() -> newGame(requestState));
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
			setSubTitle(null);

		thread = new GameThread();
		thread.start();
	}

	private void newLocal()
	{
		newGame(GameState.builder().swapped(false).build());
	}

	@Subscribe
	public void onMessageEvent(Events.TurnLocal event)
	{
		runOnUiThread(() -> turnLocal());
	}

	private void turnLocal()
	{
		if (!gs.isAi() && !gs.isHuman())
			newGame(GameState.builder().boards(gs.boards()).build());
	}

	@Override
	protected void onStart()
	{
		super.onStart();

		EventBus.getDefault().register(this);

		if (!btServiceBound)
			bindBtService();
	}

	private void bindBtService()
	{
		registerReceiver(btStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
		if (btServiceBound)
			throw new RuntimeException("BtService already bound");

		if (!isServiceRunning(BtService.class))
			startService(new Intent(this, BtService.class));

		bindService(new Intent(this, BtService.class), btServerConn, Context.BIND_AUTO_CREATE);
	}

	private final BroadcastReceiver btStateReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)
					&& btAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF)
			{
				btHostSwitch.setChecked(false);
				btService.cancelRunnable();
				keepBtOn = false;
				Log.e("btStateReceiver", "TURNING OFF");
			}
		}
	};

	private boolean isServiceRunning(Class<?> serviceClass)
	{
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
		{
			if (serviceClass.getName().equals(service.service.getClassName()))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	protected void onStop()
	{
		EventBus.getDefault().unregister(this);
		if (false) //TODO if connected
		{
			//TODO notification disable service or keep open
		}
		else if (!isChangingConfigurations())
		{
			//Stop Bluetooth service it is not a configuration change
			unbindBtService(!isChangingConfigurations());
		}

		super.onStop();
	}

	private void unbindBtService(boolean stop)
	{
		if (btServiceBound)
		{
			btHostSwitch.setChecked(false);

			unregisterReceiver(btStateReceiver);
			unbindService(btServerConn);

			btServiceBound = false;

			if (stop)
				stopService(new Intent(this, BtService.class));
		}
	}

	@Override
	protected void onDestroy()
	{
		if (!keepBtOn)
			btAdapter.disable();

		super.onDestroy();
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

				if (gs.board().nextPlayer() == PLAYER && running)
					playAndUpdateBoard((p1 != Source.AI) ? getMove(p1) : gs.extraBot().move(gs.board(), timer));

				if (gs.board().isDone() || !running)
					continue;

				if (gs.board().nextPlayer() == ENEMY && running)
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

			if (gs.players().contains(Source.Bluetooth))
				btService.sendBoard(newBoard);

			gs.pushBoard(newBoard);
		}

		boardView.drawState(gs);
	}

	@Subscribe
	public void onMessageEvent(Events.NewMove moveEvent)
	{
		synchronized (playerLock)
		{
			playerMove.set(new Pair<>(moveEvent.move, moveEvent.source));
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
			if (thread != null)
				thread.close();
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

	@Subscribe
	public void onMessageEvent(Events.Toast toastEvent)
	{
		runOnUiThread(() -> toast(toastEvent.text));
	}

	private void toast(String text)
	{
		if (toast == null)
			toast = Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT);

		toast.setText(text);
		toast.show();
	}

	private ServiceConnection btServerConn = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			Log.e("BTS", "btService Connected");

			btService = ((BtService.LocalBinder) service).getService();
			btServiceBound = true;

			btService.setAllowIncoming(allowIncoming);
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			Log.e("BTS", "btService Disconnected");
			btServiceBound = false;
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Subscribe
	public void onMessageEvent(Events.Undo undoEvent)
	{
		boolean forced = undoEvent.forced;

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
			if (Source.AI == (gs.board().nextPlayer() == PLAYER ? gs.players().first : gs.players().second)
					&& newState.boards().size() > 1)
				newState.popBoard();

			newGame(newState);

			if (btService != null && btService.getState() == BtService.State.CONNECTED)
				btService.setLocalBoard(gs.board());
		}
		else
		{
			toast("No previous moves");
		}
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item)
	{
		// Handle navigation view item clicks here.
		int id = item.getItemId();

		if (id == R.id.nav_local_human)
		{
			NewGameDialogs.newLocal(this::newLocal, this);
		}
		else if (id == R.id.nav_local_ai)
		{
			NewGameDialogs.newAi(this::newGame, this);
		}
		else if (id == R.id.nav_bt_join)
		{
			pickBluetooth();
		}
		else if (id == R.id.nav_other_feedback)
		{
			BasicDialogs.sendFeedback(this);
		}
		else if (id == R.id.nav_other_share)
		{
			BasicDialogs.share(this);
		}
		else if (id == R.id.nav_other_about)
		{
			BasicDialogs.about(this);
		}

		if (id != R.id.nav_bt_host_switch)
			((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawer(GravityCompat.START);

		return true;
	}

	private CompoundButton.OnCheckedChangeListener incomingSwitchListener = (buttonView, isChecked) ->
	{
		//Stop if there is no bluetooth available
		if (btAdapter == null)
		{
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
			btHostSwitch.setChecked(false);
			return;
		}

		if (isChecked)
		{
			boolean discoverable = btAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
			btHostSwitch.setChecked(discoverable);

			if (!discoverable)
			{
				Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
				discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
				startActivityForResult(discoverableIntent, REQUEST_ENABLE_DSC);
				return;
			}
		}
		else
		{
			if (btAdapter.isEnabled() && !keepBtOn)
				btAdapter.disable();
		}

		allowIncoming = isChecked;
		if (btServiceBound)
			btService.setAllowIncoming(allowIncoming);
	};

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode)
		{
			case REQUEST_ENABLE_BT:
				if (resultCode == RESULT_OK)
					pickBluetooth();
				break;
			case REQUEST_ENABLE_DSC:
				if (resultCode == RESULT_CANCELED)
					btHostSwitch.setChecked(false);
				else
					btHostSwitch.setChecked(true);
				break;
			case REQUEST_START_BTPICKER:
				if (resultCode == RESULT_OK)
				{
					//Create the requested gameState from the activity result
					boolean newBoard = data.getExtras().getBoolean("newBoard");
					boolean swapped = data.getExtras().getBoolean("start")
							^ (newBoard || gs.board().nextPlayer() == Player.PLAYER);
					GameState.Builder gsBuilder = GameState.builder().players(new GameState.Players(Local, Bluetooth)).swapped(swapped);
					if (!newBoard)
						gsBuilder.board(gs.board());

					btService.connect(data.getExtras().getString(BtPickerActivity.EXTRA_DEVICE_ADDRESS), gsBuilder.build());
				}
				break;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		switch (requestCode)
		{
			case REQUEST_COARSE_LOCATION:
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
					pickBluetooth();
				break;
		}

		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	private void pickBluetooth()
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
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		}
		else
		{
			//If we don't have the COARSE LOCATION permission, request it
			if (ContextCompat.checkSelfPermission(this,
					android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
			{
				ActivityCompat.requestPermissions(this,
						new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_COARSE_LOCATION);
			}
			else
			{
				//Make the bluetooth-picker main
				Intent serverIntent = new Intent(getApplicationContext(), BtPickerActivity.class);
				startActivityForResult(serverIntent, REQUEST_START_BTPICKER);
			}
		}
	}

	private void askUser(String message, Callback<Boolean> callBack)
	{
		if (askDialog != null && askDialog.isShowing())
			askDialog.dismiss();

		DialogInterface.OnClickListener dialogClickListener = (dialog, which) ->
		{
			switch (which)
			{
				case DialogInterface.BUTTON_POSITIVE:
					callBack.callback(true);
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					callBack.callback(false);
					break;
			}
		};

		askDialog = new AlertDialog.Builder(this).setMessage(message)
				.setPositiveButton("Yes", dialogClickListener)
				.setNegativeButton("No", dialogClickListener)
				.setOnDismissListener(dialogInterface -> callBack.callback(false))
				.show();

		BasicDialogs.keepDialog(askDialog);
	}
}
