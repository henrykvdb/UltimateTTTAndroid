package com.henrykvdb.sttt.util

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.flaghacker.sttt.bots.MMBot
import com.flaghacker.sttt.bots.RandomBot
import com.henrykvdb.sttt.GameState
import com.henrykvdb.sttt.R
import java.util.*

private const val DAYS_UNTIL_PROMPT = 3      //Min number of days needed before asking for rating
private const val LAUNCHES_UNTIL_PROMPT = 3  //Min number of launches before asking for rating

private const val DONT_SHOW_AGAIN = "dontshowagain"
private const val DATE_FIRST_LAUNCH = "date_firstlaunch"
private const val LAUNCH_COUNT = "launch_count"

// Prevent dialog destroy when orientation changes
fun keepDialog(dialog: AlertDialog): AlertDialog {
    try {
        val lp = WindowManager.LayoutParams()
        lp.copyFrom(dialog.window!!.attributes)
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        dialog.window!!.attributes = lp
    } catch (t: Throwable) {
        //NOP
    }

    return dialog
}

fun newTitle(context: Context, title: String): View {
    val v = View.inflate(context, R.layout.dialog_title, null)
    (v.findViewById<View>(R.id.action_bar_title) as TextView).text = title
    return v
}

fun newLoadingTitle(context: Context, title: String): View {
    val v = View.inflate(context, R.layout.dialog_title_load, null)
    (v.findViewById<View>(R.id.action_bar_title) as TextView).text = title
    return v
}

fun feedbackSender(context: Context) {
    var deviceInfo = ("\n /** please do not remove this block, technical info: "
            + "os version: ${System.getProperty("os.version")}(${android.os.Build.VERSION.INCREMENTAL})"
            + ", API: ${android.os.Build.VERSION.SDK_INT}")
    try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        deviceInfo += ", app version: ${pInfo.versionName}"
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
    deviceInfo += "**/"

    val send = Intent(Intent.ACTION_SENDTO)
    send.data = Uri.parse(
            "mailto:${Uri.encode("henrykdev@gmail.com")}?subject=${Uri.encode("Feedback")}&body=${Uri.encode(deviceInfo)}")
    context.startActivity(Intent.createChooser(send, context.getString(R.string.send_feedback)))
}

fun shareDialog(context: Context) {
    val i = Intent(Intent.ACTION_SEND)
    i.type = "text/plain"
    i.putExtra(Intent.EXTRA_SUBJECT, context.resources.getString(R.string.app_name_long))
    i.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.lets_play_together) + " " + context.getString(R.string.market_url))
    context.startActivity(Intent.createChooser(i, context.getString(R.string.share_with)))
}

@SuppressLint("SetTextI18n")
fun aboutDialog(context: Context) {
    val layout = View.inflate(context, R.layout.dialog_about, null)

    try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        (layout.findViewById<View>(R.id.versionName_view) as TextView).text = context.resources.getText(R.string.app_name_long).toString() + "\n" + context.getString(R.string.version) + " " + pInfo.versionName
    } catch (e: PackageManager.NameNotFoundException) {
        (layout.findViewById<View>(R.id.versionName_view) as TextView).text = context.resources.getText(R.string.app_name_long)
    }

    keepDialog(AlertDialog.Builder(context)
            .setCustomTitle(newTitle(context, context.getString(R.string.about)))
            .setView(layout)
            .setPositiveButton(context.getString(R.string.close)) { dialog1, _ -> dialog1.dismiss() }
            .show())
}

fun rateDialog(context: Context) {
    val prefs = context.getSharedPreferences("APP_RATER", 0)

    if (prefs.getBoolean(DONT_SHOW_AGAIN, false))
        return

    val editor = prefs.edit()

    // Increment launch counter
    val launchCount = prefs.getLong(LAUNCH_COUNT, 0) + 1
    editor.putLong(LAUNCH_COUNT, launchCount)

    // Get date of first launch
    var dateFirstLaunch: Long? = prefs.getLong(DATE_FIRST_LAUNCH, 0)
    if (dateFirstLaunch == 0L) {
        dateFirstLaunch = System.currentTimeMillis()
        editor.putLong(DATE_FIRST_LAUNCH, dateFirstLaunch)
    }

    // Wait at least n days before opening
    if (launchCount >= LAUNCHES_UNTIL_PROMPT && System.currentTimeMillis() >= dateFirstLaunch!! + DAYS_UNTIL_PROMPT * 24 * 60 * 60 * 1000) {
        @SuppressLint("InlinedApi") val dialogClickListener = DialogInterface.OnClickListener { _, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> {
                    editor.putBoolean(DONT_SHOW_AGAIN, true)
                    editor.apply()
                    val goToMarket = Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=" + context.packageName))
                    goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    goToMarket.addFlags(if (Build.VERSION.SDK_INT >= 21) Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                    else Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
                    context.startActivity(goToMarket)
                }
                DialogInterface.BUTTON_NEUTRAL -> {
                }
                DialogInterface.BUTTON_NEGATIVE -> {
                    editor.putBoolean(DONT_SHOW_AGAIN, true)
                    editor.apply()
                }
            }
        }

        keepDialog(AlertDialog.Builder(context)
                .setMessage(context.getString(R.string.rate_message))
                .setCustomTitle(newTitle(context, context.getString(R.string.rate_app)))
                .setPositiveButton(context.getString(R.string.rate), dialogClickListener)
                .setNeutralButton(context.getString(R.string.later), dialogClickListener)
                .setNegativeButton(context.getString(R.string.no_thanks), dialogClickListener)
                .show())
    }

    editor.apply()
}

fun newLocal(callback: (Boolean) -> Unit, context: Context) {
    val dialogClickListener = DialogInterface.OnClickListener { dialog, which ->
        when (which) {
            DialogInterface.BUTTON_POSITIVE -> callback.invoke(true)
            DialogInterface.BUTTON_NEGATIVE -> dialog.dismiss()
        }
    }

    keepDialog(AlertDialog.Builder(context)
            .setCustomTitle(newTitle(context, context.getString(R.string.new_local_title)))
            .setMessage(context.getString(R.string.new_local_desc))
            .setPositiveButton(context.getString(R.string.start), dialogClickListener)
            .setNegativeButton(context.getString(R.string.close), dialogClickListener).show())
}

fun newAi(callback: (GameState) -> Unit, context: Context) {
    val swapped = BooleanArray(1)

    val layout = View.inflate(context, R.layout.dialog_ai, null)
    val beginner = layout.findViewById<RadioGroup>(R.id.start_radio_group)
    beginner.setOnCheckedChangeListener { _, checkedId ->
        swapped[0] = checkedId != R.id.start_you_radiobtn && (checkedId == R.id.start_ai || Random().nextBoolean())
    }

    val dialogClickListener = DialogInterface.OnClickListener { dialog, which ->
        val progress = (layout.findViewById<View>(R.id.difficulty) as SeekBar).progress
        when (which) {
            DialogInterface.BUTTON_POSITIVE -> callback.invoke(GameState.Builder()
                    .ai(if (progress > 0) MMBot(progress) else RandomBot())
                    .swapped(swapped[0]).build())
            DialogInterface.BUTTON_NEGATIVE -> dialog.dismiss()
        }
    }

    keepDialog(AlertDialog.Builder(context)
            .setView(layout).setCustomTitle(newTitle(context, context.getString(R.string.new_ai_title)))
            .setPositiveButton(context.getString(R.string.start), dialogClickListener)
            .setNegativeButton(context.getString(R.string.close), dialogClickListener).show())
}