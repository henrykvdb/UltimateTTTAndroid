package com.henrykvdb.sttt.Util;

import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import com.henrykvdb.sttt.*;

import java.util.Random;

public class DialogUtil {
	private final static int DAYS_UNTIL_PROMPT = 3;      //Min number of days needed before asking for rating
	private final static int LAUNCHES_UNTIL_PROMPT = 3;  //Min number of launches before asking for rating

	private static final String DONT_SHOW_AGAIN = "dontshowagain";
	private static final String DATE_FIRST_LAUNCH = "date_firstlaunch";
	private static final String LAUNCH_COUNT = "launch_count";

	// Prevent dialog destroy when orientation changes
	public static AlertDialog keepDialog(AlertDialog dialog) {
		try {
			WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
			lp.copyFrom(dialog.getWindow().getAttributes());
			lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
			lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
			dialog.getWindow().setAttributes(lp);
		}
		catch (Throwable t) {
			//NOP
		}
		return dialog;
	}

	public static View newTitle(Context context, String title) {
		View v = View.inflate(context, R.layout.dialog_title, null);
		((TextView) v.findViewById(R.id.action_bar_title)).setText(title);
		return v;
	}

	public static View newLoadTitle(Context context, String title) {
		View v = View.inflate(context, R.layout.dialog_title_load, null);
		((TextView) v.findViewById(R.id.action_bar_title)).setText(title);
		return v;
	}

	public static void feedbackSender(Context context) {
		String deviceInfo = "\n /** please do not remove this block, technical info: "
				+ "os version: " + System.getProperty("os.version")
				+ "(" + android.os.Build.VERSION.INCREMENTAL + "), API: " + android.os.Build.VERSION.SDK_INT;
		try {
			PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			deviceInfo += ", app version: " + pInfo.versionName;
		}
		catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}
		deviceInfo += "**/";

		Intent send = new Intent(Intent.ACTION_SENDTO);
		Uri uri = Uri.parse("mailto:" + Uri.encode("henrykdev@gmail.com") +
				"?subject=" + Uri.encode("Feedback") +
				"&body=" + Uri.encode(deviceInfo));

		send.setData(uri);
		context.startActivity(Intent.createChooser(send, context.getString(R.string.send_feedback)));
	}

	public static void shareDialog(Context context) {
		Intent i = new Intent(Intent.ACTION_SEND);
		i.setType("text/plain");
		i.putExtra(Intent.EXTRA_SUBJECT, context.getResources().getString(R.string.app_name_long));
		i.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.lets_play_together) + " " + context.getString(R.string.market_url));
		context.startActivity(Intent.createChooser(i, context.getString(R.string.choose_one)));
	}

	public static void aboutDialog(Context context) {
		View layout = View.inflate(context, R.layout.dialog_about, null);

		try {
			PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			((TextView) layout.findViewById(R.id.versionName_view))
					.setText(context.getResources().getText(R.string.app_name_long) + "\n" + context.getString(R.string.version) + " " + pInfo.versionName);
		}
		catch (PackageManager.NameNotFoundException e) {
			((TextView) layout.findViewById(R.id.versionName_view))
					.setText(context.getResources().getText(R.string.app_name_long));
		}

		keepDialog(new AlertDialog.Builder(context)
				.setCustomTitle(newTitle(context, context.getString(R.string.about)))
				.setView(layout)
				.setPositiveButton(context.getString(R.string.close), (dialog1, which) -> dialog1.dismiss())
				.show());
	}

	public static void rateDialog(Context context) {
		SharedPreferences prefs = context.getSharedPreferences("APP_RATER", 0);

		if (prefs.getBoolean(DONT_SHOW_AGAIN, false))
			return;

		SharedPreferences.Editor editor = prefs.edit();

		// Increment launch counter
		long launch_count = prefs.getLong(LAUNCH_COUNT, 0) + 1;
		editor.putLong(LAUNCH_COUNT, launch_count);

		// Get date of first launch
		Long date_firstLaunch = prefs.getLong(DATE_FIRST_LAUNCH, 0);
		if (date_firstLaunch == 0) {
			date_firstLaunch = System.currentTimeMillis();
			editor.putLong(DATE_FIRST_LAUNCH, date_firstLaunch);
		}

		// Wait at least n days before opening
		if (launch_count >= LAUNCHES_UNTIL_PROMPT
				&& System.currentTimeMillis() >= date_firstLaunch + (DAYS_UNTIL_PROMPT * 24 * 60 * 60 * 1000)) {
			DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
				switch (which) {
					case DialogInterface.BUTTON_POSITIVE:
						editor.putBoolean(DONT_SHOW_AGAIN, true);
						editor.apply();
						Intent goToMarket = new Intent(Intent.ACTION_VIEW,
								Uri.parse("market://details?id=" + context.getPackageName()));
						goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
						goToMarket.addFlags((Build.VERSION.SDK_INT >= 21)
								? Intent.FLAG_ACTIVITY_NEW_DOCUMENT
								: Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
						context.startActivity(goToMarket);
						break;
					case DialogInterface.BUTTON_NEUTRAL:
						break;
					case DialogInterface.BUTTON_NEGATIVE:
						editor.putBoolean(DONT_SHOW_AGAIN, true);
						editor.apply();
						break;
				}
			};

			keepDialog(new AlertDialog.Builder(context)
					.setMessage(context.getString(R.string.rate_message))
					.setCustomTitle(newTitle(context, context.getString(R.string.rate_app)))
					.setPositiveButton(context.getString(R.string.rate), dialogClickListener)
					.setNeutralButton(context.getString(R.string.later), dialogClickListener)
					.setNegativeButton(context.getString(R.string.no_thanks), dialogClickListener)
					.show());
		}

		editor.apply();
	}

	public static void newLocal(Callback<Boolean> callback, Context context) {
		DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
			switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					callback.callback(true);
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					dialog.dismiss();
					break;
			}
		};

		DialogUtil.keepDialog(new AlertDialog.Builder(context)
				.setCustomTitle(newTitle(context, context.getString(R.string.new_local_title)))
				.setMessage(context.getString(R.string.new_local_desc))
				.setPositiveButton(context.getString(R.string.start), dialogClickListener)
				.setNegativeButton(context.getString(R.string.close), dialogClickListener).show());
	}

	public static void newAi(Callback<GameState> callback, Context context) {
		final boolean[] swapped = new boolean[1];

		View layout = View.inflate(context, R.layout.dialog_ai, null);
		RadioGroup beginner = (RadioGroup) layout.findViewById(R.id.start_radio_group);
		beginner.setOnCheckedChangeListener((group, checkedId) ->
				swapped[0] = checkedId != R.id.start_you && (checkedId == R.id.start_ai || new Random().nextBoolean()));

		DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
			switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					callback.callback(GameState.builder()
							.ai(new MMBot(((SeekBar) layout.findViewById(R.id.difficulty)).getProgress()))
							.swapped(swapped[0]).build());
					break;
				case DialogInterface.BUTTON_NEGATIVE:
					dialog.dismiss();
					break;
			}
		};

		DialogUtil.keepDialog(new AlertDialog.Builder(context)
				.setView(layout).setCustomTitle(newTitle(context, context.getString(R.string.new_ai_title)))
				.setPositiveButton(context.getString(R.string.start), dialogClickListener)
				.setNegativeButton(context.getString(R.string.close), dialogClickListener).show());
	}
}
