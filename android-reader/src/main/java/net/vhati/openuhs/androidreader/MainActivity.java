package net.vhati.openuhs.androidreader;

import android.app.TabActivity;
import android.content.Intent;
//import android.content.res.Resources;
import android.os.Bundle;
//import android.widget.TabHost;

import android.support.v4.content.IntentCompat;

import net.vhati.openuhs.androidreader.R;
import net.vhati.openuhs.core.UHSErrorHandlerManager;


public class MainActivity extends TabActivity {

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Just switch to the downloader immediately.
    Intent intent = new Intent().setClass(this, DownloaderActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | IntentCompat.FLAG_ACTIVITY_CLEAR_TASK | IntentCompat.FLAG_ACTIVITY_TASK_ON_HOME);
    this.startActivity(intent);
    finish();

/*
    //setContentView(R.layout.main);  // Handles by this being a TabActivity
                                      // This line seems safe until a 2nd layout xml is present.

    Resources res = getResources();  // Resource object to get Drawables
    TabHost tabHost = getTabHost();  // The activity TabHost
    TabHost.TabSpec spec;            // Resusable TabSpec for each tab
    Intent intent;                   // Reusable Intent for each tab

    AndroidUHSErrorHandler errorHandler = new AndroidUHSErrorHandler("OpenUHS");
    UHSErrorHandlerManager.setErrorHandler(errorHandler);

    // Create an Intent to launch an Activity for the tab (to be reused)
    // Initialize a TabSpec for each tab and add it to the TabHost
    intent = new Intent().setClass(this, ReaderActivity.class);
    spec = tabHost.newTabSpec("reader").setIndicator("Reader",
             res.getDrawable(R.drawable.tab_reader_selector))
             .setContent(intent);
    tabHost.addTab(spec);

    // Do the same for the other tabs
    intent = new Intent().setClass(this, DownloaderActivity.class);
    spec = tabHost.newTabSpec("downloader").setIndicator("Downloader",
             res.getDrawable(R.drawable.tab_downloader_selector))
             .setContent(intent);
    tabHost.addTab(spec);

    tabHost.setCurrentTab(0);
*/
  }
}
