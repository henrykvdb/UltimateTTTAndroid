package com.henrykvdb.sttt;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

public class Util
{
	public static void sendFeedback(Context context)
	{
		String deviceInfo = "\n /** please do not remove this block, technical info: "
				+ "os version: " + System.getProperty("os.version")
				+ "(" + android.os.Build.VERSION.INCREMENTAL + "), API: " + android.os.Build.VERSION.SDK_INT;
		try
		{
			PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			deviceInfo += ", app version: " + pInfo.versionName;
		}
		catch (PackageManager.NameNotFoundException e)
		{
			e.printStackTrace();
		}
		deviceInfo += "**/";

		Intent send = new Intent(Intent.ACTION_SENDTO);
		Uri uri = Uri.parse("mailto:" + Uri.encode("henrykdev@gmail.com") +
				"?subject=" + Uri.encode("Feedback") +
				"&body=" + Uri.encode(deviceInfo));

		send.setData(uri);
		context.startActivity(Intent.createChooser(send, "Send feedback"));
	}

	public static void share(Context context)
	{
		Intent i = new Intent(Intent.ACTION_SEND);
		i.setType("text/plain");
		i.putExtra(Intent.EXTRA_SUBJECT, context.getResources().getString(R.string.app_name_long));
		i.putExtra(Intent.EXTRA_TEXT, "Hey, let's play " + context.getResources().getString(R.string.app_name_long)
				+ " together! https://play.google.com/store/apps/details?id=Place.Holder"); //TODO replace
		context.startActivity(Intent.createChooser(i, "choose one"));
	}
}
