package com.henrykvdb.sttt;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
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
import android.support.v4.content.LocalBroadcastManager;
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

import java.lang.reflect.Field;

import static com.henrykvdb.sttt.GameService.Source.Bluetooth;
import static com.henrykvdb.sttt.GameService.Source.Local;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener
{
	//Services
	private BtService btService;
	private GameService gameService;

	//UI
	private BoardView boardView;
	private Switch btHostSwitch;

	//Other
	private BluetoothAdapter btAdapter;
	private boolean startedWithBt;
	private Dialogs dialogs;

	//Receiver
	private BroadcastReceiver uiReceiver = new BroadcastReceiver()
	{
		public Toast toast;

		@Override
		public void onReceive(Context context, Intent intent)
		{
			switch (intent.getStringExtra(Constants.EVENT_TYPE))
			{
				case Constants.TYPE_TITLE:
					setTitle(intent.getStringExtra(Constants.DATA_STRING));
					break;
				case Constants.TYPE_SUBTITLE:
					setSubtitle(intent.getStringExtra(Constants.DATA_STRING));
					break;
				case Constants.TYPE_TOAST:
					if (toast == null)
						toast = Toast.makeText(context, "", Toast.LENGTH_SHORT);
					toast.setText(intent.getStringExtra(Constants.DATA_STRING));
					toast.show();
					break;
				case Constants.TYPE_ALLOW_INCOMING_BT:
					boolean allow = intent.getBooleanExtra(Constants.DATA_BOOLEAN_ALLOW, true);
					boolean silent = intent.getBooleanExtra(Constants.DATA_BOOLEAN_SILENT, false);

					if (!silent)
						btHostSwitch.setChecked(allow);
					else
					{
						btHostSwitch.setOnCheckedChangeListener(null);
						btHostSwitch.setChecked(false);
						btService.setBlockIncoming(!allow);
						btHostSwitch.setOnCheckedChangeListener(incomingSwitchListener);
					}
					break;
				default:
					new RuntimeException();
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		//Set fields
		dialogs = new Dialogs(this);
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		startedWithBt = (savedInstanceState != null)
				? savedInstanceState.getBoolean(Constants.STARTED_WITH_BT_KEY, false)
				: btAdapter != null && btAdapter.isEnabled();
		boardView = (BoardView) findViewById(R.id.boardView);
		boardView.setNextPlayerView((TextView) findViewById(R.id.next_move_view));

		//Setup the drawer
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
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
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
				this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
		drawer.addDrawerListener(toggle);
		toggle.syncState();

		//Setup the btHostSwitch
		NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);
		btHostSwitch = (Switch) MenuItemCompat.getActionView(navigationView.getMenu().findItem(R.id.nav_bt_host_switch));
		btHostSwitch.setOnCheckedChangeListener(incomingSwitchListener);
		btHostSwitch.setChecked(btAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);

		//Add ads in portrait
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
		{
			MobileAds.initialize(getApplicationContext(), getString(R.string.banner_ad_unit_id));
			((AdView) findViewById(R.id.adView)).loadAd(new AdRequest.Builder().build());
		}

		//Ask user to rate the app
		if (savedInstanceState == null)
			dialogs.rate();
	}

	public void setSubtitle(String message)
	{
		final ActionBar actionBar = getSupportActionBar();

		if (actionBar != null)
			actionBar.setSubtitle(message);
	}

	public void setTitle(String message)
	{
		final ActionBar actionBar = getSupportActionBar();

		if (actionBar != null)
			actionBar.setTitle(message);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		outState.putBoolean(Constants.STARTED_WITH_BT_KEY, startedWithBt);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onStart()
	{
		super.onStart();

		//Register the uiReceiver
		LocalBroadcastManager.getInstance(this).registerReceiver((uiReceiver), new IntentFilter(Constants.EVENT_UI));

		//Start GameService
		bindService(new Intent(this, GameService.class), gameServiceConn, Context.BIND_AUTO_CREATE);
		startService(new Intent(this, GameService.class));

		//Start BtService
		bindService(new Intent(this, BtService.class), btServerConn, Context.BIND_AUTO_CREATE);
		startService(new Intent(this, BtService.class));
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(uiReceiver);
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		unbindService(btServerConn);
		unbindService(gameServiceConn);
	}

	private ServiceConnection btServerConn = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			Log.d("MainActivity", "btService Connected");
			btService = ((BtService.LocalBinder) service).getService();

			if (gameService != null) //TODO cleanup
			{
				gameService.setBtService(btService);
				btService.setup(gameService, dialogs);

				gameService.setBoardView(boardView);

				boolean blockIncoming = btService.blockIncoming() || !btHostSwitch.isChecked();
				if (btService.blockIncoming() != blockIncoming)
					btService.setBlockIncoming(blockIncoming);
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			Log.d("MainActivity", "btService Disconnected");
			finish(); //TODO better handling
		}
	};

	private ServiceConnection gameServiceConn = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			Log.d("MainActivity", "GameService Connected");

			gameService = ((GameService.LocalBinder) service).getService();

			if (btService != null) //TODO cleanup
			{
				gameService.setBtService(btService);
				btService.setup(gameService, dialogs);

				gameService.setBoardView(boardView);

				boolean blockIncoming = btService.blockIncoming() || !btHostSwitch.isChecked();
				if (btService.blockIncoming() != blockIncoming)
					btService.setBlockIncoming(blockIncoming);
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			Log.d("MainActivity", "GameService Disconnected");
			finish(); //TODO better handling
		}
	};

	private CompoundButton.OnCheckedChangeListener incomingSwitchListener = (buttonView, isChecked) ->
	{
		if (btAdapter != null)
		{
			if (btService != null)
				btService.setBlockIncoming(!isChecked);

			if (isChecked)
			{
				if (btAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
				{
					Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
					discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
					startActivityForResult(discoverableIntent, Constants.REQUEST_ENABLE_DSC);
				}
			}
			else
			{
				if (btAdapter.isEnabled() && !startedWithBt)
					btAdapter.disable();
			}
		}
		else
		{
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
			btHostSwitch.setChecked(false);
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
			gameService.undo(false);
			return true;
		}
		return false;
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item)
	{
		// Handle navigation view item clicks here.
		int id = item.getItemId();

		if (id == R.id.nav_local_human)
		{
			dialogs.newLocal(start ->
			{
				if (start) gameService.newLocal();
			});
		}
		if (id == R.id.nav_local_ai)
		{
			dialogs.newAi(gs -> gameService.newGame(gs));
		}
		else if (id == R.id.nav_bt_join)
		{
			pickBluetooth();
		}
		else if (id == R.id.nav_other_feedback)
		{
			Util.sendFeedback(this);
		}
		else if (id == R.id.nav_other_share)
		{
			Util.share(this);
		}
		else if (id == R.id.nav_other_about)
		{
			dialogs.about();
		}

		if (id != R.id.nav_bt_host_switch)
			((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawer(GravityCompat.START);

		return true;
	}

	private void pickBluetooth()
	{
		// If the adapter is null, then Bluetooth is not supported
		if (btAdapter == null)
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
		else
		{
			// If BT is not on, request that it be enabled first.
			if (!btAdapter.isEnabled())
			{
				Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableIntent, Constants.REQUEST_ENABLE_BT);
			}
			else
			{
				//If we don't have the COARSE LOCATION permission, request it
				if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
						PackageManager.PERMISSION_GRANTED)
				{
					ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
							Constants.REQUEST_COARSE_LOCATION);
				}
				else
				{
					//Make the bluetooth-picker main
					Intent serverIntent = new Intent(getApplicationContext(), BtPickerActivity.class);
					startActivityForResult(serverIntent, Constants.REQUEST_START_BTPICKER);
				}
			}
		}
	}

	@Override
	public void onBackPressed()
	{
		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		if (drawer.isDrawerOpen(GravityCompat.START))
			drawer.closeDrawer(GravityCompat.START);
		else
			super.onBackPressed();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode)
		{
			case Constants.REQUEST_ENABLE_BT:
				if (resultCode == RESULT_OK)
					pickBluetooth();
				break;
			case Constants.REQUEST_ENABLE_DSC:
				if (resultCode == RESULT_CANCELED)
					btHostSwitch.setChecked(false);
				break;
			case Constants.REQUEST_START_BTPICKER:
				if (resultCode == RESULT_OK)
				{
					//Create the requested gameState from the activity result
					boolean newBoard = data.getExtras().getBoolean("newBoard");
					boolean swapped = data.getExtras().getBoolean("start")
							^ (newBoard || gameService.getState().board().nextPlayer() == Player.PLAYER);
					GameState.Builder builder = GameState.builder().players(new GameState.Players(Local, Bluetooth)).swapped(swapped);
					if (!newBoard)
						builder.board(gameService.getState().board());

					btService.connect(data.getExtras().getString(BtPickerActivity.EXTRA_DEVICE_ADDRESS), builder.build());
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
			case Constants.REQUEST_COARSE_LOCATION:
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
					pickBluetooth();
				break;
		}

		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}
}
