package net.vhati.openuhs.androidreader.downloader;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import net.vhati.openuhs.core.downloader.CatalogItem;


public class CatalogItemDeserializer extends StdDeserializer<CatalogItem> {
	private DateFormat dateFormat = new SimpleDateFormat( "yyyy-MM-dd" );


	public CatalogItemDeserializer( Class<?> vc ) {
		super( vc );
	}

	public CatalogItemDeserializer() {
		this( null );
	}


	@Override
	public Class<CatalogItem> handledType() {
		return CatalogItem.class;
	}


	@Override
	public CatalogItem deserialize( JsonParser jp, DeserializationContext ctxt ) throws IOException, JsonProcessingException {
		JsonNode node = jp.getCodec().readTree( jp );

		CatalogItem catItem = new CatalogItem();
		catItem.setTitle( node.get( "title" ).asText() );
		catItem.setUrl( node.get( "url" ).asText() );
		catItem.setName( node.get( "name" ).asText() );

		if ( node.hasNonNull( "date" ) ) {
			try {
				catItem.setDate( dateFormat.parse( node.get( "date" ).asText() ) );
			}
			catch ( ParseException e ) {
				throw new JsonParseException( "Invalid date string", jp.getCurrentLocation(), e );
			}
		}

		catItem.setCompressedSize( node.get( "compressedSize" ).asText() );
		catItem.setFullSize( node.get( "fullSize" ).asText() );

		return catItem;
	}
}
