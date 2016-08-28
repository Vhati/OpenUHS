package net.vhati.openuhs.androidreader;

import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;

import android.support.v4.content.IntentCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import net.vhati.openuhs.androidreader.R;
import net.vhati.openuhs.androidreader.AndroidUHSErrorHandler;
import net.vhati.openuhs.androidreader.downloader.DownloadableUHS;
import net.vhati.openuhs.androidreader.downloader.DownloadableUHSArrayAdapter;
import net.vhati.openuhs.androidreader.downloader.UHSFetcher;
import net.vhati.openuhs.androidreader.downloader.UrlFetcher;


public class DownloaderActivity extends AppCompatActivity implements Observer {
  private static final int CATALOG_COLOR_REMOTE = android.graphics.Color.BLACK;
  private static final int CATALOG_COLOR_LOCAL = android.graphics.Color.DKGRAY;
  private static final int CATALOG_COLOR_NEWER = android.graphics.Color.LTGRAY;

  private TextView tv = null;
  private Toolbar toolbar = null;
  private ListView catalogListView = null;

  private AndroidUHSErrorHandler errorHandler = new AndroidUHSErrorHandler("OpenUHS");
  private UrlFetcher catalogFetcher = null;
  private UrlFetcher uhsFetcher = null;

  private ProgressDialog progressDlg = null;


  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    tv = new TextView(this);
    tv.setText("This is the downloader");
    //setContentView(tv);

/*
    catalogListView = new ListView(this);
      catalogListView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
      //catalogListView.setAdapter(new ArrayAdapter(this, android.R.layout.simple_list_item_1, new String[] {"a", "b", "c"}));
      setContentView(catalogListView);
*/
    this.setContentView(R.layout.downloader);

    toolbar = (Toolbar)findViewById(R.id.downloaderToolbar);
    this.setSupportActionBar(toolbar);

    catalogListView = (ListView)findViewById(R.id.catalogList);
    this.registerForContextMenu(catalogListView);

    UHSFetcher.setErrorHandler(errorHandler);

    progressDlg = new ProgressDialog(this);
      progressDlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
      progressDlg.setIndeterminate(false);
      progressDlg.setMax(100);
      progressDlg.setCancelable(false);
      progressDlg.setMessage("...");
  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.downloader_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.fetchCatalogAction:
        fetchCatalog();
        return true;

      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);

    DownloadableUHS tmpUHS = ((DownloadableUHSArrayAdapter)catalogListView.getAdapter()).getItem(((AdapterContextMenuInfo)menuInfo).position);
    if (tmpUHS != null && tmpUHS.getName().length() > 0) {

      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.downloader_context_menu, menu);

      File uhsFile = new File(this.getExternalFilesDir(null), tmpUHS.getName());
      if (uhsFile.exists()) {
        ((MenuItem)findViewById(R.id.openFileContextAction)).setEnabled(true);
        ((MenuItem)findViewById(R.id.deleteFileContextAction)).setEnabled(true);
      }
    }
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
    DownloadableUHS tmpUHS = null;
    File uhsFile = null;

    switch (item.getItemId()) {
      case R.id.openFileContextAction:
        tmpUHS = ((DownloadableUHSArrayAdapter)catalogListView.getAdapter()).getItem(info.position);
        if (tmpUHS.getName().length() == 0) {
          // TODO: Complain.
          return false;
        }
        uhsFile = new File(this.getExternalFilesDir(null), tmpUHS.getName());
        String uhsPath = uhsFile.getPath();
        if (!uhsFile.exists()) {
          // TODO: Complain.
          return false;
        }
        openFile(uhsPath);
        return true;

      case R.id.fetchFileContextAction:
        tmpUHS = ((DownloadableUHSArrayAdapter)catalogListView.getAdapter()).getItem(info.position);
        if (tmpUHS.getName().length() == 0) {
          // TODO: Complain.
          return false;
        }
        uhsFile = new File(this.getExternalFilesDir(null), tmpUHS.getName());

        // TODO: Integrate UrlFetcher into UHSFetcher (which doesn'tcurrently fetch, only unzips).
        //byte[] uhsBytes = UHSFetcher.fetchUHS(tmpUHS);
        //UHSFetcher.saveBytes(uhsFile.getPath(), uhsBytes);
        //Update the UI.
        return true;

      case R.id.deleteFileContextAction:
        tmpUHS = ((DownloadableUHSArrayAdapter)catalogListView.getAdapter()).getItem(info.position);
        if (tmpUHS.getName().length() == 0) {
          // TODO: Complain.
          return false;
        }
        uhsFile = new File(this.getExternalFilesDir(null), tmpUHS.getName());
        if (uhsFile.exists()) uhsFile.delete();
        return true;

      default:
        return super.onContextItemSelected(item);
    }
}


  private void fetchCatalog() {
    if (catalogFetcher != null && catalogFetcher.getStatus() == UrlFetcher.DOWNLOADING) {
      catalogFetcher.cancel();
      progressDlg.dismiss();
    }

    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        progressDlg.setProgress(0);
        progressDlg.setMessage("Fetching Catalog...");
        progressDlg.show();

        catalogFetcher = new UrlFetcher(UHSFetcher.getCatalogUrl());
          catalogFetcher.setErrorHandler(errorHandler);
          catalogFetcher.setUserAgent(UHSFetcher.getUserAgent());
          catalogFetcher.addObserver(DownloaderActivity.this);
          catalogFetcher.download();
      }
    });
  }


  /**
   * Triggers a callback when an Observable changes.
   */
  @Override
  public void update(Observable obj, Object arg) {
    if (obj == catalogFetcher) {
      UrlFetcher urlFetcher = (UrlFetcher)obj;
      int status = urlFetcher.getStatus();
      if (status == UrlFetcher.COMPLETE) {
        byte[] catalogBytes = urlFetcher.getReceivedBytes();
        if (catalogBytes != null && catalogBytes.length > 0) {
          final List<DownloadableUHS> catalog = UHSFetcher.parseCatalog(catalogBytes);
          if (catalog.size() > 0) {
            int catalogSize = catalog.size();
            for (int i=0; i < catalogSize; i++) {
              DownloadableUHS tmpUHS = catalog.get(i);
              if (tmpUHS.getName().length() > 0) {
                File uhsFile = new File(getExternalFilesDir(null), tmpUHS.getName());
                if (uhsFile.exists()) tmpUHS.setColor(CATALOG_COLOR_LOCAL);
                else tmpUHS.setColor(CATALOG_COLOR_REMOTE);
              }
            }

            runOnUiThread(new Runnable() {
              @Override
              public void run() {
                catalog.get(1).setColor(android.graphics.Color.GREEN);
                catalogListView.setAdapter(new DownloadableUHSArrayAdapter(DownloaderActivity.this, R.layout.catalog_row, R.id.icon, R.id.uhs_title_label, catalog));
                progressDlg.dismiss();
              }
            });
          }
        } else {
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              tv.setText("Could not fetch catalog");
              progressDlg.dismiss();
            }
          });
        }
      }
      else if (status == UrlFetcher.DOWNLOADING) {
        progressDlg.setProgress((int)urlFetcher.getProgress());
      }
      else {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            progressDlg.dismiss();
          }
        });
      }
    }
  }


  /**
   * Opens a given file in the reader.
   *
   * @param uhsPath an absolute path to a UHS file.
   */
  public void openFile(String uhsPath) {
    Intent intent = new Intent().setClass(this, ReaderActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | IntentCompat.FLAG_ACTIVITY_CLEAR_TASK | IntentCompat.FLAG_ACTIVITY_TASK_ON_HOME);
    intent.putExtra(ReaderActivity.EXTRA_OPEN_FILE, uhsPath);
    this.startActivity(intent);
    finish();
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
