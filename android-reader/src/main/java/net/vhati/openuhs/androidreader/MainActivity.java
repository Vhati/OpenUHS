package net.vhati.openuhs.androidreader;

import android.content.Intent;
import android.os.Bundle;

import android.support.v4.content.IntentCompat;
import android.support.v7.app.AppCompatActivity;

import net.vhati.openuhs.androidreader.R;


public class MainActivity extends AppCompatActivity {

	/** Called when the activity is first created. */
	@Override
	public void onCreate( Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );

		// Just switch to the downloader immediately.
		Intent intent = new Intent().setClass( this, DownloaderActivity.class );
		intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK | IntentCompat.FLAG_ACTIVITY_CLEAR_TASK | IntentCompat.FLAG_ACTIVITY_TASK_ON_HOME );
		this.startActivity( intent );
		finish();
	}
}
