package com.henrykvdb.sttt

import android.graphics.Color

object DrawSettings {
    //PLAYER color settings (X)
    const val xColor = Color.BLUE
    val xColorDarker = Color.rgb(0, 0, 230)
    val xColorDarkest = Color.rgb(0, 0, 200)
    val xColorLight = Color.rgb(75, 155, 255)

    //ENEMY color settings (O)
    const val oColor = Color.RED
    val oColorDarker = Color.rgb(230, 0, 0)
    val oColorDarkest = Color.rgb(200, 0, 0)
    val oColorLight = Color.rgb(255, 155, 75)

    //Availability color settings
    val unavailableColor = Color.argb(50, 136, 136, 136)
    const val symbolTransparency = 60 and 0xff shl 24

    //Symbol stroke width
    const val tileSymbolStroke = 16f / 984
    const val macroSymbolStroke = 40f / 984
    const val wonSymbolStroke = 120f / 984

    //Grid-line settings
    const val gridColor = Color.BLACK
    const val bigGridStroke = 8f / 984
    const val smallGridStroke = 1f / 984

    //Other settings
    const val whiteSpace = 0.02f
    const val borderX = 0.10f / 9
    const val borderO = 0.15f / 9
}
