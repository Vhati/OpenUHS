package net.vhati.openuhs.desktopreader.downloader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.swing.SwingWorker;

import net.vhati.openuhs.desktopreader.downloader.FetchUnitException;


/**
 * A background task that downloads text, then returns it, decoded as a String.
 *
 * <p>Progress can be monitored with a PropertyChangeListener.</p>
 *
 * <p>Available properties:
 * <ul>
 * <li>state: One of the SwingWorker.StateValue constants.</li>
 * <li>progress: Overall progress, from 0 to 100.</li>
 * </ul></p>
 */
public class StringFetchTask extends SwingWorker<StringFetchTask.StringFetchResult, Object> {

	// First generic is the result, returned by doInBackground().
	// Second generic is for returning intermediate results while running. (Unused)

	private volatile boolean aborting = false;
	private String userAgent = System.getProperty( "http.agent" );
	private String encoding = "utf-8";

	private String urlString;


	public StringFetchTask( String urlString ) {
		this.urlString = urlString;
	}


	public void setUserAgent( String s ) {
		this.userAgent = s;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setEncoding( String s ) {
		this.encoding = s;
	}

	public String getEncoding() {
		return encoding;
	}


	/**
	 * Signals that the task should end gracefully.
	 *
	 * <p>SwingWorker's cancel() will cause get() to throw a CancellationException.
	 * Use this method instead.</p>
	 *
	 * <p>This method is thread-safe.</p>
	 */
	public void abortTask() {
		aborting = true;
	}


	@Override
	public StringFetchResult doInBackground() {
		HttpURLConnection con = null;
		InputStream downloadStream = null;
		BufferedReader r = null;
		StringBuilder contentString = null;

		StringFetchResult fetchResult = new StringFetchResult( urlString );
		Exception ex = null;

		try {
			con = (HttpURLConnection)(new URL( urlString ).openConnection());
			con.setRequestProperty( "User-Agent", userAgent );
			con.connect();

			if ( con.getResponseCode() != HttpURLConnection.HTTP_OK ) {
				throw new FetchUnitException( "Server returned HTTP "+ con.getResponseCode() +" "+ con.getResponseMessage() );
			}

			// Get the file's length, if the server reports it. (possibly -1).
			int contentLength = con.getContentLength();

			downloadStream = con.getInputStream();
			r = new BufferedReader( new InputStreamReader( downloadStream, encoding ) );

			contentString = new StringBuilder();
			char data[] = new char[1024];
			long total = 0;
			int count;
			while ( (count=r.read( data, 0, data.length )) != -1 ) {
				if ( this.isCancelled() || aborting ) {
					r.close();

					fetchResult.status = StringFetchResult.STATUS_CANCELLED;
					return fetchResult;
				}
				total += count;
				if ( contentLength > 0 ) {
					this.setProgress( (int)(total * 100 / contentLength) );
				}
				contentString.append( data, 0, count );
			}

			r.close();

			fetchResult.status = StringFetchResult.STATUS_COMPLETED;
			fetchResult.content = contentString.toString();
		}
		catch ( IOException e ) {
			ex = e;
		}
		catch ( FetchUnitException e ) {
			ex = e;
		}
		finally {
			try {if ( r != null ) r.close();} catch ( IOException e ) {}
			try {if ( downloadStream != null ) downloadStream.close();} catch ( IOException e ) {}
			if ( con != null ) con.disconnect();
		}
		if ( ex != null ) {
			fetchResult.status = StringFetchResult.STATUS_ERROR;
			fetchResult.errorCause = ex;
		}

		return fetchResult;
	}

	@Override
	protected void done() {
		// Could trugger callbacks here, or let the listener react to a status change.
	}



	public static class StringFetchResult {
		public static final int STATUS_DOWNLOADING = 0;
		public static final int STATUS_COMPLETED = 1;
		public static final int STATUS_CANCELLED = 2;
		public static final int STATUS_ERROR = 3;

		public String urlString;
		public int status = STATUS_DOWNLOADING;
		public Throwable errorCause = null;
		public String content = null;

		public StringFetchResult( String urlString ) {
			this.urlString = urlString;
		}
	}
}
