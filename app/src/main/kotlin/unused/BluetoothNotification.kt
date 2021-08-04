package unused/*
package com.henrykvdb.sttt.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import sttt.INTENT_STOP_BT_SERVICE
import com.henrykvdb.sttt.MainActivity
import com.henrykvdb.sttt.R
import sttt.REMOTE_STILL_RUNNING

fun openBtNotification(context: Context) {
    val openIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val openPendingIntent = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_ONE_SHOT)
    val closePendingIntent = PendingIntent.getBroadcast(context, 1, Intent(INTENT_STOP_BT_SERVICE), PendingIntent.FLAG_ONE_SHOT)

    //Android O Channel magic
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationChannel = NotificationChannel("sttt", context.getString(R.string.app_name_long), NotificationManager.IMPORTANCE_LOW)
        val notificationManager = (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        notificationManager.createNotificationChannel(notificationChannel)
    }

    val text = context.getString(R.string.bt_running_notification)
    val notification = NotificationCompat.Builder(context, "sttt")
            .setSmallIcon(R.drawable.ic_icon)
            .setContentTitle(context.getString(R.string.app_name_long))
            .setContentText(text)
            .setContentIntent(openPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .addAction(R.drawable.ic_menu_bluetooth, context.getString(R.string.close), closePendingIntent)
            .setAutoCancel(true).build()
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(REMOTE_STILL_RUNNING, notification)
}

fun closeBtNotification(context: Context) {
    NotificationManagerCompat.from(context).cancel(REMOTE_STILL_RUNNING)
}*/
