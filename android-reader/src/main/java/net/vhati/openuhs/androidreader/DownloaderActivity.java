package net.vhati.openuhs.androidreader;

import java.util.Observable;
import java.util.Observer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;

import net.vhati.openuhs.androidreader.R;
import net.vhati.openuhs.androidreader.downloader.*;
import net.vhati.openuhs.core.*;


public class DownloaderActivity extends Activity implements Observer {
  private static final int CATALOG_COLOR_REMOTE = android.graphics.Color.BLACK;
  private static final int CATALOG_COLOR_LOCAL = android.graphics.Color.DKGRAY;
  private static final int CATALOG_COLOR_NEWER = android.graphics.Color.LTGRAY;

  private TextView tv = null;
  private ListView catalogListView = null;
  private UrlFetcher catalogFetcher = null;

  private ProgressDialog catalogFetchDlg = null;


  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    tv = new TextView(this);
    tv.setText("This is the downloader");
    //setContentView(tv);

    catalogListView = new ListView(this);
      catalogListView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
      //catalogListView.setAdapter(new ArrayAdapter(this, android.R.layout.simple_list_item_1, new String[] {"a", "b", "c"}));
      setContentView(catalogListView);

    catalogFetchDlg = new ProgressDialog(this);
      catalogFetchDlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
      catalogFetchDlg.setIndeterminate(false);
      catalogFetchDlg.setMax(100);
      catalogFetchDlg.setCancelable(false);
      catalogFetchDlg.setMessage("Fetching Catalog...");
      catalogFetchDlg.show();

    AndroidUHSErrorHandler errorHandler = new AndroidUHSErrorHandler("OpenUHS");

    UHSFetcher.setErrorHandler(errorHandler);

    catalogFetcher = new UrlFetcher(UHSFetcher.getCatalogUrl());
      catalogFetcher.setErrorHandler(errorHandler);
      catalogFetcher.setUserAgent(UHSFetcher.getUserAgent());
      catalogFetcher.addObserver(this);
      catalogFetcher.download();

    //testNewFile();
  }


  /**
   * Triggers a callback when an Observable changes.
   */
  public void update(Observable obj, Object arg) {
    if (obj == catalogFetcher) {
      UrlFetcher urlFetcher = (UrlFetcher)obj;
      int status = urlFetcher.getStatus();
      if (status == UrlFetcher.COMPLETE) {
        byte[] catalogBytes = urlFetcher.getReceivedBytes();
        if (catalogBytes != null && catalogBytes.length > 0) {
          final java.util.ArrayList catalog = UHSFetcher.parseCatalog(catalogBytes);
          if (catalog.size() > 0) {
            int catalogSize = catalog.size();
            for (int i=0; i < catalogSize; i++) {
              DownloadableUHS tmpUHS = (DownloadableUHS)catalog.get(i);
              if (tmpUHS.getName().length() > 0) {
                File uhsFile = new File(getExternalFilesDir(null), tmpUHS.getName());
                if (uhsFile.exists()) tmpUHS.setColor(CATALOG_COLOR_LOCAL);
                else tmpUHS.setColor(CATALOG_COLOR_REMOTE);
              }
            }

            runOnUiThread(new Runnable() {
              public void run() {
                ((DownloadableUHS)catalog.get(1)).setColor(android.graphics.Color.GREEN);
                catalogListView.setAdapter(new DownloadableUHSArrayAdapter(DownloaderActivity.this, R.layout.catalog_row, R.id.icon, R.id.uhs_title_label, catalog));
                catalogFetchDlg.dismiss();
              }
            });
          }
        } else {
          runOnUiThread(new Runnable() {
            public void run() {
              tv.setText("Could not fetch catalog");
              catalogFetchDlg.dismiss();
            }
          });
        }
      }
      else if (status == UrlFetcher.DOWNLOADING) {
        catalogFetchDlg.setProgress((int)urlFetcher.getProgress());
      }
      else {
        runOnUiThread(new Runnable() {
          public void run() {
            catalogFetchDlg.dismiss();
          }
        });
      }
    }
  }

/*
  // This is broken.

  private void testNewFile() {
    // getExternalFilesDir(null) resolves to:
    //   /sdcard/Android/data/com.myexample.myandroid/files/
    // It'll be deleted when this app is uninstalled.

    File file = new File(getExternalFilesDir(null), "DemoFile.png");
    if (file.exists()) return;

    try {
      InputStream is = getResources().openRawResource(R.drawable.ic_tab_mic_grey);
      OutputStream os = new FileOutputStream(file);
      byte[] data = new byte[is.available()];
      is.read(data);
      os.write(data);
      is.close();
      os.close();
    }
    catch (IOException e) {
      // External storage probably wasn't mounted
      Log.w("ExternalStorage", "Error writing " + file, e);
    }
  }
*/
}
