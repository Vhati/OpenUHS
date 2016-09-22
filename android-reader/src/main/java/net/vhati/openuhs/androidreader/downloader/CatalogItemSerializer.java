package net.vhati.openuhs.androidreader.downloader;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import net.vhati.openuhs.core.downloader.CatalogItem;


public class CatalogItemSerializer extends StdSerializer<CatalogItem> {
	private DateFormat dateFormat = new SimpleDateFormat( "yyyy-MM-dd" );


	public CatalogItemSerializer( Class<CatalogItem> t ) {
		super( t );
	}

	public CatalogItemSerializer() {
		this( null );
	}


	@Override
	public Class<CatalogItem> handledType() {
		return CatalogItem.class;
	}


	@Override
	public void serialize( CatalogItem value, JsonGenerator jgen, SerializerProvider provider ) throws IOException, JsonProcessingException {
		jgen.writeStartObject();
		jgen.writeStringField( "title", value.getTitle() );
		jgen.writeStringField( "url", value.getUrl() );
		jgen.writeStringField( "name", value.getName() );
		jgen.writeStringField( "date", (( value.getDate() != null ) ? dateFormat.format( value.getDate() ) : null) );
		jgen.writeStringField( "compressedSize", value.getCompressedSize() );
		jgen.writeStringField( "fullSize", value.getFullSize() );
		jgen.writeEndObject();
	}
}
