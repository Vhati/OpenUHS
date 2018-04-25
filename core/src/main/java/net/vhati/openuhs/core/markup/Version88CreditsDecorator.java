package net.vhati.openuhs.core.markup;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.vhati.openuhs.core.markup.DecoratedFragment;
import net.vhati.openuhs.core.markup.StringDecorator;
import net.vhati.openuhs.core.markup.Version88StringDecorator;


/**
 * A StringDecorator for "CreditData" nodes.
 * <p>
 * The official reader only honors line breaks
 * in credit for lines with fewer than 20 characters.
 * Otherwise, they're displayed as a space. No authors
 * ever wrote with that in mind, so it's barely worth
 * enforcing.
 */
public class Version88CreditsDecorator extends Version88StringDecorator {


	@Override
	public DecoratedFragment[] getDecoratedString( String rawContent ) {
		char[] tmp = rawContent.toCharArray();
		StringBuffer buf = new StringBuffer( tmp.length );
		char[] linebreak = StringDecorator.linebreak;

		int lineCharCount = 0;
		for ( int c=0; c < tmp.length; c++ ) {
			if ( c+linebreak.length < tmp.length ) {
				char[] chunkA = new char[linebreak.length];
				System.arraycopy( tmp, c, chunkA, 0, linebreak.length );
				if (Arrays.equals( chunkA, linebreak )) {
					if ( lineCharCount < 20 ) {
						buf.append( "\n" );
					} else {
						buf.append( " " );
					}
					lineCharCount = 0;
					c += linebreak.length-1; continue;
				}
			}

			buf.append( tmp[c] );
			lineCharCount++;
		}


		String fragment = buf.toString();
		String[] decoNames = new String[0];
		Map[] argMaps = new LinkedHashMap[0];
		DecoratedFragment[] result = new DecoratedFragment[] {new DecoratedFragment( fragment, decoNames, argMaps )};
		return result;
	}
}
