package net.vhati.openuhs.core;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;


/**
 * A filter that truncates an InputStream after N bytes have been read.
 */
public class RangeInputStream extends FilterInputStream {
	private long remaining;


	public RangeInputStream( InputStream in, long offset, long length ) throws IOException {
		super( in );
		if ( super.skip( offset ) < offset ) {
			throw new IOException( "Unable to skip leading bytes" );
		}

		remaining = length;
	}


	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public int read() throws IOException {
		return --remaining >= 0 ? super.read() : -1;
	}

	@Override
	public int read( byte[] b, int off, int len ) throws IOException {
		if ( remaining <= 0 ) return -1;

		len = (int)Math.min( (long)len, remaining );

		int result = super.read( b, off, len );
		if ( result > 0 ) {
			remaining -= result;
		}
		return result;
	}
}
