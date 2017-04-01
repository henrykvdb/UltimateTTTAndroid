package com.henrykvdb.uttt;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.Random;
import java.util.Set;

public class NewBluetoothActivity extends Activity
{
	/**
	 * Return Intent extra
	 */
	public static String EXTRA_DEVICE_ADDRESS = "device_address";

	private BluetoothAdapter btAdapter;
	private ArrayAdapter<String> newDevicesArrayAdapter;
	private ProgressBar spinner;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Setup the window
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.new_bluetooth);

		// Set result CANCELED in case the user backs out
		setResult(Activity.RESULT_CANCELED);

		spinner = (ProgressBar) findViewById(R.id.loading_spinner);

		// Initialize the button to perform device discovery
		Button scanButton = (Button) findViewById(R.id.button_scan);
		scanButton.setOnClickListener(v -> {
			doDiscovery();
			scanButton.setEnabled(false);
			spinner.setVisibility(View.VISIBLE);
			new java.util.Timer().schedule(
					new java.util.TimerTask()
					{
						@Override
						public void run()
						{
							runOnUiThread(() -> {
								scanButton.setEnabled(true);
								spinner.setVisibility(View.INVISIBLE);
							});
						}
					}, 15000
			);
		});

		// Initialize array adapters. One for already paired devices and one for newly discovered devices
		ArrayAdapter<String> pairedDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.device_name);
		newDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.device_name);

		// Find and set up the ListView for paired devices
		ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
		pairedListView.setAdapter(pairedDevicesArrayAdapter);
		pairedListView.setOnItemClickListener(mDeviceClickListener);

		// Find and set up the ListView for newly discovered devices
		ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
		newDevicesListView.setAdapter(newDevicesArrayAdapter);
		newDevicesListView.setOnItemClickListener(mDeviceClickListener);

		// Register filters
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		this.registerReceiver(mReceiver, filter);
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		this.registerReceiver(mReceiver, filter);
		filter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
		this.registerReceiver(mReceiver, filter);

		// Get the local Bluetooth adapter
		btAdapter = BluetoothAdapter.getDefaultAdapter();

		// Get a set of currently paired devices
		Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();

		// If there are paired devices, add each one to the ArrayAdapter
		if (pairedDevices.size() > 0)
		{
			findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
			for (BluetoothDevice device : pairedDevices)
				pairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
		}
		else
		{
			pairedDevicesArrayAdapter.add(getResources().getText(R.string.none_paired).toString());
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

	/**
	 * Start device discover with the BluetoothAdapter
	 */
	private void doDiscovery()
	{
		Log.d("NewBluetoothActivity", "doDiscovery()");

		// Indicate scanning in the title
		setProgressBarIndeterminateVisibility(true);
		setTitle(R.string.scanning);

		// Turn on sub-title for new devices
		findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

		// If we're already discovering, stop it
		if (btAdapter.isDiscovering())
		{
			btAdapter.cancelDiscovery();
		}

		// Request discover from BluetoothAdapter
		btAdapter.startDiscovery();
	}

	/**
	 * The on-click listener for all devices in the ListViews
	 */
	private AdapterView.OnItemClickListener mDeviceClickListener
			= new AdapterView.OnItemClickListener()
	{
		public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3)
		{
			// Cancel discovery because it's costly and we're about to connect
			btAdapter.cancelDiscovery();

			// Get the device MAC address, which is the last 17 chars in the View
			String info = ((TextView) v).getText().toString();
			String address = info.substring(info.length() - 17);

			// Create the result Intent and include the MAC address
			Intent intent = new Intent();
			intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

			int beginner = ((RadioGroup) findViewById(R.id.start_radio_group)).getCheckedRadioButtonId();
			boolean start = new Random().nextBoolean();
			if (beginner == R.id.start_you) start = true;
			else if (beginner == R.id.start_other) start = false;

			intent.putExtra("newBoard",((RadioButton)findViewById(R.id.board_new)).isChecked());
			intent.putExtra("start",start);

			// Set result and finish this Activity
			setResult(Activity.RESULT_OK, intent);
			finish();
		}
	};

	/**
	 * The BroadcastReceiver that listens for discovered devices and changes the title when discovery is finished
	 */
	private final BroadcastReceiver mReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();

			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action))
			{
				// Get the BluetoothDevice object from the Intent
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				// If it's already paired, skip it, because it's been listed already
				if (device.getBondState() != BluetoothDevice.BOND_BONDED)
				{
					newDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
				}
			}
			else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action))
			{
				finish();
			}
			else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
			{
				setProgressBarIndeterminateVisibility(false);
				setTitle(R.string.select_device);
				if (newDevicesArrayAdapter.getCount() == 0)
				{
					String noDevices = getResources().getText(R.string.none_found).toString();
					newDevicesArrayAdapter.add(noDevices);
				}
			}
		}
	};

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
	}
}
