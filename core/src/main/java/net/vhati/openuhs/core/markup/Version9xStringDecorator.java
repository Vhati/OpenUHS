package net.vhati.openuhs.core.markup;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.markup.DecoratedFragment;
import net.vhati.openuhs.core.markup.Decoration;
import net.vhati.openuhs.core.markup.StringDecorator;


/**
 * A StringDecorator ancestor.
 * <p>
 * Line breaks are initially replaced with "\n" by default,
 * but subclasses overriding getDecoratedString() can change that.
 */
public class Version9xStringDecorator extends StringDecorator {
	public static final String MONOSPACED = "Monospaced";
	public static final String HYPERLINK = "Hyperlink";

	private static final Decoration[] decorations = new Decoration[] {
		new Decoration( MONOSPACED, new char[]{'#','p','-'}, new char[]{'#','p','+'} ),
		new Decoration( HYPERLINK, new char[]{'#','h','+'}, new char[]{'#','h','-'} )
	};

	private static final char[] wspcA = new char[] {'#','w','+'};
	private static final char[] wspcB = new char[] {'#','w','.'};
	private static final char[] wnlin = new char[] {'#','w','-'};
	private static final char[] accentPrefix = new char[] {'#','a','+'};
	private static final char[] accentSuffix = new char[] {'#','a','-'};

	private static final char[] diaeresisMarkup = new char[] {':'};
	private static final char[] diaeresisAccent = new char[] {'Ä','Ë','Ï','Ö','Ü','ä','ë','ï','ö','ü'};
	private static final char[] diaeresisNormal = new char[] {'A','E','I','O','U','a','e','i','o','u'};
	private static final char[] acuteMarkup = new char[] {'\''};
	private static final char[] acuteAccent = new char[] {'Á','É','Í','Ó','Ú','á','é','í','ó','ú'};
	private static final char[] acuteNormal = new char[] {'A','E','I','O','U','a','e','i','o','u'};
	private static final char[] graveMarkup = new char[] {'`'};
	private static final char[] graveAccent = new char[] {'À','È','Ì','Ò','Ù','à','è','ì','ò','ù'};
	private static final char[] graveNormal = new char[] {'A','E','I','O','U','a','e','i','o','u'};
	private static final char[] circumflexMarkup = new char[] {'^'};
	private static final char[] circumflexAccent = new char[] {'Â','Ê','Î','Ô','Û','â','ê','î','ô','û'};
	private static final char[] circumflexNormal = new char[] {'A','E','I','O','U','a','e','i','o','u'};
	private static final char[] tildeMarkup = new char[] {'~'};
	private static final char[] tildeAccent = new char[] {'Ñ','ñ'};
	private static final char[] tildeNormal = new char[] {'N','n'};

	private static final char[][][] accents = new char[][][] {
		{diaeresisMarkup, diaeresisAccent, diaeresisNormal},
		{acuteMarkup, acuteAccent, acuteNormal},
		{graveMarkup, graveAccent, graveNormal},
		{circumflexMarkup, circumflexAccent, circumflexNormal},
		{tildeMarkup, tildeAccent, tildeNormal}
	};


	public Version9xStringDecorator() {
		super();
	}


	@Override
	public DecoratedFragment[] getDecoratedString( String rawContent ) {
		List<DecoratedFragment> resultList = new ArrayList<DecoratedFragment>();
		char[] tmp = rawContent.toCharArray();
		StringBuffer buf = new StringBuffer( tmp.length );
		int consumedOffset = -1;
		String[] breakStr = new String[] {"\n"};  // Initial value varies by decorator
		Decoration[] decos = getDecorations();
		int[] decoStates = new int[decorations.length];

		for ( int c=0; c < tmp.length; c++ ) {
			consumedOffset = parseSymbolMarkup( tmp, buf, c );
			if ( consumedOffset != -1 ) {c += consumedOffset; continue;}

			consumedOffset = parseLineBreakMarkup( tmp, buf, c, breakStr );
			if ( consumedOffset != -1 ) {c += consumedOffset; continue;}

			consumedOffset = parseDecorationMarkup( tmp, buf, c, decos, decoStates, resultList );
			if ( consumedOffset != -1 ) {c += consumedOffset; continue;}

			buf.append( tmp[c] );
		}

		// Handle lingering content
		if ( buf.length() > 0 ) {
			String fragment = buf.toString();
			List<String> attribList = new ArrayList<String>( 1 );
			for ( int d=0; d < decorations.length; d++ ) {
				if ( decoStates[d] > 0 ) attribList.add( decorations[d].name );
			}
			String[] decoNames = attribList.toArray( new String[attribList.size()] );
			Map[] argMaps = new LinkedHashMap[attribList.size()];
			resultList.add( new DecoratedFragment( fragment, decoNames, argMaps ) );
		}

		return resultList.toArray( new DecoratedFragment[resultList.size()] );
	}


	/**
	 * Consumes any markup at an index and adds replacement chars to a buffer.
	 * <p>
	 * This should be called early to intercept the escaped '#' escape
	 * character before other parse methods.
	 * <br>
	 * <ul>
	 * <li><b>##</b> a '#' character.</li>
	 * <li><b>#a+</b>[AaEeIiOoUu][:'`^]<b>#a-</b> accent enclosed letter; :=diaeresis,'=acute,`=grave,^=curcumflex.</li>
	 * <li><b>#a+</b>[Nn]~<b>#a-</b> accent enclosed letter with a tilde.</li>
	 * <li><b>#a+</b>ae<b>#a-</b> an ash character.</li>
	 * <li><b>#a+</b>TM<b>#a-</b> a trademark character.</li>
	 * </ul>
	 *
	 * @param contentChars  raw content with markup
	 * @param buf  a destination buffer
	 * @param c  index in contentChars to check for markup
	 * @return offset to the final consumed char
	 */
	public int parseSymbolMarkup( char[] contentChars, StringBuffer buf, int c ) {
		char[] tmp = contentChars;

		if ( c+1 < tmp.length ) {
			if ( tmp[c] == '#' && tmp[c+1] == '#' ) {buf.append( '#' ); return 1;}
		}

		if ( c+7 < tmp.length ) {
			char[] chunkA = new char[] {tmp[c],tmp[c+1],tmp[c+2]};
			char[] chunkB = new char[] {tmp[c+5],tmp[c+6],tmp[c+7]};
			if ( Arrays.equals( chunkA, accentPrefix ) && Arrays.equals( chunkB, accentSuffix ) ) {
				boolean replaced = false;
				for ( int i=0; !replaced && i < accents.length; i++ ) {
					char markup = accents[i][0][0]; char[] accent = accents[i][1]; char[] normal = accents[i][2];
					if ( tmp[c+4] == markup ) {
						for ( int a=0; !replaced && a < accent.length; a++ ) {
							if ( tmp[c+3] == normal[a] ) {
								buf.append( accent[a] );
								replaced = true;
							}
						}
					}
				}
				if ( !replaced && tmp[c+3] == 'a' && tmp[c+4] == 'e' ) {
					buf.append( 'æ' ); replaced = true;
				}
				if ( !replaced && tmp[c+3] == 'T' && tmp[c+4] == 'M' ) {
					buf.append( '™' ); replaced = true;
				}
				if ( !replaced ) {
					// TODO: Get the logger out of here?
					Logger logger = LoggerFactory.getLogger( Version9xStringDecorator.class );
					logger.warn( "StringDecorator encountered an expected accent ({}) in node content: {}", String.format( "%c%c", tmp[c+3], tmp[c+4] ), (new String(tmp)) );
				}
				if ( replaced ) {return 7;}
			}
		}

		return -1;
	}


	/**
	 * Consumes any markup at an index and adds replacement chars to a buffer.
	 * <br>
	 * <ul>
	 * <li><b>#w.</b> raw newlines are spaces.</li>
	 * <li><b>#w+</b> raw newlines are spaces (default).</li>
	 * <li><b>#w-</b> raw newlines are newlines.</li>
	 * </ul>
	 *
	 * @param contentChars  raw content with markup
	 * @param buf  a destination buffer
	 * @param c  index in contentChars to check for markup
	 * @param breakStr  a single-element wrapper array to return the current line break replacement string
	 * @return offset to the final consumed char
	 * @see StringDecorator#linebreak
	 */
	public int parseLineBreakMarkup( char[] contentChars, StringBuffer buf, int c, String[] breakStr ) {
		char[] tmp = contentChars;
		if ( c+2 < tmp.length ) {
			char[] chunkA = new char[] {tmp[c],tmp[c+1],tmp[c+2]};
			if ( Arrays.equals( chunkA, wspcA ) || Arrays.equals( chunkA, wspcB ) ) {breakStr[0] = " "; return 2;}
			else if ( Arrays.equals( chunkA, wnlin ) ) {breakStr[0] = "\n"; return 2;}
		}

		char[] linebreak = StringDecorator.linebreak;
		if ( c+linebreak.length <= tmp.length ) {
			char[] chunkA = new char[linebreak.length];
			System.arraycopy( tmp, c, chunkA, 0, linebreak.length );
			if ( Arrays.equals( chunkA, linebreak ) ) {buf.append( breakStr[0] ); return linebreak.length-1;}
		}

		return -1;
	}


	/**
	 * Consumes markup, if found, and creates a decorated fragment using buffer contents.
	 * <p>
	 * A dictionary of decoration markup is used to determine where new
	 * fragments begin, so subclasses can customize what to look for.
	 * The destination buffer will be cleared as fragments are created.
	 * <p>
	 * This should be called last after other parse methods have
	 * filled the buffer.
	 *
	 * @param contentChars  raw content with markup
	 * @param buf  a destination buffer
	 * @param c  index in contentChars to check for markup
	 * @param decorations  a dictionary of Decorations to check for
	 * @param decoStates  an array to return the current decoration in-use counts
	 * @param fragmentList  a List in which to store any new DecoratedFragments
	 * @return offset to the final consumed char
	 * @see #getDecorations()
	 */
	public int parseDecorationMarkup( char[] contentChars, StringBuffer buf, int c, Decoration[] decorations, int[] decoStates, List<DecoratedFragment> fragmentList ) {
		char[] tmp = contentChars;
		int result = -1;
		int dNum = -1;  // Decoration to apply delayed inc/dec
		int dOff = 0;   // Delayed inc/dec amount

		// Discover a pending decoration state change, but change it later
		for ( int d=0; d < decorations.length; d++ ) {
			if ( decorations[d].prefixMatches( tmp, c ) ) {
				dNum = d; dOff += 1;
				result = decorations[d].prefix.length-1;
				break;
			}
			if ( decoStates[d] > 0 && decorations[d].suffixMatches( tmp, c ) ) {
				dNum = d; dOff -= 1;
				result = decorations[d].suffix.length-1;
				break;
			}
		}

		// Finalize the current decoration state, then apply the new state
		if ( result != -1 ) {
			if ( buf.length() > 0 ) {
				String fragment = buf.toString();
				List<String> attribList = new ArrayList<String>( 1 );
				for ( int d=0; d < decorations.length; d++ ) {
					if ( decoStates[d] > 0 ) attribList.add( decorations[d].name );
				}
				String[] decoNames = attribList.toArray( new String[attribList.size()] );
				Map[] argMaps = new LinkedHashMap[attribList.size()];
				fragmentList.add( new DecoratedFragment( fragment, decoNames, argMaps ) );
				buf.setLength( 0 );
			}
			decoStates[dNum] += dOff;  // Now apply the new decoration state
		}

		return result;
	}


	/**
	 * Returns known markup decorations.
	 * <p>
	 * Subclasses may call this, add their own decorations,
	 * and then call parseDecorationMarkup().
	 * <br>
	 * <ul>
	 * <li><b>#h+</b> through <b>#h-</b> is a hyperlink (http or email).</li>
	 * <li><b>#p+</b> proportional font (default).</li>
	 * <li><b>#p-</b> non-proportional font.</li>
	 * </ul>
	 * <br>
	 * <ul>
	 * <li>Illustrative UHS: <i>Portal: Achievements</i> (hyperlink)</li>
	 * </ul>
	 *
	 * @see #parseDecorationMarkup(char[], StringBuffer, int, Decoration[], int[], List)
	 */
	public Decoration[] getDecorations() {
		return decorations;
	}
}
