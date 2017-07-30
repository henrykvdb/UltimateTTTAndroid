package com.henrykvdb.sttt;

import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.flaghacker.uttt.common.Player;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.henrykvdb.sttt.DialogUtil.BasicDialogs;
import com.henrykvdb.sttt.DialogUtil.NewGameDialogs;

import java.lang.reflect.Field;

import static com.henrykvdb.sttt.Game.Source.Bluetooth;
import static com.henrykvdb.sttt.Game.Source.Local;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener
{
	//Keys
	private static final String STATE_KEY = "STATE_KEY";
	private static final String ALLOW_INCOMING_KEY = "ALLOW_INCOMING_KEY";
	private static final String STARTED_WITH_BT_KEY = "STARTED_WITH_BT_KEY";
	private static final String SAVE_TIME_KEY = "SAVE_TIME_KEY";

	//Request codes
	public static final int REQUEST_START_BTPICKER = 100;
	public static final int REQUEST_ENABLE_BT = 200;
	public static final int REQUEST_ENABLE_DSC = 201;
	public static final int REQUEST_COARSE_LOCATION = 202;

	private BluetoothAdapter btAdapter;
	private Switch btHostSwitch;

	private boolean allowIncoming;
	private boolean startedWithBt;

	private Game game;

	private BtService btService;
	private boolean btServiceBound;

	private void debug() //TODO remove
	{
		Log.e("DEBUG", Thread.currentThread().getStackTrace()[3].getMethodName());
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		debug();

		//Time from onSaveInstance() till onCreate() //TODO fix (SO bounty?)
		long time = System.currentTimeMillis();
		if (savedInstanceState != null)
			time -= savedInstanceState.getLong(SAVE_TIME_KEY, System.currentTimeMillis() + 10000);

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

		//Add listener to open and close drawer
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
		startedWithBt = savedInstanceState == null
				? btAdapter != null && btAdapter.isEnabled()
				: savedInstanceState.getBoolean(STARTED_WITH_BT_KEY, false);

		//Add a btHostSwitch listener and set the switch to the correct state
		if (savedInstanceState == null || time > 999)
		{
			//Not rotated
			btHostSwitch.setOnCheckedChangeListener(incomingSwitchListener);
			btHostSwitch.setChecked(btAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
		}
		else
		{
			//Rotated, bypass the listener to avoid permission request
			allowIncoming = savedInstanceState.getBoolean(ALLOW_INCOMING_KEY, false);
			btHostSwitch.setChecked(allowIncoming);
			btHostSwitch.setOnCheckedChangeListener(incomingSwitchListener);
		}

		//Add ads in portrait
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
		{
			MobileAds.initialize(getApplicationContext(), getString(R.string.banner_ad_unit_id));
			((AdView) findViewById(R.id.adView)).loadAd(new AdRequest.Builder().build());
		}

		//Prepare the BoardView and the game object
		BoardView boardView = (BoardView) findViewById(R.id.boardView);
		boardView.setNextPlayerView((TextView) findViewById(R.id.next_move_view));

		//Start an actual game
		game = new Game(gameCallback, (BoardView) findViewById(R.id.boardView));
		if (savedInstanceState != null)
			game.newGame((GameState) savedInstanceState.getSerializable(STATE_KEY));

		//Ask the user to rate the app
		if (savedInstanceState == null)
			BasicDialogs.rate(this);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		debug();

		outState.putLong(SAVE_TIME_KEY, System.currentTimeMillis());
		outState.putBoolean(ALLOW_INCOMING_KEY, allowIncoming);
		outState.putBoolean(STARTED_WITH_BT_KEY, startedWithBt);
		outState.putSerializable(STATE_KEY, game.getState());
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onStart()
	{
		debug();
		super.onStart();

		if (!btServiceBound)
			startBtService();
	}

	@Override
	protected void onStop()
	{
		//If connected show the user a notification to decide if he wants to leave the btService open
		if (false) //TODO if connected
		{
			//TODO notification disable service or keep open
		}
		//If not connected and user opened the app with bluetooth off -> disable it
		else if (!startedWithBt)
		{
			stopBtService();
			btAdapter.disable();
		}

		debug();
		super.onStop();
	}

	@Override
	protected void onDestroy()
	{
		debug();

		stopBtService();

		if (!startedWithBt)
			btAdapter.disable();

		super.onDestroy();
	}

	private void startBtService()
	{
		if (btServiceBound)
			throw new RuntimeException("BtService already bound");

		bindService(new Intent(this, BtService.class), btServerConn, Context.BIND_AUTO_CREATE);
		startService(new Intent(this, BtService.class));
	}

	private void stopBtService()
	{
		if (btServiceBound)
		{
			unbindService(btServerConn);
			btServiceBound = false;
			stopService(new Intent(this, BtService.class));
		}
	}

	private ServiceConnection btServerConn = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			Log.d("BTS", "btService Connected");

			btService = ((BtService.LocalBinder) service).getService();
			btServiceBound = true;

			btService.setAllowIncoming(allowIncoming);

			//TODO remove?
			//boolean blockIncoming = btService.blockIncoming() || !btHostSwitch.isChecked();
			//if (btService.blockIncoming() != blockIncoming)
			//	btService.setBlockIncoming(blockIncoming);
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			Log.d("BTS", "btService Disconnected");
			btServiceBound = false;
			//finish(); //TODO better handling
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == R.id.action_undo)
		{
			game.undo();
			return true;
		}
		return false;
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item)
	{
		// Handle navigation view item clicks here.
		int id = item.getItemId();
		debug();

		if (id == R.id.nav_local_human)
		{
			NewGameDialogs.newLocal(() -> game.newLocal(), this);
		}
		else if (id == R.id.nav_local_ai)
		{
			NewGameDialogs.newAi(gs -> game.newGame(gs), this);
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

	Game.GameCallback gameCallback = new Game.GameCallback()
	{
		public Toast toast;

		@Override
		public void setTitle(String title)
		{
			final ActionBar actionBar = getSupportActionBar();

			if (actionBar != null)
				actionBar.setTitle(title);
		}

		@Override
		public void setSubTitle(String subTitle)
		{
			final ActionBar actionBar = getSupportActionBar();

			if (actionBar != null)
				actionBar.setSubtitle(subTitle);
		}

		@Override
		public void sendToast(String text)
		{
			if (toast == null)
				toast = Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT);

			toast.setText(text);
			toast.show();
		}
	};

	private CompoundButton.OnCheckedChangeListener incomingSwitchListener = (buttonView, isChecked) ->
	{
		debug();
		//Update the field and btService
		allowIncoming = isChecked;

		if (btServiceBound)
			btService.setAllowIncoming(allowIncoming);

		//Stop if there is no bluetooth available
		if (btAdapter == null)
		{
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
			btHostSwitch.setChecked(false);
			return;
		}

		if (isChecked)
		{
			if (btAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
			{
				Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
				discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
				startActivityForResult(discoverableIntent, REQUEST_ENABLE_DSC);
			}
		}
		else
		{
			if (btAdapter.isEnabled() && !startedWithBt)
				btAdapter.disable();
		}
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
				break;
			case REQUEST_START_BTPICKER:
				if (resultCode == RESULT_OK)
				{
					//Create the requested gameState from the activity result
					boolean newBoard = data.getExtras().getBoolean("newBoard");
					boolean swapped = data.getExtras().getBoolean("start")
							^ (newBoard || game.getState().board().nextPlayer() == Player.PLAYER);
					GameState.Builder builder = GameState.builder().players(new GameState.Players(Local, Bluetooth)).swapped(swapped);
					if (!newBoard)
						builder.board(game.getState().board());

					//TODO actually connect
					//btService.connect(data.getExtras().getString(BtPickerActivity.EXTRA_DEVICE_ADDRESS), builder.build());
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
}
