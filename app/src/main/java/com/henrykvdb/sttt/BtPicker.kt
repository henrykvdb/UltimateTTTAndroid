package com.henrykvdb.sttt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.henrykvdb.sttt.Util.DialogUtil
import java.util.*

class BtPicker(private val context: Context, private val btAdapter: BluetoothAdapter, private val addressCallback: Callback<String>) {
    val alertDialog: AlertDialog
    private val devicesLayout: LinearLayout
    private val devices = ArrayList<BluetoothDevice>()

    private val btReceiver : BroadcastReceiver

    init {
        val view = View.inflate(context, R.layout.dialog_bt_join, null)
        devicesLayout = view.findViewById<View>(R.id.devices) as LinearLayout
        alertDialog = DialogUtil.keepDialog(AlertDialog.Builder(context)
                .setCustomTitle(DialogUtil.newLoadTitle(context, context.getString(R.string.join_bluetooth_game)))
                .setView(view)
                .setNegativeButton(context.getString(R.string.close)) { dialog, which -> dialog.dismiss() }
                .setOnDismissListener { destroy() }
                .show())

        btReceiver = object :BroadcastReceiver(){
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        var add = true
                        if (device.name != null)
                            devices.forEach { d -> add = d.address != device.address && add }
                        if (add) {
                            devices.add(device)
                            updateLayout()
                        }
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> alertDialog.dismiss()
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> doDiscovery()
                }
            }
        }

        // Register filters
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        context.registerReceiver(btReceiver, filter)

        doDiscovery()
    }

    private fun updateLayout() {
        devicesLayout.removeAllViews()
        if (devices.size > 0) {
            for (device in devices) {
                val view1 = TextView(context)
                @SuppressLint("SetTextI18n")
                view1.text = device.name + "\n" + device.address

                view1.setOnClickListener { v ->
                    // Cancel discovery because it's costly and we're aboutDialog to connect
                    btAdapter.cancelDiscovery()

                    // Get the device MAC address, which is the last 17 chars in the View
                    val info = (v as TextView).text.toString()
                    val address = info.substring(info.length - 17)

                    alertDialog.dismiss()
                    addressCallback.callback(address)
                }

                devicesLayout.addView(view1)
            }
        } else {
            //There are no devices in the list
            val view1 = TextView(context)
            view1.text = context.resources.getText(R.string.none_found).toString()
            devicesLayout.addView(view1)
        }
    }

    private fun doDiscovery() {
        Log.d("NewBluetoothActivity", "doDiscovery()")

        //Clear the devices list
        devices.clear()
        updateLayout()

        // If we're already discovering, stop it
        if (btAdapter.isDiscovering)
            btAdapter.cancelDiscovery()

        // Request discover from BluetoothAdapter
        btAdapter.startDiscovery()
    }

    private fun destroy() {
        Log.e(Constants.LOG_TAG, "Dialog dismissed")

        // Unregister broadcast listeners
        context.unregisterReceiver(btReceiver)

        // Make sure we're not doing discovery anymore
        btAdapter.cancelDiscovery()
    }
}