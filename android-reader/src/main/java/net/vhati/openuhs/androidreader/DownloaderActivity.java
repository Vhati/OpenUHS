package net.vhati.openuhs.androidreader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.support.v4.content.IntentCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.androidreader.R;
import net.vhati.openuhs.androidreader.AndroidUHSConstants;
import net.vhati.openuhs.androidreader.downloader.CatalogAdapter;
import net.vhati.openuhs.androidreader.downloader.CatalogItemDeserializer;
import net.vhati.openuhs.androidreader.downloader.CatalogItemSerializer;
import net.vhati.openuhs.androidreader.downloader.UHSFetchTask;
import net.vhati.openuhs.androidreader.downloader.UHSFetchTask.UHSFetchObserver;
import net.vhati.openuhs.androidreader.downloader.UHSFetchTask.UHSFetchResult;
import net.vhati.openuhs.androidreader.downloader.StringFetchTask;
import net.vhati.openuhs.androidreader.downloader.StringFetchTask.StringFetchObserver;
import net.vhati.openuhs.androidreader.downloader.StringFetchTask.StringFetchResult;
import net.vhati.openuhs.core.downloader.CatalogParser;
import net.vhati.openuhs.core.downloader.CatalogItem;
import net.vhati.openuhs.core.downloader.CatalogItemComparator;


public class DownloaderActivity extends AppCompatActivity implements UHSFetchObserver {

	private final Logger logger = LoggerFactory.getLogger( AndroidUHSConstants.LOG_TAG );

	private Toolbar toolbar = null;

	private ListView catalogListView = null;
	private CatalogAdapter catalogAdapter = null;
	private Comparator<CatalogItem> catalogTitleComparator = new CatalogItemComparator( CatalogItemComparator.SORT_TITLE );
	private Comparator<CatalogItem> catalogDateComparator = Collections.reverseOrder( new CatalogItemComparator( CatalogItemComparator.SORT_DATE ) );

	private File externalDir = null;
	private File hintsDir = null;
	private File cachedCatalogFile = null;

	private CatalogParser catalogParser = null;
	private StringFetchTask catalogFetchTask = null;
	private UHSFetchTask uhsFetchTask = null;

	private ProgressDialog progressDlg = null;

	ObjectMapper jsonMapper = null;


	/** Called when the activity is first created. */
	@Override
	public void onCreate( Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );

		this.setContentView( R.layout.downloader );

		toolbar = (Toolbar)findViewById( R.id.downloaderToolbar );
		//toolbar.setTitle( "" );
		this.setSupportActionBar( toolbar );


		if ( !Environment.MEDIA_MOUNTED.equals( Environment.getExternalStorageState() ) ) {
			logger.error( "External storage is not mounted and writable" );
			// TODO: Show a GUI alert for the the user, and exit gracefully!
		}

		externalDir = this.getExternalFilesDir( null );
		hintsDir = new File( externalDir, "hints" );
		cachedCatalogFile = new File( externalDir, "cached_catalog.txt" );

		if ( !hintsDir.exists() ) {
			if ( hintsDir.mkdir() ) {
				logger.info( "Created \"hints/\" dir: {}", hintsDir.getAbsolutePath() );
			} else {
				logger.error( "Failed to created \"hints/\" dir: {}", hintsDir.getAbsolutePath() );
			}
		}

		catalogListView = (ListView)findViewById( R.id.catalogList );
		this.registerForContextMenu( catalogListView );

		catalogListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick( AdapterView<?> parent, View view, int position, long id ) {
				CatalogItem catItem = catalogAdapter.getItem( position );
				File uhsFile = null;

				if ( catItem != null && catItem.getName().length() > 0 ) {
					uhsFile = new File( hintsDir, catItem.getName() );
				}
				if ( uhsFile != null && uhsFile.exists() ) {
					openFile( uhsFile.getAbsolutePath() );
				} else {
					view.showContextMenu();
				}
			}
		});

		catalogAdapter = new CatalogAdapter( this );
		catalogAdapter.setSortFilter( catalogTitleComparator );
		catalogListView.setAdapter( catalogAdapter );

		catalogParser = new CatalogParser();

		progressDlg = new ProgressDialog( this );
			progressDlg.setProgressStyle( ProgressDialog.STYLE_HORIZONTAL );
			progressDlg.setIndeterminate( true );
			progressDlg.setMax( 100 );
			progressDlg.setCancelable( false );
			progressDlg.setMessage( "..." );

		progressDlg.setOnCancelListener(new DialogInterface.OnCancelListener() {
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

		jsonMapper = new ObjectMapper();
		SimpleModule uhsModule = new SimpleModule( "OpenUHS", new Version( 1, 0, 0, null ) );
		uhsModule.addSerializer( CatalogItem.class, new CatalogItemSerializer() );
		uhsModule.addDeserializer( CatalogItem.class, new CatalogItemDeserializer() );
		jsonMapper.registerModule( uhsModule );

		if ( cachedCatalogFile.exists() ) {
			loadCatalog();
		}
	}


	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate( R.menu.downloader_menu, menu );

		// Handle search internally, without involving SearchManager or Intent passing.
		// It may not be possible to disable suggestions reliably.
		SearchView searchView = (SearchView)MenuItemCompat.getActionView( menu.findItem( R.id.catalogSearchAction ) );
		searchView.setSubmitButtonEnabled( false );
		searchView.setQueryHint( getResources().getString( R.string.catalog_search_hint ) );
		searchView.setInputType( InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_FILTER|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS );
		searchView.setSuggestionsAdapter( null );

		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() { 
			@Override 
			public boolean onQueryTextChange( String query ) {
				catalogAdapter.setLocalFilterEnabled( false );
				catalogAdapter.setSortFilter( catalogTitleComparator );
				catalogAdapter.setTitleFilter( query );
				catalogAdapter.applyFilters();
				return true; 
			}

			@Override
			public boolean onQueryTextSubmit( String query ) {
				return false;
			}
		});

		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch ( item.getItemId() ) {
			case R.id.catalogFetchAction:
				fetchCatalog();
				return true;

			case R.id.catalogFilterLocalAction:
				catalogAdapter.setLocalFilterEnabled( true );
				catalogAdapter.setSortFilter( catalogTitleComparator );
				catalogAdapter.applyFilters();
				return true;

			case R.id.catalogSortTitleAction:
				catalogAdapter.setLocalFilterEnabled( false );
				catalogAdapter.setSortFilter( catalogTitleComparator );
				catalogAdapter.applyFilters();
				return true;

			case R.id.catalogSortDateAction:
				catalogAdapter.setLocalFilterEnabled( false );
				catalogAdapter.setSortFilter( catalogDateComparator );
				catalogAdapter.applyFilters();
				return true;

			default:
				return super.onOptionsItemSelected( item );
		}
	}

	@Override
	public void onCreateContextMenu( ContextMenu menu, View v, ContextMenuInfo menuInfo ) {
		super.onCreateContextMenu( menu, v, menuInfo );

		CatalogItem catItem = catalogAdapter.getItem( ((AdapterContextMenuInfo)menuInfo).position );
		if ( catItem != null && catItem.getName().length() > 0 ) {

			MenuInflater inflater = getMenuInflater();
			inflater.inflate( R.menu.downloader_context_menu, menu );

			menu.setHeaderTitle( catItem.getName() );

			File uhsFile = new File( hintsDir, catItem.getName() );
			if ( uhsFile.exists() ) {
				menu.findItem( R.id.openFileContextAction ).setEnabled( true );
				menu.findItem( R.id.deleteFileContextAction ).setEnabled( true );
			}
		}
	}

	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
		CatalogItem catItem = null;
		File uhsFile = null;

		switch ( item.getItemId() ) {
			case R.id.openFileContextAction:
				catItem = catalogAdapter.getItem( info.position );
				if ( catItem.getName().length() == 0 ) {
					// TODO: Complain.
					return false;
				}
				uhsFile = new File(hintsDir, catItem.getName());
				String uhsPath = uhsFile.getAbsolutePath();
				if ( !uhsFile.exists() ) {
					// TODO: Complain.
					return false;
				}
				openFile( uhsPath );
				return true;

			case R.id.fetchFileContextAction:
				catItem = catalogAdapter.getItem( info.position );
				if ( catItem.getName().length() == 0 ) {
					// TODO: Complain.
					return false;
				}

				fetchUHS( catItem );
				return true;

			case R.id.deleteFileContextAction:
				catItem = catalogAdapter.getItem( info.position );
				if ( catItem.getName().length() == 0 ) {
					// TODO: Complain.
					return false;
				}
				uhsFile = new File( hintsDir, catItem.getName() );
				if ( uhsFile.exists() ) {
					uhsFile.delete();
					colorizeCatalogRow( catItem );
					catalogAdapter.notifyDataSetChanged();
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

	private void fetchUHS( CatalogItem catItem ) {
		cancelFetching();
		logger.info( "Fetching \"{}\"", catItem.getName() );

		uhsFetchTask = new UHSFetchTask( hintsDir );
		uhsFetchTask.setUserAgent( CatalogParser.DEFAULT_USER_AGENT );
		uhsFetchTask.setObserver( this );
		uhsFetchTask.execute( catItem );
	}

	private void fetchCatalog() {
		cancelFetching();
		logger.info( "Fetching catalog" );

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
			logger.info( "Saved \"{}\"", fetchResult.file.getName() );
			Toast.makeText( this, String.format( "Saved %s", fetchResult.file.getName() ), Toast.LENGTH_SHORT ).show();
		}
		else {
			if ( fetchResult.status != UHSFetchResult.STATUS_CANCELLED ) {
				Throwable t = fetchResult.errorCause;
				logger.error( "Download failed: {}", (( t != null ) ? t : "Unknown error") );

				String message = String.format( "Download failed: %s", (( t != null ) ? t : "Unknown error") );
				Toast.makeText( this, message, Toast.LENGTH_LONG ).show();
			}
			if ( fetchResult.file != null && fetchResult.file.exists() ) {
				fetchResult.file.delete();
			}
		}
		colorizeCatalogRow( fetchResult.catItem );
		catalogAdapter.notifyDataSetChanged();
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

			List<CatalogItem> catalog = catalogParser.parseCatalog( fetchResult.content );
			if ( catalog.size() > 0 ) {
				setCatalog( catalog );
				storeCatalog( catalog );
			}
			else {
				logger.error( "Catalog was empty or parsing failed" );
				Toast.makeText( this, "Catalog was empty or parsing failed", Toast.LENGTH_LONG ).show();
			}
		}
		else {
			if ( fetchResult.status != StringFetchResult.STATUS_CANCELLED ) {
				Throwable t = fetchResult.errorCause;
				logger.error( "Download failed: {}", (( t != null ) ? t : "Unknown error") );

				String message = String.format( "Download failed: %s", (( t != null ) ? t : "Unknown error") );
				Toast.makeText( this, message, Toast.LENGTH_LONG ).show();
			}
		}
	}


	/**
	 * Updates state flags on all catalog entries.
	 *
	 * <p>Note: Remember to call notifyDataSetChanged() on the ListView's ArrayAdapter afterward.</p>
	 */
	public void colorizeCatalog( List<CatalogItem> catalog ) {

		String[] hintNames = hintsDir.list();
		Arrays.sort( hintNames );

		for ( CatalogItem catItem : catalog ) {
			catItem.resetState();

			if ( Arrays.binarySearch( hintNames, catItem.getName() ) >= 0 ) {
				catItem.setLocal( true );
				File uhsFile = new File( hintsDir, catItem.getName() );
				Date localDate = new Date( uhsFile.lastModified() );

				if ( catItem.getDate() != null && catItem.getDate().after( localDate ) ) {
					catItem.setNewer( true );
				}
			}
		}
	}

	/**
	 * Updates state flags on a catalog entry.
	 *
	 * <p>Note: Remember to call notifyDataSetChanged() on the ListView's ArrayAdapter afterward.</p>
	 */
	public void colorizeCatalogRow( CatalogItem catItem ) {
		catItem.resetState();

		if ( catItem.getName().length() > 0 ) {
			File uhsFile = new File( hintsDir, catItem.getName() );
			if ( uhsFile.exists() ) {
				catItem.setLocal( true );

				if ( catItem.getDate() != null && catItem.getDate().after( new Date( uhsFile.lastModified() ) ) ) {
						catItem.setNewer( true );
				}
			}
		}
	}


	/**
	 * Repopulates the catalog list with new items.
	 */
	public void setCatalog( List<CatalogItem> catalog ) {
		colorizeCatalog( catalog );
		catalogAdapter.setCatalog( catalog );
		catalogAdapter.setLocalFilterEnabled( false );
		catalogAdapter.setSortFilter( catalogTitleComparator );
		catalogAdapter.applyFilters();
	}

	/**
	 * Deserializes a cached catalog file.
	 */
	public void loadCatalog() {
		logger.info( "Loading cached catalog" );
		FileInputStream fis = null;
		BufferedReader reader = null;
		boolean failed = false;
		try {
			fis = new FileInputStream( cachedCatalogFile );
			reader = new BufferedReader( new InputStreamReader( fis, "UTF-8" ) );
			List<CatalogItem> catalog = jsonMapper.readValue( reader, new TypeReference<List<CatalogItem>>() {} );
			setCatalog( catalog );
			catalogAdapter.setLocalFilterEnabled( true );
			catalogAdapter.setSortFilter( catalogTitleComparator );
			catalogAdapter.applyFilters();

			Date cacheDate = new Date( cachedCatalogFile.lastModified() );
			String cacheDateString = new SimpleDateFormat( "yyyy-MM-dd" ).format( cacheDate );
			Toast.makeText( this, String.format( "Last refresh: %s", cacheDateString ), Toast.LENGTH_SHORT ).show();
		}
		catch ( IOException e ) {
			logger.error( "Error loading cached catalog", e );
			Toast.makeText( this, String.format( "Error loading cached catalog: %s", e.getMessage() ), Toast.LENGTH_LONG ).show();
			failed = true;
		}
		finally {
			try {if ( reader != null ) reader.close();} catch ( IOException e ) {}
			try {if ( fis != null ) fis.close();} catch ( IOException e ) {}
			if ( failed && cachedCatalogFile.exists() ) cachedCatalogFile.delete();
		}
	}

	/**
	 * Serializes a catalog to a file.
	 */
	public void storeCatalog( List<CatalogItem> catalog ) {
		logger.info( "Caching the catalog" );
		FileOutputStream fos = null;
		BufferedWriter writer = null;
		boolean failed = false;
		try {
			fos = new FileOutputStream( cachedCatalogFile );
			writer = new BufferedWriter( new OutputStreamWriter( fos, "UTF-8" ) );
			//jsonMapper.writerWithType( new TypeReference<CatalogItem[]>() {} ).writeValue( writer, catalog.toArray( new CatalogItem[catalog.size()] ) );
			jsonMapper.writerWithType( new TypeReference<List<CatalogItem>>() {} ).writeValue( writer, catalog );
		}
		catch ( IOException e ) {
			logger.error( "Error caching catalog", e );
			Toast.makeText( this, String.format( "Error caching catalog: %s", e.getMessage() ), Toast.LENGTH_LONG ).show();
			failed = true;
		}
		finally {
			try {if ( writer != null ) writer.close();} catch ( IOException e ) {}
			try {if ( fos != null ) fos.close();} catch ( IOException e ) {}
			if ( failed && cachedCatalogFile.exists() ) cachedCatalogFile.delete();
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
