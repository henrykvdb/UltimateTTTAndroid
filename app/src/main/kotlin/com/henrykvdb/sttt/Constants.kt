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
const val INTENT_TURNLOCAL = "INTENT_TURNLOCAL"
const val INTENT_DATA = "INTENT_DATA"

//Keys for saving to bundle
const val GAMESTATE_KEY = "GAMESTATE_KEY"
