package net.vhati.openuhs.core;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;

import net.vhati.openuhs.core.ByteReference;


public class ArrayByteReference implements ByteReference {
	protected byte[] data;


	public ArrayByteReference( byte[] data ) {
		this.data = data;
	}


	@Override
	public long length() {
		return data.length;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return new ByteArrayInputStream( data );
	}
}
