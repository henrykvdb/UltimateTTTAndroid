package com.henrykvdb.sttt;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Random;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

public class Dialogs
{
	private final Activity activity;

	public Dialogs(Activity activity)
	{
		this.activity = activity;
	}

	public void about()
	{
		LayoutInflater inflater = (LayoutInflater) activity.getSystemService(LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.dialog_about, (ViewGroup) activity.findViewById(R.id.dialog_about_layout));

		try
		{
			PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
			((TextView) layout.findViewById(R.id.versionName_view))
					.setText(activity.getResources().getText(R.string.app_name_long) + "\nVersion " + pInfo.versionName);
		}
		catch (PackageManager.NameNotFoundException e)
		{
			((TextView) layout.findViewById(R.id.versionName_view))
					.setText(activity.getResources().getText(R.string.app_name_long));
		}

		keepDialog(new AlertDialog.Builder(activity)
				.setTitle("About")
				.setView(layout)
				.setPositiveButton("Close", (dialog1, which) -> dialog1.dismiss())
				.show());
	}

	private final static int DAYS_UNTIL_PROMPT = 3;//Min number of days
	private final static int LAUNCHES_UNTIL_PROMPT = 3;//Min number of launches

	public void rate()
	{
		SharedPreferences prefs = activity.getSharedPreferences("APP_RATER", 0);

		if (prefs.getBoolean("dontshowagain", false))
			return;

		SharedPreferences.Editor editor = prefs.edit();

		// Increment launch counter
		long launch_count = prefs.getLong("launch_count", 0) + 1;
		editor.putLong("launch_count", launch_count);

		// Get date of first launch
		Long date_firstLaunch = prefs.getLong("date_firstlaunch", 0);
		if (date_firstLaunch == 0)
		{
			date_firstLaunch = System.currentTimeMillis();
			editor.putLong("date_firstlaunch", date_firstLaunch);
		}

		// Wait at least n days before opening
		if (launch_count >= LAUNCHES_UNTIL_PROMPT
				&& System.currentTimeMillis() >= date_firstLaunch + (DAYS_UNTIL_PROMPT * 24 * 60 * 60 * 1000))
		{
			DialogInterface.OnClickListener dialogClickListener = (dialog, which) ->
			{
				switch (which)
				{
					case DialogInterface.BUTTON_POSITIVE:
						Intent goToMarket = new Intent(Intent.ACTION_VIEW,
								Uri.parse("market://details?id=" + activity.getPackageName()));
						goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
						goToMarket.addFlags((Build.VERSION.SDK_INT >= 21)
								? Intent.FLAG_ACTIVITY_NEW_DOCUMENT
								: Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
						activity.startActivity(goToMarket);
						break;
					case DialogInterface.BUTTON_NEUTRAL:
						break;
					case DialogInterface.BUTTON_NEGATIVE:
						editor.putBoolean("dontshowagain", true);
						editor.apply();
						break;
				}
			};

			keepDialog(new AlertDialog.Builder(activity)
					.setMessage("If you enjoy using " + activity.getResources().getString(R.string.app_name_long)
							+ ", please take a moment to rate it. Thanks for your support!")
					.setTitle("Rate app")
					.setPositiveButton("Rate", dialogClickListener)
					.setNeutralButton("Later", dialogClickListener)
					.setNegativeButton("No, thanks", dialogClickListener)
					.show());
		}

		editor.apply();
	}

	public void newLocal(Callback<Boolean> callback)
	{
		DialogInterface.OnClickListener dialogClickListener = (dialog, which) ->
		{
			switch (which)
			{
				case DialogInterface.BUTTON_POSITIVE:
					callback.callback(true);
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					dialog.dismiss();
					break;
			}
		};

		keepDialog(new AlertDialog.Builder(activity)
				.setTitle("Start a new game?")
				.setMessage("This wil create a new local two player game.")
				.setPositiveButton("Start", dialogClickListener)
				.setNegativeButton("Close", dialogClickListener)
				.show());
	}

	public void newAi(Callback<GameState> callback)
	{
		final boolean[] swapped = new boolean[1];

		LayoutInflater inflater = (LayoutInflater) activity.getSystemService(LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.dialog_ai, (ViewGroup) activity.findViewById(R.id.new_ai_layout));

		RadioGroup beginner = (RadioGroup) layout.findViewById(R.id.start_radio_group);
		beginner.setOnCheckedChangeListener((group, checkedId) ->
				swapped[0] = checkedId != R.id.start_you && (checkedId == R.id.start_ai || new Random().nextBoolean()));

		DialogInterface.OnClickListener dialogClickListener = (dialog, which) ->
		{
			switch (which)
			{
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

		keepDialog(new AlertDialog.Builder(activity)
				.setView(layout)
				.setTitle("Start a new ai game?")
				.setPositiveButton("Start", dialogClickListener)
				.setNegativeButton("Close", dialogClickListener)
				.show());
	}

	public void btPicker(Callback<String> callback)
	{
		final boolean[] swapped = new boolean[1];

		LayoutInflater inflater = (LayoutInflater) activity.getSystemService(LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.dialog_ai, (ViewGroup) activity.findViewById(R.id.new_ai_layout));

		RadioGroup beginner = (RadioGroup) layout.findViewById(R.id.start_radio_group);
		beginner.setOnCheckedChangeListener((group, checkedId) ->
				swapped[0] = checkedId != R.id.start_you && (checkedId == R.id.start_ai || new Random().nextBoolean()));

		DialogInterface.OnClickListener dialogClickListener = (dialog, which) ->
		{
			switch (which)
			{
				case DialogInterface.BUTTON_POSITIVE:
					//callback.callback(); //TODO CALLBACK
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					dialog.dismiss();
					break;
			}
		};


		//Create custom title
		TypedValue outValue = new TypedValue();
		activity.getTheme().resolveAttribute(R.attr.alertDialogTheme, outValue, true);
		TextView tv = new TextView(activity,null,outValue.resourceId);
		tv.setText("This is a title");

		keepDialog(new AlertDialog.Builder(activity)
				.setView(layout)
				.setTitle(Html.fromHtml("<font color='#000000'>Start a new ai game?</font>"))
				//.setCustomTitle(tv)
				.setPositiveButton("Start", dialogClickListener)
				.setNegativeButton("Close", dialogClickListener)
				.show());
	}

	private AlertDialog btAskDialog;

	public void askUser(String message, com.henrykvdb.sttt.Callback<Boolean> callBack)
	{
		DialogInterface.OnClickListener dialogClickListener = (dialog, which) ->
		{
			switch (which)
			{
				case DialogInterface.BUTTON_POSITIVE:
					callBack.callback(true);
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					callBack.callback(false);
					break;
			}
		};

		new Handler(activity.getMainLooper()).post(() ->
		{
			if (btAskDialog != null && btAskDialog.isShowing())
				btAskDialog.dismiss();

			btAskDialog = new AlertDialog.Builder(activity).setMessage(message)
					.setPositiveButton("Yes", dialogClickListener)
					.setNegativeButton("No", dialogClickListener)
					.setOnDismissListener(dialogInterface -> callBack.callback(false))
					.show();

			keepDialog(btAskDialog);
		});
	}

	// Prevent dialog dismiss when orientation changes
	public static void keepDialog(Dialog dialog)
	{
		try
		{
			WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
			lp.copyFrom(dialog.getWindow().getAttributes());
			lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
			lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
			dialog.getWindow().setAttributes(lp);
		}
		catch (Throwable t)
		{
			//NOP
		}
	}
}
