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

package sttt

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.widget.AppCompatTextView
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
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

private const val DAYS_UNTIL_RATE = 3      //Min number of days needed before asking for rating
private const val LAUNCHES_UNTIL_RATE = 3  //Min number of launches before asking for rating

private const val DONT_SHOW_AGAIN = "dontshowagain"
private const val DATE_FIRST_LAUNCH = "date_firstlaunch"
private const val LAUNCH_COUNT = "launch_count"

// Prevent dialog destroy when orientation changes
fun keepDialog(dialog: AlertDialog) = dialog.apply {
	dialog.window?.attributes = WindowManager.LayoutParams().apply {
		copyFrom(dialog.window?.attributes)
		width = WindowManager.LayoutParams.WRAP_CONTENT
		height = WindowManager.LayoutParams.WRAP_CONTENT
	}
}

fun Context.newTitle(title: String): View = View.inflate(this, R.layout.dialog_title, null).apply {
	(findViewById<View>(R.id.action_bar_title) as TextView).text = title
}

fun Context.newLoadingTitle(title: String): View = View.inflate(this,
	R.layout.dialog_title_load, null).apply {
	(findViewById<View>(R.id.action_bar_title) as TextView).text = title
}

fun Context.feedbackSender() {
	// @formatter:off
    val deviceInfo = ("\n /** please do not remove this block, technical info: "
            + "os version: ${System.getProperty("os.version")}(${Build.VERSION.INCREMENTAL})"
            + ", API: ${Build.VERSION.SDK_INT}"
            + try { ", app version: ${packageManager.getPackageInfo(packageName, 0).versionName}" }
              catch (e: PackageManager.NameNotFoundException) { "" } + "**/")
    // @formatter:on

	startActivity(Intent.createChooser(Intent(Intent.ACTION_SENDTO).apply {
		data = Uri.parse("mailto:${Uri.encode("henrykdev@gmail.com")}?subject=${Uri.encode("Feedback")}&body=${Uri.encode(deviceInfo)}")
	}, getString(R.string.send_feedback)))
}

fun Context.shareDialog() = startActivity(Intent.createChooser(with(Intent(Intent.ACTION_SEND)) {
	type = "text/plain"
	putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.app_name_long))
	putExtra(Intent.EXTRA_TEXT, getString(R.string.lets_play_together) + " " + getString(R.string.market_url))
}, getString(R.string.share_with)))

fun Context.aboutDialog() {
	val layout = View.inflate(this, R.layout.dialog_body_about, null)

	(layout.findViewById<View>(R.id.versionName_view) as TextView).text = try {
		resources.getText(R.string.app_name_long).toString() + "\n" + getString(R.string.version) + " " +
				packageManager.getPackageInfo(packageName, 0).versionName
	} catch (e: PackageManager.NameNotFoundException) {
		resources.getText(R.string.app_name_long)
	}

	keepDialog(AlertDialog.Builder(this)
			.setView(layout)
			.setPositiveButton(getString(R.string.close)) { dlg, _ -> dlg.dismiss() }
			.show())
}

fun Context.triggerDialogs() {
	val prefs = getSharedPreferences("APP_RATER", 0)
	if (prefs.getBoolean(DONT_SHOW_AGAIN, false)) return
	val editor = prefs.edit()

	// Increment launch counter
	val launchCount = prefs.getLong(LAUNCH_COUNT, 0) + 1
	editor.putLong(LAUNCH_COUNT, launchCount)

	// Get date of first launch
	var firstLaunch = prefs.getLong(DATE_FIRST_LAUNCH, 0)
	if (firstLaunch == 0L) {
		firstLaunch = System.currentTimeMillis()
		editor.putLong(DATE_FIRST_LAUNCH, firstLaunch)
	}

	// Open tutorial if conditions are met
	if (launchCount == 1L)
		startActivity(Intent(this, TutorialActivity::class.java))

	// Open rate dialog if conditions are met
	if (System.currentTimeMillis() >= firstLaunch + DAYS_UNTIL_RATE * 24 * 60 * 60 * 1000 && launchCount >= LAUNCHES_UNTIL_RATE)
		newRateDialog()

	editor.apply()
}

fun Context.newRateDialog() {
	val editor = getSharedPreferences("APP_RATER", 0).edit()
	val dialogClickListener = DialogInterface.OnClickListener { _, which ->
		when (which) {
			DialogInterface.BUTTON_POSITIVE -> {
				editor.putBoolean(DONT_SHOW_AGAIN, true)
				editor.apply()
				startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
					addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
					addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
				})
			}
			DialogInterface.BUTTON_NEGATIVE -> {
				editor.putBoolean(DONT_SHOW_AGAIN, true)
				editor.apply()
			}
		}
	}

	val layout = View.inflate(this, R.layout.dialog_body_basic, null)
	layout.findViewById<TextView>(R.id.textView).text = getString(R.string.rate_message)
	keepDialog(AlertDialog.Builder(this)
			.setView(layout)
			.setCustomTitle(newTitle(getString(R.string.rate_app)))
			.setPositiveButton(getString(R.string.rate), dialogClickListener)
			.setNeutralButton(getString(R.string.later), dialogClickListener)
			.setNegativeButton(getString(R.string.no_thanks), dialogClickListener)
			.show())
}

fun Context.newLocalDialog() {
	val dialogClickListener = DialogInterface.OnClickListener { dialog, which ->
		when (which) {
			DialogInterface.BUTTON_NEGATIVE -> dialog.dismiss()
			DialogInterface.BUTTON_POSITIVE -> sendBroadcast(Intent(INTENT_NEWGAME).putExtra(
				INTENT_DATA,
					GameState.Builder().swapped(false).build()))
		}
	}

	val layout = View.inflate(this, R.layout.dialog_body_basic, null)
	layout.findViewById<TextView>(R.id.textView).text = getString(R.string.new_local_desc)
	keepDialog(AlertDialog.Builder(this)
			.setCustomTitle(newTitle(getString(R.string.new_local_title)))
			.setView(layout)
			.setPositiveButton(getString(R.string.start), dialogClickListener)
			.setNegativeButton(getString(R.string.close), dialogClickListener).show())
}

fun Context.newAiDialog() {
	val swapped = BooleanArray(1)
	val layout = View.inflate(this, R.layout.dialog_body_ai, null)
	val beginner = layout.findViewById<RadioGroup>(R.id.start_radio_group)
	beginner.setOnCheckedChangeListener { _, checkedId ->
		swapped[0] = checkedId != R.id.start_you_radiobtn && (checkedId == R.id.start_ai || Random().nextBoolean())
	}

	val dialogClickListener = DialogInterface.OnClickListener { dialog, which ->
		val progress = (layout.findViewById<View>(R.id.difficulty) as SeekBar).progress
		val bot = if (progress > 0) MMBot(progress) else RandomBot()
		when (which) {
			DialogInterface.BUTTON_POSITIVE -> sendBroadcast(Intent(INTENT_NEWGAME).putExtra(
				INTENT_DATA,
					GameState.Builder().ai(bot).swapped(swapped[0]).build()))
			DialogInterface.BUTTON_NEGATIVE -> dialog.dismiss()
		}
	}

	keepDialog(AlertDialog.Builder(this)
			.setView(layout)
			.setCustomTitle(newTitle(getString(R.string.new_ai_title)))
			.setPositiveButton(getString(R.string.start), dialogClickListener)
			.setNegativeButton(getString(R.string.close), dialogClickListener).show())
}

@Suppress("DEPRECATION")
class LinkView(context: Context, attrs: AttributeSet) : AppCompatTextView(context, attrs) {
	init {
		movementMethod = LinkMovementMethod.getInstance()
		text = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) Html.fromHtml(text.toString())
		else Html.fromHtml(text.toString(), Html.FROM_HTML_MODE_LEGACY)
	}
}
