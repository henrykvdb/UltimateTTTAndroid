package com.henrykvdb.sttt;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class BtPickerActivity extends Activity
{
	public static String EXTRA_DEVICE_ADDRESS = "device_address";

	private BluetoothAdapter btAdapter;

	private List<BluetoothDevice> devices = new ArrayList<>();
	private LinearLayout devicesLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Setup the window
		setContentView(R.layout.dialog_bt_join);

		// Set result CANCELED in case the user backs out
		setResult(Activity.RESULT_CANCELED);

		// Set fields
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		devicesLayout = (LinearLayout) findViewById(R.id.devices);

		// Register filters
		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		registerReceiver(mReceiver, filter);

		doDiscovery();
	}

	private void updateLayout()
	{
		//Clear layout
		devicesLayout.removeAllViews();

		//Add all the devices
		if (devices.size() > 0)
		{
			for (BluetoothDevice device : devices)
			{
				TextView view = new TextView(this);
				view.setText(device.getName() + "\n" + device.getAddress());

				view.setOnClickListener(v ->
				{
					// Cancel discovery because it's costly and we're about to connect
					btAdapter.cancelDiscovery();

					// Get the device MAC address, which is the last 17 chars in the View
					String info = ((TextView) v).getText().toString();
					String address = info.substring(info.length() - 17);

					// Create the result Intent and include the MAC address
					Intent intent = new Intent();
					intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

					// Set result and finish this Activity
					setResult(Activity.RESULT_OK, intent);
					finish();
				});

				devicesLayout.addView(view);
			}
		}
		else
		{
			//There are no devices in the list
			TextView view = new TextView(this);
			view.setText(getResources().getText(R.string.none_found).toString());
			devicesLayout.addView(view);
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		// Make sure we're not doing discovery anymore
		if (btAdapter != null)
			btAdapter.cancelDiscovery();

		// Unregister broadcast listeners
		this.unregisterReceiver(mReceiver);
	}

	private void doDiscovery()
	{
		Log.d("NewBluetoothActivity", "doDiscovery()");

		//Clear the devices list
		devices.clear();
		updateLayout();

		// If we're already discovering, stop it
		if (btAdapter.isDiscovering())
			btAdapter.cancelDiscovery();

		// Request discover from BluetoothAdapter
		btAdapter.startDiscovery();
	}

	private final BroadcastReceiver mReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();

			if (BluetoothDevice.ACTION_FOUND.equals(action))
			{
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

				boolean add = true;
				if (device.getName() != null)
					for (BluetoothDevice d : devices)
						add = !d.getAddress().equals(device.getAddress()) && add;

				if (add)
				{
					devices.add(device);
					updateLayout();
				}
			}
			else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action))
			{
				finish();
			}
			else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
			{
				doDiscovery();
			}
		}
	};
}
