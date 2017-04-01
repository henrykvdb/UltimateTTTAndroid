package com.henrykvdb.uttt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import com.flaghacker.uttt.bots.RandomBot;

import java.util.Random;

public class NewLocalActivity extends Activity
{
	private RadioButton radio_ai;
	private LinearLayout aiOptions;
	private Button startButton;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Setup the window
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.new_local);

		// Set result CANCELED in case the user backs out
		setResult(Activity.RESULT_CANCELED);

		//Vars
		radio_ai = (RadioButton) findViewById(R.id.radio_ai);
		aiOptions = (LinearLayout) findViewById(R.id.aiOptions);
		startButton = (Button) findViewById(R.id.button_start);

		//On start button click listener
		startButton.setOnClickListener(v -> {
			Intent intent = new Intent();

			if (radio_ai.isChecked())
			{
				RandomBot bot = new RandomBot();

				int beginner = ((RadioGroup) findViewById(R.id.start_radio_group)).getCheckedRadioButtonId();

				boolean swapped = new Random().nextBoolean();
				if (beginner == R.id.start_you) swapped = false;
				else if (beginner == R.id.start_ai) swapped = true;

				intent.putExtra("GameState", new GameState(bot,swapped));
			}
			else intent.putExtra("GameState", new GameState());

			setResult(Activity.RESULT_OK, intent);
			finish();
		});
	}

	public void radioClick(View view)
	{
		aiOptions.setVisibility(radio_ai.isChecked() ? LinearLayout.VISIBLE : View.GONE);
		startButton.invalidate();
	}
}
