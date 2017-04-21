package com.henrykvdb.sttt;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

public class RateDialog extends DialogFragment
{
	private final static int DAYS_UNTIL_PROMPT = 3;//Min number of days
	private final static int LAUNCHES_UNTIL_PROMPT = 3;//Min number of launches

	public static void rate(Context context, android.support.v4.app.FragmentManager fragmentManager)
	{
		SharedPreferences prefs = context.getSharedPreferences("APP_RATER", 0);

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
		if (launch_count >= LAUNCHES_UNTIL_PROMPT)
			if (System.currentTimeMillis() >= date_firstLaunch + (DAYS_UNTIL_PROMPT * 24 * 60 * 60 * 1000))
				new RateDialog().show(fragmentManager, null);

		editor.apply();
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		DialogInterface.OnClickListener dialogClickListener = (dialog, which) ->
		{
			switch (which)
			{
				case DialogInterface.BUTTON_POSITIVE: //TODO replace rate link
					getActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + "com.example.name")));
					break;
				case DialogInterface.BUTTON_NEUTRAL:
					break;
				case DialogInterface.BUTTON_NEGATIVE:
					SharedPreferences.Editor editor = getActivity().getSharedPreferences("APP_RATER", 0).edit();
					editor.putBoolean("dontshowagain", true);
					editor.commit();
					break;
			}
		};

		return new AlertDialog.Builder(getActivity())
				.setMessage("If you enjoy using " + getActivity().getResources().getString(R.string.app_name_long)
						+ ", please take a moment to rate it. Thanks for your support!")
				.setTitle("Rate app")
				.setPositiveButton("Rate", dialogClickListener)
				.setNeutralButton("Later", dialogClickListener)
				.setNegativeButton("No, thanks", dialogClickListener)
				.show();
	}
}