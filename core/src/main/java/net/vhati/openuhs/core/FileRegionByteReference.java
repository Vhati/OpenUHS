package net.vhati.openuhs.core;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

import net.vhati.openuhs.core.ByteReference;
import net.vhati.openuhs.core.RangeInputStream;


public class FileRegionByteReference implements ByteReference {
	protected final File f;
	protected final long offset;
	protected final long length;


	public FileRegionByteReference( File f, long offset, long length ) {
		this.f = f;
		this.offset = offset;
		this.length = length;
	}


	@Override
	public long length() {
		return length;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		FileInputStream fis = null;
		try {
			return new RangeInputStream( new BufferedInputStream( new FileInputStream( f ) ), offset, length );
		}
		catch ( IOException e ) {
			try {if ( fis != null ) fis.close();} catch( IOException f ) {}
			throw e;
		}
	}
}
