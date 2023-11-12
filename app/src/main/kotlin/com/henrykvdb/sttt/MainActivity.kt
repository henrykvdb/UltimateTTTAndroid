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

import android.R.attr.label
import android.R.attr.text
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import java.util.Random


private const val DAYS_UNTIL_RATE = 3      //Min number of days needed before asking for rating
private const val LAUNCHES_UNTIL_RATE = 3  //Min number of launches before asking for rating

private const val DONT_SHOW_AGAIN = "dontshowagain"
private const val DATE_FIRST_LAUNCH = "date_firstlaunch"
private const val LAUNCH_COUNT = "launch_count"

/** This is the top Activity, which implements all the dialogs **/
class MainActivity : MainActivityBaseRemote() {
    override fun feedbackSender() {
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

    override fun shareDialog() = startActivity(Intent.createChooser(with(Intent(Intent.ACTION_SEND)) {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.app_name_long))
        putExtra(
            Intent.EXTRA_TEXT,
            getString(R.string.share_message) + " " + getString(R.string.market_url)
        )
    }, getString(R.string.share_title)))

    override fun aboutDialog() {
        val layout = View.inflate(this, R.layout.dialog_body_about, null)

        (layout.findViewById<View>(R.id.versionName_view) as TextView).text = try {
            resources.getText(R.string.app_name_newline).toString() + "\n" +
                    getString(R.string.about_version) + " " + BuildConfig.VERSION_NAME
        } catch (e: PackageManager.NameNotFoundException) {
            resources.getText(R.string.app_name_newline)
        }

        openDialog?.dismiss()
        openDialog = MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialogTheme)
            .setView(layout)
            .setPositiveButton(getString(R.string.about_close)) { dlg, _ -> dlg.dismiss() }
            .show()
    }

    override fun triggerDialogs() {
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
            val enoughUseTime = System.currentTimeMillis() >= firstLaunch + DAYS_UNTIL_RATE * 24 * 60 * 60 * 1000
            val enoughLaunches = launchCount >= LAUNCHES_UNTIL_RATE
            if (enoughUseTime && enoughLaunches) newRateDialog()
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

    private fun newRateDialog() {
        val editor = getSharedPreferences("APP_RATER", 0).edit()
        val dialogClickListener = DialogInterface.OnClickListener { _, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> {
                    editor.putBoolean(DONT_SHOW_AGAIN, true)
                    editor.apply()
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")
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
        openDialog?.dismiss()
        openDialog = MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialogTheme)
            .setView(layout)
            .setTitle(getString(R.string.rate_title))
            .setPositiveButton(getString(R.string.rate_positive), dialogClickListener)
            .setNeutralButton(getString(R.string.rate_neutral), dialogClickListener)
            .setNegativeButton(getString(R.string.rate_negative), dialogClickListener)
            .show()
    }

    override fun newLocalDialog() {
        val dialogClickListener = DialogInterface.OnClickListener { dialog, which ->
            when (which) {
                DialogInterface.BUTTON_NEGATIVE -> dialog.dismiss()
                DialogInterface.BUTTON_POSITIVE -> newLocal()
            }
        }

        val layout = View.inflate(this, R.layout.dialog_body_basic, null)
        layout.findViewById<TextView>(R.id.textView).text = getString(R.string.new_local_message)
        openDialog?.dismiss()
        openDialog = MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialogTheme)
            .setTitle(getString(R.string.new_local_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.dialog_start_game), dialogClickListener)
            .setNegativeButton(getString(R.string.dialog_cancel), dialogClickListener).show()

    }

    override fun newAiDialog() {
        val layout = View.inflate(this, R.layout.dialog_body_ai, null)
        val dialogClickListener = DialogInterface.OnClickListener { dialog, which ->
            val progress = (layout.findViewById<View>(R.id.difficulty) as SeekBar).progress

            val startRadioGrp = (layout.findViewById<View>(R.id.start_radio_group) as RadioGroup)
            val start = when (startRadioGrp.checkedRadioButtonId) {
                R.id.start_you -> true
                R.id.start_other -> false
                else -> Random().nextBoolean()
            }
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> newAi(!start, progress)
                DialogInterface.BUTTON_NEGATIVE -> dialog.dismiss()
            }
        }

        openDialog?.dismiss()
        openDialog = MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialogTheme)
            .setView(layout)
            .setTitle(getString(R.string.new_ai_title))
            .setPositiveButton(getString(R.string.dialog_start_game), dialogClickListener)
            .setNegativeButton(getString(R.string.dialog_cancel), dialogClickListener).show()
    }

    class RemoteHostFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            return inflater.inflate(R.layout.dialog_body_online_merged, container, false).apply {
                findViewById<LinearLayout>(R.id.layout_host).visibility = View.VISIBLE
            }
        }
    }

    class RemoteJoinFragment : Fragment() {
        var forceRefreshHost = false
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            return inflater.inflate(R.layout.dialog_body_online_merged, container, false).apply {
                findViewById<LinearLayout>(R.id.layout_join).visibility = View.VISIBLE

                val editText = findViewById<EditText>(R.id.remote_join_edit)
                editText.filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(6))
                editText?.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus)
                        forceRefreshHost = true // focus shift things around and makes a mess
                    else {
                        val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(editText?.windowToken, 0)
                    }
                }
            }
        }
    }

    override fun newRemoteDialog() {
        val layout = View.inflate(this, R.layout.dialog_body_online, null)
        val viewPager = layout.findViewById<ViewPager2>(R.id.pager)
        val tabs = layout.findViewById<TabLayout>(R.id.remote_tabs)
        var newGameId = ""
        var destroyOnDismiss = true

        // Set up viewpager
        val hostFragment = RemoteHostFragment(); val joinFragment = RemoteJoinFragment()
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int) = if (position == 0) hostFragment else joinFragment
        }

        // Create dialog
        openDialog?.dismiss()
        val newDialog = MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialogTheme)
            .setView(layout)
            .setNegativeButton(getString(R.string.dialog_cancel)) { dialog, _ -> dialog?.dismiss() }
            .setPositiveButton(tabs.getTabAt(0)?.text, null).show()
        openDialog = newDialog

        // Add correct flags to dialog
        newDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        newDialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)

        // Set up viewpager listener
        val pageChangeCallback = object: ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(pos: Int, posOffset: Float, posOffsetPx: Int) {
                super.onPageScrolled(pos, posOffset, posOffsetPx)

                // Clear focus to trigger the listener
                joinFragment.view?.findViewById<AppCompatEditText>(R.id.remote_join_edit)?.clearFocus()

                // Force refresh fragment (keyboard might have caused a temporary resize)
                if (pos == 0 && joinFragment.forceRefreshHost){
                    joinFragment.forceRefreshHost = false
                    viewPager.post { viewPager.adapter?.notifyItemChanged(0) }
                }
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                // Remove database entry on switch
                if (newGameId.isNotEmpty()){
                    removeOnlineGame(newGameId)
                    newGameId = ""
                }

                // Reset host description
                if (position == 1) {
                    val textView =
                        (hostFragment.view?.findViewById<View>(R.id.bt_host_desc) as TextView)
                    textView.text = getString(R.string.online_create_message_wait)
                }

                // Select correct tab
                val selectedTab = tabs.getTabAt(position)
                selectedTab?.select()
                newDialog.getButton(AlertDialog.BUTTON_POSITIVE).text = selectedTab?.text
                newDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
            }
        }
        viewPager.registerOnPageChangeCallback(pageChangeCallback)
        newDialog.setOnDismissListener {
            viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
            if (newGameId.isNotEmpty() && destroyOnDismiss){
                removeOnlineGame(newGameId); newGameId = ""
            }
        }

        // Handle positive button
        val buttonPositive = newDialog.getButton(DialogInterface.BUTTON_POSITIVE)
        buttonPositive.setOnClickListener {
            // Host game
            if (viewPager.currentItem == 0) {
                buttonPositive.isEnabled = false
                createOnlineGame(afterSuccess = { gameId ->
                    newGameId = gameId // Store value for viewpager

                    // Update dialog
                    buttonPositive.isEnabled = false
                    val textView = layout.findViewById<TextView?>(R.id.bt_host_desc)
                    if (textView == null) // at time callback dialog may be destroyed
                        return@createOnlineGame
                    else{
                        textView.setOnLongClickListener {
                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("UTTT", gameId)
                            clipboard.setPrimaryClip(clip)
                            toast(R.string.copied); true
                        }
                        textView.text = HtmlCompat.fromHtml(
                            getString(R.string.online_create_message_ready, gameId),
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                        )
                    }

                    var handshakeComplete = false
                    createListener(gameId, onChange = { data ->
                        if (handshakeComplete) onDbEntryChange(data)
                        else if(data.idRemote.isNotEmpty()){
                            handshakeComplete = true

                            //Get game settings
                            val startRadioGrp = (layout.findViewById<View>(R.id.start_radio_group) as RadioGroup)
                            val newBoard = (layout.findViewById<View>(R.id.board_new) as RadioButton).isChecked
                            val startHost = when (startRadioGrp.checkedRadioButtonId) {
                                R.id.start_you -> true
                                R.id.start_other -> false
                                else -> Random().nextBoolean()
                            }

                            // Create game
                            val history = if (newBoard) listOf(-1) else gs.history
                            val nextPlayX = if (newBoard) true else history.size % 2 == 1
                            val swap = startHost xor nextPlayX
                            newRemote(swap, history, gameId)

                            // Store game update in server
                            val gameRef = gameId.getDbRef()
                            gameRef.child("hostIsX").setValue(!swap)
                            gameRef.child("history").setValue(history)

                            // Close dialog
                            destroyOnDismiss = false
                            newDialog.dismiss()
                        }
                    })
                }, afterFail = { errMsgRes ->
                    buttonPositive.isEnabled = true
                    toast(errMsgRes)
                }, attempts=3)
            }
            else if (viewPager.currentItem == 1){
                var handshakeComplete = false
                val gameId = layout.findViewById<EditText?>(R.id.remote_join_edit)?.text.toString()
                if (gameId.length != 6) toast(R.string.toast_online_not_exist)
                else {
                    buttonPositive.isEnabled = false
                    joinOnlineGame(gameId, afterSuccess = { isHost ->
                        createListener(gameId, onChange = { data ->
                            if (handshakeComplete)
                                onDbEntryChange(data)
                            else if(isHost && data.idRemote.isEmpty())
                                removeOnlineGame(gameId)
                            else if(data.history.isNotEmpty()) {
                                handshakeComplete = true

                                // Create game
                                val history = data.history
                                val nextPlayX = history.size % 2 == 1
                                val start = if (isHost) data.hostIsX else !data.hostIsX
                                val swap = start xor nextPlayX
                                newRemote(swap, history, gameId)

                                // Process undo request (if it exists)
                                if ((isHost && data.undoRemote) || (!isHost && data.undoHost))
                                    undo(ask = true)

                                // Close dialog
                                destroyOnDismiss = false
                                newDialog.dismiss()
                            }
                        })
                    }, afterFail = { errMsgRes ->
                        buttonPositive.isEnabled = true
                        toast(errMsgRes)
                    })
                }
            }
        }

        // Make tabs change viewpager position
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewPager.currentItem = tab?.position ?: 0
            }
        })
    }
}

class LinkView(context: Context, attrs: AttributeSet) : AppCompatTextView(context, attrs) {
    init {
        movementMethod = LinkMovementMethod.getInstance()
        text = HtmlCompat.fromHtml(text.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}
