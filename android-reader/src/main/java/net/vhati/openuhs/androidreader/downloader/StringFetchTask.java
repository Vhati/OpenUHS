package net.vhati.openuhs.androidreader.downloader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import android.os.AsyncTask;


public class StringFetchTask extends AsyncTask<String, Integer, StringFetchTask.StringFetchResult> {

	// doInBackground()'s param is the first generic: [url].
	// It reports the second generic, [a percentage], to onProgressUpdate().
	// It returns the third generic [result] to onPostExecute().

	private StringFetchObserver delegate = null;
	private String userAgent = System.getProperty("http.agent");
	private String encoding = "utf-8";


	public StringFetchTask() {
	}


	public void setObserver(StringFetchObserver delegate) {
		this.delegate = delegate;
	}

	public void setUserAgent(String s) {
		this.userAgent = s;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setEncoding(String s) {
		this.encoding = s;
	}

	public String getEncoding() {
		return encoding;
	}


	// This runs in a background thread, unlike the other methods here.
	@Override
	protected StringFetchResult doInBackground(String... urlStrings) {
		HttpURLConnection con = null;
		InputStream downloadStream = null;
		BufferedReader r = null;
		StringBuilder contentString = null;

		String urlString = urlStrings[0];
		StringFetchResult fetchResult = new StringFetchResult(urlString);

		try {
			con = (HttpURLConnection)(new URL(urlString).openConnection());
			con.setRequestProperty("User-Agent", userAgent);
			con.connect();

			if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
				fetchResult.status = StringFetchResult.STATUS_ERROR;
				fetchResult.message = "Server returned HTTP "+ con.getResponseCode() +" "+ con.getResponseMessage();
				return fetchResult;
			}

			// Get the file's length, if the server reports it. (possibly -1).
			int contentLength = con.getContentLength();

			downloadStream = con.getInputStream();
			r = new BufferedReader(new InputStreamReader(downloadStream, encoding));

			contentString = new StringBuilder();
			char data[] = new char[1024];
			long total = 0;
			int count;
			while ((count=r.read(data, 0, data.length)) != -1) {
				if (isCancelled()) {
					r.close();

					fetchResult.status = StringFetchResult.STATUS_CANCELLED;
					return fetchResult;
				}
				total += count;
				if (contentLength > 0) {
					this.publishProgress((int)(total * 100 / contentLength));
				}
				contentString.append(data, 0, count);
			}

			r.close();
		}
		catch (Exception e) {
			fetchResult.status = StringFetchResult.STATUS_ERROR;
			fetchResult.message = toString();
			return fetchResult;
		}
		finally {
			try {if (r != null) r.close();} catch (IOException e) {}
			try {if (downloadStream != null) downloadStream.close();} catch (IOException e) {}
			if (con != null) con.disconnect();
		}

		fetchResult.status = StringFetchResult.STATUS_COMPLETED;
		fetchResult.content = contentString.toString();
		return fetchResult;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		if (delegate != null) delegate.stringFetchStarted();
	}

	@Override
	protected void onProgressUpdate(Integer... progress) {
		super.onProgressUpdate(progress);
		if (delegate != null) delegate.stringFetchUpdate(progress[0].intValue());
	}

	@Override
	protected void onPostExecute(StringFetchResult fetchResult) {
		if (delegate != null) delegate.stringFetchEnded(fetchResult);
	}



	public static class StringFetchResult {
		public static final int STATUS_DOWNLOADING = 0;
		public static final int STATUS_COMPLETED = 1;
		public static final int STATUS_CANCELLED = 2;
		public static final int STATUS_ERROR = 3;

		public String urlString;
		public int status = STATUS_DOWNLOADING;
		public String message = null;
		public String content = null;

		public StringFetchResult(String urlString) {
			this.urlString = urlString;
		}
	}


	public static interface StringFetchObserver {
		public void stringFetchStarted();
		public void stringFetchUpdate(int progress);
		public void stringFetchEnded(StringFetchResult fetchResult);
	}
}
