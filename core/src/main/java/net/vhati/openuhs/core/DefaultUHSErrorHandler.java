package net.vhati.openuhs.core;

import java.io.PrintStream;


/**
 * A simple logger that prints to a stream.
 */
public class DefaultUHSErrorHandler implements UHSErrorHandler {

	private String prefixFormat = "%-9s";
	private String errorPrefix = String.format( prefixFormat, "Error:" );
	private String infoPrefix = String.format( prefixFormat, "Info:" );
	private String unknownPrefix = String.format( prefixFormat, "?:" );
	private String indentPrefix = String.format( prefixFormat, "" );
	private String problemPrefix = String.format( prefixFormat, "Problem:" );
	private String causePrefix = String.format( prefixFormat, "Cause:" );
	private String extraIndent = "  ";


	private PrintStream[] outStreams = null;


	/**
	 * Creates an error handler.
	 *
	 * @param out  a stream to print to (e.g., System.err)
	 */
	public DefaultUHSErrorHandler( PrintStream out ) {
		this( new PrintStream[] {out} );
	}

	/**
	 * Creates an error handler.
	 *
	 * @param out  one or more streams to print to (e.g., System.err)
	 */
	public DefaultUHSErrorHandler( PrintStream... out ) {
		outStreams = new PrintStream[out.length];
		for ( int i=0; i < out.length; i++ ) {
			outStreams[i] = out[i];
		}
	}


	public void log( int severity, Object source, String message, int line, Exception e ) {
		if ( outStreams == null || outStreams.length == 0 ) return;

		StringBuffer result = new StringBuffer();

		if ( message != null ) message = message.replaceAll( "\r?\n", "\n" );

		if ( line > 0 ) {
			if (message != null && message.length() > 0) {
				message = String.format( "%s (line %d)", message, line );
			} else {
				message = String.format( "Something happened on line %d", line );
			}
		}

		String severityPrefix = null;
		if ( severity == UHSErrorHandler.ERROR ) {
			severityPrefix = errorPrefix;
		}
		else if ( severity == UHSErrorHandler.INFO ) {
			severityPrefix = infoPrefix;
		}
		else {
			severityPrefix = unknownPrefix;
		}

		if ( message != null && message.length() > 0 ) {
			String[] chunks = message.split( "\n", -1 );
			boolean first = true;
			for ( String chunk : chunks ) {
				result.append( (( first ) ? severityPrefix : "\n"+ indentPrefix) ).append( chunk );
				first = false;
			}
		}

		if ( e != null ) {
			result.append( (( result.length() > 0 ) ? "\n" : "") ).append( problemPrefix ).append( e.toString() );

			StackTraceElement[] tmpStack = e.getStackTrace();
			for ( StackTraceElement element : tmpStack ) {
				result.append( "\n"+ indentPrefix ).append( extraIndent ).append( element.toString() );
			}

			Throwable cause = e.getCause();
			while ( cause != null ) {
				result.append( "\nCause:   " ).append( cause.toString() );

				tmpStack = cause.getStackTrace();
				for ( StackTraceElement element : tmpStack ) {
					result.append( "\n"+ indentPrefix ).append( extraIndent ).append( element.toString() );
				}

				cause = cause.getCause();
			}
		}

		massPrintln( result.toString().replaceAll( "\n", System.getProperty( "line.separator" ) ) );
	}


	/**
	 * Prints the current date. An optional call on startup.
	 */
	public void logWelcomeMessage() {
		String dateMsg = String.format( "Started: %s", new java.util.Date().toString() );
		String osMsg = String.format( "OS: %s %s", System.getProperty("os.name"), System.getProperty("os.version") );
		String vmMsg = String.format( "VM: %s, %s, %s", System.getProperty("java.vm.name"), System.getProperty("java.version"), System.getProperty("os.arch") );
		massPrintln( dateMsg );
		massPrintln( osMsg );
		massPrintln( vmMsg );
	}

	protected void massPrintln( String s ) {
		for ( int i=0; i < outStreams.length; i++ ) {
			synchronized ( outStreams[i] ) {
				outStreams[i].println( s );
			}
		}
	}
}
