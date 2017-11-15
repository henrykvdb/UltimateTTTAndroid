package com.henrykvdb.sttt;

public class Constants {
	//Intents
	public static final String INTENT_TOAST = "INTENT_TOAST";       //String text
	public static final String INTENT_NEWGAME = "INTENT_NEWGAME";   //GameState requeststate
	public static final String INTENT_MOVE = "INTENT_MOVE";         //Source source, Coord move
	public static final String INTENT_UNDO = "INTENT_UNDO";         //Boolean force
	public static final String INTENT_STOP_BT_SERVICE = "INTENT_STOP_BT_SERVICE";
	public static final String INTENT_TURNLOCAL = "INTENT_TURNLOCAL";

	//Intents data types
	public static final String INTENT_DATA_FIRST = "INTENT_DATA_FIRST";
	public static final String INTENT_DATA_SECOND = "INTENT_DATA_SECOND";

	//Request codes
	public static final int REQUEST_ENABLE_BT = 100;        //Permission required to enable Bluetooth
	public static final int REQUEST_ENABLE_DSC = 101;       //Permission required to enable discoverability
	public static final int REQUEST_COARSE_LOCATION = 102;  //Permission required to search nearby devices

	//Keys for saving to bundle
	public static final String GAMESTATE_KEY = "GAMESTATE_KEY";
	public static final String STARTED_WITH_BT_KEY = "STARTED_WITH_BT_KEY";

	//Notification ID's
	public static final int BT_STILL_RUNNING = 1;

	//Misc
	public static final String LOG_TAG = "DEBUGLOG";
}
