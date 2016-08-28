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
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.support.v4.content.IntentCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import net.vhati.openuhs.androidreader.R;
import net.vhati.openuhs.androidreader.AndroidUHSErrorHandler;
import net.vhati.openuhs.androidreader.downloader.DownloadableUHS;
import net.vhati.openuhs.androidreader.downloader.DownloadableUHSArrayAdapter;
import net.vhati.openuhs.androidreader.downloader.UHSFetcher;
import net.vhati.openuhs.androidreader.downloader.UHSFetchTask;
import net.vhati.openuhs.androidreader.downloader.UHSFetchTask.UHSFetchObserver;
import net.vhati.openuhs.androidreader.downloader.UHSFetchTask.UHSFetchResult;
import net.vhati.openuhs.androidreader.downloader.UrlFetcher;


public class DownloaderActivity extends AppCompatActivity implements Observer, UHSFetchTask.UHSFetchObserver {
  private static final int CATALOG_COLOR_REMOTE = android.graphics.Color.BLACK;
  private static final int CATALOG_COLOR_LOCAL = android.graphics.Color.DKGRAY;
  private static final int CATALOG_COLOR_NEWER = android.graphics.Color.LTGRAY;

  private TextView tv = null;
  private Toolbar toolbar = null;
  private ListView catalogListView = null;

  private AndroidUHSErrorHandler errorHandler = new AndroidUHSErrorHandler("OpenUHS");
  private UrlFetcher catalogFetcher = null;
  private final UHSFetchTask uhsFetchTask = new UHSFetchTask(this, this);

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

    catalogListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        DownloadableUHS tmpUHS = ((DownloadableUHSArrayAdapter)catalogListView.getAdapter()).getItem(position);
        File uhsFile = null;

        if (tmpUHS != null && tmpUHS.getName().length() > 0) {
          uhsFile = new File(DownloaderActivity.this.getExternalFilesDir(null), tmpUHS.getName());
        }
        if (uhsFile != null && uhsFile.exists()) {
          openFile(uhsFile.getPath());
        } else {
          view.showContextMenu();
        }
      }
    });

    UHSFetcher.setErrorHandler(errorHandler);

    progressDlg = new ProgressDialog(this);
      progressDlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
      progressDlg.setIndeterminate(true);
      progressDlg.setMax(100);
      progressDlg.setCancelable(false);
      progressDlg.setMessage("...");

    progressDlg.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        if (catalogFetcher != null && catalogFetcher.getStatus() == UrlFetcher.DOWNLOADING) {
          catalogFetcher.cancel();
        }
        if (AsyncTask.Status.RUNNING.equals(uhsFetchTask.getStatus())) {
          uhsFetchTask.cancel(true);
        }
        progressDlg.dismiss();
      }
    });
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

      menu.setHeaderTitle(tmpUHS.getName());

      File uhsFile = new File(this.getExternalFilesDir(null), tmpUHS.getName());
      if (uhsFile.exists()) {
        menu.findItem(R.id.openFileContextAction).setEnabled(true);
        menu.findItem(R.id.deleteFileContextAction).setEnabled(true);
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

        if (!uhsFile.exists()) {
          uhsFetchTask.execute(tmpUHS);
        }
        return true;

      case R.id.deleteFileContextAction:
        tmpUHS = ((DownloadableUHSArrayAdapter)catalogListView.getAdapter()).getItem(info.position);
        if (tmpUHS.getName().length() == 0) {
          // TODO: Complain.
          return false;
        }
        uhsFile = new File(this.getExternalFilesDir(null), tmpUHS.getName());
        if (uhsFile.exists()) {
          uhsFile.delete();
          colorizeCatalogRow(tmpUHS);
          ((DownloadableUHSArrayAdapter)catalogListView.getAdapter()).notifyDataSetChanged();
        }
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
    if (AsyncTask.Status.RUNNING.equals(uhsFetchTask.getStatus())) {
      uhsFetchTask.cancel(true);
      progressDlg.dismiss();
    }

    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        progressDlg.setCancelable(false);
        progressDlg.setIndeterminate(true);
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
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              if (catalog.size() > 0) {
                colorizeCatalog(catalog);
                catalogListView.setAdapter(new DownloadableUHSArrayAdapter(DownloaderActivity.this, R.layout.catalog_row, R.id.icon, R.id.uhs_title_label, catalog));
                progressDlg.dismiss();
              }
            }
          });
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
        progressDlg.setIndeterminate(false);
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

  @Override
  public void uhsFetchStarted() {
    progressDlg.setCancelable(true);
    progressDlg.setIndeterminate(true);
    progressDlg.setMessage("...");
    progressDlg.setProgress(0);
    progressDlg.show();
  }

  @Override
  public void uhsFetchUpdate(int progress) {
    progressDlg.setIndeterminate(false);
    progressDlg.setMax(100);
    progressDlg.setProgress(progress);
  }

  @Override
  public void uhsFetchEnded(UHSFetchTask.UHSFetchResult fetchResult) {
    // This won't run if cancelled.

    progressDlg.dismiss();
    if (fetchResult.status == UHSFetchResult.STATUS_COMPLETED) {
      Toast.makeText(this, "Saved "+ fetchResult.file.getName(), Toast.LENGTH_SHORT).show();
    }
    else {
      if (fetchResult.status != UHSFetchResult.STATUS_CANCELLED) {
        String message = (fetchResult.message != null) ? fetchResult.message : "Unknown error";
        Toast.makeText(this, "Download failed: "+ message, Toast.LENGTH_LONG).show();
      }
      if (fetchResult.file != null && fetchResult.file.exists()) {
        fetchResult.file.delete();
      }
    }
    colorizeCatalogRow(fetchResult.duh);
    ((DownloadableUHSArrayAdapter)catalogListView.getAdapter()).notifyDataSetChanged();
  }


  public void colorizeCatalog(List<DownloadableUHS> catalog) {
    for (DownloadableUHS tmpUHS : catalog) {
      colorizeCatalogRow(tmpUHS);
    }
  }

  public void colorizeCatalogRow(DownloadableUHS tmpUHS) {
    int c = CATALOG_COLOR_REMOTE;
    if (tmpUHS.getName().length() > 0) {
      File uhsFile = new File(getExternalFilesDir(null), tmpUHS.getName());
      if (uhsFile.exists()) {
        c = CATALOG_COLOR_LOCAL;
      }
    }
    tmpUHS.setColor(c);
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
}
