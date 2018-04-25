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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.downloader.CatalogItem;


/**
 * A parser for catalogs downloaded from the official UHS server.
 * <p>
 * The catalog advertises zip archives, each with a lone UHS file inside.
 * <p>
 * Note: The catalog text varies depending on the user-agent that downloaded it!
 * <p>
 * This is what "Mozilla" user-agents see...
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
 * <p>
 * This is what "UHSWIN/5.2" user-agents see:
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
 * <p>
 * FSIZE is the size of the zip file.
 * <p>
 * FFULLSIZE is the size of the UHS file, inside the zip, after extraction.
 * <p>
 * MESSAGE only appears for old UHS user-agents since "UHSWIN/4.0".
 * <p>
 * FDATE is usually just date. Except for "Mozilla", it's both date AND time (and +1 day!?).
 * <p>
 * So FDATE can be "dd-MMM-yy" or "dd-MMM-yy HH:mm:ss".
 *
 * @see net.vhati.openuhs.core.downloader.CatalogItem
 */
public class CatalogParser {
	public static final String DEFAULT_CATALOG_URL = "http://www.uhs-hints.com:80/cgi-bin/update.cgi";

	// According to the server's Content-Type HTTP response header.
	public static final String DEFAULT_CATALOG_ENCODING = "ISO-8859-1";

	public static final String DEFAULT_USER_AGENT = "UHSWIN/5.2";


	private final Logger logger = LoggerFactory.getLogger( CatalogParser.class );

	/*
	 * SimpleDateFormat stops reading strings longer than the pattern.
	 * So just parse date. The time segment, if present, will be ignored.
	 */
	private DateFormat goofyDateFormat = new SimpleDateFormat( "dd-MMM-yy" );


	public CatalogParser() {
	}


	/**
	 *
	 * Parses the catalog of available hint files.
	 *
	 * @param catalogString  the xml-like string downloaded from the server
	 * @return a List of CatalogItem objects
	 */
	public List<CatalogItem> parseCatalog( String catalogString ) {
		logger.debug( "Catalog parse started" );

		List<CatalogItem> catalog = new ArrayList<CatalogItem>();

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
			CatalogItem catItem = new CatalogItem();

			m = titlePtn.matcher( fileChunk );
			if ( m.find() ) catItem.setTitle( m.group( 1 ) );

			m = urlPtn.matcher( fileChunk );
			if ( m.find() ) catItem.setUrl( m.group( 1 ) );

			m = namePtn.matcher( fileChunk );
			if ( m.find() ) catItem.setName( m.group( 1 ) );

			m = datePtn.matcher( fileChunk );
			if ( m.find() ) {
				try {
					catItem.setDate( goofyDateFormat.parse( m.group( 1 ) ) );
				}
				catch ( ParseException e ) {
					logger.warn( "Unexpected catalog date format: '{}'", m.group( 1 ) );
				}
			}

			m = compressedSizePtn.matcher( fileChunk );
			if ( m.find() ) catItem.setCompressedSize( m.group( 1 ) );

			m = fullSizePtn.matcher( fileChunk );
			if ( m.find() ) catItem.setFullSize( m.group(1) );

			catalog.add( catItem );
		}

		logger.debug( "Catalog parse finished (count: {})", catalog.size() );

		return catalog;
	}
}
