package net.vhati.openuhs.core;

import java.io.InputStream;
import java.io.IOException;


public interface ByteReference {
	public long length();

	public InputStream getInputStream() throws IOException;
}
