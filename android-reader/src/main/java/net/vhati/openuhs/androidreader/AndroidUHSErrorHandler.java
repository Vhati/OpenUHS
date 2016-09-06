package net.vhati.openuhs.androidreader;

import android.util.Log;

import net.vhati.openuhs.core.UHSErrorHandler;


/**
 * A simple logger that prints to Android's log.
 */
public class AndroidUHSErrorHandler implements UHSErrorHandler {
	private String tag = "";
	private String prefixFormat = "%-9s";
	private String indentPrefix = String.format( prefixFormat, "" );
	private String problemPrefix = String.format( prefixFormat, "Problem:" );
	private String causePrefix = String.format( prefixFormat, "Cause:" );
	private String extraIndent = "  ";


	/**
	 * Creates an error handler.
	 *
	 * @param tag  a string to identify this app in the log
	 */
	public AndroidUHSErrorHandler( String tag ) {
		if ( tag != null ) this.tag = tag;
	}


	public void log( int severity, Object source, String message, int line, Exception e ) {
		StringBuffer result = new StringBuffer();

		if ( message != null ) message = message.replaceAll( "\r?\n", "\n" );

		if ( line > 0 ) {
			if ( message != null && message.length() > 0 ) {
				message = String.format( "%s (line %d)", message, line );
			} else {
				message = String.format( "Something happened on line %d", line );
			}
		}

		if ( message != null && message.length() > 0 ) {
			String[] chunks = message.split( "\n", -1 );
			boolean first = true;
			for ( String chunk : chunks ) {
				result.append( (( first ) ? "" : "\n"+ indentPrefix) ).append( chunk );
				first = false;
			}
		}

		if ( e != null ) {
			result.append( (( result.length() ) > 0 ? "\n" : "") ).append( problemPrefix ).append( e.toString() );

			StackTraceElement[] tmpStack = e.getStackTrace();
			for ( StackTraceElement element : tmpStack ) {
				result.append( "\n"+ indentPrefix ).append( extraIndent ).append( element.toString() );
			}

			Throwable cause = e.getCause();
			while ( cause != null ) {
				result.append( "\n"+ causePrefix ).append( cause.toString() );

				tmpStack = cause.getStackTrace();
				for ( StackTraceElement element : tmpStack ) {
					result.append( "\n"+ indentPrefix ).append( extraIndent ).append( element.toString() );
				}

				cause = cause.getCause();
			}
		}

		String resultString = result.toString().replaceAll( "\n", System.getProperty( "line.separator" ) );
		if ( severity == UHSErrorHandler.ERROR ) {
			Log.e( tag, resultString );
		}
		else if ( severity == UHSErrorHandler.INFO ) {
			Log.i( tag, resultString );
		}
		else {
			Log.w( tag, resultString );
		}
	}
}
