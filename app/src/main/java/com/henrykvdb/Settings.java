package com.henrykvdb;

import android.graphics.Color;

public class Settings
{
	//Colors of the ui elements
	private int xColor = Color.BLUE;
	private int xColorDark = Color.rgb(0, 0, 230);
	private int oColor = Color.RED;
	private int oColorDark = Color.rgb(230, 0, 0);
	private int availableColor = Color.rgb(255, 255, 100);
	private int unavailableColor = Color.GRAY;
	private int gridColor = Color.BLACK;

	//Other settings
	private int unavailableAlpha = 50;
	private float relativeWhiteSpace = 0.02f;
	private float relativeBorderX = 0.10f / 9;
	private float relativeBorderO = 0.15f / 9;

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

	public float relativeWhiteSpace()
	{
		return relativeWhiteSpace;
	}

	public void setRelativeWhiteSpace(float relativeWhiteSpace)
	{
		this.relativeWhiteSpace = relativeWhiteSpace;
	}

	public float relativeBorderX()
	{
		return relativeBorderX;
	}

	public void setRelativeBorderX(float relativeBorderX)
	{
		this.relativeBorderX = relativeBorderX;
	}

	public float relativeBorderO()
	{
		return relativeBorderO;
	}

	public void setRelativeBorderO(float relativeBorderO)
	{
		this.relativeBorderO = relativeBorderO;
	}
}
