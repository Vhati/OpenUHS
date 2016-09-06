package net.vhati.openuhs.desktopreader.downloader;


public class FetchUnitException extends Exception {


    public FetchUnitException() {
	}

	public FetchUnitException( String message ) {
		super(message);
	}

	public FetchUnitException( Throwable cause ) {
		super( cause );
	}

	public FetchUnitException( String message, Throwable cause ) {
		super( message, cause );
	}
}
