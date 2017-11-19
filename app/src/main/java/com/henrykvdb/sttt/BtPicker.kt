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
    private val devices = ArrayList<BluetoothDevice>()

    private val view = View.inflate(context, R.layout.dialog_bt_join, null)
    private val devicesLayout = view.findViewById<View>(R.id.devices) as LinearLayout

    val alertDialog: AlertDialog = DialogUtil.keepDialog(AlertDialog.Builder(context)
            .setCustomTitle(DialogUtil.newLoadTitle(context, context.getString(R.string.join_bluetooth_game)))
            .setView(view)
            .setNegativeButton(context.getString(R.string.close)) { dialog, _ -> dialog.dismiss() }
            .setOnDismissListener { destroy() }
            .show())

    private val btReceiver = object : BroadcastReceiver() {
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

    init {
        IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            context.registerReceiver(btReceiver, this)
        }
        doDiscovery()
    }

    private fun updateLayout() {
        devicesLayout.removeAllViews()
        if (devices.size > 0) {
            for (device in devices) {
                val view = TextView(context)
                @SuppressLint("SetTextI18n")
                view.text = device.name + "\n" + device.address

                view.setOnClickListener {
                    // Cancel discovery because it's costly and we're aboutDialog to connect
                    btAdapter.cancelDiscovery()

                    // Get the device MAC address, which is the last 17 chars in the View
                    val address = view.text.substring(view.text.length - 17)

                    alertDialog.dismiss()
                    addressCallback(address)
                }

                devicesLayout.addView(view)
            }
        } else {//There are no devices in the list
            val view = TextView(context)
            view.text = context.resources.getText(R.string.none_found).toString()
            devicesLayout.addView(view)
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