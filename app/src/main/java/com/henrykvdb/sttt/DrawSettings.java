package com.henrykvdb.sttt;

import android.graphics.Color;

public class DrawSettings {
	//PLAYER settings (X)
	public final int xColor = Color.BLUE;
	public final int xColorDarker = Color.rgb(0, 0, 230);
	public final int xColorDarkest = Color.rgb(0, 0, 200);
	public final int xColorLight = Color.rgb(75, 155, 255);

	//ENEMY settings (O)
	public final int oColor = Color.RED;
	public final int oColorDarker = Color.rgb(230, 0, 0);
	public final int oColorDarkest = Color.rgb(200, 0, 0);
	public final int oColorLight = Color.rgb(255, 155, 75);

	//Symbol stroke width
	public final float tileSymbolStroke = 16f / 984;
	public final float macroSymbolStroke = 40f / 984;
	public final float wonSymbolStroke = 120f / 984;

	//Availability color settings
	public final int unavailableColor = Color.argb(50, 136, 136, 136);
	public final int symbolTransparency = (60 & 0xff) << 24;

	//Grid-line settings
	public final int gridColor = Color.BLACK;
	public final float bigGridStroke = 8f / 984;
	public final float smallGridStroke = 1f / 984;

	//Other settings
	public final float whiteSpace = 0.02f;
	public final float borderX = 0.10f / 9;
	public final float borderO = 0.15f / 9;
}
