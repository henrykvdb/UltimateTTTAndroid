/*
 * This file is part of Ultimate Tic Tac Toe.
 * Copyright (C) 2023 Henryk Van der Bruggen <henrykdev@gmail.com>
 *
 * This work is licensed under a Creative Commons
 * Attribution-NonCommercial-NoDerivatives 4.0 International License.
 *
 * You should have received a copy of the CC NC ND License along
 * with Ultimate Tic Tac Toe.  If not, see <https://creativecommons.org/>.
 */

package com.henrykvdb.sttt

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentCallbacks
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.widget.AppCompatTextView
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.flaghacker.sttt.bots.MMBot
import com.flaghacker.sttt.bots.RandomBot
import com.flaghacker.sttt.common.Player
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import java.util.*

private const val DAYS_UNTIL_RATE = 3      //Min number of days needed before asking for rating
private const val LAUNCHES_UNTIL_RATE = 3  //Min number of launches before asking for rating

private const val DONT_SHOW_AGAIN = "dontshowagain"
private const val DATE_FIRST_LAUNCH = "date_firstlaunch"
private const val LAUNCH_COUNT = "launch_count"

fun Context.newTitle(title: String): View = View.inflate(this, R.layout.dialog_title, null).apply {
	(findViewById<View>(R.id.action_bar_title) as TextView).text = title
}

fun Context.feedbackSender() {
	// @formatter:off
	val deviceInfo = ("\n /** please do not remove this block, technical info: "
			+ "os version: ${System.getProperty("os.version")}(${Build.VERSION.INCREMENTAL})"
			+ ", API: ${Build.VERSION.SDK_INT}"
			+ try {
		", app version: ${BuildConfig.VERSION_NAME}"
	} catch (e: PackageManager.NameNotFoundException) {
		""
	} + "**/")
	// @formatter:on

	startActivity(Intent.createChooser(Intent(Intent.ACTION_SENDTO).apply {
		data = Uri.parse(
			"mailto:${Uri.encode("henrykdev@gmail.com")}?subject=${Uri.encode("Feedback")}&body=${
				Uri.encode(deviceInfo)
			}"
		)
	}, getString(R.string.send_feedback)))
}

fun Context.shareDialog() = startActivity(Intent.createChooser(with(Intent(Intent.ACTION_SEND)) {
	type = "text/plain"
	putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.app_name_long))
	putExtra(
		Intent.EXTRA_TEXT,
		getString(R.string.lets_play_together) + " " + getString(R.string.market_url)
	)
}, getString(R.string.share_with)))

fun Context.aboutDialog() {
	val layout = View.inflate(this, R.layout.dialog_body_about, null)

	(layout.findViewById<View>(R.id.versionName_view) as TextView).text = try {
		resources.getText(R.string.app_name_long)
			.toString() + "\n" + getString(R.string.version) + " " +
				BuildConfig.VERSION_NAME
	} catch (e: PackageManager.NameNotFoundException) {
		resources.getText(R.string.app_name_long)
	}

	AlertDialog.Builder(this)
		.setView(layout)
		.setPositiveButton(getString(R.string.close)) { dlg, _ -> dlg.dismiss() }
		.show().autoDismiss(this)
}

fun MainActivity.triggerDialogs() {
	val prefs = getSharedPreferences("APP_RATER", 0)
	if (prefs.getBoolean(DONT_SHOW_AGAIN, false)) return

	// Update first launch
	val editor = prefs.edit()
	var firstLaunch = prefs.getLong(DATE_FIRST_LAUNCH, 0)
	if (firstLaunch == 0L) {
		firstLaunch = System.currentTimeMillis()
		editor.putLong(DATE_FIRST_LAUNCH, firstLaunch)
		editor.apply()
	}

	// Increment launch counter
	val launchCount = prefs.getLong(LAUNCH_COUNT, 0) + 1
	if (launchCount > 1) {
		editor.putLong(LAUNCH_COUNT, launchCount)
		editor.apply()

		// Open rate dialog if conditions are met
		if (System.currentTimeMillis() >= firstLaunch + DAYS_UNTIL_RATE * 24 * 60 * 60 * 1000 && launchCount >= LAUNCHES_UNTIL_RATE)
			newRateDialog()
	} else {
		val intent = Intent(this, TutorialActivity::class.java)
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			if (result.resultCode == Activity.RESULT_OK) {
				editor.putLong(LAUNCH_COUNT, 1)
				editor.apply()
			}
		}.launch(intent)
	}
}

fun Context.newRateDialog() {
	val editor = getSharedPreferences("APP_RATER", 0).edit()
	val dialogClickListener = DialogInterface.OnClickListener { _, which ->
		when (which) {
			DialogInterface.BUTTON_POSITIVE -> {
				editor.putBoolean(DONT_SHOW_AGAIN, true)
				editor.apply()
				startActivity(
					Intent(
						Intent.ACTION_VIEW,
						Uri.parse("market://details?id=$packageName")
					).apply {
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
	AlertDialog.Builder(this)
		.setView(layout)
		.setCustomTitle(newTitle(getString(R.string.rate_app)))
		.setPositiveButton(getString(R.string.rate), dialogClickListener)
		.setNeutralButton(getString(R.string.later), dialogClickListener)
		.setNegativeButton(getString(R.string.no_thanks), dialogClickListener)
		.show().autoDismiss(this)
}

fun Context.newLocalDialog() {
	val dialogClickListener = DialogInterface.OnClickListener { dialog, which ->
		when (which) {
			DialogInterface.BUTTON_NEGATIVE -> dialog.dismiss()
			DialogInterface.BUTTON_POSITIVE -> sendBroadcast(
				Intent(INTENT_NEWGAME).putExtra(
					INTENT_DATA,
					GameState.Builder().swapped(false).build()
				)
			)
		}
	}

	val layout = View.inflate(this, R.layout.dialog_body_basic, null)
	layout.findViewById<TextView>(R.id.textView).text = getString(R.string.new_local_desc)
	AlertDialog.Builder(this)
		.setCustomTitle(newTitle(getString(R.string.new_local_title)))
		.setView(layout)
		.setPositiveButton(getString(R.string.start), dialogClickListener)
		.setNegativeButton(getString(R.string.close), dialogClickListener).show().autoDismiss(this)
}

class RemoteHostFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		return inflater.inflate(R.layout.dialog_body_host, container, false)
	}
}

class RemoteJoinFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		return inflater.inflate(R.layout.dialog_body_join, container, false)
	}
}

fun FragmentActivity.newRemoteDialog(oldGs: GameState) {
	val layout = View.inflate(this, R.layout.dialog_body_internet, null)
	val viewPager = layout.findViewById<ViewPager2>(R.id.pager)
	val tabs = layout.findViewById<TabLayout>(R.id.remote_tabs)

	// Set up viewpager
	val hostFragment = RemoteHostFragment(); val joinFragment = RemoteJoinFragment()
	viewPager.adapter = object : FragmentStateAdapter(this) {
		override fun getItemCount() = 2
		override fun createFragment(position: Int) = if (position == 0) hostFragment else joinFragment
	}

	// Create dialog
	val dialog = AlertDialog.Builder(this)
		.setView(layout)
		.setNegativeButton(getString(R.string.close)) { dialog, _ -> dialog?.dismiss() }
		.setPositiveButton(tabs.getTabAt(0)?.text, null).show().autoDismiss(this)

	// Handle positive button
	val buttonPositive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
	buttonPositive.setOnClickListener {
		// Host game
		if (viewPager.currentItem == 0) {
			// Update dialog
			buttonPositive.isEnabled = false
			val textView = (layout.findViewById<View>(R.id.bt_host_desc) as TextView)
			textView.text = HtmlCompat.fromHtml(
				getString(R.string.host_desc, "9999"),
				HtmlCompat.FROM_HTML_MODE_LEGACY
			)

			//Get game settings
			val startRadioGrp = (layout.findViewById<View>(R.id.start_radio_group) as RadioGroup)
			val newBoard = (layout.findViewById<View>(R.id.board_new) as RadioButton).isChecked
			val start = when (startRadioGrp.checkedRadioButtonId) {
					R.id.start_you -> true
					R.id.start_other -> false
					else -> Random().nextBoolean()
			}


			//Create the actual requested gamestate
			val swapped = if (newBoard) !start else start xor (oldGs.board.nextPlayer == Player.PLAYER)
			val gsBuilder = GameState.Builder().remote(9999).swapped(swapped)
			if (!newBoard) gsBuilder.boards(oldGs.boards)
			val gs = gsBuilder.build()

			//main.remote.listen(gs)
			/*sendBroadcast(Intent(INTENT_NEWGAME).putExtra(INTENT_DATA,GameState.Builder().swapped(false).build()*/  //TODO IMPLEMENT BUTTONS THAT DO SOMETHING))
		}
		// Create game
		else {
			dialog.dismiss()
		}
	}

	// Make tabs change viewpager position
	tabs.addOnTabSelectedListener(object : OnTabSelectedListener {
		override fun onTabUnselected(tab: TabLayout.Tab?) {}
		override fun onTabReselected(tab: TabLayout.Tab?) {}
		override fun onTabSelected(tab: TabLayout.Tab?) {
			viewPager.currentItem = tab?.position ?: 0
		}
	})

	// Make viewpager change tab position
	viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
		override fun onPageScrollStateChanged(state: Int) {
			super.onPageScrollStateChanged(state)
			if (viewPager.scrollState == ViewPager2.SCROLL_STATE_IDLE) {
				val selectedTab = tabs.getTabAt(viewPager.currentItem)
				selectedTab?.select()
				dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = selectedTab?.text
				dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true

				if (selectedTab?.position != 0) {
					val textView =
						(hostFragment.view?.findViewById<View>(R.id.bt_host_desc) as TextView)
					textView.text = getString(R.string.no_host_desc)
				}
			}

		}
	})
}

fun Context.newAiDialog() {
	val layout = View.inflate(this, R.layout.dialog_body_ai, null)
	val dialogClickListener = DialogInterface.OnClickListener { dialog, which ->
		val progress = (layout.findViewById<View>(R.id.difficulty) as SeekBar).progress
		val bot = if (progress > 0) MMBot(progress) else RandomBot()

		val startRadioGrp = (layout.findViewById<View>(R.id.start_radio_group) as RadioGroup)
		val start = when (startRadioGrp.checkedRadioButtonId) {
			R.id.start_you -> true
			R.id.start_other -> false
			else -> Random().nextBoolean()
		}
		when (which) {
			DialogInterface.BUTTON_POSITIVE -> sendBroadcast(
				Intent(INTENT_NEWGAME).putExtra(INTENT_DATA, GameState.Builder().ai(bot).swapped(!start).build()) //TODO CHECK IF WORKING WHEN GAMES IMPLEMENTED AGAIN
			)
			DialogInterface.BUTTON_NEGATIVE -> dialog.dismiss()
		}
	}

	AlertDialog.Builder(this)
		.setView(layout)
		.setCustomTitle(newTitle(getString(R.string.new_ai_title)))
		.setPositiveButton(getString(R.string.start), dialogClickListener)
		.setNegativeButton(getString(R.string.close), dialogClickListener).show().autoDismiss(this)
}

private fun AlertDialog.autoDismiss(c: Context) = apply {
	c.registerComponentCallbacks(object : ComponentCallbacks {
		override fun onLowMemory() {}
		override fun onConfigurationChanged(newConfig: Configuration) {
			this@autoDismiss.dismiss()
		}
	})
}

@Suppress("DEPRECATION")
class LinkView(context: Context, attrs: AttributeSet) : AppCompatTextView(context, attrs) {
	init {
		movementMethod = LinkMovementMethod.getInstance()
		text = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) Html.fromHtml(text.toString())
		else Html.fromHtml(text.toString(), Html.FROM_HTML_MODE_LEGACY)
	}
}
