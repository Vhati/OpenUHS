package net.vhati.openuhs.androidreader.downloader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.vhati.openuhs.androidreader.downloader.DownloadableUHS;
import net.vhati.openuhs.core.DefaultUHSErrorHandler;
import net.vhati.openuhs.core.UHSErrorHandler;


/**
 * A parser for catalogs downloaded from the official UHS server.
 */
public class CatalogParser {
  public static final String DEFAULT_CATALOG_URL = "http://www.uhs-hints.com:80/cgi-bin/update.cgi";

  // According to the server's Content-Type HTTP response header.
  public static final String DEFAULT_CATALOG_ENCODING = "ISO-8859-1";

  public static final String DEFAULT_USER_AGENT = "UHSWIN/5.2";


  private UHSErrorHandler errorHandler = new DefaultUHSErrorHandler(System.err);


  public CatalogParser() {
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


  /**
   *
   * Parses the catalog of available hint files.
   *
   * @param catalogString the xml-like string downloaded from the server
   * @return a List of DownloadableUHS objects
   */
  public List<DownloadableUHS> parseCatalog(String catalogString) {
    errorHandler.log(UHSErrorHandler.INFO, null, "Catalog parse started", 0, null);

    List<DownloadableUHS> catalog = new ArrayList<DownloadableUHS>();

    if (catalogString == null || catalogString.length() == 0) return catalog;


    Pattern msgPtn = Pattern.compile("<MESSAGE>(.*?)</MESSAGE>");

    Pattern fileChunkPtn = Pattern.compile("(?s)<FILE>(.*?)</FILE>\\s*");
    Pattern titlePtn = Pattern.compile("<FTITLE>(.*?)</FTITLE>");
    Pattern urlPtn = Pattern.compile("<FURL>(.*?)</FURL>");
    Pattern namePtn = Pattern.compile("<FNAME>(.*?)</FNAME>");
    Pattern datePtn = Pattern.compile("<FDATE>(.*?)</FDATE>");
    Pattern compressedSizePtn = Pattern.compile("<FSIZE>(.*?)</FSIZE>");
    Pattern fullSizePtn = Pattern.compile("<FFULLSIZE>(.*?)</FFULLSIZE>");

    // Catalogs may begin with <message>string</message> advising an update.
    // This occurs when fetched by old user-agents since "UHSWIN/4.0".

    Matcher fileChunkMatcher = fileChunkPtn.matcher(catalogString);
    Matcher m = null;
    while (fileChunkMatcher.find()) {
      String fileChunk = fileChunkMatcher.group(1);
      DownloadableUHS tmpUHS = new DownloadableUHS();

      m = titlePtn.matcher(fileChunk);
      if (m.find()) tmpUHS.setTitle(m.group(1));

      m = urlPtn.matcher(fileChunk);
      if (m.find()) tmpUHS.setUrl(m.group(1));

      m = namePtn.matcher(fileChunk);
      if (m.find()) tmpUHS.setName(m.group(1));

      m = datePtn.matcher(fileChunk);
      if (m.find()) tmpUHS.setDate(m.group(1));

      m = compressedSizePtn.matcher(fileChunk);
      if (m.find()) tmpUHS.setCompressedSize(m.group(1));

      m = fullSizePtn.matcher(fileChunk);
      if (m.find()) tmpUHS.setFullSize(m.group(1));

      catalog.add(tmpUHS);
    }

    errorHandler.log(UHSErrorHandler.INFO, null, String.format("Catalog parse finished (count: %d)", catalog.size()), 0, null);

    return catalog;
  }
}
