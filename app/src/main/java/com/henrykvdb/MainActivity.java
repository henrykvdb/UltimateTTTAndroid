package com.henrykvdb;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import com.flaghacker.uttt.bots.RandomBot;
import com.henrykvdb.utt.R;

public class MainActivity extends AppCompatActivity
{

	BoardView boardView;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setLayout();

		boardView = (BoardView) findViewById(R.id.boardView);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);

		setLayout();
	}

	private void setLayout()
	{
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
			setContentView(R.layout.vertical);

		} else {
			setContentView(R.layout.horizontal);
		}
	}

	public void newGame(View view)
	{
		boardView.newGame();
	}

	public void botGame(View view)
	{
		boardView.newGame(new RandomBot());
	}
}
