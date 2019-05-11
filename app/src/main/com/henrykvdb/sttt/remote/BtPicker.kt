/*
 * This file is part of Super Tic Tac Toe.
 * Copyright (C) 2018 Henryk Van der Bruggen <henrykdev@gmail.com>
 *
 * Super Tic Tac Toe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Super Tic Tac Toe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Super Tic Tac Toe.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.henrykvdb.sttt.remote

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.henrykvdb.sttt.R
import com.henrykvdb.sttt.keepDialog
import com.henrykvdb.sttt.log
import com.henrykvdb.sttt.newLoadingTitle
import java.util.*

class BtPicker(private val context: Context, private val btAdapter: BluetoothAdapter, private val addressCallback: (String) -> Unit) {
	private val devices = ArrayList<BluetoothDevice>()
	private val view = View.inflate(context, R.layout.dialog_body_bt_join, null)
	private val devicesLayout = view.findViewById<View>(R.id.devices) as LinearLayout

	val alertDialog: AlertDialog = keepDialog(AlertDialog.Builder(context)
			.setCustomTitle(context.newLoadingTitle(context.getString(R.string.join_bluetooth_game)))
			.setView(view)
			.setNegativeButton(context.getString(R.string.close)) { dialog, _ -> dialog.dismiss() }
			.setOnDismissListener {
				context.unregisterReceiver(btReceiver)
				btAdapter.cancelDiscovery()
			}.show())

	private val btReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			when (intent.action) {
				BluetoothDevice.ACTION_ACL_CONNECTED -> alertDialog.dismiss()
				BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> doDiscovery()
				BluetoothDevice.ACTION_FOUND -> {
					val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
					if (devices.none { it.address == device.address }) {
						devices.add(device)
						updateLayout()
					}
				}
			}
		}
	}

	init {
		context.registerReceiver(btReceiver, IntentFilter().apply {
			addAction(BluetoothDevice.ACTION_FOUND)
			addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
			addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
		})
		doDiscovery()
	}

	@SuppressLint("SetTextI18n")
	private fun updateLayout() {
		devicesLayout.removeAllViews()
		if (devices.size > 0) {
			for (device in devices) {
				devicesLayout.addView(TextView(context).apply {
					text = device.name + "\n" + device.address
					setOnClickListener {
						alertDialog.dismiss()
						addressCallback(text.substring(text.length - 17))
					}
				})
			}
		} else devicesLayout.addView(TextView(context).apply { text = context.resources.getText(R.string.none_found) })
	}

	private fun doDiscovery() {
		log("doDiscovery()")
		devices.clear()
		updateLayout()

		if (btAdapter.isDiscovering) btAdapter.cancelDiscovery()
		btAdapter.startDiscovery()
	}
}