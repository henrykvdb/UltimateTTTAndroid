package com.henrykvdb;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import com.flaghacker.uttt.bots.RandomBot;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;
import com.henrykvdb.utt.R;

public class MainActivity extends AppCompatActivity
{

	BoardView boardView;
	Game game;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setLayout();
		boardView = (BoardView) findViewById(R.id.boardView);

		newGame();
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

	public void newGame()
	{
		AndroidBot ab = new AndroidBot();
		boardView.setAndroidBot(ab);

		newGame(ab,ab);
	}

	public void botGame()
	{
		AndroidBot ab = new AndroidBot();
		boardView.setAndroidBot(ab);

		newGame(ab,new RandomBot());
	}

	public void newGame(Bot p1, Bot p2)
	{
		boardView.setBoard(new Board());

		if (game != null)
			game.close();

		game = new Game(boardView, p1, p2);
		game.run();
	}

	public void botGameClicked(View view)
	{
		botGame();
	}

	public void newGameClicked(View view)
	{
		newGame();
	}
}
