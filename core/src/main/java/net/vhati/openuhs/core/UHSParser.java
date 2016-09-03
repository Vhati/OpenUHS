package net.vhati.openuhs.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import net.vhati.openuhs.core.CRC16;
import net.vhati.openuhs.core.HotSpot;
import net.vhati.openuhs.core.UHSErrorHandler;
import net.vhati.openuhs.core.UHSErrorHandlerManager;
import net.vhati.openuhs.core.UHSHotSpotNode;
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.core.UHSRootNode;
import net.vhati.openuhs.core.markup.Version9xCommentDecorator;
import net.vhati.openuhs.core.markup.Version9xCreditDecorator;
import net.vhati.openuhs.core.markup.Version9xHintDecorator;
import net.vhati.openuhs.core.markup.Version9xInfoDecorator;
import net.vhati.openuhs.core.markup.Version9xStringDecorator;
import net.vhati.openuhs.core.markup.Version9xTextDecorator;
import net.vhati.openuhs.core.markup.Version9xTitleDecorator;
import net.vhati.openuhs.core.markup.Version9xVersionDecorator;
import net.vhati.openuhs.core.markup.Version88CreditDecorator;


/**
 * UHS file parser.
 */
public class UHSParser {
	/** Honor the actual UHS file structure for version 9x auxiliary nodes */
	public static final int AUX_NORMAL = 0;

	/** Drop version 9x auxiliary nodes */
	public static final int AUX_IGNORE = 1;

	/** Move version 9x auxiliary nodes to within the master subject node and make that the new root */
	public static final int AUX_NEST = 2;

	private static boolean force88a = false;

	private int logHeader = 0;
	private int logLine = -1;


	/**
	 * Toggles parsing 9x files as an 88a reader.
	 * Old readers attempting to read a 9x file will see a
	 * deprecation notice.
	 */
	public static void setForce88a( boolean b ) {
		force88a = b;
	}


	public UHSParser() {
	}


	/**
	 * Generates a decryption key for formats after 88a.
	 *
	 * @param name the name of the master subject node of the UHS document (not the filename)
	 * @return the key
	 * @see #decryptNestString(CharSequence, int[])
	 * @see #decryptTextHunk(CharSequence, int[])
	 */
	public int[] generateKey( CharSequence name ) {
		int[] key = new int[name.length()];
		int[] k = {'k', 'e', 'y'};
		for ( int i=0; i < name.length(); i++ ) {
			key[i] = (int)name.charAt( i ) + ( k[i%3] ^ (i + 40) );
			while ( key[i] > 127 ) {
				key[i] -= 96;
			}
		}
		return key;
	}

	/**
	 * Decrypts the content of standalone 'hint' hunks, and all 88a blocks.
	 *
	 * <p>This is only necessary when initially parsing a file.</p>
	 *
	 * @param input ciphertext
	 * @return the decrypted text
	 */
	public String decryptString( CharSequence input ) {
		StringBuffer tmp = new StringBuffer(input.length());

		for (int i=0; i < input.length(); i++) {
			int mychar = (int)input.charAt( i );
			if ( mychar < 32 ) {
				// NOP
			}
			else if ( mychar < 80 ) {
				mychar = mychar*2 - 32;
			}
			else {
				mychar = mychar*2 - 127;
			}

			tmp.append( (char)mychar );
		}

		return tmp.toString();
	}


	/**
	 * Decrypts the content of 'nesthint' and 'incentive' hunks.
	 *
	 * <p>This is only necessary when initially parsing a file.</p>
	 *
	 * @param input ciphertext
	 * @param key this file's hint decryption key
	 * @return the decrypted text
	 */
	public String decryptNestString( CharSequence input, int[] key ) {
		StringBuffer tmp = new StringBuffer( input.length() );
		int tmpChar = 0;

		for ( int i=0; i < input.length(); i++ ) {
			int codeoffset = i % key.length;
			tmpChar = input.charAt( i ) - ( key[codeoffset] ^ (i + 40) );
			while ( tmpChar < 32 ) {
				tmpChar += 96;
			}
			tmp.append( (char)tmpChar );
		}

		return tmp.toString();
	}


	/**
	 * Decrypts the content of 'text' hunks.
	 *
	 * <p>This is only necessary when initially parsing a file.</p>
	 *
	 * @param input ciphertext
	 * @param key this file's hint decryption key
	 * @return the decrypted text
	 */
	public String decryptTextHunk( CharSequence input, int[] key ) {
		StringBuffer tmp = new StringBuffer(input.length());
		int tmpChar = 0;

		for ( int i=0; i < input.length(); i++ ) {
			int codeoffset = i % key.length;
			tmpChar = input.charAt( i ) - ( key[codeoffset] ^ (codeoffset + 40) );
			while ( tmpChar < 32 ) {
				tmpChar += 96;
			}
			tmp.append( (char)tmpChar );
		}

		return tmp.toString();
	}


	/**
	 * Reads a UHS file into a List of text lines and an array of bytes (for binary content).
	 * Then calls an appropriate parser to construct a UHSRootNode and a tree of UHSNodes.
	 *
	 * <p>This is likely the only method you'll need.</p>
	 *
	 * @param f a file to read
	 * @param auxStyle option for 9x files: AUX_NORMAL, AUX_IGNORE, or AUX_NEST
	 * @return the root of a tree of nodes representing the hint file
	 * @see #parse88Format(List, String, int)
	 * @see #parse9xFormat(List, byte[], long, int)
	 */
	public UHSRootNode parseFile( File f, int auxStyle ) {
		if ( auxStyle != AUX_NORMAL && auxStyle != AUX_IGNORE && auxStyle != AUX_NEST ) return null;
		UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();
		logHeader = 0; logLine = -1;

		String tmp = "";
		// Four-line header is here
		int endSectionTitles = 0;

		int startHintSection = 0;
		int endHintSection = 0;

		List<String> uhsFileArray = new ArrayList<String>();
		String name = "";

		long rawOffset = -1;
		byte[] binHunk = new byte[0];

		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile( f, "r" );

			logHeader++;  // For risky IO operations, increment beforehand
			tmp = raf.readLine();
			if (!tmp.equals( "UHS" )) {
				if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Not a UHS file!", logHeader, null );
				return null;
			}

			logHeader++;
			tmp = raf.readLine();
			name = tmp;

			// The indeces, from this point, of the first/last lines of hints in 88a files
			// After 88a, those lines contain an "upgrade your reader" notice
			logHeader++;
			tmp = raf.readLine();    // Skip the startHintSection

			logHeader++;
			tmp = raf.readLine();
			endHintSection = Integer.parseInt( tmp );

			// There's a hunk of binary referenced by offset at the end of 91a and newer files
			// One can skip to it by searching for 0x1Ah.
			byte tmpByte = -1;
			while ( (tmpByte = (byte)raf.read()) != -1 && tmpByte != 0x1a ) {
				raf.getChannel().position( raf.getChannel().position()-1 );
				logLine++;
				tmp = raf.readLine();               // RandomAccessFile reads as ASCII
				tmp = tmp.replaceAll( "\\x00", "" );  // A couple malformed 88a's have nulls at the end
				uhsFileArray.add( tmp );
			}

			rawOffset = raf.getChannel().position();
			long binSize = raf.length()-rawOffset;
			if ( binSize > 0 && binSize <= Integer.MAX_VALUE ) {
				binHunk = new byte[(int)binSize];
				raf.readFully( binHunk );
			}
			else {
				rawOffset = -1;
			}
		}
		catch ( FileNotFoundException e ) {
			if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "No file", logHeader+logLine+1, e );
			return null;
		}
		catch ( IOException e ) {
			if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Could not read file", logHeader+logLine+1, e );
			return null;
		}
		catch ( NumberFormatException e ) {
			if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Could not parse header", logHeader+logLine+1, e );
			return null;
		}
		finally {
			try {if ( raf != null ) raf.close();} catch ( IOException e ) {}
		}

		boolean version88a = true;
		for (int i=endHintSection-1+1; i < uhsFileArray.size(); i++) {
			if ( uhsFileArray.get( i ).equals( "** END OF 88A FORMAT **" ) ) {
				if ( force88a == true ) {
					// Trim off all the new junk and keep the 88A section.
					uhsFileArray = new ArrayList<String>( uhsFileArray.subList( 0, i ) );
					break;
				}
				else {
					version88a = false;

					// Since v91a, the line count starts here, after the old-style 88a section and its "end of" comment.
					logHeader += i;
					for ( int j=1; j <= i; j++ ) {
						uhsFileArray.remove( 0 );
					}
					break;
				}
			}
		}


		UHSRootNode rootNode = null;
		if ( version88a ) {
			rootNode = parse88Format( uhsFileArray, name, endHintSection );
		}
		else {
			rootNode = parse9xFormat( uhsFileArray, binHunk, rawOffset, auxStyle );

			long storedSum = readChecksum( binHunk );
			long calcSum = calcChecksum( f );
			if ( storedSum == -1 ) {
				if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Could not read the stored security checksum from this file", 0, null );
			}
			else if ( calcSum == -1 ) {
				if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Could not calculate the security checksum for this file", 0, null );
			}
			else if ( storedSum != calcSum ) {
				if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Calculated CRC differs from CRC stored in file ("+ calcSum +" vs "+ storedSum +"="+ (storedSum - calcSum) +")", 0, null );
			}
		}
		return rootNode;
	}


	/**
	 * Generates a tree of UHSNodes from UHS 88a.
	 *
	 * <p>A Version node will be added, since that was not natively reported in 88a.</p>
	 *
	 * <blockquote><pre>
	 * {@code
	 * UHS
	 * document title
	 * # (index of first hint)
	 * # (index of last hint)
	 * subject
	 * # (index of first question)
	 * subject
	 * # (index of first question)
	 * subject
	 * # (index of first question)
	 * question (without a '?')
	 * # (index of first hint)
	 * question (without a '?')
	 * # (index of first hint)
	 * question (without a '?')
	 * # (index of first hint)
	 * hint (encrypted)
	 * hint (encrypted)
	 * hint (encrypted)
	 * hint (encrypted)
	 * credit sentences (several lines)
	 * }
	 * </pre></blockquote>
	 *
	 * <p>Index references begin at the first subject,
	 * as do the lines of uhsFileArray.</p>
	 *
	 * <p>The official reader only honors line breaks
	 * in credit for lines with fewer than 20 characters.
	 * Otherwise, they're displayed as a space. No authors
	 * ever wrote with that in mind, so it's not worth
	 * enforcing.</p>
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param name the UHS document's name (not the filename)
	 * @param hintSectionEnd index of the last hint, relative to the first subject (as in the file, 1-based)
	 * @return the root of a tree of nodes
	 */
	public UHSRootNode parse88Format( List<String> uhsFileArray, String name, int hintSectionEnd ) {
		UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();

		try {
			UHSRootNode rootNode = new UHSRootNode();
				rootNode.setContent( name, UHSNode.STRING );
			int fudge = 1; // The format's 1-based, the array's 0-based

			int questionSectionStart = Integer.parseInt( getLoggedString( uhsFileArray, 1 ) ) - fudge;

			for ( int s=0; s < questionSectionStart; s+=2 ) {
				UHSNode currentSubject = new UHSNode( "Subject" );
					currentSubject.setContent( decryptString( getLoggedString( uhsFileArray, s ) ), UHSNode.STRING );
					rootNode.addChild( currentSubject );

				int firstQuestion = Integer.parseInt( getLoggedString( uhsFileArray, s+1 ) ) - fudge;
				int nextSubjectsFirstQuestion = Integer.parseInt( getLoggedString( uhsFileArray, s+3 ) ) - fudge;
					// On the last loop, s+3 is a question's first hint

				for ( int q=firstQuestion; q < nextSubjectsFirstQuestion; q+=2 ) {
					UHSNode currentQuestion = new UHSNode( "Question" );
						currentQuestion.setContent( decryptString( getLoggedString( uhsFileArray, q ) ) +"?", UHSNode.STRING );
						currentSubject.addChild( currentQuestion );

					int firstHint = Integer.parseInt( getLoggedString( uhsFileArray, q+1 ) ) - fudge;
					int lastHint = 0;
					if ( s == questionSectionStart - 2 && q == nextSubjectsFirstQuestion - 2 ) {
						lastHint = hintSectionEnd + 1 - fudge;
							// Line after the final hint
					} else {
						lastHint = Integer.parseInt( getLoggedString( uhsFileArray, q+3 ) ) - fudge;
							// Next question's first hint
					}

					for ( int h=firstHint; h < lastHint; h++ ) {
						UHSNode currentHint = new UHSNode( "Hint" );
							currentHint.setContent( decryptString( getLoggedString( uhsFileArray, h ) ), UHSNode.STRING );
							currentQuestion.addChild( currentHint );
					}
				}
			}
			UHSNode blankNode = new UHSNode( "Blank" );
				blankNode.setContent( "--=File Info=--", UHSNode.STRING );
				rootNode.addChild( blankNode );
			UHSNode fauxVersionNode = new UHSNode( "Version" );
				fauxVersionNode.setContent( "Version: 88a", UHSNode.STRING );
				rootNode.addChild( fauxVersionNode );
				UHSNode fauxVersionDataNode = new UHSNode( "VersionData" );
					fauxVersionDataNode.setContent( "This version info was added by OpenUHS during parsing because the 88a format does not report it.", UHSNode.STRING );
					fauxVersionNode.addChild( fauxVersionDataNode );
			UHSNode creditNode = new UHSNode( "Credit" );
				creditNode.setContent( "Credits", UHSNode.STRING );
				rootNode.addChild( creditNode );

				String breakChar = "^break^";
				StringBuffer tmpContent = new StringBuffer();
				UHSNode newNode = new UHSNode( "CreditData" );
					for ( int i=hintSectionEnd; i < uhsFileArray.size(); i++ ) {
						if ( getLoggedString( uhsFileArray, i ).equals( "** END OF 88A FORMAT **" ) ) break;
						if ( tmpContent.length() > 0 ) tmpContent.append( breakChar );
						tmpContent.append( getLoggedString( uhsFileArray, i ) );
					}
					newNode.setContent( tmpContent.toString(), UHSNode.STRING );
					newNode.setStringContentDecorator( new Version88CreditDecorator() );
					creditNode.addChild( newNode );

			return rootNode;
		}
		catch ( NumberFormatException e ) {
			if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Could not parse nodes", logHeader+logLine+1, e );
			return null;
		}
	}


	/**
	 * Generates a tree of UHSNodes from UHS 91a format onwards.
	 *
	 * <p>Versions 91a, 95a, and 96a have been seen in the wild.
	 * These UHS files are prepended with an 88a section containing an "upgrade your reader" notice.
	 * Below, uhsFileArray begins after "** END OF 88A FORMAT **".</p>
	 *
	 * <blockquote><pre>
	 * {@code
	 * UHS
	 * # Subject
	 * title
	 * ...
	 * # version
	 * title
	 * content
	 * # incentive
	 * title
	 * content
	 * # info
	 * title
	 * content
	 * 0x1Ah character
	 * {binary hunk}
	 * {2-byte CRC16 of everything prior}
	 * }
	 * </pre></blockquote>
	 *
	 * <p>The root node would normally contain up to four children.
	 * <ul>
	 * <li>A 'subject', containing all the subjects, hints, etc., that users care about.</li>
	 * <li>A 'version', mentioning the UHS compiler that made the file.</li>
	 * <li>An 'info', mentioning the author, publisher, etc.</li>
	 * <li>And an 'incentive', listing nodes to show/block if the reader is unregistered.</li>
	 * </ul></p>
	 *
	 * <p>For convenience, these auxiliary nodes can be treated differently.</p>
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param auxStyle AUX_NORMAL (canon), AUX_IGNORE (omit), or AUX_NEST (move inside the master subject and make that the new root).
	 * @return the root of a tree of nodes
	 * @see #buildNodes(List, byte[], long, UHSRootNode, UHSNode, int[], int)
	 * @see #calcChecksum(File)
	 */
	public UHSRootNode parse9xFormat( List<String> uhsFileArray, byte[] binHunk, long rawOffset, int auxStyle ) {
		if ( auxStyle != AUX_NORMAL && auxStyle != AUX_IGNORE && auxStyle != AUX_NEST ) return null;
		UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();

		try {
			UHSRootNode rootNode = new UHSRootNode();
				rootNode.setContent( "root", UHSNode.STRING );

			String name = getLoggedString( uhsFileArray, 2 ); // This is the title of the master subject node
			int[] key = generateKey( name );

			int index = 1;
			index += buildNodes( uhsFileArray, binHunk, rawOffset, rootNode, rootNode, key, index );

			if ( auxStyle != AUX_IGNORE ) {
				if ( auxStyle == AUX_NEST ) {
					UHSNode tmpChildNode = rootNode.getChild( 0 );
					rootNode.setChildren( tmpChildNode.getChildren() );
					rootNode.setContent( name, UHSNode.STRING );

					UHSNode blankNode = new UHSNode( "Blank" );
						blankNode.setContent( "--=File Info=--", UHSNode.STRING );
						rootNode.addChild( blankNode );
				}
				while ( index < uhsFileArray.size() ) {
					index += buildNodes( uhsFileArray, binHunk, rawOffset, rootNode, rootNode, key, index );
				}
			}
			return rootNode;
		}
		catch ( NumberFormatException e ) {
			if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Could not parse nodes", logHeader+logLine+1, e );
			return null;
		}
	}


	/**
	 * Recursively parses UHS newer than 88a.
	 *
	 * <p>This recognizes various types of hints, and runs specialized methods to decode them.
	 * Unrecognized hints are harmlessly omitted.</p>
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param rootNode an existing root node
	 * @param currentNode an existing node to add children to
	 * @param key this file's hint decryption key
	 * @param startIndex the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int buildNodes( List<String> uhsFileArray, byte[] binHunk, long rawOffset, UHSRootNode rootNode, UHSNode currentNode, int[] key, int startIndex ) {
		int index = startIndex;

		String tmp = getLoggedString( uhsFileArray, index );
		if ( tmp.matches( "[0-9]+ [A-Za-z]+$" ) == true ) {
			if (tmp.endsWith( " comment" )) {
				index += parseCommentNode( uhsFileArray, binHunk, rawOffset, rootNode, currentNode, key, index );
			}
			else if (tmp.endsWith( " credit" )) {
				index += parseCreditNode( uhsFileArray, binHunk, rawOffset, rootNode, currentNode, key, index );
			}
			else if (tmp.endsWith( " hint" )) {
				index += parseHintNode( uhsFileArray, binHunk, rawOffset, rootNode, currentNode, key, index );
			}
			else if (tmp.endsWith( " nesthint" )) {
				index += parseNestHintNode( uhsFileArray, binHunk, rawOffset, rootNode, currentNode, key, index );
			}
			else if (tmp.endsWith( " subject" )) {
				index += parseSubjectNode( uhsFileArray, binHunk, rawOffset, rootNode, currentNode, key, index );
			}
			else if (tmp.endsWith( " link" )) {
				index += parseLinkNode( uhsFileArray, binHunk, rawOffset, rootNode, currentNode, key, index );
			}
			else if (tmp.endsWith( " text" )) {
				index += parseTextNode( uhsFileArray, binHunk, rawOffset, rootNode, currentNode, key, index );
			}
			else if (tmp.endsWith( " hyperpng" )) {
				index += parseHyperImgNode( uhsFileArray, binHunk, rawOffset, rootNode, currentNode, key, index );
			}
			else if (tmp.endsWith( " gifa" )) {
				index += parseHyperImgNode( uhsFileArray, binHunk, rawOffset, rootNode, currentNode, key, index );
			}
			else if (tmp.endsWith( " sound" )) {
				index += parseSoundNode( uhsFileArray, binHunk, rawOffset, rootNode, currentNode, key, index );
			}
			else if (tmp.endsWith( " blank" )) {
				index += parseBlankNode( uhsFileArray, binHunk, rawOffset, rootNode, currentNode, key, index );
			}
			else if (tmp.endsWith( " version" )) {
				index += parseVersionNode( uhsFileArray, binHunk, rawOffset, rootNode, currentNode, key, index );
			}
			else if (tmp.endsWith( " info" )) {
				index += parseInfoNode( uhsFileArray, binHunk, rawOffset, rootNode, currentNode, key, index );
			}
			else if (tmp.endsWith( " incentive" )) {
				index += parseIncentiveNode( uhsFileArray, binHunk, rawOffset, rootNode, currentNode, key, index );
			}
			else {
				index += parseUnknownNode( uhsFileArray, binHunk, rawOffset, rootNode, currentNode, key, index );
			}
		}
		else {
			index++;
		}

		return index-startIndex;
	}


	/**
	 * Generates a subject UHSNode and its contents.
	 *
	 * <blockquote><pre>
	 * {@code
	 * # subject
	 * title
	 * embedded hunk
	 * embedded hunk
	 * embedded hunk
	 * }
	 * </pre></blockquote>
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param rootNode an existing root node
	 * @param currentNode an existing node to add children to
	 * @param key this file's hint decryption key
	 * @param startIndex the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseSubjectNode( List<String> uhsFileArray, byte[] binHunk, long rawOffset, UHSRootNode rootNode, UHSNode currentNode, int[] key, int startIndex ) {
		int index = startIndex;
		String tmp = getLoggedString( uhsFileArray, index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode newNode = new UHSNode( "Subject" );
			newNode.setContent( getLoggedString( uhsFileArray, index ), UHSNode.STRING );
			newNode.setStringContentDecorator( new Version9xTitleDecorator() );
			newNode.setId( startIndex );
			currentNode.addChild( newNode );
			rootNode.addLink( newNode );
		index++;
		innerCount--;

		for ( int j=0; j < innerCount; ) {
			j += buildNodes( uhsFileArray, binHunk, rawOffset, rootNode, newNode, key, index+j );
		}

		index += innerCount;
		return index-startIndex;
	}

	/**
	 * Generates a nested hint UHSNode and its contents.
	 *
	 * <blockquote><pre>
	 * {@code
	 * # nesthint
	 * Question
	 * hint (encrypted)
	 * -
	 * partial hint (encrypted)
	 * =
	 * embedded hunk
	 * rest of hint (encrypted)
	 * -
	 * hint (encrypted)
	 * }
	 * </pre></blockquote>
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param rootNode an existing root node
	 * @param currentNode an existing node to add children to
	 * @param key this file's hint decryption key
	 * @param startIndex the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 * @see #decryptNestString(CharSequence, int[])
	 */
	public int parseNestHintNode( List<String> uhsFileArray, byte[] binHunk, long rawOffset, UHSRootNode rootNode, UHSNode currentNode, int[] key, int startIndex ) {
		String breakChar = "^break^";

		int index = startIndex;
		String tmp = getLoggedString( uhsFileArray, index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode hintNode = new UHSNode( "NestHint" );
			hintNode.setContent( getLoggedString( uhsFileArray, index ), UHSNode.STRING );
			hintNode.setStringContentDecorator( new Version9xTitleDecorator() );
			hintNode.setId( startIndex );
			currentNode.addChild( hintNode );
			rootNode.addLink( hintNode );
		index++;
		innerCount--;

		StringBuffer tmpContent = new StringBuffer();
		UHSNode newNode = new UHSNode( "HintData" );

		for ( int j=0; j < innerCount; j++ ) {
			tmp = getLoggedString( uhsFileArray, index+j );
			if ( tmp.equals( "-" ) ) {
				// A hint, add last content
				if ( tmpContent.length() > 0 ) {
					newNode.setContent( tmpContent.toString(), UHSNode.STRING );
					newNode.setStringContentDecorator( new Version9xHintDecorator() );
					hintNode.addChild( newNode );
					newNode = new UHSNode( "HintData" );
					tmpContent.delete( 0, tmpContent.length() );
				}
			}
			else if ( tmp.equals( "=" ) ) {
				// Nested hunk, add last content
				if ( tmpContent.length() > 0 ) {
					newNode.setContent( tmpContent.toString(), UHSNode.STRING );
					newNode.setStringContentDecorator( new Version9xHintDecorator() );
					hintNode.addChild( newNode );
				}

				j += buildNodes( uhsFileArray, binHunk, rawOffset, rootNode, hintNode, key, index+j+1 );

				if ( tmpContent.length() > 0 ) {
					newNode = new UHSNode( "HintData" );
					tmpContent.delete( 0, tmpContent.length() );
				}
			}
			else {
				if ( tmpContent.length() > 0 ) tmpContent.append( breakChar );
				tmpContent.append( decryptNestString( getLoggedString( uhsFileArray, index+j ), key ) );
			}

			if ( j == innerCount-1 && tmpContent.length() > 0 ) {
				newNode.setContent( tmpContent.toString(), UHSNode.STRING );
				newNode.setStringContentDecorator( new Version9xHintDecorator() );
				hintNode.addChild( newNode );
			}
		}

		index += innerCount;
		return index-startIndex;
	}

	/**
	 * Generates a normal hint UHSNode.
	 *
	 * <blockquote><pre>
	 * {@code
	 * # hint
	 * Question
	 * hint (encrypted)
	 * -
	 * hint (encrypted)
	 * }
	 * </pre></blockquote>
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param rootNode an existing root node
	 * @param currentNode an existing node to add children to
	 * @param key this file's hint decryption key
	 * @param startIndex the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 * @see #decryptString(CharSequence)
	 */
	public int parseHintNode( List<String> uhsFileArray, byte[] binHunk, long rawOffset, UHSRootNode rootNode, UHSNode currentNode, int[] key, int startIndex ) {
		String breakChar = "^break^";

		int index = startIndex;
		String tmp = getLoggedString( uhsFileArray, index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1 - 1;

		UHSNode hintNode = new UHSNode( "Hint" );
			hintNode.setContent( getLoggedString( uhsFileArray, index ), UHSNode.STRING );
			hintNode.setStringContentDecorator( new Version9xTitleDecorator() );
			hintNode.setId( startIndex );
			currentNode.addChild( hintNode );
			rootNode.addLink( hintNode );
		index++;

		StringBuffer tmpContent = new StringBuffer();
		UHSNode newNode = new UHSNode( "HintData" );

		for ( int j=0; j < innerCount; j++ ) {
			tmp = getLoggedString( uhsFileArray, index+j );
			if ( tmp.equals( "-" ) ) {
				if ( tmpContent.length() > 0 ) {
					newNode.setContent( tmpContent.toString(), UHSNode.STRING );
					newNode.setStringContentDecorator( new Version9xHintDecorator() );
					hintNode.addChild( newNode );
					newNode = new UHSNode( "HintData" );
					tmpContent.delete( 0, tmpContent.length() );
				}
			} else {
				if ( tmpContent.length() > 0 ) tmpContent.append( breakChar );

				tmp = getLoggedString( uhsFileArray, index+j );
				tmpContent.append( decryptString( tmp ) );
			}

			if ( j == innerCount-1 && tmpContent.length() > 0 ) {
				newNode.setContent( tmpContent.toString(), UHSNode.STRING );
				newNode.setStringContentDecorator( new Version9xHintDecorator() );
				hintNode.addChild( newNode );
			}
		}

		index += innerCount;
		return index-startIndex;
	}

	/**
	 * Generates a comment UHSNode.
	 *
	 * <blockquote><pre>
	 * {@code
	 * # comment
	 * title
	 * sentence
	 * sentence
	 * sentence
	 * }
	 * </pre></blockquote>
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param rootNode an existing root node
	 * @param currentNode an existing node to add children to
	 * @param key this file's hint decryption key
	 * @param startIndex the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseCommentNode( List<String> uhsFileArray, byte[] binHunk, long rawOffset, UHSRootNode rootNode, UHSNode currentNode, int[] key, int startIndex ) {
		String breakChar = "^break^";

		int index = startIndex;
		String tmp = getLoggedString( uhsFileArray, index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode commentNode = new UHSNode( "Comment" );
			commentNode.setContent( getLoggedString( uhsFileArray, index ), UHSNode.STRING );
			commentNode.setStringContentDecorator( new Version9xTitleDecorator() );
			commentNode.setId( startIndex );
			currentNode.addChild( commentNode );
			rootNode.addLink( commentNode );
		index++;
		innerCount--;

		StringBuffer tmpContent = new StringBuffer();
		UHSNode newNode = new UHSNode( "CommentData" );

		for ( int j=0; j < innerCount; j++ ) {
			if (tmpContent.length() > 0) tmpContent.append( breakChar );
			tmpContent.append( getLoggedString( uhsFileArray, index+j ) );
		}
		newNode.setContent( tmpContent.toString(), UHSNode.STRING );
		newNode.setStringContentDecorator( new Version9xCommentDecorator() );
		commentNode.addChild( newNode );

		index += innerCount;
		return index-startIndex;
	}

	/**
	 * Generates a credit UHSNode.
	 *
	 * <blockquote><pre>
	 * {@code
	 * # credit
	 * title
	 * sentence
	 * sentence
	 * sentence
	 * }
	 * </pre></blockquote>
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param rootNode an existing root node
	 * @param currentNode an existing node to add children to
	 * @param key this file's hint decryption key
	 * @param startIndex the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseCreditNode( List<String> uhsFileArray, byte[] binHunk, long rawOffset, UHSRootNode rootNode, UHSNode currentNode, int[] key, int startIndex ) {
		String breakChar = "^break^";

		int index = startIndex;
		String tmp = getLoggedString( uhsFileArray, index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode creditNode = new UHSNode( "Credit" );
			creditNode.setContent( getLoggedString( uhsFileArray, index ), UHSNode.STRING );
			creditNode.setStringContentDecorator( new Version9xTitleDecorator() );
			creditNode.setId( startIndex );
			currentNode.addChild( creditNode );
			rootNode.addLink( creditNode );
		index++;
		innerCount--;

		StringBuffer tmpContent = new StringBuffer();
		UHSNode newNode = new UHSNode( "CreditData" );

		for ( int j=0; j < innerCount; j++ ) {
			if ( tmpContent.length() > 0 ) tmpContent.append( breakChar );
			tmpContent.append( getLoggedString( uhsFileArray, index+j ) );
		}
		newNode.setContent( tmpContent.toString(), UHSNode.STRING );
		newNode.setStringContentDecorator( new Version9xCreditDecorator() );
		creditNode.addChild( newNode );

		index += innerCount;
		return index-startIndex;
	}

	/**
	 * Generates a text UHSNode.
	 *
	 * <blockquote><pre>
	 * {@code
	 * # text
	 * title
	 * 000000 0 offset length
	 * }
	 * </pre></blockquote>
	 *
	 * <p>Offset and length are zero-padded to 6 or 7 digits.</p>
	 * <p>The binary content is encrypted.</p>
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param rootNode an existing root node
	 * @param currentNode an existing node to add children to
	 * @param key this file's hint decryption key
	 * @param startIndex the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 * @see #decryptTextHunk(CharSequence, int[])
	 */
	public int parseTextNode( List<String> uhsFileArray, byte[] binHunk, long rawOffset, UHSRootNode rootNode, UHSNode currentNode, int[] key, int startIndex ) {
		UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();
		String breakChar = "^break^";

		int index = startIndex;
		String tmp = getLoggedString( uhsFileArray, index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		tmp ="";
		UHSNode textNode = new UHSNode( "Text" );
			textNode.setContent( getLoggedString( uhsFileArray, index ), UHSNode.STRING );
			textNode.setStringContentDecorator( new Version9xTitleDecorator() );
			textNode.setId( startIndex );
			currentNode.addChild( textNode );
			rootNode.addLink( textNode );
		index++;

		tmp = getLoggedString( uhsFileArray, index );
		index++;
		long offset = Long.parseLong( tmp.substring( 9, tmp.lastIndexOf( " " ) ) ) - rawOffset;
		int length = Integer.parseInt( tmp.substring( tmp.lastIndexOf( " " )+1, tmp.length() ) );

		StringBuffer tmpContent = new StringBuffer();
		UHSNode newNode = new UHSNode( "TextData" );

		byte[] tmpBytes = null;
		if ( rawOffset != -1 ) tmpBytes = readBinaryHunk( binHunk, offset, length );
		if ( tmpBytes != null ) {
			tmp = new String( tmpBytes );
		} else {
			// This error would be at index-1, if not for getLoggedString()'s counter
			if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Could not read referenced raw bytes", logHeader+logLine+1, null );
			tmp = "";
		}
		String[] lines = tmp.split( "(\r\n)|\r|\n" );
		for ( int i=0; i < lines.length; i++ ) {
			if ( tmpContent.length() > 0 ) tmpContent.append( breakChar );
			tmpContent.append( decryptTextHunk( lines[i], key ) );
		}
		newNode.setContent( tmpContent.toString(), UHSNode.STRING );
		newNode.setStringContentDecorator( new Version9xTextDecorator() );
		textNode.addChild( newNode );

		return index-startIndex;
	}

	/**
	 * Generates a link UHSNode.
	 *
	 * <p>Nodes like this that have link targets behave like conventional hyperlinks instead of containing child nodes.</p>
	 *
	 * <blockquote><pre>
	 * {@code
	 * # link
	 * title
	 * index
	 * }
	 * </pre></blockquote>
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param rootNode an existing root node
	 * @param currentNode an existing node to add children to
	 * @param key this file's hint decryption key
	 * @param startIndex the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseLinkNode( List<String> uhsFileArray, byte[] binHunk, long rawOffset, UHSRootNode rootNode, UHSNode currentNode, int[] key, int startIndex ) {
		int index = startIndex;
		String tmp = getLoggedString( uhsFileArray, index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode newNode = new UHSNode( "Link" );
			newNode.setContent( getLoggedString( uhsFileArray, index ), UHSNode.STRING );
			newNode.setStringContentDecorator( new Version9xTitleDecorator() );
			newNode.setId( startIndex );
			currentNode.addChild( newNode );
			rootNode.addLink( newNode );
		index++;

		int targetIndex = Integer.parseInt( getLoggedString( uhsFileArray, index ) );
			newNode.setLinkTarget( targetIndex );
		index++;

		// Removed since it ran endlessly when nodes link in both directions.
		// buildNodes( uhsFileArray, binHunk, rawOffset, rootNode, newNode, key, targetIndex );

		return index-startIndex;
	}

	/**
	 * Generates an image-filled UHSNode.
	 *
	 * <p>The UHS format allows for regions that trigger links or reveal overlaid subimages.
	 * Regions may also trigger a move into nested hyperpng, text, or hint nodes.</p>
	 *
	 * <p>UHSHotSpotNode was written to handle regions.</p>
	 *
	 * <p><ul>
	 * <li>Illustrative UHS: <i>The Longest Journey</i>: Chapter 7, the Stone Altar, Can you give me a picture of the solution?</li>
	 * <li>Illustrative UHS: <i>Deja Vu I</i>: Sewer, The Map</li>
	 * <li>Illustrative UHS: <i>Arcania: Gothic 4</i>: Jungle and Mountains, Jungle Map</li>
	 * <li>Illustrative UHS: <i>Nancy Drew 4: TitRT</i>: Shed, How can I solve, Finished Leaf Puzzle</li>
	 * <li>Illustrative UHS: <i>Elder Scrolls III: Tribunal</i>: Maps, Norenen-Dur, Details</li>
	 * <li>Illustrative UHS: <i>Dungeon Siege</i>: Chapter 6, Maps, Fire Village (Incentive'd HyperImg image)</li>
	 * </ul></p>
	 *
	 * <blockquote><pre>
	 * {@code
	 * # hyperpng (or gifa)
	 * title
	 * 000000 offset length (the main image, gets id at this line)
	 * --not-a-gap--
	 * x y x+w y+h (id line is here)
	 * # link
	 * title
	 * index
	 * --not-a-gap--
	 * x y x+w y+h (id line is here)
	 * # link
	 * title
	 * index
	 * --not-a-gap--
	 * x y x+w y+h (id line is here)
	 * # overlay
	 * title
	 * 000000 offset length x y
	 * --not-a-gap--
	 * x y x+w y+h (id line is here)
	 * # overlay
	 * title
	 * 000000 offset length x y
	 * --not-a-gap--
	 * x y x+w y+h (id line is here)
	 * 3 hyperpng
	 * title
	 * 000000 offset length
	 * }
	 * </pre></blockquote>
	 *
	 * <p>Offset and length are zero-padded to 6 or 7 digits.</p>
	 * <p>A gifa has the same structure, but might not officially contain regions.</p>
	 * <p>The main image gets an id, which may be referenced by an Incentive node.</p>
	 *
	 * </p>Line ids of nodes nested within a HyperImg are skewed
	 * because their initial line is the region coords, and the
	 * node type comes second.</p>
	 *
	 * <p>Nested HyperImgs aren't expected to recurse further
	 * with additional nested nodes.</p>
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param rootNode an existing root node
	 * @param currentNode an existing node to add children to
	 * @param key this file's hint decryption key
	 * @param startIndex the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 * @see net.vhati.openuhs.core.UHSHotSpotNode
	 */
	public int parseHyperImgNode( List<String> uhsFileArray, byte[] binHunk, long rawOffset, UHSRootNode rootNode, UHSNode currentNode, int[] key, int startIndex ) {
		UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();
		int index = startIndex;
		String[] tokens = null;
		long offset = 0;
		int length = 0;
		byte[] tmpBytes = null;
		int x = 0;
		int y = 0;
		UHSHotSpotNode hotspotNode = new UHSHotSpotNode( "HotSpot" );  // This may or may not get used

		String tmp = getLoggedString( uhsFileArray, index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		String type = "";
		if ( tmp.indexOf( "hyperpng" ) != -1 ) type = "Hyperpng";
		else if ( tmp.indexOf( "gifa" ) != -1 ) type = "Hypergif";
		else {
			if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "parseHyperImgNode() is for hyperpng and gifa hunks only.", logHeader+logLine+1, null );
			index += innerCount;
			return index-startIndex;
		}

		UHSNode imgNode = new UHSNode( type );
		String title = getLoggedString( uhsFileArray, index );
		index++;
		innerCount--;

		int mainImgIndex = index;  // Yeah, the number triplet gets an id
		tokens = (getLoggedString( uhsFileArray, index )).split( " " );
		index++;
		innerCount--;
		if ( tokens.length != 3 ) {
			if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Could not parse HyperImg stats", logHeader+logLine+1, null );
			return innerCount+3;
		}
		// Skip dummy zeroes
		offset = Long.parseLong( tokens[1] ) - rawOffset;
		length = Integer.parseInt( tokens[2] );
		tmpBytes = null;
		if ( rawOffset != -1 ) tmpBytes = readBinaryHunk( binHunk, offset, length );
		if ( tmpBytes == null ) {
			// This error would be at index-1, if not for getLoggedString()'s counter
			if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Could not read referenced raw bytes", logHeader+logLine+1, null );
		}
		imgNode.setContent( tmpBytes, UHSNode.IMAGE );

		// This if-else would make regionless hyperimgs standalone and unnested
		//if ( innerCount+3 > 3 ) {
			imgNode.setId( mainImgIndex );
			hotspotNode.addChild( imgNode );
			rootNode.addLink( imgNode );

			hotspotNode.setContent( title, UHSNode.STRING );
			hotspotNode.setId( startIndex );
			currentNode.addChild( hotspotNode );
			rootNode.addLink( hotspotNode );
		//} else {
		//  imgNode.setId( startIndex );
		//  currentNode.addChild( imgNode );
		//  rootNode.addLink( imgNode );
		//}


		for ( int j=0; j < innerCount; ) {
			// Nested ids in HyperImg point to zone. Node type is zone-line+1.
			int nestedIndex = index+j;

			tokens = (getLoggedString( uhsFileArray, index+j )).split( " " );
			j++;
			if ( tokens.length != 4 ) {
				if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Could not parse HyperImg zone stats", logHeader+logLine+1, null );
				return innerCount+3;
			}
			int zoneX1 = Integer.parseInt( tokens[0] )-1;
			int zoneY1 = Integer.parseInt( tokens[1] )-1;
			int zoneX2 = Integer.parseInt( tokens[2] )-1;
			int zoneY2 = Integer.parseInt( tokens[3] )-1;

			tmp = getLoggedString( uhsFileArray, index+j );
			j++;
			if (tmp.matches( "[0-9]+ [A-Za-z]+$" ) == true) {
				int innerInnerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;
				if (tmp.endsWith( " overlay" )) {
					title = getLoggedString( uhsFileArray, index+j );
					j++;
					tokens = ( getLoggedString( uhsFileArray, index+j ) ).split( " " );
					j++;

					if ( tokens.length != 5 ) {
						if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Could not parse Overlay stats", logHeader+logLine+1, null );
						return innerCount+3;
					}
					// Skip dummy zeroes
					offset = Long.parseLong( tokens[1] ) - rawOffset;
					length = Integer.parseInt( tokens[2] );
					int posX = Integer.parseInt( tokens[3] )-1;
					int posY = Integer.parseInt( tokens[4] )-1;

					tmpBytes = null;
					if ( rawOffset != -1 ) tmpBytes = readBinaryHunk( binHunk, offset, length );
					if ( tmpBytes == null ) {
						// This error would be at index+j-1, if not for getLoggedString()'s counter
						if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Could not read referenced raw bytes", logHeader+logLine+1, null );
					}
					UHSNode overlayNode = new UHSNode( "Overlay" );
						overlayNode.setContent( tmpBytes, UHSNode.IMAGE );
						overlayNode.setId( nestedIndex );
						hotspotNode.addChild( overlayNode );
						rootNode.addLink( overlayNode );
						hotspotNode.setSpot( overlayNode, new HotSpot( zoneX1, zoneY1, zoneX2-zoneX1, zoneY2-zoneY1, posX, posY ) );

					// Reader's NodePanel would need to look two children deep
					//UHSNode newNode = new UHSNode( "OverlayData" );
					//  newNode.setContent( tmpBytes, UHSNode.IMAGE );
					//  overlayNode.addChild( newNode );
				}
				else if ( tmp.endsWith( " link" ) || tmp.endsWith( " hyperpng" ) || tmp.endsWith( " text" ) || tmp.endsWith( " hint" ) ) {
					int childrenBefore = hotspotNode.getChildCount();
					j--;  // Back up to the hunk type line
					j += buildNodes( uhsFileArray, binHunk, rawOffset, rootNode, hotspotNode, key, index+j );
					if (hotspotNode.getChildCount() == childrenBefore+1) {
						UHSNode newNode = hotspotNode.getChild( hotspotNode.getChildCount()-1 );
						newNode.shiftId( -1, rootNode );
						// It might be weird to recurse HyperImg id shifts
						if ( tmp.endsWith( "hyperpng" ) && newNode.getChildCount() != 1 ) {
							if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Nested HyperImage has an unexpected child count", logHeader+logLine+1, null );
						}
						hotspotNode.setSpot( newNode, new HotSpot( zoneX1, zoneY1, zoneX2-zoneX1, zoneY2-zoneY1, -1, -1 ) );
					}
					else {
						if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Failed to add nested hunk", logHeader+logLine+1, null );
					}
				}
				else {
					if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.INFO, this, "Unknown Hunk in HyperImage: "+ tmp, logHeader+logLine+1, null );
					j += innerInnerCount-1;
				}
			} else {j++;}
		}
		index += innerCount;
		return index-startIndex;
	}


	/**
	 * Generates a sound UHSNode.
	 *
	 * <p>This seems to be limited to PCM WAV audio.</p>
	 *
	 * <p><ul>
	 * <li>Illustrative UHS: <i>Tex Murphy: Overseer</i>: Day Two, Bosworth Clark's Lab, How do I operate that keypad?</li>
	 * </ul></p>
	 *
	 * <blockquote><pre>
	 * {@code
	 * # sound
	 * title
	 * 000000 offset length
	 * }
	 * </pre></blockquote>
	 *
	 * <p>Offset and length are zero-padded to 6 or 7 digits.</p>
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param rootNode an existing root node
	 * @param currentNode an existing node to add children to
	 * @param key this file's hint decryption key
	 * @param startIndex the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 * @see #decryptTextHunk(CharSequence, int[])
	 */
	public int parseSoundNode( List<String> uhsFileArray, byte[] binHunk, long rawOffset, UHSRootNode rootNode, UHSNode currentNode, int[] key, int startIndex ) {
		UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();
		int index = startIndex;
		String tmp = getLoggedString( uhsFileArray, index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		tmp ="";
		UHSNode soundNode = new UHSNode( "Sound" );
			soundNode.setContent( getLoggedString( uhsFileArray, index ), UHSNode.STRING );
			soundNode.setStringContentDecorator( new Version9xTitleDecorator() );
			soundNode.setId( startIndex );
			currentNode.addChild( soundNode );
			rootNode.addLink( soundNode );
		index++;

		tmp = getLoggedString( uhsFileArray, index );
		index++;
		long offset = Long.parseLong( tmp.substring( tmp.indexOf( " " )+1, tmp.lastIndexOf( " " ) ) ) - rawOffset;
		int length = Integer.parseInt( tmp.substring( tmp.lastIndexOf( " " )+1, tmp.length() ) );

		UHSNode newNode = new UHSNode( "SoundData" );

		byte[] tmpBytes = null;
		if ( rawOffset != -1 ) tmpBytes = readBinaryHunk( binHunk, offset, length );
		if ( tmpBytes == null ) {
			// This error would be at index-1, if not for getLoggedString()'s counter
			if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Could not read referenced raw bytes", logHeader+logLine+1, null );
		}

		newNode.setContent( tmpBytes, UHSNode.AUDIO );
		soundNode.addChild( newNode );

		return index-startIndex;
	}


	/**
	 * Generates a blank UHSNode for spacing.
	 *
	 * <blockquote><pre>
	 * {@code
	 * 2 blank
	 * -
	 * }
	 * </pre></blockquote>
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param rootNode an existing root node
	 * @param currentNode an existing node to add children to
	 * @param key this file's hint decryption key
	 * @param startIndex the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseBlankNode( List<String> uhsFileArray, byte[] binHunk, long rawOffset, UHSRootNode rootNode, UHSNode currentNode, int[] key, int startIndex ) {
		int index = startIndex;
		String tmp = getLoggedString( uhsFileArray, index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode newNode = new UHSNode( "Blank" );
			newNode.setContent( "^^^", UHSNode.STRING );
			newNode.setId( startIndex );
			currentNode.addChild( newNode );
			rootNode.addLink( newNode );
		index += innerCount;
		return index-startIndex;
	}


	/**
	 * Generates a version UHSNode.
	 *
	 * <p>This is the version reported by the hint file.
	 * It may be inaccurate, blank, or conflict with what is claimed in the info node.</p>
	 *
	 * <blockquote><pre>
	 * {@code
	 * # version
	 * title
	 * sentence
	 * sentence
	 * sentence
	 * }
	 * </pre></blockquote>
	 *
	 * <p><ul>
	 * <li>Illustrative UHS: <i>Frankenstein: Through the Eyes of the Monster</i> (blank version)</li>
	 * <li>Illustrative UHS: <i>Kingdom O' Magic</i> (blank version)</li>
	 * <li>Illustrative UHS: <i>Out of This World</i> (blank version)</li>
	 * <li>Illustrative UHS: <i>Spycraft: The Great Game</i> (blank version)</li>
	 * <li>Illustrative UHS: <i>Star Control 3</i> (blank version)</li>
	 * <li>Illustrative UHS: <i>System Shock</i> (blank version)</li>
	 * <li>Illustrative UHS: <i>The Bizarre Adventures of Woodruff</i> (blank version)</li>
	 * </ul></p>
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param rootNode an existing root node
	 * @param currentNode an existing node to add children to
	 * @param key this file's hint decryption key
	 * @param startIndex the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseVersionNode( List<String> uhsFileArray, byte[] binHunk, long rawOffset, UHSRootNode rootNode, UHSNode currentNode, int[] key, int startIndex ) {
		String breakChar = "^break^";

		int index = startIndex;
		String tmp = getLoggedString( uhsFileArray, index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode versionNode = new UHSNode( "Version" );
			versionNode.setContent( "Version: "+ getLoggedString( uhsFileArray, index ), UHSNode.STRING );
			versionNode.setStringContentDecorator( new Version9xTitleDecorator() );
			versionNode.setId( startIndex );
			currentNode.addChild( versionNode );
			rootNode.addLink( versionNode );
		index++;
		innerCount--;

		StringBuffer tmpContent = new StringBuffer();
		UHSNode newNode = new UHSNode( "VersionData" );

		for ( int j=0; j < innerCount; j++ ) {
			if ( tmpContent.length() > 0 ) tmpContent.append( breakChar );
			tmpContent.append( getLoggedString( uhsFileArray, index+j ) );
		}
		newNode.setContent( tmpContent.toString(), UHSNode.STRING );
		newNode.setStringContentDecorator( new Version9xVersionDecorator() );
		versionNode.addChild( newNode );

		index += innerCount;
		return index-startIndex;
	}


	/**
	 * Generates an info UHSNode.
	 *
	 * <blockquote><pre>
	 * {@code
	 * # info
	 * -
	 * length=#######
	 * date=DD-Mon-YY
	 * time=24:00:00
	 * author=name
	 * publisher=name
	 * copyright=sentence
	 * copyright=sentence
	 * copyright=sentence
	 * >sentence
	 * >sentence
	 * >sentence
	 * }
	 * </pre></blockquote>
	 *
	 * @param uhsFileArray array of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param rootNode an existing root node
	 * @param currentNode an existing node to add children to
	 * @param key this file's hint decryption key
	 * @param startIndex the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseInfoNode( List<String> uhsFileArray, byte[] binHunk, long rawOffset, UHSRootNode rootNode, UHSNode currentNode, int[] key, int startIndex ) {
		String breakChar = "^break^";

		int index = startIndex;
		String tmp = getLoggedString( uhsFileArray, index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode infoNode = new UHSNode( "Info" );
			infoNode.setContent( "Info: "+ getLoggedString( uhsFileArray, index ), UHSNode.STRING );
			infoNode.setStringContentDecorator( new Version9xTitleDecorator() );
			infoNode.setId( startIndex );
			currentNode.addChild( infoNode );
			rootNode.addLink( infoNode );
		index++;
		innerCount--;

		if ( innerCount > 0 ) {
			StringBuffer tmpContent = new StringBuffer();

			UHSNode newNode = new UHSNode( "InfoData" );

			for ( int j=0; j < innerCount; j++ ) {
				tmp = getLoggedString( uhsFileArray, index+j );
				if ( tmpContent.length() > 0 ) tmpContent.append( breakChar );
				tmpContent.append( tmp );
			}

			newNode.setContent( tmpContent.toString(), UHSNode.STRING );
			newNode.setStringContentDecorator( new Version9xInfoDecorator() );
			infoNode.addChild( newNode );
		}

		index += innerCount;
		return index-startIndex;
	}


	/**
	 * Generates an incentive UHSNode.
	 *
	 * <p>This node lists ids to show/block if the reader is unregistered.</p>
	 *
	 * <p>The list is a space-separated string of numbers, each with 'Z' or 'A' appended.
	 * 'Z' means the node is a nag message that should be hidden from registered readers.
	 * 'A' means only registered readers can see the node's children or link target.
	 * In some files, there is no list, and this node only occupies 2 lines.</p>
	 *
	 * <blockquote><pre>
	 * {@code
	 * # incentive
	 * -
	 * ID list (encrypted)
	 * }
	 * </pre></blockquote>
	 *
	 * <p>Upon parsing this node, all referenced ids will be
	 * looked up by calling getLink(id) on the rootNode. The nodes
	 * will have their restriction attribute set, but it is up to
	 * readers to actually honor them.</p>
	 *
	 * <p><ul>
	 * <li>Illustrative UHS: <i>AGON</i> (no IDs)</li>
	 * </ul></p>
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param rootNode an existing root node
	 * @param currentNode an existing node to add children to
	 * @param key this file's hint decryption key
	 * @param startIndex the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseIncentiveNode( List<String> uhsFileArray, byte[] binHunk, long rawOffset, UHSRootNode rootNode, UHSNode currentNode, int[] key, int startIndex ) {
		int index = startIndex;
		String tmp = getLoggedString( uhsFileArray, index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode incentiveNode = new UHSNode( "Incentive" );
			incentiveNode.setContent( "Incentive: "+ getLoggedString( uhsFileArray, index ), UHSNode.STRING );
			incentiveNode.setId( startIndex );
			currentNode.addChild( incentiveNode );
			rootNode.addLink( incentiveNode );
		index++;
		innerCount--;

		if ( innerCount > 0 ) {
			tmp = decryptNestString( getLoggedString( uhsFileArray, index ), key );
			index++;
			UHSNode newNode = new UHSNode( "IncentiveData" );
				newNode.setContent( tmp, UHSNode.STRING );
				incentiveNode.addChild( newNode );

			applyRestrictions( rootNode, tmp );
		}

		return index-startIndex;
	}

	/**
	 * Sets access restrictions on UHSNodes based on ids in an incentive UHSNode.
	 *
	 * @param rootNode an existing root node
	 * @param incentiveString a space-separated string of numbers, each with 'Z' or 'A' appended
	 * @see #parseIncentiveNode(List, byte[], long, UHSRootNode, UHSNode, int[], int)
	 */
	public void applyRestrictions( UHSRootNode rootNode, String incentiveString ) {
		UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();

		String[] tokens = incentiveString.split( " " );
		for ( int i=0; i < tokens.length; i++ ) {
			if ( tokens[i].matches( "[0-9]+[AZ]" ) ) {
				int tmpId = Integer.parseInt( tokens[i].substring(0, tokens[i].length()-1) );
				UHSNode tmpNode = rootNode.getNodeByLinkId( tmpId );
				if ( tmpNode != null ) {
					if ( tokens[i].endsWith( "Z" ) ) {
						tmpNode.setRestriction( UHSNode.RESTRICT_NAG );
					} else if ( tokens[i].endsWith("A") ) {
						tmpNode.setRestriction( UHSNode.RESTRICT_REGONLY );
					}
				} else {
					if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.INFO, this, "Unknown node id: "+ tmpId, logHeader+logLine+1, null );
				}
			}
		}
	}


	/**
	 * Generates a stand-in UHSNode for an unknown hunk.
	 *
	 * @param uhsFileArray a List of all available lines in the file
	 * @param binHunk array of raw bytes at the end of the file
	 * @param rawOffset offset to the raw bytes from the beginning of the file
	 * @param rootNode an existing root node
	 * @param currentNode an existing node to add children to
	 * @param key this file's hint decryption key
	 * @param startIndex the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseUnknownNode( List<String> uhsFileArray, byte[] binHunk, long rawOffset, UHSRootNode rootNode, UHSNode currentNode, int[] key, int startIndex ) {
		UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();

		int index = startIndex;
		String tmp = getLoggedString( uhsFileArray, index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.INFO, this, "Unknown Hunk: "+ tmp, logHeader+logLine+1, null );

		UHSNode newNode = new UHSNode( "Unknown" );
			newNode.setContent( "^UNKNOWN HUNK^", UHSNode.STRING );
			currentNode.addChild( newNode );

		index += innerCount;
		return index-startIndex;
	}


	/**
	 * Reads some raw bytes originally from the end of a UHS file.
	 *
	 * <p>Images, comments, sounds, etc., are stored there.</p>
	 *
	 * <p>This offset is relative to the start of the raw bytes, NOT the beginning of the file.</p>
	 *
	 * @param binHunk array of bytes at the end of the file (after 0x1Ah)
	 * @param offset starting index within the array (offset + length must not exceed binHunk.length)
	 * @param length the desired number of bytes to retrieve
	 * @return the relevant bytes, or null if the offset or length is invalid
	 */
	public byte[] readBinaryHunk( byte[] binHunk, long offset, int length ) {
		if ( offset < 0 || length < 0 || offset+length > binHunk.length )
			return null;
		byte[] result = new byte[length];
		for ( int i=0; i < length; i++ ) {
			result[i] = binHunk[(int)offset+i];
		}
		return result;
	}


	private String getLoggedString( List<String> uhsFileArray, int n ) {
		logLine = n;
		return uhsFileArray.get( n );
	}


	/**
	 * Calculates the security checksum of a UHS file.
	 *
	 * <p>It's a CRC16 of the entire file (read as unsigned bytes),
	 * minus the last two bytes.</p>
	 */
	public long calcChecksum( File f ) {
		UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();
		long result = -1;

		CRC16 crc = new CRC16();
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile( f, "r" );
			long len = raf.length();

			byte[] tmpBytes = new byte[1024];
			int count;
			while ( (count=raf.read( tmpBytes )) != -1 ) {
				long pos = raf.getChannel().position();
				if ( pos >= len-2 ) {
					count -= (int)(pos - (len-2));
				}
				// Let the CRC class clear out the signedness
				crc.update( tmpBytes, 0, count );
			}
			result = crc.getValue();

			// I'm not sure what's going on below...
			// Values past the half-way point for short (32768),
			// must add 1+FF. Java's dealing with a wider
			// numeric type than short, so it's oblivious to the
			// sign bit, if any. The 1 might make sense as part
			// of twos complement sign-flipping, but not the FF.
			// It might be sign-flipping the most significant
			// byte alone (adding 1*256) but that seems weird
			// too.
			// In any case, my hex editor's reported unsigned
			// values for high numbers stored in files always
			// disagree with the calc'd CRC by 256. So the
			// problem is here and not in parsing the stored
			// expected CRC.
			// Since adding's happening, the AND truncates
			// a potential overflowing 17th bit.
			// <li>Illustrative UHS: <i>Star Trek: Borg</i> (low)</li>
			// <li>Illustrative UHS: <i>Rent-A-Hero</i> (high)</li>
			// <li>Illustrative UHS: <i>Azrael's Tear</i> (overflow)</li>
			if ( result >= 0x8000 ) result = (result + 0x0100) & 0xFFFF;
		}
		catch ( IOException e ) {
			if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, "Couldn't calculate checksum", 0, null );
		}
		finally {
			try {if ( raf != null ) raf.close();} catch ( IOException e ) {}
		}

		return result;
	}

	/**
	 * Reads the security checksum stored in a UHS file.
	 */
	private long readChecksum( File f ) {
		UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();
		int result = -1;

		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile( f, "r" );
			long len = raf.length();
			if ( len >= 2 ) {
				raf.getChannel().position( len-2 );
/*
				// Read unsigned bytes
				int leastByte = inFile.read();
				int mostByte = inFile.read();
				if ( leastByte != -1 && mostByte != -1 ) {
					result = mostByte << 8 | leastByte;
				}
*/
				ByteBuffer tmpBytes = ByteBuffer.allocate( 4 );
				tmpBytes.order( ByteOrder.LITTLE_ENDIAN );
				while ( raf.getChannel().read( tmpBytes ) != -1 ) {
					if ( tmpBytes.position() == 2 ) {
						tmpBytes.put( (byte)0 ).put( (byte)0 );
						result = tmpBytes.getInt( 0 );
						result &= 0xFFFF;
						break;
					}
				}

			}
		}
		catch ( IOException e ) {
			if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, this, String.format("Couldn't read stored checksum from: ", f.getAbsolutePath()), 0, null );
		}
		finally {
			try {if ( raf != null ) raf.close();} catch ( IOException e ) {}
		}

		return result;
	}

	/**
	 * Reads the security checksum stored in an array.
	 *
	 * <p>The final two bytes contain the expected CRC16
	 * of the rest of the file.</p>
	 *
	 * <p>It's a little-endian short ((un)signed?).</p>
	 *
	 * @param a any byte array that includes the end of a UHS file
	 */
	public long readChecksum( byte[] a ) {
		if ( a.length < 2 ) return -1;

		// Strip the signedness, if any
		int leastByte = a[a.length-2] & 0xFF;
		int mostByte = a[a.length-1] & 0xFF;

		return ( mostByte << 8 | leastByte );
	}
}
