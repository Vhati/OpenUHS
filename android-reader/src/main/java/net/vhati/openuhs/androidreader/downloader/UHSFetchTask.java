package net.vhati.openuhs.androidreader.downloader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

import android.content.Context;
import android.os.AsyncTask;

import net.vhati.openuhs.core.downloader.DownloadableUHS;


public class UHSFetchTask extends AsyncTask<DownloadableUHS, Integer, UHSFetchTask.UHSFetchResult> {

	// doInBackground()'s param is the first generic: [duhs].
	// It reports the second generic, [a percentage], to onProgressUpdate().
	// It returns the third generic [result] to onPostExecute().

	private UHSFetchObserver delegate = null;
	private String userAgent = System.getProperty("http.agent");

	private File destDir;


	public UHSFetchTask(File destDir) {
		this.destDir = destDir;
	}


	public void setObserver(UHSFetchObserver delegate) {
		this.delegate = delegate;
	}

	public void setUserAgent(String s) {
		this.userAgent = s;
	}

	public String getUserAgent() {
		return userAgent;
	}


	// This runs in a background thread, unlike the other methods here.
	@Override
	protected UHSFetchResult doInBackground(DownloadableUHS... duhs) {
		HttpURLConnection con = null;
		InputStream downloadStream = null;
		ZipInputStream unzipStream = null;
		OutputStream os = null;
		File uhsFile = null;

		DownloadableUHS tmpUHS = duhs[0];
		String urlString = tmpUHS.getUrl();
		UHSFetchResult fetchResult = new UHSFetchResult(tmpUHS);

		try {
			con = (HttpURLConnection)(new URL(urlString).openConnection());
			con.setRequestProperty("User-Agent", userAgent);
			con.connect();

			if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
				fetchResult.status = UHSFetchResult.STATUS_ERROR;
				fetchResult.message = "Server returned HTTP "+ con.getResponseCode() +" "+ con.getResponseMessage();
				return fetchResult;
			}

			// Get the zip file's length, if the server reports it. (possibly -1).
			//int zipLength = con.getContentLength();

			downloadStream = con.getInputStream();
			unzipStream = new ZipInputStream(new BufferedInputStream(downloadStream));

			//No need for a while loop; only one file.
			//  Each pass reads the zip stream /as if/ it were one entry.
			//  Contrary to the doc, zip errors can occur if there is no next entry
			ZipEntry ze;
			if ((ze = unzipStream.getNextEntry()) != null) {
				// Get the uncompressed uhs file's length. (possibly -1)
				long uhsLength = ze.getSize();

				// Presumably ze.getName().equals(tmpUHS.getName()).

				uhsFile = new File(destDir, tmpUHS.getName());
				fetchResult.file = uhsFile;
				os = new FileOutputStream(uhsFile);

				byte data[] = new byte[4096];
				long total = 0;
				int count;
				while ((count=unzipStream.read(data)) != -1) {
					if (isCancelled()) {
						unzipStream.close();
						if (uhsFile.exists()) uhsFile.delete();

						fetchResult.status = UHSFetchResult.STATUS_CANCELLED;
						return fetchResult;
					}
					total += count;
					if (uhsLength > 0) {
						this.publishProgress((int)(total * 100 / uhsLength));
					}
					os.write(data, 0, count);
				}
			}
			unzipStream.close();
		}
		catch (Exception e) {
			fetchResult.status = UHSFetchResult.STATUS_ERROR;
			fetchResult.message = toString();
			return fetchResult;
		}
		finally {
			try {if (unzipStream != null) unzipStream.close();} catch (IOException e) {}
			try {if (downloadStream != null) downloadStream.close();} catch (IOException e) {}
			try {if (os != null) os.close();} catch (IOException e) {}
			if (con != null) con.disconnect();
		}

		fetchResult.status = UHSFetchResult.STATUS_COMPLETED;
		return fetchResult;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		delegate.uhsFetchStarted();
	}

	@Override
	protected void onProgressUpdate(Integer... progress) {
		super.onProgressUpdate(progress);
		if (delegate != null) delegate.uhsFetchUpdate(progress[0].intValue());
	}

	@Override
	protected void onPostExecute(UHSFetchResult fetchResult) {
		if (delegate != null) delegate.uhsFetchEnded(fetchResult);
	}



	public static class UHSFetchResult {
		public static final int STATUS_DOWNLOADING = 0;
		public static final int STATUS_COMPLETED = 1;
		public static final int STATUS_CANCELLED = 2;
		public static final int STATUS_ERROR = 3;

		public DownloadableUHS duh;
		public int status = STATUS_DOWNLOADING;
		public String message = null;
		public File file = null;

		public UHSFetchResult(DownloadableUHS duh) {
			this.duh = duh;
		}
	}


	public static interface UHSFetchObserver {
		public void uhsFetchStarted();
		public void uhsFetchUpdate(int progress);
		public void uhsFetchEnded(UHSFetchResult fetchResult);
	}
}
