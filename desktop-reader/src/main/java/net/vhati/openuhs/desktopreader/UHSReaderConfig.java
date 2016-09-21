package net.vhati.openuhs.desktopreader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Properties;


public class UHSReaderConfig {

	private Properties config;
	private File configFile;


	public UHSReaderConfig( File configFile ) {
		this( new Properties(), configFile );
	}

	public UHSReaderConfig( Properties config, File configFile ) {
		this.config = config;
		this.configFile = configFile;
	}

	/**
	 * Returns a copy of an existing UHSReaderConfig object.
	 */
	public UHSReaderConfig( UHSReaderConfig srcConfig ) {
		this.configFile = srcConfig.getConfigFile();
		this.config = new Properties();
		this.config.putAll( srcConfig.getConfig() );
	}


	public Properties getConfig() {
		return config;
	}

	public File getConfigFile() {
		return configFile;
	}


	public Object setProperty( String key, String value ) {
		return config.setProperty( key, value );
	}

	public int getPropertyAsInt( String key, int defaultValue ) {
		String s = config.getProperty( key );
		if ( s != null && s.matches("^\\d+$") )
			return Integer.parseInt( s );
		else
			return defaultValue;
	}

	public String getProperty( String key, String defaultValue ) {
		return config.getProperty( key, defaultValue );
	}

	public String getProperty( String key ) {
		return config.getProperty( key );
	}


	public void load() throws IOException {
		FileInputStream fis = null;
		try {
			fis = new FileInputStream( configFile );
			config.load( new InputStreamReader( fis, "UTF-8" ) );
		}
		finally {
			try {if ( fis != null ) fis.close();} catch ( IOException e ) {}
		}
	}

	public void store() throws IOException {

		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream( configFile );
			StringBuilder commentsBuf = new StringBuilder();
			commentsBuf.append( "\n" );
			commentsBuf.append( "font_size   - The reader's font size. Default: 12" ).append( "\n" );
			commentsBuf.append( "http_proxy  - An optional network proxy (example: 127.0.0.1:1234)" ).append( "\n" );
			commentsBuf.append( "socks_proxy - An optional network proxy (example: 127.0.0.1:1234)" ).append( "\n" );

			Writer writer = new BufferedWriter( new OutputStreamWriter( fos, "UTF-8" ) );
			config.store( writer, commentsBuf.toString() );
			writer.flush();
		}
		finally {
			try {if ( fos != null ) fos.close();} catch ( IOException e ) {}
		}
	}
}
