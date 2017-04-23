package com.henrykvdb.sttt;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
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
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
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
import android.view.WindowManager;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.flaghacker.uttt.common.Board;
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
	private Switch btHostSwitch;

	//Other
	private GameState requestState;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		//Init fields and gui
		initGui();

		//If in portrait add ads
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
		{
			MobileAds.initialize(getApplicationContext(), getString(R.string.banner_ad_unit_id));
			((AdView) findViewById(R.id.adView)).loadAd(new AdRequest.Builder().build());
		}

		//Automatically start/close btService when you enable/disable bt
		registerReceiver(btStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

		//Start Services
		enableBtService();

		//Ask user to rate the app
		if (savedInstanceState == null)
			RateDialog.rate(this, getSupportFragmentManager());
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
		btAdapter = BluetoothAdapter.getDefaultAdapter();
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
		catch (IllegalAccessException | NoSuchFieldException e)
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
				if (btHostSwitch.isChecked())
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
					if (btAdapter.isEnabled())
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

	private void setBtStatusMessage(String message)
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
			if (gameService.getState().players().contains(GameService.Source.Bluetooth))
			{
				if (btService != null)
					btService.requestUndo(false);
				else
					gameService.turnLocal();
			}
			else
			{
				gameService.undo();
			}
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

			doKeepDialog(new AlertDialog.Builder(this)
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

			doKeepDialog(new AlertDialog.Builder(this)
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
			Uri uri = Uri.parse("mailto:" + Uri.encode("dummy@gmail.com") + //TODO replace
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

			doKeepDialog(new AlertDialog.Builder(this)
					.setTitle("About")
					.setView(layout)
					.setPositiveButton("Close", (dialog1, which) -> dialog1.dismiss())
					.show());
		}

		if (id != R.id.nav_bt_host_switch)
			((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawer(GravityCompat.START);

		return true;
	}

	// Prevent dialog dismiss when orientation changes
	public static void doKeepDialog(Dialog dialog)
	{
		try
		{
			WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
			lp.copyFrom(dialog.getWindow().getAttributes());
			lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
			lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
			dialog.getWindow().setAttributes(lp);
		}
		catch (Throwable t)
		{
			//NOP
		}
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
					//Make the bluetooth-picker activity
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
					boolean swapped = data.getExtras().getBoolean("start")
							^ gameService.getState().board().nextPlayer() == Player.PLAYER;

					GameState.Builder builder = GameState.builder().bt(btHandler).swapped(swapped);
					if (!data.getExtras().getBoolean("newBoard"))
						builder.board(gameService.getState().board());
					requestState = builder.build();

					if (!requestState.board().isDone())
						btService.connect(data.getExtras().getString(BtPickerActivity.EXTRA_DEVICE_ADDRESS));
					else
						Log.d(TAG, "You can't send a finished board");
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

					if (btService != null)
					{
						unbindService(btServerConn);
						btService = null;
					}

					setBtStatusMessage(null);
				}
				if (btAdapter.getState() == BluetoothAdapter.STATE_OFF)
					setBtStatusMessage(null);
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

			btService.setup(gameService, btHandler);
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			Log.d(TAG, "btService Disconnected");
			btService = null;
		}
	};

	private void enableBtService()
	{
		if (btAdapter != null && btAdapter.isEnabled() && btService == null)
		{
			bindService(new Intent(this, BtService.class), btServerConn, Context.BIND_AUTO_CREATE);
		}
		else
		{
			setBtStatusMessage(null);
		}
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
				btService.setup(gameService, btHandler);
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			Log.d(TAG, "GameService Disconnected");
			gameService = null;
			finish();
		}
	};

	/**
	 * The Handler that gets information back from the btService
	 */
	private final Handler btHandler = new Handler()
	{
		Activity activity = MainActivity.this;
		String connectedDeviceName;

		@Override
		public void handleMessage(Message msg)
		{
			if (msg.what == BtService.Message.STATE_CHANGE.ordinal())
			{
				BtService.State state = (BtService.State) msg.getData().getSerializable(BtService.STATE);

				if (state == BtService.State.CONNECTED)
					setBtStatusMessage("connected to " + connectedDeviceName);
				else if (state == BtService.State.CONNECTING)
					setBtStatusMessage(null);
				else if (state == BtService.State.NONE)
					setBtStatusMessage(null);
				else if (state == BtService.State.LISTEN)
					setBtStatusMessage(null);
			}
			else if (msg.what == BtService.Message.SEND_BOARD_UPDATE.ordinal())
			{
				Board board = (Board) msg.getData().getSerializable("myBoard");

				if (btService != null)
					btService.sendBoard(board);

				if (board != null)
					Log.d(TAG, "boardUpdate: " + board.getLastMove());
			}
			else if (msg.what == BtService.Message.RECEIVE_UNDO.ordinal())
			{
				boolean force = (boolean) msg.obj;

				if (!force)
				{
					if (askDialog == null || !askDialog.isShowing())
					{
						askUser(connectedDeviceName + " requests to undo the last move, do you accept?", allow ->
						{
							if (allow && btService != null)
							{
								gameService.undo();
								btService.updateLocalBoard(gameService.getState().board());
								btService.requestUndo(true);
							}
						});
					}
				}
				else
				{
					gameService.undo();
					btService.updateLocalBoard(gameService.getState().board());
				}
			}
			else if (msg.what == BtService.Message.SEND_SETUP.ordinal())
			{
				if (btService != null)
					btService.sendState(requestState, false);
			}
			else if (msg.what == BtService.Message.RECEIVE_SETUP.ordinal())
			{
				Bundle data = msg.getData();
				boolean swapped = !data.getBoolean("swapped");
				Board board = (Board) data.getSerializable("board");

				if (!data.getBoolean("force"))
				{
					askUser(connectedDeviceName + " challenges you for a duel, do you accept?", allow ->
					{
						if (btService != null)
						{
							if (allow)
							{
								requestState = GameState.builder().bt(btHandler).swapped(swapped).board(board).build();
								btService.updateLocalBoard(requestState.board());
								btService.sendState(requestState, true);
								gameService.newGame(requestState);
							}
							else
							{
								btService.start();
							}
						}
					});
				}
				else
				{
					requestState = GameState.builder().bt(btHandler).swapped(swapped).board(board).build();
					btService.updateLocalBoard(requestState.board());
					gameService.newGame(requestState);
				}
			}
			else if (msg.what == BtService.Message.DEVICE_NAME.ordinal())
			{
				connectedDeviceName = (String) msg.obj;
			}
			else if (msg.what == BtService.Message.TOAST.ordinal() && activity != null)
			{
				Toast.makeText(activity, (String) msg.obj, Toast.LENGTH_SHORT).show();
			}
			else if (msg.what == BtService.Message.ERROR_TOAST.ordinal() && activity != null)
			{
				gameService.turnLocal();

				Toast.makeText(activity, (String) msg.obj, Toast.LENGTH_SHORT).show();
			}
		}
	};

	private interface CallBack<T>
	{
		void callback(T t);
	}

	AlertDialog askDialog;

	private void askUser(String message, CallBack<Boolean> callBack)
	{
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
				.show();

		doKeepDialog(askDialog);
	}
}
