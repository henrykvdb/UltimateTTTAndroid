package com.henrykvdb.sttt;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.ViewDragHelper;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.henrykvdb.sttt.DialogUtil.BasicDialogs;
import com.henrykvdb.sttt.DialogUtil.NewGameDialogs;

import java.lang.reflect.Field;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener
{
	private static final String STATE_KEY = "GAMESTATE";
	private Game game;

	private void debug()
	{
		Log.e("DEBUG",Thread.currentThread().getStackTrace()[3].getMethodName());
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		debug();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		//Setup the drawer
		setupDrawer();

		//Add ads in portrait
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
		{
			MobileAds.initialize(getApplicationContext(), getString(R.string.banner_ad_unit_id));
			((AdView) findViewById(R.id.adView)).loadAd(new AdRequest.Builder().build());
		}

		//Prepare the BoardView and the game object
		BoardView boardView = (BoardView) findViewById(R.id.boardView);
		boardView.setNextPlayerView((TextView) findViewById(R.id.next_move_view));

		//Start an actual game
		game = new Game(gameCallback, (BoardView) findViewById(R.id.boardView));
		if (savedInstanceState != null)
			game.newGame((GameState) savedInstanceState.getSerializable(STATE_KEY));

		//Ask the user to rate the app
		if (savedInstanceState == null)
			BasicDialogs.rate(this);
	}

	Game.GameCallback gameCallback = new Game.GameCallback()
	{
		public Toast toast;

		@Override
		public void setTitle(String title)
		{
			final ActionBar actionBar = getSupportActionBar();

			if (actionBar != null)
				actionBar.setTitle(title);
		}

		@Override
		public void setSubTitle(String subTitle)
		{
			final ActionBar actionBar = getSupportActionBar();

			if (actionBar != null)
				actionBar.setSubtitle(subTitle);
		}

		@Override
		public void sendToast(String text)
		{
			if (toast == null)
				toast = Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT);

			toast.setText(text);
			toast.show();
		}
	};

	private void setupDrawer()
	{
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

		try
		{
			Field mDragger = drawer.getClass().getDeclaredField("mLeftDragger");
			mDragger.setAccessible(true);
			ViewDragHelper draggerObj = (ViewDragHelper) mDragger.get(drawer);
			Field mEdgeSize = draggerObj.getClass().getDeclaredField("mEdgeSize");
			mEdgeSize.setAccessible(true);
			mEdgeSize.setInt(draggerObj, mEdgeSize.getInt(draggerObj) * 4);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
				this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
		drawer.addDrawerListener(toggle);
		toggle.syncState();

		NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		//TODO save instance
		//outState.putBoolean(Constants.STARTED_WITH_BT_KEY, startedWithBt);
		debug();
		outState.putSerializable(STATE_KEY,game.getState());
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onStart()
	{
		debug();
		super.onStart();
	}

	@Override
	protected void onStop()
	{
		debug();
		super.onStop();
	}

	@Override
	protected void onDestroy()
	{
		debug();
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == R.id.action_undo)
		{
			game.undo();
			return true;
		}
		return false;
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item)
	{
		// Handle navigation view item clicks here.
		int id = item.getItemId();
		debug();

		if (id == R.id.nav_local_human)
		{
			NewGameDialogs.newLocal(() -> game.newLocal(), this);
		}
		else if (id == R.id.nav_local_ai)
		{
			NewGameDialogs.newAi(gs -> game.newGame(gs),this);
		}
		else if (id == R.id.nav_bt_join)
		{
			throw new UnsupportedOperationException(); //TODO WIP
			//pickBluetooth();
		}
		else if (id == R.id.nav_other_feedback)
		{
			BasicDialogs.sendFeedback(this);
		}
		else if (id == R.id.nav_other_share)
		{
			BasicDialogs.share(this);
		}
		else if (id == R.id.nav_other_about)
		{
			BasicDialogs.about(this);
		}

		if (id != R.id.nav_bt_host_switch)
			((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawer(GravityCompat.START);

		return true;
	}
}
