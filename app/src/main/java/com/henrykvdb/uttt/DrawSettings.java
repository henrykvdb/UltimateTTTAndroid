package com.henrykvdb.uttt;

import android.graphics.Color;

public class DrawSettings
{
	//PLAYER settings (X)
	private int xColor = Color.BLUE;
	private int xColorDarker = Color.rgb(0, 0, 230);
	private int xColorDarkest = Color.rgb(0, 0, 200);
	private int xColorLast = Color.rgb(75, 155, 255);

	//ENEMY settings (O)
	private int oColor = Color.RED;
	private int oColorDarker = Color.rgb(230, 0, 0);
	private int oColorDarkest = Color.rgb(200, 0, 0);
	private int oColorLast = Color.rgb(255, 155, 75);

	//Symbol stroke width
	private float tileSymbolStroke = 16f / 984;
	private float macroSymbolStroke = 40f / 984;
	private float wonSymbolStroke = 120f / 984;

	//Availability color settings
	private int availableColor = Color.rgb(255, 255, 100);
	private int unavailableColor = Color.argb(50,136,136,136);
	private int symbolTransparency = (60 & 0xff) << 24;

	//Grid-line settings
	private int gridColor = Color.BLACK;
	private float bigGridStroke = 8f / 984;
	private float smallGridStroke = 1f / 984;

	//Other settings
	private float whiteSpace = 0.02f;
	private float borderX = 0.10f / 9;
	private float borderO = 0.15f / 9;

	public int xColor()
	{
		return xColor;
	}

	public void setxColor(int xColor)
	{
		this.xColor = xColor;
	}

	public int xColorDarker()
	{
		return xColorDarker;
	}

	public void setxColorDarker(int xColorDarker)
	{
		this.xColorDarker = xColorDarker;
	}

	public int xColorDarkest()
	{
		return xColorDarkest;
	}

	public void setxColorDarkest(int xColorDarkest)
	{
		this.xColorDarkest = xColorDarkest;
	}

	public int xColorLast()
	{
		return xColorLast;
	}

	public void setxColorLast(int xColorLast)
	{
		this.xColorLast = xColorLast;
	}

	public int oColor()
	{
		return oColor;
	}

	public void setoColor(int oColor)
	{
		this.oColor = oColor;
	}

	public int oColorDarker()
	{
		return oColorDarker;
	}

	public void setoColorDarker(int oColorDarker)
	{
		this.oColorDarker = oColorDarker;
	}

	public int oColorDarkest()
	{
		return oColorDarkest;
	}

	public void setoColorDarkest(int oColorDarkest)
	{
		this.oColorDarkest = oColorDarkest;
	}

	public int oColorLast()
	{
		return oColorLast;
	}

	public void setoColorLast(int oColorLast)
	{
		this.oColorLast = oColorLast;
	}

	public float tileSymbolStroke()
	{
		return tileSymbolStroke;
	}

	public void setTileSymbolStroke(float tileSymbolStroke)
	{
		this.tileSymbolStroke = tileSymbolStroke;
	}

	public float macroSymbolStroke()
	{
		return macroSymbolStroke;
	}

	public void setMacroSymbolStroke(float macroSymbolStroke)
	{
		this.macroSymbolStroke = macroSymbolStroke;
	}

	public float wonSymbolStroke()
	{
		return wonSymbolStroke;
	}

	public void setWonSymbolStroke(float wonSymbolStroke)
	{
		this.wonSymbolStroke = wonSymbolStroke;
	}

	public int availableColor()
	{
		return availableColor;
	}

	public void setAvailableColor(int availableColor)
	{
		this.availableColor = availableColor;
	}

	public int unavailableColor()
	{
		return unavailableColor;
	}

	public void setUnavailableColor(int unavailableColor)
	{
		this.unavailableColor = unavailableColor;
	}

	public int symbolTransparency()
	{
		return symbolTransparency;
	}

	public void setSymbolTransparency(int symbolTransparency)
	{
		this.symbolTransparency = symbolTransparency;
	}

	public int gridColor()
	{
		return gridColor;
	}

	public void setGridColor(int gridColor)
	{
		this.gridColor = gridColor;
	}

	public float bigGridStroke()
	{
		return bigGridStroke;
	}

	public void setBigGridStroke(float bigGridStroke)
	{
		this.bigGridStroke = bigGridStroke;
	}

	public float smallGridStroke()
	{
		return smallGridStroke;
	}

	public void setSmallGridStroke(float smallGridStroke)
	{
		this.smallGridStroke = smallGridStroke;
	}

	public float whiteSpace()
	{
		return whiteSpace;
	}

	public void setWhiteSpace(float whiteSpace)
	{
		this.whiteSpace = whiteSpace;
	}

	public float borderX()
	{
		return borderX;
	}

	public void setBorderX(float borderX)
	{
		this.borderX = borderX;
	}

	public float borderO()
	{
		return borderO;
	}

	public void setBorderO(float borderO)
	{
		this.borderO = borderO;
	}
}
