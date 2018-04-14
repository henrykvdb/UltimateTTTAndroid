/*
 * This file is part of Super Tic Tac Toe.
 * Copyright (C) 2018 Henryk Van der Bruggen <henrykdev@gmail.com>
 *
 * Super Tic Tac Toe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Super Tic Tac Toe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Super Tic Tac Toe.  If not, see <http://www.gnu.org/licenses/>.
 */

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
