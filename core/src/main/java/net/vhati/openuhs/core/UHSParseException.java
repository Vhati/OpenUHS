package net.vhati.openuhs.core;

import java.io.IOException;


public class UHSParseException extends IOException {


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
