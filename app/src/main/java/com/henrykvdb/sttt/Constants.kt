package com.henrykvdb.sttt

//Intents
const val INTENT_STOP_BT_SERVICE = "INTENT_STOP_BT_SERVICE"

//Request codes
const val REQUEST_ENABLE_BT = 100        //Permission required to enable Bluetooth
const val REQUEST_ENABLE_DSC = 101       //Permission required to enable discoverability
const val REQUEST_COARSE_LOCATION = 102  //Permission required to search nearby devices

//Keys for saving to bundle
const val BTSERVICE_STARTED_KEY = "BTSERVICE_STARTED_KEY"
const val GAMESTATE_KEY = "GAMESTATE_KEY"
const val KEEP_BT_ON_KEY = "KEEP_BT_ON_KEY"

//Notification ID's
const val BT_STILL_RUNNING = 1

//Misc
const val LOG_TAG = "DEBUGLOG"