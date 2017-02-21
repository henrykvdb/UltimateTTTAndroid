package com.henrykvdb.uttt;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import com.flaghacker.uttt.bots.RandomBot;
import com.henrykvdb.utt.R;

public class MainActivity extends AppCompatActivity
{
	private static final String STATE_KEY = "game";

	private Game game;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		if (savedInstanceState != null)
		{
			GameState state = (GameState) savedInstanceState.getSerializable(STATE_KEY);
			game = new Game(state,(BoardView) findViewById(R.id.boardView),new AndroidBot());
		}
		else
		{
			game = newGame();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		if (game!=null)
			game.close();

		super.onSaveInstanceState(outState);
		outState.putSerializable(STATE_KEY, game.getState());
	}

	@Override
	protected void onResume()
	{

		if (game!=null && !game.getState().running())
			game.run();

		super.onResume();
	}

	@Override
	protected void onPause()
	{
		if (game!=null)
			game.close();

		super.onPause();
	}

	public void botGameClicked(View view)
	{
		game = botGame();
	}

	public void newGameClicked(View view)
	{
		game = newGame();
	}

	private Game newGame()
	{
		if (game!=null)
			game.close();

		AndroidBot androidBot = new AndroidBot();
		return Game.newGame((BoardView) findViewById(R.id.boardView),androidBot, androidBot);
	}

	private Game botGame()
	{
		if (game!=null)
			game.close();

		AndroidBot androidBot = new AndroidBot();
		return Game.newGame((BoardView) findViewById(R.id.boardView),androidBot, new RandomBot());
	}
}
