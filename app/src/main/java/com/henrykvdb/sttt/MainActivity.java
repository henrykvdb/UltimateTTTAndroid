package com.henrykvdb.sttt;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.flaghacker.uttt.common.Player;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import java.lang.reflect.Field;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener
{
	//Debug
	private static final String TAG = "MainActivity";

	//New bt game request code
	private static final int REQUEST_NEW_BLUETOOTH = 100;

	//Permission request codes
	private static final int REQUEST_ENABLE_BT = 200;
	private static final int REQUEST_ENABLE_DSC = 201;
	private static final int REQUEST_COARSE_LOCATION = 202;

	//Services
	private BtService btService;
	private GameService gameService;

	//Init
	private BluetoothAdapter btAdapter;
	private BoardView boardView;

	//Switch related
	private Switch btHostSwitch;
	private boolean startedWithBt;
	private static final String STARTED_WITH_BT_KEY = "STARTED_WITH_BT_KEY";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		//Set fields
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		if (savedInstanceState != null)
			startedWithBt = savedInstanceState.getBoolean(STARTED_WITH_BT_KEY, false);
		else
			startedWithBt = btAdapter != null && btAdapter.isEnabled();

		initGui();

		//If in portrait add ads
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
		{
			MobileAds.initialize(getApplicationContext(), getString(R.string.banner_ad_unit_id));
			((AdView) findViewById(R.id.adView)).loadAd(new AdRequest.Builder().build());
		}

		//Automatically start/close btService when you enable/disable bt & attempt to start the service
		registerReceiver(btStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
		enableBtService();

		//Ask user to rate the app
		if (savedInstanceState == null)
			RateDialog.rate(this, getSupportFragmentManager());
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		outState.putBoolean(STARTED_WITH_BT_KEY, startedWithBt);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onStart()
	{
		super.onStart();

		//Start GameService
		Intent intent = new Intent(this, GameService.class);
		bindService(intent, gameServiceConn, Context.BIND_AUTO_CREATE);
		startService(intent);

		//Start BtService
		enableBtService();
		startService(new Intent(this, BtService.class));
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		//Stop BtService
		if (btService != null)
			unbindService(btServerConn);
		unregisterReceiver(btStateReceiver);

		//Close GameService
		unbindService(gameServiceConn);
	}

	private void initGui()
	{
		boardView = (BoardView) findViewById(R.id.boardView);
		boardView.setNextPlayerView((TextView) findViewById(R.id.next_move_view));

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

		NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);

		MenuItem btHost = navigationView.getMenu().findItem(R.id.nav_bt_host_switch);
		btHostSwitch = (Switch) MenuItemCompat.getActionView(btHost);
		btHostSwitch.setChecked(btAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
		btHostSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
		{
			if (btAdapter != null)
			{
				gameService.setBlockIncomingBt(!isChecked);

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
			}
			else
			{
				Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
				btHostSwitch.setChecked(false);
			}
		});
	}

	public void setBtStatusMessage(String message)
	{
		final ActionBar actionBar = getSupportActionBar();
		if (null == actionBar)
			return;
		actionBar.setSubtitle(message);
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
			DialogInterface.OnClickListener dialogClickListener = (dialog, which) ->
			{
				switch (which)
				{
					case DialogInterface.BUTTON_POSITIVE:
						gameService.newLocal();
						break;

					case DialogInterface.BUTTON_NEGATIVE:
						dialog.dismiss();
						break;
				}
			};

			Util.doKeepDialog(new AlertDialog.Builder(this)
					.setTitle("Start a new game?")
					.setMessage("This wil create a new local two player game.")
					.setPositiveButton("Start", dialogClickListener)
					.setNegativeButton("Close", dialogClickListener)
					.show());
		}
		if (id == R.id.nav_local_ai)
		{
			final boolean[] swapped = new boolean[1];

			LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
			View layout = inflater.inflate(R.layout.dialog_ai, (ViewGroup) findViewById(R.id.new_ai_layout));

			RadioGroup beginner = (RadioGroup) layout.findViewById(R.id.start_radio_group);
			beginner.setOnCheckedChangeListener((group, checkedId) ->
					swapped[0] = checkedId != R.id.start_you && (checkedId == R.id.start_ai || new Random().nextBoolean()));

			DialogInterface.OnClickListener dialogClickListener = (dialog, which) ->
			{
				switch (which)
				{
					case DialogInterface.BUTTON_POSITIVE:
						gameService.newGame(GameState.builder()
								.ai(new MMBot(((SeekBar) layout.findViewById(R.id.difficulty)).getProgress()))
								.swapped(swapped[0]).build());
						break;

					case DialogInterface.BUTTON_NEGATIVE:
						dialog.dismiss();
						break;
				}
			};

			Util.doKeepDialog(new AlertDialog.Builder(this)
					.setView(layout)
					.setTitle("Start a new ai game?")
					.setPositiveButton("Start", dialogClickListener)
					.setNegativeButton("Close", dialogClickListener)
					.show());
		}
		else if (id == R.id.nav_bt_join)
		{
			pickBluetooth();
		}
		else if (id == R.id.nav_other_feedback)
		{
			String deviceInfo = "\n /** please do not remove this block, technical info: "
					+ "os version: " + System.getProperty("os.version")
					+ "(" + android.os.Build.VERSION.INCREMENTAL + "), API: " + android.os.Build.VERSION.SDK_INT;
			try
			{
				PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
				deviceInfo += ", app version: " + pInfo.versionName;
			}
			catch (PackageManager.NameNotFoundException e)
			{
				e.printStackTrace();
			}
			deviceInfo += "**/";

			Intent send = new Intent(Intent.ACTION_SENDTO);
			Uri uri = Uri.parse("mailto:" + Uri.encode("henrykdev@gmail.com") +
					"?subject=" + Uri.encode("Feedback") +
					"&body=" + Uri.encode(deviceInfo));

			send.setData(uri);
			startActivity(Intent.createChooser(send, "Send feedback"));
		}
		else if (id == R.id.nav_other_share)
		{
			Intent i = new Intent(Intent.ACTION_SEND);
			i.setType("text/plain");
			i.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(R.string.app_name_long));
			i.putExtra(Intent.EXTRA_TEXT, "Hey, let's play " + getResources().getString(R.string.app_name_long)
					+ " together! https://play.google.com/store/apps/details?id=Place.Holder"); //TODO replace
			startActivity(Intent.createChooser(i, "choose one"));
		}
		else if (id == R.id.nav_other_about)
		{
			LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
			View layout = inflater.inflate(R.layout.dialog_about, (ViewGroup) findViewById(R.id.dialog_about_layout));

			try
			{
				PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
				((TextView) layout.findViewById(R.id.versionName_view))
						.setText(getResources().getText(R.string.app_name_long) + "\nVersion " + pInfo.versionName);
			}
			catch (PackageManager.NameNotFoundException e)
			{
				((TextView) layout.findViewById(R.id.versionName_view))
						.setText(getResources().getText(R.string.app_name_long));
			}

			Util.doKeepDialog(new AlertDialog.Builder(this)
					.setTitle("About")
					.setView(layout)
					.setPositiveButton("Close", (dialog1, which) -> dialog1.dismiss())
					.show());
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
				startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			}
			else
			{
				//If we don't have the COARSE LOCATION permission, request it
				if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
						PackageManager.PERMISSION_GRANTED)
				{
					ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
							REQUEST_COARSE_LOCATION);
				}
				else
				{
					//Make the bluetooth-picker main
					Intent serverIntent = new Intent(getApplicationContext(), BtPickerActivity.class);
					startActivityForResult(serverIntent, REQUEST_NEW_BLUETOOTH);
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
			case REQUEST_ENABLE_BT:
				if (resultCode == RESULT_OK)
					pickBluetooth();
				break;
			case REQUEST_ENABLE_DSC:
				if (resultCode == RESULT_CANCELED)
					btHostSwitch.setChecked(false);
				break;
			case REQUEST_NEW_BLUETOOTH:
				if (resultCode == RESULT_OK)
				{

					//Create the requested gameState from the activity result
					boolean swapped = data.getExtras().getBoolean("start") ^ gameService.getState().board().nextPlayer() == Player.PLAYER;
					GameState.Builder builder = GameState.builder().swapped(swapped);
					if (!data.getExtras().getBoolean("newBoard")) builder.board(gameService.getState().board());
					GameState requestState = builder.build();

					if (gameService != null)
						gameService.startBtGame(data.getExtras().getString(BtPickerActivity.EXTRA_DEVICE_ADDRESS), requestState);
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

	private final BroadcastReceiver btStateReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED))
			{
				if (btAdapter.getState() == BluetoothAdapter.STATE_ON)
					enableBtService();
				if (btAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF)
				{
					gameService.turnLocal();

					if (btService != null) //TODO move/make cleaner
					{
						btService.stop();

						gameService.setBtServiceAndMain(null, MainActivity.this);
						btService = null;

						unbindService(btServerConn);
					}

					setBtStatusMessage(null);
				}
				if (btAdapter.getState() == BluetoothAdapter.STATE_OFF)
				{
					setBtStatusMessage(null);
					btHostSwitch.setChecked(false);
				}
			}
		}
	};

	private ServiceConnection btServerConn = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			Log.d(TAG, "btService Connected");
			btService = ((BtService.LocalBinder) service).getService();

			if (gameService != null)
			{
				gameService.setBtServiceAndMain(btService, MainActivity.this);
				gameService.setBlockIncomingBt(btService.blockIncoming() || btHostSwitch.isChecked());
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			Log.d(TAG, "btService Disconnected");
			btService = null;

			if (gameService != null)
				gameService.setBtServiceAndMain(null, MainActivity.this);
		}
	};

	private void enableBtService()
	{
		if (btAdapter != null && btAdapter.isEnabled() && btService == null)
			bindService(new Intent(this, BtService.class), btServerConn, Context.BIND_AUTO_CREATE);
		else
			setBtStatusMessage(null);
	}

	private ServiceConnection gameServiceConn = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			Log.d(TAG, "GameService Connected");

			gameService = ((GameService.LocalBinder) service).getService();
			gameService.setBoardView(boardView);

			if (btService != null)
				gameService.setBtServiceAndMain(btService, MainActivity.this);
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			Log.d(TAG, "GameService Disconnected");
			gameService = null;
			finish();
		}
	};
}
