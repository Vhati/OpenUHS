package net.vhati.openuhs.androidreader.downloader;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.UnknownHostException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import net.vhati.openuhs.androidreader.downloader.DownloadableUHS;
import net.vhati.openuhs.core.DefaultUHSErrorHandler;
import net.vhati.openuhs.core.UHSErrorHandler;


/**
 * A collection of utility methods for downloading/saving files and parsing the official UHS catalog.
 */
public class UHSFetcher {
  public static String catalogUrl = "http://www.uhs-hints.com:80/cgi-bin/update.cgi";
  public static String userAgent = "UHSWIN/5.2";

  private static UHSErrorHandler errorHandler = new DefaultUHSErrorHandler(System.err);


  /**
   * Sets the error handler to notify of exceptions.
   * This is a convenience for logging/muting.
   * The default handler prints to System.err.
   *
   * @param eh the error handler, or null, for quiet parsing
   */
  public static void setErrorHandler(UHSErrorHandler eh) {
    errorHandler = eh;
  }


  /**
   *
   * Parses the xml catalog of available hint files.
   *
   * @return an array of DownloadableUHS objects
   */
  public static List<DownloadableUHS> parseCatalog(byte[] catalogBytes) {
    List<DownloadableUHS> catalog = new ArrayList<DownloadableUHS>();

    if (catalogBytes == null) return catalog;

    String catalogString = new String(catalogBytes);
    catalogString = catalogString.replaceAll("[\r\n]", "");

    int length = catalogString.length();
    for (int i=0; i < length;) {
      String fileChunk = parseChunk(catalogString, "<FILE>", "</FILE>", i);
      if (fileChunk == null) break;
      i = catalogString.indexOf("<FILE>", i) + 6 + fileChunk.length() + 7;

      DownloadableUHS tmpUHS = new DownloadableUHS();
        tmpUHS.setTitle( parseChunk(fileChunk, "<FTITLE>", "</FTITLE>", 0) );

        tmpUHS.setUrl( parseChunk(fileChunk, "<FURL>", "</FURL>", 0) );

        tmpUHS.setName( parseChunk(fileChunk, "<FNAME>", "</FNAME>", 0) );

        tmpUHS.setDate( parseChunk(fileChunk, "<FDATE>", "</FDATE>", 0) );

        tmpUHS.setCompressedSize( parseChunk(fileChunk, "<FSIZE>", "</FSIZE>", 0) );

        tmpUHS.setFullSize( parseChunk(fileChunk, "<FFULLSIZE>", "</FFULLSIZE>", 0) );

      catalog.add(tmpUHS);
    }

    return catalog;
  }


  /**
   * Extracts a substring between known tokens.
   * <br />This is used to parse the catalog's xml-like markup.
   *
   * @return the substring, or null if the tokens are not present.
   */
  private static String parseChunk(String line, String prefix, String suffix, int fromIndex) {
    int start = line.indexOf(prefix, fromIndex);
    if (start == -1) return null;
    start += prefix.length();
    int end = line.indexOf(suffix, start);
    if (end == -1) return null;

    if (end - start < 0) return null;
    String tmp = line.substring(start, end);
    return tmp;
  }


  /**
   *
   * Downloads a hint file.
   *
   * @param parentComponent a component to be the monitor's parent
   * @param uhs hint to fetch
   * @return the downloaded data
   */
  public static byte[] fetchUHS(/*Component parentComponent,*/ DownloadableUHS uhs) {
    byte[] fullBytes = null;

    if (errorHandler != null) errorHandler.log(UHSErrorHandler.INFO, null, "Fetching "+ uhs.getName() +"...", 0, null);
    byte[] responseBytes = null; //fetchURL(/*parentComponent, "Fetching "+ uhs.getName() +"...",*/ uhs.getUrl());
    if (responseBytes == null) return fullBytes;

    ZipInputStream is = new ZipInputStream(new ByteArrayInputStream(responseBytes));
    try {
      //No need for a while loop; only one file
      //  and contrary to the doc, zip errors can occur if there is no next entry
      ZipEntry ze;
      if ((ze = is.getNextEntry()) != null) {
        if (errorHandler != null) errorHandler.log(UHSErrorHandler.INFO, null, "Extracting "+ ze.getName() +"...", 0, null);

        int chunkSize = 1024;
        ArrayList bufs = new ArrayList();
        int tmpByte;
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(chunkSize);
        bufs.add(buf);
        while ((tmpByte = is.read()) != -1) {
          buf.put((byte)tmpByte);
          if (buf.remaining()==0) {
            buf = java.nio.ByteBuffer.allocate(chunkSize);
            bufs.add(buf);
          }
        }

        int bufCount = bufs.size();
        int bufTotalSize = chunkSize * bufCount;
        if (bufCount > 1)
          bufTotalSize -= ((java.nio.ByteBuffer)bufs.get(bufCount-1)).remaining();
        fullBytes = new byte[bufTotalSize];
        for (int i=0; i <= bufCount-2; i++) {
          buf = ((java.nio.ByteBuffer)bufs.get(i));
          buf.rewind();
          buf.get(fullBytes, i*chunkSize, chunkSize);
        }
        if (bufCount > 1) {
          buf = ((java.nio.ByteBuffer)bufs.get(bufCount-1));
          buf.flip();
          buf.get(fullBytes, (bufCount-1)*chunkSize, buf.remaining());
        }

      }
      is.close();
    }
    catch (ZipException e) {
      if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, null, "Could not extract downloaded hints for "+ uhs.getName(), 0, e);
    }
    catch (IOException e) {
      if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, null, "Could not extract downloaded hints for "+ uhs.getName(), 0, e);
    }
    finally {
      try{is.close();}
      catch (Exception f) {}
    }

    return fullBytes;
  }


  /**
   *
   * Saves an array of bytes to a file.
   * <br />If the destination exists, a confirmation dialog will appear.
   *
   * @param parentComponent a component to be the parent of any error popups
   * @param path path to a file to save
   * @param bytes data to save
   * @return true if successful, false otherwise
   */
  public static boolean saveBytes(/*Component parentComponent,*/ String path, byte[] bytes) {
    boolean result = false;
    File destFile = new File(path);
    if (bytes == null || new File(destFile.getParent()).exists() == false) return result;

    if (destFile.exists()) {
      destFile.delete();
    }

    BufferedOutputStream dest = null;
    try {
      int chunkSize = 1024;
      FileOutputStream fos = new FileOutputStream(path);
      dest = new BufferedOutputStream(fos, chunkSize);
      dest.write(bytes);
      dest.flush();
      dest.close();

      result = true;
    }
    catch (IOException e) {
      if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, null, "Could not write to "+ path, 0, e);
    }
    finally {
      try{if (dest != null) dest.close();}
      catch (Exception f) {}
    }

    return result;
  }


  /**
   * Gets the User-Agent to use for http requests.
   *
   * @return the agent text
   * @see #setUserAgent(String) setUserAgent(String)
   */
  public static String getUserAgent() {
    return userAgent;
  }

  /**
   * Sets the User-Agent to use for http requests.
   *
   * The default is "UHSWIN/5.2".
   *
   * @param s the agent text
   * @see #getUserAgent() getUserAgent()
   */
  public static void setUserAgent(String s) {
    if (s != null) userAgent = s;
  }


  /**
   * Gets the url of the hint catalog.
   *
   * @return the url
   * @see #setCatalogUrl(String) setCatalogUrl(String)
   */
  public static String getCatalogUrl() {
    return catalogUrl;
  }

  /**
   * Gets the url of the hint catalog.
   *
   * The default is "http://www.uhs-hints.com:80/cgi-bin/update.cgi".
   *
   * @param s the url
   * @see #getCatalogUrl() getCatalogUrl()
   */
  public static void setCatalogUrl(String s) {
    if (s != null) catalogUrl = s;
  }
}
