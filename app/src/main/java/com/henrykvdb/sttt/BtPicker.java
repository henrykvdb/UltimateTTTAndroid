package com.henrykvdb.sttt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.henrykvdb.sttt.Util.DialogUtil;
import com.henrykvdb.sttt.Util.Util;

import java.util.ArrayList;
import java.util.List;

public class BtPicker {
	private final BluetoothAdapter btAdapter;
	private final Context context;

	private final AlertDialog alertDialog;
	private final Callback<String> addressCallback;
	private final LinearLayout devicesLayout;

	private List<BluetoothDevice> devices = new ArrayList<>();

	private BtPicker(Context context, BluetoothAdapter btAdapter, Callback<String> addressCallback) {
		this.addressCallback = addressCallback;
		this.btAdapter = btAdapter;
		this.context = context;

		View view = View.inflate(context, R.layout.dialog_bt_join, null);
		devicesLayout = (LinearLayout) view.findViewById(R.id.devices);

		alertDialog = DialogUtil.keepDialog(new AlertDialog.Builder(context)
				.setCustomTitle(DialogUtil.newLoadTitle(context, Util.getString(context, R.string.join_bluetooth_game)))
				.setView(view)
				.setNegativeButton(Util.getString(context,R.string.close), (dialog, which) -> dialog.dismiss())
				.setOnDismissListener(dialog -> destroy())
				.show());

		// Register filters
		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		context.registerReceiver(btReceiver, filter);

		doDiscovery();
	}

	public static AlertDialog createDialog(Context context, BluetoothAdapter btAdapter, Callback<String> addressCallback) {
		return new com.henrykvdb.sttt.BtPicker(context, btAdapter, addressCallback).alertDialog;
	}

	public AlertDialog updateLayout() {
		//Clear layout
		devicesLayout.removeAllViews();

		//Add all the devices
		if (devices.size() > 0) {
			for (BluetoothDevice device : devices) {
				TextView view1 = new TextView(context);
				view1.setText(device.getName() + "\n" + device.getAddress());

				view1.setOnClickListener(v ->
				{
					// Cancel discovery because it's costly and we're aboutDialog to connect
					btAdapter.cancelDiscovery();

					// Get the device MAC address, which is the last 17 chars in the View
					String info = ((TextView) v).getText().toString();
					String address = info.substring(info.length() - 17);

					alertDialog.dismiss();
					addressCallback.callback(address);
				});

				devicesLayout.addView(view1);
			}
		}
		else {
			//There are no devices in the list
			TextView view1 = new TextView(context);
			view1.setText(context.getResources().getText(R.string.none_found).toString());
			devicesLayout.addView(view1);
		}

		return alertDialog;
	}

	private void doDiscovery() {
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

	private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

				boolean add = true;
				if (device.getName() != null)
					for (BluetoothDevice d : devices)
						add = !d.getAddress().equals(device.getAddress()) && add;

				if (add) {
					devices.add(device);
					updateLayout();
				}
			}
			else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
				alertDialog.dismiss();
			}
			else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				doDiscovery();
			}
		}
	};

	public void destroy() {
		Log.e(Constants.LOG_TAG, "Dialog dismissed");

		// Unregister broadcast listeners
		context.unregisterReceiver(btReceiver);

		// Make sure we're not doing discovery anymore
		if (btAdapter != null)
			btAdapter.cancelDiscovery();
	}
}
