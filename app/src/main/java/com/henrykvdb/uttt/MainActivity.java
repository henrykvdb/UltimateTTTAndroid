package com.henrykvdb.uttt;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
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
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Player;

import java.lang.reflect.Field;
import java.util.Collections;

public class MainActivity extends AppCompatActivity
		implements NavigationView.OnNavigationItemSelectedListener
{
	//Debug
	private static final String TAG = "MainActivity";

	//New game picker activity launch codes
	private static final int REQUEST_NEW_LOCAL = 100;
	private static final int REQUEST_NEW_BLUETOOTH = 101;

	//Permission request codes
	private static final int REQUEST_ENABLE_BT = 200;
	private static final int REQUEST_ENABLE_DSC = 201;
	private static final int REQUEST_COARSE_LOCATION = 202;

	//Services
	private BtService btService;
	private GameService gameService;

	//Other
	private BluetoothAdapter btAdapter;
	private BoardView boardView;
	private Switch btHostSwitch;
	private GameState requestState;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initGui();

		//Automatically start/close btService when you enable/disable bt
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		registerReceiver(btStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
		btAdapter.disable();

		//Start Services
		startGameService();
		startBtService();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		closeBtService();
		closeGameService();
		unregisterReceiver(btStateReceiver);
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
		catch (IllegalAccessException | NoSuchFieldException e)
		{
			e.printStackTrace();
		}

		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
				this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
		drawer.setDrawerListener(toggle);
		toggle.syncState();

		NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);

		MenuItem btHost = navigationView.getMenu().findItem(R.id.nav_bt_host_switch);
		btHostSwitch = (Switch) MenuItemCompat.getActionView(btHost);
		btHostSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
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
		if (item.getItemId() == R.id.action_undo && gameService != null)
		{
			if (gameService.getState().players().contains(GameService.Source.Bluetooth))
			{
				if (btService != null)
					btService.requestUndo(false);
				else gameService.turnLocal();
			}
			else gameService.undo();
			return true;
		}
		return false;
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item)
	{
		// Handle navigation view item clicks here.
		int id = item.getItemId();

		if (id == R.id.nav_local)
		{
			Intent serverIntent = new Intent(getApplicationContext(), NewLocalActivity.class);
			startActivityForResult(serverIntent, REQUEST_NEW_LOCAL);
		}
		else if (id == R.id.nav_bt_join)
		{
			pickBluetooth();
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
					startBtService();

					//Make the bluetooth-picker activity //TODO kill if bluetooth gets turned off
					Intent serverIntent = new Intent(getApplicationContext(), NewBluetoothActivity.class);
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
			case REQUEST_NEW_LOCAL:
				if (resultCode == RESULT_OK)
				{
					if (gameService != null)
					{
						GameState gs = (GameState) data.getSerializableExtra("GameState");
						gameService.newGame(gs);
					}
					else
					{
						Log.e(TAG, "GameService == null @ case REQUEST_NEW_LOCAL:");
					}
				}
				break;
			case REQUEST_NEW_BLUETOOTH:
				if (resultCode == RESULT_OK)
				{
					boolean swapped = data.getExtras().getBoolean("start")
							^ gameService.getState().board().nextPlayer() == Player.PLAYER;
					requestState = new GameState(btHandler, swapped);

					if (!data.getExtras().getBoolean("newBoard"))
						requestState.setBoards(Collections.singletonList(gameService.getState().board()));

					btService.connect(data.getExtras().getString(NewBluetoothActivity.EXTRA_DEVICE_ADDRESS));
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
					startBtService();
				if (btAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF)
					closeBtService();
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
			Log.d(TAG, "bt: onServiceConnected");
			BtService.LocalBinder binder = (BtService.LocalBinder) service;
			btService = binder.getService();

			if (gameService != null)
				btService.setGame(gameService);

			//Give the service a handler and start
			btService.setHandler(btHandler);
			btService.start();
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			Log.d(TAG, "bt: onServiceDisconnected");

			closeBtService();
			startBtService();
		}
	};

	private void closeBtService()
	{
		try
		{
			if (btService != null)
				btService.stop();

			stopService(new Intent(this, BtService.class));
			unbindService(btServerConn);
			btService = null;

			gameService.turnLocal();
		}
		catch (Throwable t)
		{
			Log.d(TAG, "Error closing btService");
		}
	}

	private void startBtService()
	{
		if (btAdapter != null)
		{
			if (btAdapter.isEnabled())
			{
				if (btService == null)
				{
					Intent intent = new Intent(this, BtService.class);
					startService(intent);
					bindService(intent, btServerConn, Context.BIND_AUTO_CREATE);
				}
			}
			else setBtStatusMessage(null);
		}
		else setBtStatusMessage(null);
	}

	private ServiceConnection gameServiceConn = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			Log.d(TAG, "gameService Connected");
			GameService.LocalBinder binder = (GameService.LocalBinder) service;
			gameService = binder.getService();

			//Setup a new local game
			gameService.setBoardView(boardView);
			gameService.newLocal();

			if (btService != null)
				btService.setGame(gameService);
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			Log.d(TAG, "gameService Disconnected");

			closeGameService();
			startGameService();
		}
	};

	private void startGameService()
	{
		if (gameService == null)
		{
			Intent intent = new Intent(this, GameService.class);
			startService(intent);
			bindService(intent, gameServiceConn, Context.BIND_AUTO_CREATE);
		}
	}

	private void closeGameService()
	{
		try
		{
			if (gameService != null)
				gameService.close();

			stopService(new Intent(this, GameService.class));
			unbindService(gameServiceConn);
			gameService = null;
		}
		catch (Throwable t)
		{
			Log.d(TAG, "Error closing btService");
		}
	}

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
					//else if (state == BtService.State.CONNECTING)
					//setBtStatusMessage("connecting...");
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

				Log.d(TAG, "boardUpdate: " + board.getLastMove());
			}
			else if (msg.what == BtService.Message.SEND_SETUP.ordinal())
			{
				if (btService != null)
					btService.sendState(requestState, false);
			}
			else if (msg.what == BtService.Message.RECEIVE_UNDO.ordinal())
			{
				boolean force = (boolean) msg.obj;

				if (!force)
				{
					askUser(connectedDeviceName + " requests to undo the last move, do you accept?", allow -> {
						if (allow && btService != null && gameService != null)
						{
							gameService.undo();
							btService.updateLocalBoard(gameService.getState().board());
							btService.requestUndo(true);
						}
					});
				}
				else gameService.undo();
			}
			else if (msg.what == BtService.Message.RECEIVE_SETUP.ordinal())
			{
				Bundle data = msg.getData();
				boolean swapped = !data.getBoolean("swapped");
				Board board = (Board) data.getSerializable("board");

				if (!data.getBoolean("force"))
				{
					askUser(connectedDeviceName + " challenges you for a duel, do you accept?", allow -> {
						if (btService != null)
						{
							if (allow)
							{
								requestState = new GameState(new GameState(swapped, Collections.singletonList(board)), btHandler);
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
					gameService.newGame(new GameState(new GameState(swapped, Collections.singletonList(board)), btHandler));
				}
			}
			else if (msg.what == BtService.Message.DEVICE_NAME.ordinal())
			{
				connectedDeviceName = (String) msg.obj;
				if (activity != null)
					Toast.makeText(activity, "Connected to " + connectedDeviceName, Toast.LENGTH_SHORT).show();
			}
			else if (msg.what == BtService.Message.ERROR_TOAST.ordinal() && activity != null)
			{
				if (gameService != null)
					gameService.turnLocal();

				Toast.makeText(activity, (String) msg.obj, Toast.LENGTH_SHORT).show();
			}
		}
	};

	private interface CallBack<T>
	{
		void callback(T t);
	}

	private void askUser(String message, CallBack<Boolean> callBack)
	{
		DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
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

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(message)
				.setPositiveButton("Yes", dialogClickListener)
				.setNegativeButton("No", dialogClickListener)
				.show();
	}
}
