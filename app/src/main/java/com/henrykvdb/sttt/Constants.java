package com.henrykvdb.sttt;

public class Constants
{
	//MainActivity UI broadcast receiver name and type
	public static final String EVENT_UI = "EVENT_UI";
	public static final String EVENT_TYPE = "EVENT_TYPE";

	//Types of broadcast messages
	public static final String TYPE_TOAST = "toast";
	public static final String TYPE_TITLE = "title";
	public static final String TYPE_SUBTITLE = "subtitle";
	public static final String TYPE_ALLOW_INCOMING_BT = "allowIncomingBt";

	//Data included in the broadcasts
	public static final String DATA_STRING = "string";
	public static final String DATA_BOOLEAN_ALLOW = "allow";
	public static final String DATA_BOOLEAN_SILENT = "silent";

	//Other request codes
	public static final int REQUEST_START_BTPICKER = 100;

	//Permission request codes
	public static final int REQUEST_ENABLE_BT = 200;
	public static final int REQUEST_ENABLE_DSC = 201;
	public static final int REQUEST_COARSE_LOCATION = 202;

	//Other
	public static final String STARTED_WITH_BT_KEY = "STARTED_WITH_BT_KEY";
}
