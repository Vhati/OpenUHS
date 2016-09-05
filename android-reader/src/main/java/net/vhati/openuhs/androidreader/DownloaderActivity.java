package net.vhati.openuhs.androidreader;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.support.v4.content.IntentCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import net.vhati.openuhs.androidreader.R;
import net.vhati.openuhs.androidreader.AndroidUHSErrorHandler;
import net.vhati.openuhs.androidreader.downloader.DownloadableUHSArrayAdapter;
import net.vhati.openuhs.androidreader.downloader.UHSFetchTask;
import net.vhati.openuhs.androidreader.downloader.UHSFetchTask.UHSFetchObserver;
import net.vhati.openuhs.androidreader.downloader.UHSFetchTask.UHSFetchResult;
import net.vhati.openuhs.androidreader.downloader.StringFetchTask;
import net.vhati.openuhs.androidreader.downloader.StringFetchTask.StringFetchObserver;
import net.vhati.openuhs.androidreader.downloader.StringFetchTask.StringFetchResult;
import net.vhati.openuhs.core.UHSErrorHandler;
import net.vhati.openuhs.core.downloader.CatalogParser;
import net.vhati.openuhs.core.downloader.DownloadableUHS;


public class DownloaderActivity extends AppCompatActivity implements UHSFetchObserver {
	private Toolbar toolbar = null;
	private ListView catalogListView = null;

	private File externalDir = null;
	private File hintsDir = null;

	private AndroidUHSErrorHandler errorHandler = new AndroidUHSErrorHandler( "OpenUHS" );
	private CatalogParser catalogParser = null;
	private StringFetchTask catalogFetchTask = null;
	private UHSFetchTask uhsFetchTask = null;

	private ProgressDialog progressDlg = null;


	/** Called when the activity is first created. */
	@Override
	public void onCreate( Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );

		this.setContentView( R.layout.downloader );

		toolbar = (Toolbar)findViewById( R.id.downloaderToolbar );
		this.setSupportActionBar( toolbar );

		externalDir = this.getExternalFilesDir( null );
		hintsDir = new File( externalDir, "hints" );

		if ( !hintsDir.exists() ) {
			if ( hintsDir.mkdir() ) {
				errorHandler.log( UHSErrorHandler.INFO, this, String.format( "Created 'hints/' dir: %s", hintsDir.getAbsolutePath() ), 0, null );
			} else {
				errorHandler.log( UHSErrorHandler.ERROR, this, String.format( "Failed to created 'hints/' dir: %s", hintsDir.getAbsolutePath() ), 0, null );
			}
		}

		catalogListView = (ListView)findViewById( R.id.catalogList );
		this.registerForContextMenu( catalogListView );

		catalogListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick( AdapterView<?> parent, View view, int position, long id ) {
				DownloadableUHS tmpUHS = ((DownloadableUHSArrayAdapter)catalogListView.getAdapter()).getItem( position );
				File uhsFile = null;

				if ( tmpUHS != null && tmpUHS.getName().length() > 0 ) {
					uhsFile = new File( hintsDir, tmpUHS.getName() );
				}
				if ( uhsFile != null && uhsFile.exists() ) {
					openFile( uhsFile.getAbsolutePath() );
				} else {
					view.showContextMenu();
				}
			}
		});

		catalogParser = new CatalogParser();
		catalogParser.setErrorHandler( errorHandler );

		progressDlg = new ProgressDialog( this );
			progressDlg.setProgressStyle( ProgressDialog.STYLE_HORIZONTAL );
			progressDlg.setIndeterminate( true );
			progressDlg.setMax( 100 );
			progressDlg.setCancelable( false );
			progressDlg.setMessage( "..." );

		progressDlg.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel( DialogInterface dialog ) {
				if ( AsyncTask.Status.RUNNING.equals( catalogFetchTask.getStatus() ) ) {
					catalogFetchTask.cancel( true );
				}
				if ( AsyncTask.Status.RUNNING.equals( uhsFetchTask.getStatus() ) ) {
					uhsFetchTask.cancel( true );
				}
				progressDlg.dismiss();
			}
		});
	}


	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate( R.menu.downloader_menu, menu );
		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch ( item.getItemId() ) {
			case R.id.fetchCatalogAction:
				fetchCatalog();
				return true;

			default:
				return super.onOptionsItemSelected( item );
		}
	}

	@Override
	public void onCreateContextMenu( ContextMenu menu, View v, ContextMenuInfo menuInfo ) {
		super.onCreateContextMenu( menu, v, menuInfo );

		DownloadableUHS tmpUHS = ((DownloadableUHSArrayAdapter)catalogListView.getAdapter()).getItem( ((AdapterContextMenuInfo)menuInfo).position );
		if ( tmpUHS != null && tmpUHS.getName().length() > 0 ) {

			MenuInflater inflater = getMenuInflater();
			inflater.inflate( R.menu.downloader_context_menu, menu );

			menu.setHeaderTitle( tmpUHS.getName() );

			File uhsFile = new File( hintsDir, tmpUHS.getName() );
			if ( uhsFile.exists() ) {
				menu.findItem( R.id.openFileContextAction ).setEnabled( true );
				menu.findItem( R.id.deleteFileContextAction ).setEnabled( true );
			}
		}
	}

	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
		DownloadableUHS tmpUHS = null;
		File uhsFile = null;

		switch (item.getItemId()) {
			case R.id.openFileContextAction:
				tmpUHS = ((DownloadableUHSArrayAdapter)catalogListView.getAdapter()).getItem( info.position );
				if (tmpUHS.getName().length() == 0) {
					// TODO: Complain.
					return false;
				}
				uhsFile = new File(hintsDir, tmpUHS.getName());
				String uhsPath = uhsFile.getAbsolutePath();
				if ( !uhsFile.exists() ) {
					// TODO: Complain.
					return false;
				}
				openFile( uhsPath );
				return true;

			case R.id.fetchFileContextAction:
				tmpUHS = ((DownloadableUHSArrayAdapter)catalogListView.getAdapter()).getItem( info.position );
				if ( tmpUHS.getName().length() == 0 ) {
					// TODO: Complain.
					return false;
				}

				fetchUHS( tmpUHS );
				return true;

			case R.id.deleteFileContextAction:
				tmpUHS = ((DownloadableUHSArrayAdapter)catalogListView.getAdapter()).getItem( info.position );
				if ( tmpUHS.getName().length() == 0 ) {
					// TODO: Complain.
					return false;
				}
				uhsFile = new File( hintsDir, tmpUHS.getName() );
				if ( uhsFile.exists() ) {
					uhsFile.delete();
					colorizeCatalogRow( tmpUHS );
					((DownloadableUHSArrayAdapter)catalogListView.getAdapter()).notifyDataSetChanged();
				}
				return true;

			default:
				return super.onContextItemSelected( item );
		}
	}

	private void cancelFetching() {
		if ( catalogFetchTask != null && AsyncTask.Status.RUNNING.equals( catalogFetchTask.getStatus() ) ) {
			catalogFetchTask.cancel( true );
			progressDlg.dismiss();
		}
		if ( uhsFetchTask != null && AsyncTask.Status.RUNNING.equals( uhsFetchTask.getStatus() ) ) {
			uhsFetchTask.cancel( true );
			progressDlg.dismiss();
		}
	}

	private void fetchUHS( DownloadableUHS duh ) {
		cancelFetching();

		uhsFetchTask = new UHSFetchTask( hintsDir );
		uhsFetchTask.setUserAgent( CatalogParser.DEFAULT_USER_AGENT );
		uhsFetchTask.setObserver( this );
		uhsFetchTask.execute( duh );
	}

	private void fetchCatalog() {
		cancelFetching();

		catalogFetchTask = new StringFetchTask();
		catalogFetchTask.setUserAgent( CatalogParser.DEFAULT_USER_AGENT );
		catalogFetchTask.setEncoding( CatalogParser.DEFAULT_CATALOG_ENCODING );

		catalogFetchTask.setObserver(new StringFetchObserver() {
			@Override
			public void stringFetchStarted() {
				catalogFetchStarted();
			}

			@Override
			public void stringFetchUpdate( int progress ) {
				catalogFetchUpdate( progress );
			}

			@Override
			public void stringFetchEnded( StringFetchResult fetchResult ) {
				catalogFetchEnded( fetchResult );
			}
		});

		catalogFetchTask.execute( CatalogParser.DEFAULT_CATALOG_URL );
	}


	@Override
	public void uhsFetchStarted() {
		progressDlg.setCancelable( true );
		progressDlg.setIndeterminate( true );
		progressDlg.setMessage( "Fetching uhs..." );
		progressDlg.setProgress( 0 );
		progressDlg.show();
	}

	@Override
	public void uhsFetchUpdate( int progress ) {
		progressDlg.setIndeterminate( false );
		progressDlg.setMax( 100 );
		progressDlg.setProgress( progress );
	}

	@Override
	public void uhsFetchEnded( UHSFetchResult fetchResult ) {
		// This won't run if cancelled.

		progressDlg.dismiss();
		if ( fetchResult.status == UHSFetchResult.STATUS_COMPLETED ) {
			Toast.makeText( this, String.format( "Saved %s", fetchResult.file.getName() ), Toast.LENGTH_SHORT ).show();
		}
		else {
			if ( fetchResult.status != UHSFetchResult.STATUS_CANCELLED ) {
				String message = (( fetchResult.message != null ) ? fetchResult.message : "Unknown error");
				Toast.makeText( this, String.format( "Download failed: %s", message ), Toast.LENGTH_LONG ).show();
			}
			if ( fetchResult.file != null && fetchResult.file.exists() ) {
				fetchResult.file.delete();
			}
		}
		colorizeCatalogRow( fetchResult.duh );
		((DownloadableUHSArrayAdapter)catalogListView.getAdapter()).notifyDataSetChanged();
	}


	public void catalogFetchStarted() {
		progressDlg.setCancelable( true );
		progressDlg.setIndeterminate( true );
		progressDlg.setMessage( "Fetching catalog..." );
		progressDlg.setProgress( 0 );
		progressDlg.show();
	}

	public void catalogFetchUpdate( int progress ) {
		progressDlg.setIndeterminate( false );
		progressDlg.setMax( 100 );
		progressDlg.setProgress( progress );
	}

	public void catalogFetchEnded( StringFetchResult fetchResult ) {
		// This won't run if cancelled.

		progressDlg.dismiss();
		if ( fetchResult.status == StringFetchResult.STATUS_COMPLETED ) {
			//Toast.makeText( this, "Fetched catalog", Toast.LENGTH_SHORT ).show();

			List<DownloadableUHS> catalog = catalogParser.parseCatalog( fetchResult.content );
			if ( catalog.size() > 0 ) {
				colorizeCatalog( catalog );
				catalogListView.setAdapter(new DownloadableUHSArrayAdapter( DownloaderActivity.this, R.layout.catalog_row, R.id.icon, R.id.uhs_title_label, catalog ));
			}
			else {
				Toast.makeText( this, "Catalog was empty or parsing failed", Toast.LENGTH_LONG ).show();
			}
		}
		else {
			if ( fetchResult.status != StringFetchResult.STATUS_CANCELLED ) {
				String message = (( fetchResult.message != null ) ? fetchResult.message : "Unknown error");
				Toast.makeText( this, String.format( "Download failed: %s", message ), Toast.LENGTH_LONG ).show();
			}
		}
	}


	/**
	 * Updates state flags on all catalog entries.
	 *
	 * <p>Note: Remember to call notifyDataSetChanged() on the ListView's ArrayAdapter afterward.</p>
	 */
	public void colorizeCatalog( List<DownloadableUHS> catalog ) {

		String[] hintNames = hintsDir.list();
		Arrays.sort( hintNames );

		for ( DownloadableUHS tmpUHS : catalog ) {
			tmpUHS.resetState();

			if ( Arrays.binarySearch( hintNames, tmpUHS.getName() ) >= 0 ) {
				tmpUHS.setLocal( true );
				File uhsFile = new File(hintsDir, tmpUHS.getName());
				Date localDate = new Date( uhsFile.lastModified() );

				if ( tmpUHS.getDate() != null && tmpUHS.getDate().after( localDate ) ) {
					tmpUHS.setNewer( true );
				}
			}
		}
	}

	/**
	 * Updates state flags on a catalog entry.
	 *
	 * <p>Note: Remember to call notifyDataSetChanged() on the ListView's ArrayAdapter afterward.</p>
	 */
	public void colorizeCatalogRow( DownloadableUHS tmpUHS ) {
		tmpUHS.resetState();

		if ( tmpUHS.getName().length() > 0 ) {
			File uhsFile = new File( hintsDir, tmpUHS.getName() );
			if ( uhsFile.exists() ) {
				tmpUHS.setLocal( true );

				if ( tmpUHS.getDate() != null && tmpUHS.getDate().after( new Date( uhsFile.lastModified() ) ) ) {
						tmpUHS.setNewer( true );
				}
			}
		}
	}


	/**
	 * Opens a given file in the reader.
	 *
	 * @param uhsPath  an absolute path to a UHS file.
	 */
	public void openFile( String uhsPath ) {
		Intent intent = new Intent().setClass( this, ReaderActivity.class );
		intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK | IntentCompat.FLAG_ACTIVITY_CLEAR_TASK | IntentCompat.FLAG_ACTIVITY_TASK_ON_HOME );
		intent.putExtra( ReaderActivity.EXTRA_OPEN_FILE, uhsPath );
		this.startActivity( intent );
		finish();
	}
}
