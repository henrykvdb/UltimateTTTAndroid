package com.henrykvdb.uttt;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.ViewDragHelper;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;

public class MainActivity extends AppCompatActivity
		implements NavigationView.OnNavigationItemSelectedListener
{
	TextView statusView;

	private static final String STATE_KEY = "game";

	private Game game;

	private static final int REQUEST_NEW_LOCAL = 100;
	private static final int REQUEST_NEW_BLUETOOTH = 101;

	private static final int REQUEST_ENABLE_BT = 200;
	private static final int REQUEST_COARSE_LOCATION = 201;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initGui();

		if (savedInstanceState != null)
		{
			GameState state = (GameState) savedInstanceState.getSerializable(STATE_KEY);
			game = new Game(state, (BoardView) findViewById(R.id.boardView), new AndroidBot());
		}
		else
		{
			closeGame();
			AndroidBot androidBot = new AndroidBot();
			game = Game.newGame((BoardView) findViewById(R.id.boardView), androidBot, androidBot);
		}
	}

	private void initGui()
	{
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		try
		{
			Field mDragger = drawer.getClass().getDeclaredField(
					"mLeftDragger");
			mDragger.setAccessible(true);
			ViewDragHelper draggerObj = (ViewDragHelper) mDragger
					.get(drawer);
			Field mEdgeSize = draggerObj.getClass().getDeclaredField(
					"mEdgeSize");
			mEdgeSize.setAccessible(true);
			int edge = mEdgeSize.getInt(draggerObj);
			mEdgeSize.setInt(draggerObj, edge * 4);
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

		statusView = (TextView) findViewById(R.id.statusView);
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
	public boolean onNavigationItemSelected(MenuItem item)
	{
		// Handle navigation view item clicks here.
		int id = item.getItemId();

		if (id == R.id.nav_local)
		{
			Intent serverIntent = new Intent(getApplicationContext(), NewLocalActivity.class);
			startActivityForResult(serverIntent, REQUEST_NEW_LOCAL);
		}
		else if (id == R.id.nav_bluetooth)
		{
			pickBluetooth();
		}

		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		drawer.closeDrawer(GravityCompat.START);
		return true;
	}

	private void pickBluetooth()
	{
		// Get local Bluetooth adapter
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// If the adapter is null, then Bluetooth is not supported
		if (mBluetoothAdapter == null)
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
		else
		{
			// If BT is not on, request that it be enabled first.
			if (! mBluetoothAdapter.isEnabled())
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
					Intent serverIntent = new Intent(getApplicationContext(), NewBluetoothActivity.class);
					startActivityForResult(serverIntent, REQUEST_NEW_BLUETOOTH);
				}
			}
		}

		if (!isBtServiceOn)
		{
			stopBtService();
			startBtService();
		}
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
			case REQUEST_NEW_LOCAL:
				if (resultCode == RESULT_OK)
				{
					closeGame();
					GameState gs = (GameState) data.getSerializableExtra("GameState");
					game = Game.newGame(gs, (BoardView) findViewById(R.id.boardView), new AndroidBot());
					statusView.setText("Local: " + game.getType());
				}
				break;
			case REQUEST_NEW_BLUETOOTH:
				if (resultCode == RESULT_OK)
				{
					btService.connect(data.getExtras().getString(NewBluetoothActivity.EXTRA_DEVICE_ADDRESS));
					statusView.setText("Local: " + game.getType());
					//btService.write("test".getBytes());
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

	BtService btService;
	boolean isBtServiceOn;
	protected ServiceConnection mServerConn = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			Log.d("LOGTAG", "onServiceConnected");
			BtService.LocalBinder binder = (BtService.LocalBinder) service;
			btService = binder.getService();
			btService.setHandler(mHandler);
			isBtServiceOn = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			Log.d("LOGTAG", "onServiceDisconnected");
			isBtServiceOn = false;
		}
	};

	public void startBtService()
	{
		// mContext is defined upper in code, I think it is not necessary to explain what is it
		Intent intent = new Intent(this, BtService.class);
		bindService(intent, mServerConn, Context.BIND_AUTO_CREATE);
		startService(intent);
	}

	public void stopBtService()
	{
		if (btService != null)
			btService.stop();

		stopService(new Intent(this, BtService.class));
		unbindService(mServerConn);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		closeGame();
		super.onSaveInstanceState(outState);
		outState.putSerializable(STATE_KEY, game.getState());
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		if (game != null && ! game.getState().running())
			game.run();

		if (btService != null)
			if (btService.getState() == BtService.State.NONE)
				btService.start();
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		closeGame();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		closeGame();
		stopBtService();
	}

	private void closeGame()
	{
		if (game != null)
			game.close();
	}

	/**
	 * The Handler that gets information back from the BluetoothService
	 */
	private final Handler mHandler = new Handler()
	{
		String connectedDeviceName;

		@Override
		public void handleMessage(Message msg)
		{
			Activity activity = getActivity();

			if (msg.what == BtService.Message.STATE_CHANGE.ordinal())
			{
				switch (btService.getState())
				{
					case CONNECTED:
						statusView.setText("Bluetooth: connected to " + connectedDeviceName);
						break;
					case CONNECTING:
						statusView.setText("Bluetooth: connecting...");
						break;
					case LISTEN:
						statusView.setText("Bluetooth: listening");
					case NONE:
						statusView.setText("Bluetooth: not connected");
						break;
				}
			}
			else if (msg.what == BtService.Message.WRITE.ordinal())
			{
				byte[] writeBuf = (byte[]) msg.obj;
				// construct a string from the buffer
				String writeMessage = new String(writeBuf);
				Log.d("MACT", "write: " + writeMessage);
			}
			else if (msg.what == BtService.Message.READ.ordinal())
			{
				byte[] readBuf = (byte[]) msg.obj;
				// construct a string from the valid bytes in the buffer
				String readMessage = new String(readBuf, 0, msg.arg1);
				Log.d("MACT", "read: " + readMessage);
			}
			else if (msg.what == BtService.Message.DEVICE_NAME.ordinal())
			{
				connectedDeviceName = (String) msg.obj;
				if (null != activity)
					Toast.makeText(activity, "Connected to " + connectedDeviceName, Toast.LENGTH_SHORT).show();
			}
			else if (msg.what == BtService.Message.TOAST.ordinal())
			{
				if (null != activity)
					Toast.makeText(activity, (String) msg.obj, Toast.LENGTH_SHORT).show();
			}
		}
	};

	private MainActivity getActivity()
	{
		return this;
	}
}
