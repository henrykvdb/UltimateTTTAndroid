package com.henrykvdb.sttt.Util;

import android.content.Context;
import android.content.Intent;
import com.flaghacker.uttt.common.Coord;
import com.henrykvdb.sttt.*;

public class IntentUtil
{
	public static void sendMove(Context context, MainActivity.Source src, Coord move)
	{
		Intent i = new Intent(Constants.INTENT_MOVE);
		i.putExtra(Constants.INTENT_DATA_FIRST,src);
		i.putExtra(Constants.INTENT_DATA_SECOND,move);
		context.sendBroadcast(i);
	}

	public static void sendNewGame(Context context, GameState gs)
	{
		Intent i = new Intent(Constants.INTENT_NEWGAME);
		i.putExtra(Constants.INTENT_DATA_FIRST,gs);
		context.sendBroadcast(i);
	}

	public static void sendToast(Context context, String text)
	{
		Intent i = new Intent(Constants.INTENT_TOAST);
		i.putExtra(Constants.INTENT_DATA_FIRST,text);
		context.sendBroadcast(i);
	}

	public static void sendUndo(Context context, Boolean force)
	{
		Intent i = new Intent(Constants.INTENT_UNDO);
		i.putExtra(Constants.INTENT_DATA_FIRST,force);
		context.sendBroadcast(i);
	}

	public static void sendTurnLocal(Context context)
	{
		context.sendBroadcast(new Intent(Constants.INTENT_TURNLOCAL));
	}
}
