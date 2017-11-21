package com.henrykvdb.sttt

//Intents
const val INTENT_TOAST = "INTENT_TOAST"       //String text
const val INTENT_NEWGAME = "INTENT_NEWGAME"   //GameState requeststate
const val INTENT_MOVE = "INTENT_MOVE"         //Source source, Coord move
const val INTENT_UNDO = "INTENT_UNDO"         //Boolean force
const val INTENT_STOP_BT_SERVICE = "INTENT_STOP_BT_SERVICE"
const val INTENT_TURNLOCAL = "INTENT_TURNLOCAL"

//Intents data types
const val INTENT_DATA_FIRST = "INTENT_DATA_FIRST"
const val INTENT_DATA_SECOND = "INTENT_DATA_SECOND"

//Request codes
const val REQUEST_ENABLE_BT = 100        //Permission required to enable Bluetooth
const val REQUEST_ENABLE_DSC = 101       //Permission required to enable discoverability
const val REQUEST_COARSE_LOCATION = 102  //Permission required to search nearby devices

//Keys for saving to bundle
const val BTSERVICE_STARTED_KEY = "BTSERVICE_STARTED_KEY"
const val GAMESTATE_KEY = "GAMESTATE_KEY"
const val STARTED_WITH_BT_KEY = "STARTED_WITH_BT_KEY"

//Notification ID's
const val BT_STILL_RUNNING = 1

//Misc
const val LOG_TAG = "DEBUGLOG"