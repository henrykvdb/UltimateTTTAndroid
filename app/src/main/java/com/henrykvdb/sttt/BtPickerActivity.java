package com.henrykvdb.sttt;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BtPickerActivity extends Activity
{
	/**
	 * Return Intent extra
	 */
	public static String EXTRA_DEVICE_ADDRESS = "device_address";

	private BluetoothAdapter btAdapter;

	private List<BluetoothDevice> devices = new ArrayList<>();

	private LinearLayout devicesLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Setup the window
		setContentView(R.layout.dialog_bluetooth);

		// Set result CANCELED in case the user backs out
		setResult(Activity.RESULT_CANCELED);

		// Set fields
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		devicesLayout = (LinearLayout) findViewById(R.id.devices);

		//Set local vars
		ProgressBar spinner = (ProgressBar) findViewById(R.id.loading_spinner);
		Button scanButton = (Button) findViewById(R.id.button_scan);

		// Initialize the button to perform device discovery
		scanButton.setOnClickListener(v ->
		{
			doDiscovery();
			scanButton.setEnabled(false);
			spinner.setVisibility(View.VISIBLE);
			new java.util.Timer().schedule(
					new java.util.TimerTask()
					{
						@Override
						public void run()
						{
							runOnUiThread(() ->
							{
								scanButton.setEnabled(true);
								spinner.setVisibility(View.INVISIBLE);
							});
						}
					}, 15000
			);
		});

		// Register filters
		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		registerReceiver(mReceiver, filter);
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

					int beginner = ((RadioGroup) findViewById(R.id.start_radio_group)).getCheckedRadioButtonId();
					boolean start = new Random().nextBoolean();
					if (beginner == R.id.start_you) start = true;
					else if (beginner == R.id.start_other) start = false;

					intent.putExtra("newBoard", ((RadioButton) findViewById(R.id.board_new)).isChecked());
					intent.putExtra("start", start);

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

			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action))
			{
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				boolean available = false;

				for (ParcelUuid uuid : device.getUuids())
					available = available || (uuid.getUuid().equals(BtService.UUID));

				devices.add(device);
				updateLayout();
			}
			else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action))
			{
				finish();
			}
			else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
			{
				//Nothing
			}
		}
	};

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
	}
}
