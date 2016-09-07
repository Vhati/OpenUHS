package net.vhati.openuhs.core.markup;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.markup.DecoratedFragment;
import net.vhati.openuhs.core.markup.Decoration;
import net.vhati.openuhs.core.markup.StringDecorator;
import net.vhati.openuhs.core.markup.Version9xStringDecorator;


/**
 * A StringDecorator for "InfoData" nodes.
 *
 * <p>Unlike other classes, this will ignore symbols and decorations.
 * Instead, lines will be tested for prefixes, then
 * un-wordwrapped and/or prepended by a linebreak.</p>
 */
public class Version9xInfoDecorator extends Version9xStringDecorator {


	@Override
	public DecoratedFragment[] getDecoratedString( String rawContent ) {
		String[] lines = rawContent.split( "\\^break\\^" );

		StringBuffer lengthBuf = new StringBuffer();
		StringBuffer dateBuf = new StringBuffer();
		StringBuffer timeBuf = new StringBuffer();
		StringBuffer authorBuf = new StringBuffer();
		StringBuffer publisherBuf = new StringBuffer();
		StringBuffer copyrightBuf = new StringBuffer();
		StringBuffer authorNoteBuf = new StringBuffer();
		StringBuffer gameNoteBuf = new StringBuffer();
		StringBuffer noticeBuf = new StringBuffer();  // ">"

		StringBuffer unknownBuf = new StringBuffer();
		StringBuffer[] buffers = new StringBuffer[] {lengthBuf, dateBuf, timeBuf, authorBuf, publisherBuf, copyrightBuf, authorNoteBuf, gameNoteBuf, noticeBuf, unknownBuf};
		String[] prefixes = new String[] {"length=", "date=", "time=", "author=", "publisher=", "copyright=", "author-note=", "game-note=", ">"};
		String[] breaks = new String[] {"\n", "\n", "\n", "\n", "\n", " ", " ", " ", " "};
		boolean[] trims = new boolean[] {false, false, false, false, false, true, true, true, true};

		StringBuffer currentBuffer = null;
		String breakChar = " ";

		for ( int i=0; i < lines.length; i++ ) {
			String tmp = lines[i];

			currentBuffer = null;
			for ( int p=0; p < prefixes.length; p++ ) {
				if (tmp.startsWith( prefixes[p] )) {
					currentBuffer = buffers[p];
					breakChar = breaks[p];
					if ( trims[p] == true ) tmp = tmp.substring( prefixes[p].length() );
					break;
				}
			}
			if ( currentBuffer == null ) {
				// TODO: Get the logger out of here?
				Logger logger = LoggerFactory.getLogger( Version9xInfoDecorator.class );
				logger.warn( "InfoDecorator encountered an unexpected line: {}", tmp );

				currentBuffer = unknownBuf;
				breakChar = "\n";
			}

			// Certain multi-line buffers, get a prefix when they're 0-length
			if ( currentBuffer.length() > 0 ) currentBuffer.append( breakChar );
			else if ( currentBuffer == copyrightBuf ) currentBuffer.append( "copyright=" );
			else if ( currentBuffer == authorNoteBuf ) currentBuffer.append( "author-note=" );
			else if ( currentBuffer == gameNoteBuf ) currentBuffer.append( "game-note=" );
			currentBuffer.append( tmp );
		}

		StringBuffer tmpContent = new StringBuffer();
		for ( int i=0; i < buffers.length; i++ ) {
			if ( buffers[i].length() == 0 ) continue;
			if ( tmpContent.length() > 0 ) {
				tmpContent.append( "\n" );
				if ( buffers[i] == copyrightBuf ||
						buffers[i] == authorNoteBuf ||
						buffers[i] == gameNoteBuf ||
						buffers[i] == noticeBuf ) {
					tmpContent.append( "\n" );
				}
			}
			tmpContent.append( buffers[i] );
		}

		String fragment = tmpContent.toString();
		String[] decoNames = new String[0];
		Map[] argMaps = new LinkedHashMap[0];
		DecoratedFragment[] result = new DecoratedFragment[] {new DecoratedFragment( fragment, decoNames, argMaps )};
		return result;
	}
}
