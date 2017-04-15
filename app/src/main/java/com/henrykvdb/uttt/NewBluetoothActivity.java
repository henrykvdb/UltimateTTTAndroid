package com.henrykvdb.uttt;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.Objects;
import java.util.Random;

public class NewBluetoothActivity extends Activity
{
	/**
	 * Return Intent extra
	 */
	public static String EXTRA_DEVICE_ADDRESS = "device_address";

	private BluetoothAdapter btAdapter;
	private ArrayAdapter<String> devicesArrayAdapter;
	private LinearLayout devicesLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Setup the window
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.new_bluetooth);

		// Set result CANCELED in case the user backs out
		setResult(Activity.RESULT_CANCELED);

		ProgressBar spinner = (ProgressBar) findViewById(R.id.loading_spinner);
		Button scanButton = (Button) findViewById(R.id.button_scan);

		// Initialize the button to perform device discovery
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

		devicesArrayAdapter = new ArrayAdapter<>(this, R.layout.device_name);
		devicesLayout = (LinearLayout) findViewById(R.id.devices);
		devicesArrayAdapter.registerDataSetObserver(new DataSetObserver()
		{
			@Override
			public void onChanged()
			{
				updateLayout();
				super.onChanged();
			}
		});
		devicesArrayAdapter.add(getResources().getText(R.string.none_found).toString());

		// Register filters
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		this.registerReceiver(mReceiver, filter);
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		this.registerReceiver(mReceiver, filter);
		filter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
		this.registerReceiver(mReceiver, filter);

		// Get the local Bluetooth adapter
		btAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	private void updateLayout()
	{
		devicesLayout.removeAllViews();
		for (int i = 0; i < devicesArrayAdapter.getCount(); i++)
		{
			View item = devicesArrayAdapter.getView(i, null, null);
			if (!Objects.equals(((TextView) item).getText().toString(), getResources().getText(R.string.none_found).toString()))
			{
				item.setOnClickListener(v -> {
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
			}
			devicesLayout.addView(item);
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
		devicesArrayAdapter.clear();
		devicesArrayAdapter.add(getResources().getText(R.string.none_found).toString());
		Log.d("NewBluetoothActivity", "doDiscovery()");

		// Indicate scanning in the title
		setProgressBarIndeterminateVisibility(true);
		setTitle(R.string.scanning);

		// If we're already discovering, stop it
		if (btAdapter.isDiscovering())
			btAdapter.cancelDiscovery();

		// Request discover from BluetoothAdapter
		btAdapter.startDiscovery();
	}

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
				devicesArrayAdapter.remove(getResources().getText(R.string.none_found).toString());

				// Get the BluetoothDevice object from the Intent and add it to the adapter
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				devicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
			}
			else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action))
			{
				finish();
			}
			else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
			{
				setProgressBarIndeterminateVisibility(false);
				setTitle(R.string.select_device);
				if (devicesArrayAdapter.getCount() == 0)
					devicesArrayAdapter.add(getResources().getText(R.string.none_found).toString());
			}
		}
	};

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
	}
}
