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
import com.flaghacker.uttt.common.Bot;

import java.util.Arrays;
import java.util.List;
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

			WaitBot ab = new WaitBot();
			List<Bot> bots;
			if (radio_ai.isChecked())
			{
				RandomBot rb = new RandomBot();

				int beginner = ((RadioGroup) findViewById(R.id.start_radio_group)).getCheckedRadioButtonId();
				boolean aiBegins = new Random().nextBoolean();

				if (beginner == R.id.start_you) aiBegins = false;
				else if (beginner == R.id.start_ai) aiBegins = true;

				bots = Arrays.asList(aiBegins ? rb : ab, aiBegins ? ab : rb);
			}
			else bots = Arrays.asList(ab, ab);

			intent.putExtra("GameState", new GameState(bots, false));
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
