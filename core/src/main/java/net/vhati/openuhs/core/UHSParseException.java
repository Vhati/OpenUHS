package net.vhati.openuhs.core;


public class UHSParseException extends Exception {
	protected int line;


    public UHSParseException() {
	}

	public UHSParseException( String message ) {
		super(message);
	}

	public UHSParseException( Throwable cause ) {
		super( cause );
	}

	public UHSParseException( String message, Throwable cause ) {
		super( message, cause );
	}
}
