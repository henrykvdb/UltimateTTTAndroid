package com.henrykvdb.uttt;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.ViewDragHelper;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import java.lang.reflect.Field;

public class MainActivity extends AppCompatActivity
		implements NavigationView.OnNavigationItemSelectedListener
{

	private static final String STATE_KEY = "game";

	private Game game;

	private static final int REQUEST_NEW_LOCAL = 100;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initGui();

		if (savedInstanceState != null)
		{
			GameState state = (GameState) savedInstanceState.getSerializable(STATE_KEY);
			game = new Game(state, (BoardView) findViewById(R.id.boardView), new AndroidBot());
		}
		else
		{
			closeGame();
			AndroidBot androidBot = new AndroidBot();
			game = Game.newGame((BoardView) findViewById(R.id.boardView), androidBot, androidBot);
		}
	}

	private void initGui()
	{
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		try{
			Field mDragger = drawer.getClass().getDeclaredField(
					"mLeftDragger");
			mDragger.setAccessible(true);
			ViewDragHelper draggerObj = (ViewDragHelper) mDragger
					.get(drawer);
			Field mEdgeSize = draggerObj.getClass().getDeclaredField(
					"mEdgeSize");
			mEdgeSize.setAccessible(true);
			int edge = mEdgeSize.getInt(draggerObj);
			mEdgeSize.setInt(draggerObj, edge * 4);
		}
		catch (IllegalAccessException | NoSuchFieldException e)
		{
			e.printStackTrace();
		}

		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
				this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
		drawer.setDrawerListener(toggle);
		toggle.syncState();

		NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);
	}

	@Override
	public void onBackPressed()
	{
		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		if (drawer.isDrawerOpen(GravityCompat.START))
			drawer.closeDrawer(GravityCompat.START);
		else
			super.onBackPressed();
	}

	@Override
	public boolean onNavigationItemSelected(MenuItem item)
	{
		// Handle navigation view item clicks here.
		int id = item.getItemId();

		if (id == R.id.nav_local)
		{
			Intent serverIntent = new Intent(getApplicationContext(), NewGameActivity.class);
			startActivityForResult(serverIntent, REQUEST_NEW_LOCAL);
		}
		else if (id == R.id.nav_bluetooth)
		{
		}

		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		drawer.closeDrawer(GravityCompat.START);
		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{

		if (requestCode == REQUEST_NEW_LOCAL)
		{
			if (resultCode == RESULT_OK)
			{
				closeGame();
				GameState gs = (GameState) data.getSerializableExtra("GameState");
				game = Game.newGame(gs, (BoardView) findViewById(R.id.boardView), new AndroidBot());
			}
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		closeGame();
		super.onSaveInstanceState(outState);
		outState.putSerializable(STATE_KEY, game.getState());
	}

	@Override
	protected void onResume()
	{

		if (game != null && !game.getState().running())
			game.run();

		super.onResume();
	}

	@Override
	protected void onPause()
	{
		closeGame();
		super.onPause();
	}

	private void closeGame()
	{
		if (game != null)
			game.close();
	}
}
