package com.henrykvdb.uttt;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import java.util.Set;

public class NewBluetoothActivity extends Activity
{
	private RadioButton radioHost;
	private CheckBox checkBoxVisible;
	private LinearLayout layoutHost;
	private LinearLayout layoutJoin;
	/**
	 * Return Intent extra
	 */
	public static String EXTRA_DEVICE_ADDRESS = "device_address";

	/**
	 * Member fields
	 */
	private BluetoothAdapter mBtAdapter;

	/**
	 * Newly discovered devices
	 */
	private ArrayAdapter<String> mNewDevicesArrayAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Setup the window
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.new_bluetooth);

		// Set result CANCELED in case the user backs out
		setResult(Activity.RESULT_CANCELED);

		radioHost = (RadioButton) findViewById(R.id.radio_host);
		checkBoxVisible = (CheckBox) findViewById(R.id.checkBox_visible);
		layoutHost = (LinearLayout) findViewById(R.id.layout_host);
		layoutJoin = (LinearLayout) findViewById(R.id.layout_join);

		// Initialize the button to perform device discovery
		Button scanButton = (Button) findViewById(R.id.button_scan);
		scanButton.setOnClickListener(v -> {
			doDiscovery();
			v.setVisibility(View.GONE);
			new java.util.Timer().schedule(
					new java.util.TimerTask()
					{
						@Override
						public void run()
						{
							runOnUiThread(() -> v.setVisibility(View.VISIBLE));
						}
					}, 15000
			);
		});

		// Initialize array adapters. One for already paired devices and one for newly discovered devices
		ArrayAdapter<String> pairedDevicesArrayAdapter =
				new ArrayAdapter<>(this, R.layout.device_name);
		mNewDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.device_name);

		// Find and set up the ListView for paired devices
		ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
		pairedListView.setAdapter(pairedDevicesArrayAdapter);
		pairedListView.setOnItemClickListener(mDeviceClickListener);

		// Find and set up the ListView for newly discovered devices
		ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
		newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
		newDevicesListView.setOnItemClickListener(mDeviceClickListener);

		// Register filters
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		this.registerReceiver(mReceiver, filter);
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		this.registerReceiver(mReceiver, filter);
		filter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
		this.registerReceiver(mReceiver, filter);

		// Get the local Bluetooth adapter
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();

		// Get a set of currently paired devices
		Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

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
		if (mBtAdapter != null)
			mBtAdapter.cancelDiscovery();

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
		if (mBtAdapter.isDiscovering())
		{
			mBtAdapter.cancelDiscovery();
		}

		// Request discover from BluetoothAdapter
		mBtAdapter.startDiscovery();
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
			mBtAdapter.cancelDiscovery();

			// Get the device MAC address, which is the last 17 chars in the View
			String info = ((TextView) v).getText().toString();
			String address = info.substring(info.length() - 17);

			// Create the result Intent and include the MAC address
			Intent intent = new Intent();
			intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

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
					mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
				}
				// When discovery is finished, change the Activity title
			}
			else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action))
			{
				finish();
			}
			else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
			{
				setProgressBarIndeterminateVisibility(false);
				setTitle(R.string.select_device);
				if (mNewDevicesArrayAdapter.getCount() == 0)
				{
					String noDevices = getResources().getText(R.string.none_found).toString();
					mNewDevicesArrayAdapter.add(noDevices);
				}
			}
		}
	};

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
	}

	public void radioClick(View view)
	{
		boolean host = radioHost.isChecked();

		layoutHost.setVisibility(host ? View.VISIBLE : View.GONE);
		layoutJoin.setVisibility(host ? View.GONE : View.VISIBLE);

		if (host)
		{
			if (mBtAdapter.isDiscovering())
				mBtAdapter.cancelDiscovery();
		}
		else
		{
			//Make invisible again? TODO
		}
	}

	public void checkClick(View view)
	{
		if (checkBoxVisible.isChecked())
		{
			checkBoxVisible.setEnabled(false);
			new CountDownTimer(300000, 1000)
			{

				public void onTick(long millisUntilFinished)
				{
					long s = millisUntilFinished / 1000;
					checkBoxVisible.setText("Visible (" + s / 60 + "m" + s % 60 + "s left)");
				}

				public void onFinish()
				{
					checkBoxVisible.setText("Visible for other devices");
					checkBoxVisible.setEnabled(true);
					checkBoxVisible.setChecked(false);
				}
			}.start();
			ensureDiscoverable();
		}
	}

	private void ensureDiscoverable()
	{

		if (mBtAdapter.getScanMode() !=
				BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
		{
			Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			startActivity(discoverableIntent); //TODO check if user accept ffs
		}
	}
}
