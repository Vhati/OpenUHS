package net.vhati.openuhs.core;

import java.io.IOException;


public class UHSGenerationException extends IOException {


    public UHSGenerationException() {
	}

	public UHSGenerationException( String message ) {
		super(message);
	}

	public UHSGenerationException( Throwable cause ) {
		super( cause );
	}

	public UHSGenerationException( String message, Throwable cause ) {
		super( message, cause );
	}
}
