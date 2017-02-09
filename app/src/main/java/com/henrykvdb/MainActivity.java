package com.henrykvdb;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import com.henrykvdb.utt.R;

public class MainActivity extends AppCompatActivity
{
	private static final String GH_KEY = "gh";

	private GameHandler gh;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		if (savedInstanceState != null)
		{
			gh = (GameHandler) savedInstanceState.getSerializable(GH_KEY);

			gh.setBoardView((BoardView) findViewById(R.id.boardView));
			gh.redraw();
		}
		else
		{
			gh = new GameHandler((BoardView) findViewById(R.id.boardView));
			gh.newGame(); //Start default game TODO make options window
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putSerializable(GH_KEY, gh);
	}

	public void botGameClicked(View view)
	{
		gh.botGame();
	}

	public void newGameClicked(View view)
	{
		gh.newGame();
	}
}
