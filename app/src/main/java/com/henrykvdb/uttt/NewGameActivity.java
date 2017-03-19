package com.henrykvdb.uttt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import com.flaghacker.uttt.bots.RandomBot;
import com.henrykvdb.utt.R;

import java.util.Arrays;
import java.util.Collections;

public class NewGameActivity extends Activity
{
	private CheckBox shuffleCheckBox;
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

		shuffleCheckBox = (CheckBox) findViewById(R.id.shuffleCheckBox);
		radio_ai = (RadioButton) findViewById(R.id.radio_ai);
		aiOptions = (LinearLayout) findViewById(R.id.aiOptions);
		startButton = (Button) findViewById(R.id.button_start);

		// Initialize the button to perform device discovery
		startButton.setOnClickListener(v -> {
			// Create the result Intent and include game settings
			Intent intent = new Intent();

			AndroidBot ab = new AndroidBot();
			intent.putExtra("GameState",
					new GameState(Collections.unmodifiableList(Arrays.asList(
							ab, radio_ai.isChecked() ?new RandomBot():ab))
							, shuffleCheckBox.isChecked()));

			// Set result and finish this Activity
			setResult(Activity.RESULT_OK, intent);
			finish();
		});
	}

	public void radioClick(View view)
	{
		aiOptions.setVisibility(radio_ai.isChecked() ?LinearLayout.VISIBLE:View.GONE);
		startButton.invalidate();
	}
}
