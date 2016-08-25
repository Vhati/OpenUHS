// http://www.java-tips.org/java-se-tips/javax.swing/how-to-create-a-download-manager-in-java.html

package net.vhati.openuhs.androidreader.downloader;

import java.util.ArrayList;
import java.util.Observable;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.UnknownHostException;
import java.net.URL;
import java.net.URLConnection;

import net.vhati.openuhs.core.DefaultUHSErrorHandler;
import net.vhati.openuhs.core.UHSErrorHandler;


public class UrlFetcher extends Observable implements Runnable {
  private static final int BUFFER_SIZE = 1024;

  public static final String STATUSES[] = {"Downloading", "Complete", "Cancelled", "Error"};
  public static final int DOWNLOADING = 0;
  public static final int COMPLETE = 1;
  public static final int CANCELLED = 2;
  public static final int ERROR = 3;

  private UHSErrorHandler errorHandler = new DefaultUHSErrorHandler(System.err);

  private String address = null;
  private String userAgent = "";
  private int size = -1;
  private int downloaded = 0;
  private int status = DOWNLOADING;
  private byte[] receivedBytes = null;


  public UrlFetcher(String address) {
    super();
    this.address = address;
  }


  /**
   * Sets the error handler to notify of exceptions.
   * This is a convenience for logging/muting.
   * The default handler prints to System.err.
   *
   * @param eh the error handler, or null, for quiet parsing
   */
  public void setErrorHandler(UHSErrorHandler eh) {
    errorHandler = eh;
  }


  public void setUserAgent(String s) {userAgent = s;}
  public String getUserAgent() {return userAgent;}

  public String getUrl() {return address;}
  public int getSize() {return size;}
  public int getStatus() {return status;}
  public byte[] getReceivedBytes() {
    if (getStatus() == COMPLETE) return receivedBytes;
    else return null;
  }

  /**
   * Returns the percentage transferred.
   */
  public float getProgress() {return ((float)downloaded/size)*100;}


  public void cancel() {status = CANCELLED; stateChanged();}
  public void error() {status = ERROR; stateChanged();}


  public void download() {
    Thread t = new Thread(this);
    t.start();
  }


  public void run() {
    HttpURLConnection.setFollowRedirects(false);
    try {
      URL url = new URL(address);
      URLConnection connection = url.openConnection();
      if (connection instanceof HttpURLConnection) {
        HttpURLConnection httpConnection = (HttpURLConnection)connection;
          httpConnection.addRequestProperty("User-Agent", userAgent);
          httpConnection.connect();

        int response = httpConnection.getResponseCode();
        if (response < 200 || response >= 300) {
          if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, null, "Could not fetch file. Response code: "+ response, 0, null);
          error();
          return;
        }

        int contentLength = connection.getContentLength();
        if (contentLength < 1) {error(); return;}
        if (size == -1) {size = contentLength; stateChanged();}

        InputStream is = httpConnection.getInputStream();
        java.nio.channels.ReadableByteChannel channel = java.nio.channels.Channels.newChannel(is);

        ArrayList bufs = new ArrayList();
        int tmpByte;
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(BUFFER_SIZE);
        bufs.add(buf);
        while (status == DOWNLOADING) {
          int read = channel.read(buf);
          if (read == -1) break;

          if (buf.remaining() == 0) {
            buf = java.nio.ByteBuffer.allocate(BUFFER_SIZE);
            bufs.add(buf);
          }
          downloaded += read;
          stateChanged();
        }
        is.close();

        int bufCount = bufs.size();
        int bufTotalSize = BUFFER_SIZE * bufCount;
        if (bufCount > 0)
          bufTotalSize -= ((java.nio.ByteBuffer)bufs.get(bufCount-1)).remaining();
        byte[] bytes = new byte[bufTotalSize];
        for (int i=0; i <= bufCount-2; i++) {
          buf = ((java.nio.ByteBuffer)bufs.get(i));
          buf.rewind();
          buf.get(bytes, i*BUFFER_SIZE, BUFFER_SIZE);
        }
        if (bufCount > 0) {
          buf = ((java.nio.ByteBuffer)bufs.get(bufCount-1));
          buf.flip();
          buf.get(bytes, (bufCount-1)*BUFFER_SIZE, buf.remaining());
        }
        receivedBytes = bytes;
      }
      if (status == DOWNLOADING) {
        status = COMPLETE;
        stateChanged();
        return;
      }
    }
    catch (InterruptedIOException e) {
      if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, null, "Download thread was interrupted", 0, e);
    }
    catch (UnknownHostException e) {
      if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, null, "Could not locate remote server", 0, e);
    }
    catch(ConnectException e) {
      if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, null, "Could not connect to remote server", 0, e);
    }
    catch (IOException e) {
      if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, null, "Could not download from server", 0, e);
    }
    error();
  }


  private void stateChanged() {
    setChanged();
    notifyObservers();
  }
}
