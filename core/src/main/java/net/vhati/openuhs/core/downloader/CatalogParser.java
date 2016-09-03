package net.vhati.openuhs.core.downloader;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.vhati.openuhs.core.DefaultUHSErrorHandler;
import net.vhati.openuhs.core.UHSErrorHandler;
import net.vhati.openuhs.core.downloader.DownloadableUHS;


/**
 * A parser for catalogs downloaded from the official UHS server.
 *
 * <p>The catalog advertises zip archives, each with a lone UHS file inside.</p>
 *
 * <p>Note: The catalog text varies depending on the user-agent that downloaded it!</p>
 *
 * <p>This is what "Mozilla" user-agents see...</p>
 *
 * <blockquote><pre>
 * {@code
 * <FILE><FTITLE>The 11th Hour</FTITLE>
 * <FURL>http://www.uhs-hints.com/rfiles/11thhour.zip</FURL>
 * <FNAME>11thhour.uhs</FNAME><FDATE>24-Jan-96 00:01:39</FDATE>
 * <FSIZE>25024</FSIZE>
 * <FFULLSIZE>51278</FFULLSIZE></FILE>
 * }
 * </pre></blockquote>
 *
 * <p>This is what "UHSWIN/5.2" user-agents see:</p>
 *
 * <blockquote><pre>
 * {@code
 * <MESSAGE>A new version of the UHS Reader is now available.  Version 6.00 offers [...]</MESSAGE>
 * <FILE><FTITLE>The 11th Hour</FTITLE>
 * <FURL>http://www.uhs-hints.com/rfiles/11thhour.zip</FURL>
 * <FNAME>11thhour.uhs</FNAME><FDATE>23-Jan-96</FDATE>
 * <FSIZE>25024</FSIZE>
 * <FFULLSIZE>51278</FFULLSIZE></FILE>
 * }
 * </pre></blockquote>
 *
 * <p>FSIZE is the size of the zip file.</p>
 * <p>FFULLSIZE is the size of the UHS file, inside the zip, after extraction.</p>
 * <p>MESSAGE only appears for old UHS user-agents since "UHSWIN/4.0".</p>
 * <p>FDATE is usually just date. Except for "Mozilla", it's both date AND time (and +1 day!?).</p>
 *
 * <p>So FDATE can be "dd-MMM-yy" or "dd-MMM-yy HH:mm:ss".</p>
 *
 * @see net.vhati.openuhs.core.downloader.DownloadableUHS
 */
public class CatalogParser {
	public static final String DEFAULT_CATALOG_URL = "http://www.uhs-hints.com:80/cgi-bin/update.cgi";

	// According to the server's Content-Type HTTP response header.
	public static final String DEFAULT_CATALOG_ENCODING = "ISO-8859-1";

	public static final String DEFAULT_USER_AGENT = "UHSWIN/5.2";


	/*
	 * SimpleDateFormat stops reading strings longer than the pattern.
	 * So just parse date. The time segment, if present, will be ignored.
	 */
	private DateFormat goofyDateFormat = new SimpleDateFormat( "dd-MMM-yy" );

	private UHSErrorHandler errorHandler = new DefaultUHSErrorHandler( System.err );


	public CatalogParser() {
	}


	/**
	 * Sets the error handler to notify of exceptions.
	 *
	 * <p>This is a convenience for logging/muting.</p>
	 *
	 * <p>The default handler prints to System.err.</p>
	 *
	 * @param eh  the error handler, or null, for quiet parsing
	 */
	public void setErrorHandler( UHSErrorHandler eh ) {
		errorHandler = eh;
	}


	/**
	 *
	 * Parses the catalog of available hint files.
	 *
	 * @param catalogString  the xml-like string downloaded from the server
	 * @return a List of DownloadableUHS objects
	 */
	public List<DownloadableUHS> parseCatalog( String catalogString ) {
		errorHandler.log( UHSErrorHandler.INFO, null, "Catalog parse started", 0, null );

		List<DownloadableUHS> catalog = new ArrayList<DownloadableUHS>();

		if (catalogString == null || catalogString.length() == 0) return catalog;


		Pattern msgPtn = Pattern.compile( "<MESSAGE>(.*?)</MESSAGE>" );

		Pattern fileChunkPtn = Pattern.compile( "(?s)<FILE>(.*?)</FILE>\\s*" );
		Pattern titlePtn = Pattern.compile( "<FTITLE>(.*?)</FTITLE>" );
		Pattern urlPtn = Pattern.compile( "<FURL>(.*?)</FURL>" );
		Pattern namePtn = Pattern.compile( "<FNAME>(.*?)</FNAME>" );
		Pattern datePtn = Pattern.compile( "<FDATE>(.*?)</FDATE>" );
		Pattern compressedSizePtn = Pattern.compile( "<FSIZE>(.*?)</FSIZE>" );
		Pattern fullSizePtn = Pattern.compile( "<FFULLSIZE>(.*?)</FFULLSIZE>" );

		// Let chunk's find() skip the <MESSAGE> tag, if present.
		// Could've done find(index) from the end of the message.
		//   and used "\\G" (previous match) for strict back-to-back <FILE> parsing.

		Matcher fileChunkMatcher = fileChunkPtn.matcher( catalogString );
		Matcher m = null;
		while (fileChunkMatcher.find()) {
			String fileChunk = fileChunkMatcher.group( 1 );
			DownloadableUHS tmpUHS = new DownloadableUHS();

			m = titlePtn.matcher( fileChunk );
			if ( m.find() ) tmpUHS.setTitle( m.group( 1 ) );

			m = urlPtn.matcher( fileChunk );
			if ( m.find() ) tmpUHS.setUrl( m.group( 1 ) );

			m = namePtn.matcher( fileChunk );
			if ( m.find() ) tmpUHS.setName( m.group( 1 ) );

			m = datePtn.matcher( fileChunk );
			if ( m.find() ) {
				try {
					tmpUHS.setDate( goofyDateFormat.parse( m.group( 1 ) ) );
				}
				catch ( ParseException e ) {
					errorHandler.log( UHSErrorHandler.ERROR, null, String.format( "Unexpected date format: '%s'", m.group( 1 ) ), 0, null );
				}
			}

			m = compressedSizePtn.matcher( fileChunk );
			if ( m.find() ) tmpUHS.setCompressedSize( m.group( 1 ) );

			m = fullSizePtn.matcher( fileChunk );
			if ( m.find() ) tmpUHS.setFullSize( m.group(1) );

			catalog.add( tmpUHS );
		}

		errorHandler.log( UHSErrorHandler.INFO, null, String.format( "Catalog parse finished (count: %d)", catalog.size() ), 0, null );

		return catalog;
	}
}
