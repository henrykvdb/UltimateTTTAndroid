package com.henrykvdb.sttt

//Intents
const val INTENT_TOAST = "INTENT_TOAST"       //String: text to toast
const val INTENT_NEWGAME = "INTENT_NEWGAME"   //GameState: requested new gamestate
const val INTENT_MOVE = "INTENT_MOVE"         //Byte: move played by the remote
const val INTENT_UNDO = "INTENT_UNDO"         //Boolean: forced undo or not
const val INTENT_STOP_BT_SERVICE = "INTENT_STOP_BT_SERVICE"
const val INTENT_TURNLOCAL = "INTENT_TURNLOCAL"
const val INTENT_DATA = "INTENT_DATA"

//Request codes
const val REQUEST_ENABLE_BT = 100        //Permission required to enable Bluetooth
const val REQUEST_ENABLE_DSC = 101       //Permission required to enable discoverability
const val REQUEST_COARSE_LOC = 102       //Permission required to search nearby devices

//Keys for saving to bundle
const val BTSERVICE_STARTED_KEY = "BTSERVICE_STARTED_KEY"
const val GAMESTATE_KEY = "GAMESTATE_KEY"
const val KEEP_BT_ON_KEY = "KEEP_BT_ON_KEY"

//Notification ID's
const val REMOTE_STILL_RUNNING = 1

//Misc
const val LOG_TAG = "DEBUGLOG"