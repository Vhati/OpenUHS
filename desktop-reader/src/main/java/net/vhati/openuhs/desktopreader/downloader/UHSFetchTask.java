package net.vhati.openuhs.desktopreader.downloader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import javax.swing.SwingWorker;

import net.vhati.openuhs.core.downloader.CatalogItem;
import net.vhati.openuhs.desktopreader.downloader.FetchUnitException;


/**
 * A background task that downloads a hint file, while unzipping it.
 * <p>
 * Progress can be monitored with a PropertyChangeListener.
 * <p>
 * Available properties:
 * <ul>
 * <li>state: One of the SwingWorker.StateValue constants.</li>
 * <li>progress: Overall progress, from 0 to 100.</li>
 * <li>PROP_UNIT_NAME: the name of an individual download currently in progress.</li>
 * <li>PROP_UNIT_PROGRESS: progress for an individual download.</li>
 * </ul>
 */
public class UHSFetchTask extends SwingWorker<List<UHSFetchTask.UHSFetchResult>, Object> {

	// First generic is the result, returned by doInBackground().
	// Second generic is for returning intermediate results while running. (Unused)

	public static final String PROP_UNIT_NAME = "unitName";
	public static final String PROP_UNIT_PROGRESS = "unitProgress";

	private volatile boolean aborting = false;
	private String userAgent = System.getProperty( "http.agent" );

	private File destDir;
	private CatalogItem[] catItems;


	public UHSFetchTask( File destDir, CatalogItem... catItems ) {
		this.destDir = destDir;
		this.catItems = catItems;
	}


	public void setUserAgent( String s ) {
		this.userAgent = s;
	}

	public String getUserAgent() {
		return userAgent;
	}


	/**
	 * Signals that the task should end gracefully.
	 * <p>
	 * SwingWorker's cancel() will cause get() to throw a CancellationException.
	 * Use this method instead.
	 * <p>
	 * This method is thread-safe.
	 */
	public void abortTask() {
		aborting = true;
	}


	@Override
	protected List<UHSFetchResult> doInBackground() {
		List<UHSFetchResult> fetchResults = new ArrayList<UHSFetchResult>( catItems.length );
		String unitName = null;
		int unitProgress = 0;

		for ( int unitIndex=0; unitIndex < catItems.length; unitIndex++ ) {
			CatalogItem catItem = catItems[unitIndex];
			UHSFetchResult fetchResult = new UHSFetchResult( catItem );

			if ( isCancelled() || aborting ) {
				fetchResult.status = UHSFetchResult.STATUS_CANCELLED;
				fetchResults.add( fetchResult );
				continue;
			}

			String unitNameOld = unitName;
			unitName = catItem.getName();
			this.getPropertyChangeSupport().firePropertyChange( PROP_UNIT_NAME, unitNameOld, unitName );

			int unitProgressOld = unitProgress;
			unitProgress = 0;
			this.getPropertyChangeSupport().firePropertyChange( PROP_UNIT_PROGRESS, unitProgressOld, unitProgress );

			HttpURLConnection con = null;
			InputStream downloadStream = null;
			ZipInputStream unzipStream = null;
			OutputStream os = null;
			File uhsFile = null;

			String urlString = catItem.getUrl();
			Exception ex = null;
			try {
				con = (HttpURLConnection)(new URL( urlString ).openConnection());
				con.setRequestProperty( "User-Agent", userAgent );
				con.connect();

				if ( con.getResponseCode() != HttpURLConnection.HTTP_OK ) {
					throw new FetchUnitException( String.format( "Server returned HTTP %s %s", con.getResponseCode(), con.getResponseMessage() ) );
				}

				// Get the zip file's length, if the server reports it. (possibly -1).
				//int zipLength = con.getContentLength();

				downloadStream = con.getInputStream();
				unzipStream = new ZipInputStream( new BufferedInputStream( downloadStream ) );

				//No need for a while loop; only one file.
				//  Each pass reads the zip stream /as if/ it were one entry.
				//  Contrary to the doc, zip errors can occur if there is no next entry
				ZipEntry ze;
				if ( (ze = unzipStream.getNextEntry()) != null ) {
					// Get the uncompressed uhs file's length. (possibly -1)
					long uhsLength = ze.getSize();

					// Presumably ze.getName().equals( catItem.getName() ).

					uhsFile = new File( destDir, catItem.getName() );
					fetchResult.file = uhsFile;
					os = new FileOutputStream( uhsFile );

					byte data[] = new byte[4096];
					long total = 0;
					int count;
					while ( (count=unzipStream.read( data )) != -1 ) {
						if ( this.isCancelled() || aborting ) {
							fetchResult.status = UHSFetchResult.STATUS_CANCELLED;
							break;
						}
						total += count;
						if ( uhsLength > 0 ) {
							unitProgressOld = unitProgress;
							unitProgress = (int)(total * 100 / uhsLength);
							this.getPropertyChangeSupport().firePropertyChange( PROP_UNIT_PROGRESS, unitProgressOld, unitProgress );
						}
						os.write( data, 0, count );
					}
				}

				if ( fetchResult.status == UHSFetchResult.STATUS_DOWNLOADING ) {
					fetchResult.status = UHSFetchResult.STATUS_COMPLETED;
				}
				fetchResults.add( fetchResult );
			}
			catch ( IOException e ) {
				ex = e;
			}
			catch ( FetchUnitException e ) {
				ex = e;
			}
			finally {
				try {if ( unzipStream != null ) unzipStream.close();} catch ( IOException e ) {}
				try {if ( downloadStream != null ) downloadStream.close();} catch ( IOException e ) {}
				try {if ( os != null ) os.close();} catch ( IOException e ) {}
				if ( con != null ) con.disconnect();
			}
			if ( ex != null ) {
				fetchResult.status = UHSFetchResult.STATUS_ERROR;
				fetchResult.errorCause = ex;
				fetchResults.add( fetchResult );
			}
			if ( fetchResult.status != UHSFetchResult.STATUS_COMPLETED && fetchResult.file != null && fetchResult.file.exists() ) {
				fetchResult.file.delete();
			}

			this.setProgress( ((unitIndex+1) * 100 / catItems.length) );
		}

		return fetchResults;
	}

	@Override
	protected void done() {
		// Could trugger callbacks here, or let the listener react to a status change.
	}



	public static class UHSFetchResult {
		public static final int STATUS_DOWNLOADING = 0;
		public static final int STATUS_COMPLETED = 1;
		public static final int STATUS_CANCELLED = 2;
		public static final int STATUS_ERROR = 3;

		public CatalogItem catItem;
		public int status = STATUS_DOWNLOADING;
		public Throwable errorCause = null;
		public File file = null;

		public UHSFetchResult( CatalogItem catItem ) {
			this.catItem = catItem;
		}
	}
}
