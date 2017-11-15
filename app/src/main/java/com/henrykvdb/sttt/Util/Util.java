package com.henrykvdb.sttt.Util;

import android.app.ActivityManager;
import android.content.Context;
import com.flaghacker.uttt.common.Board;

public class Util {
	public static String getString(Context context, int resId) //TODO inline
	{
		return context.getString(resId);
	}

	public static boolean isServiceRunning(Class<?> serviceClass, Context context) {
		ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (serviceClass.getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	public static boolean isValidBoard(Board cBoard, Board newBoard) {
		if (!cBoard.availableMoves().contains(newBoard.getLastMove()))
			return false;

		Board verifyBoard = cBoard.copy();
		verifyBoard.play(newBoard.getLastMove());

		return verifyBoard.equals(newBoard);
	}
}
