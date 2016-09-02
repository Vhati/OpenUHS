package net.vhati.openuhs.androidreader;

import android.util.Log;

import net.vhati.openuhs.core.UHSErrorHandler;


/**
 * A simple logger that prints to Android's log.
 */
public class AndroidUHSErrorHandler implements UHSErrorHandler {
	String tag = "";


	/**
	 * Creates an error handler.
	 *
	 * @param tag a string to identify this app in the log
	 */
	public AndroidUHSErrorHandler( String tag ) {
		if ( tag != null ) this.tag = tag;
	}


	public void log( int severity, Object source, String message, int line, Exception e ) {
		String indent = "  ";

		StringBuffer result = new StringBuffer();

		if ( line > 0 ) {
			if ( message != null && message.length() > 0 ) {
				message = message +" (line "+ line +")";
			} else {
				message = "Something happened on line "+ line;
			}
		}

		if ( message != null && message.length() > 0 ) {
			String[] chunks = message.split( "\n" );
			result.append( chunks[0] );
			for ( int i=1; i < chunks.length; i++ ) {
				result.append( "\n         " ).append( chunks[i] );
			}
		}

		if ( e != null ) {
			result.append( (( result.length() ) > 0 ? "\n" : "") ).append( "Problem: " ).append( e.toString() );

			StackTraceElement[] tmpStack = e.getStackTrace();
			for ( int i=0; i < tmpStack.length; i++ ) {
				result.append( "\n         " ).append( indent ).append( tmpStack[i].toString() );
			}

			Throwable cause = e.getCause();
			while ( cause != null ) {
				result.append( "\nCause:   " ).append( cause.toString() );

				tmpStack = cause.getStackTrace();
				for ( int i=0; i < tmpStack.length; i++ ) {
					result.append( "\n         " ).append( indent ).append( tmpStack[i].toString() );
				}

				cause = cause.getCause();
			}
		}

		if ( severity == UHSErrorHandler.ERROR )
			Log.e(tag, result.toString());
		else if ( severity == UHSErrorHandler.INFO )
			Log.i(tag, result.toString());
		else
			Log.w(tag, result.toString());
	}
}
