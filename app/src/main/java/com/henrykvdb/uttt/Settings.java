package com.henrykvdb.uttt;

import android.graphics.Color;

public class Settings
{
	//ENEMY & PLAYER settings
	private int xColor = Color.BLUE;
	private int xColorDark = Color.rgb(0, 0, 230);
	private int oColor = Color.RED;
	private int oColorDark = Color.rgb(230, 0, 0);
	private float playerTileSymbolStroke = 16f / 984;
	private float playerMacroSymbolStroke = 40f / 984;

	//Availability color settings
	private int availableColor = Color.rgb(255, 255, 100);
	private int unavailableColor = Color.GRAY;

	//Grid-line settings
	private int gridColor = Color.BLACK;
	private float bigGridStroke = 8f / 984;
	private float smallGridStroke = 1f / 984;

	//Other settings
	private int unavailableAlpha = 50;
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

	public int xColorDark()
	{
		return xColorDark;
	}

	public void setxColorDark(int xColorDark)
	{
		this.xColorDark = xColorDark;
	}

	public int oColor()
	{
		return oColor;
	}

	public void setoColor(int oColor)
	{
		this.oColor = oColor;
	}

	public int oColorDark()
	{
		return oColorDark;
	}

	public void setoColorDark(int oColorDark)
	{
		this.oColorDark = oColorDark;
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

	public int gridColor()
	{
		return gridColor;
	}

	public void setGridColor(int gridColor)
	{
		this.gridColor = gridColor;
	}

	public int unavailableAlpha()
	{
		return unavailableAlpha;
	}

	public void setUnavailableAlpha(int unavailableAlpha)
	{
		this.unavailableAlpha = unavailableAlpha;
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

	public float playerTileSymbolStroke()
	{
		return playerTileSymbolStroke;
	}

	public void setPlayerTileSymbolStroke(float playerTileSymbolStroke)
	{
		this.playerTileSymbolStroke = playerTileSymbolStroke;
	}

	public float playerMacroSymbolStroke()
	{
		return playerMacroSymbolStroke;
	}

	public void setPlayerMacroSymbolStroke(float playerMacroSymbolStroke)
	{
		this.playerMacroSymbolStroke = playerMacroSymbolStroke;
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
}
