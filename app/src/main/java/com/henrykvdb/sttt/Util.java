package com.henrykvdb.sttt;

import android.app.Dialog;
import android.view.WindowManager;

class Util
{
	// Prevent dialog dismiss when orientation changes
	public static void doKeepDialog(Dialog dialog)
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
