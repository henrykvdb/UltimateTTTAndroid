package com.henrykvdb.sttt.DialogUtil;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import com.henrykvdb.sttt.Util.Callback;

public class AskDialog
{
	private AlertDialog btAskDialog;

	public AskDialog(String message, Callback<Boolean> callBack, Activity activity)
	{
		askUser(message, callBack, activity);
	}

	public void askUser(String message, Callback<Boolean> callBack, Activity activity)
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

			BasicDialogs.keepDialog(btAskDialog);
		});
	}
}
