package net.vhati.openuhs.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.CRC16;
import net.vhati.openuhs.core.HotSpot;
import net.vhati.openuhs.core.UHSAudioNode;
import net.vhati.openuhs.core.UHSBatchNode;
import net.vhati.openuhs.core.UHSHotSpotNode;
import net.vhati.openuhs.core.UHSImageNode;
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.core.UHSParseContext;
import net.vhati.openuhs.core.UHSParseException;
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
 * A parser for 88a format and 9x format UHS files.
 */
public class UHSParser {
	/** Honor the actual UHS file structure for version 9x auxiliary nodes */
	public static final int AUX_NORMAL = 0;

	/** Drop version 9x auxiliary nodes */
	public static final int AUX_IGNORE = 1;

	/** Move version 9x auxiliary nodes to within the master subject node and make that the new root */
	public static final int AUX_NEST = 2;


	private final Logger logger = LoggerFactory.getLogger( UHSParser.class );

	private boolean force88a = false;


	public UHSParser() {
	}


	/**
	 * Toggles parsing 9x files as an 88a reader.
	 *
	 * <p>Old readers attempting to read a 9x file will see a
	 * deprecation notice.</p>
	 */
	public void setForce88a( boolean b ) {
		force88a = b;
	}


	/**
	 * Generates a decryption key for various hunks' text in the 9x format.
	 *
	 * @param title  the name of the master subject node of the UHS document (not the filename)
	 * @return the key
	 * @see #decryptNestString(CharSequence, int[])
	 * @see #decryptTextHunk(CharSequence, int[])
	 */
	public int[] generate9xKey( CharSequence title ) {
		if ( title == null || !Charset.forName( "US-ASCII" ).newEncoder().canEncode( title ) ) {
			throw new IllegalArgumentException( "Version 9x requires an ASCII title for its encryption key" );
		}

		int[] key = new int[title.length()];
		int[] k = {'k', 'e', 'y'};
		for ( int i=0; i < title.length(); i++ ) {
			key[i] = (int)title.charAt( i ) + ( k[i%3] ^ (i + 40) );
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
	 * @param input  ciphertext
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
	 * @param input  ciphertext
	 * @param key  this file's hint decryption key
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
	 * @param input  ciphertext
	 * @param key  this file's hint decryption key
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
	 * @param f  a file to read
	 * @param auxStyle  option for 9x files: AUX_NORMAL, AUX_IGNORE, or AUX_NEST
	 * @return the root of a tree of nodes representing the hint file
	 * @see #parse88Format(UHSParseContext)
	 * @see #parse9xFormat(UHSParseContext, int)
	 */
	public UHSRootNode parseFile( File f, int auxStyle ) throws IOException, UHSParseException {
		if ( auxStyle != AUX_NORMAL && auxStyle != AUX_IGNORE && auxStyle != AUX_NEST ) {
			throw new IllegalArgumentException( String.format( "Invalid auxStyle: %d", auxStyle ) );
		};

		int index = -1;  // Increment before reads, and this will be the last read 0-based index.
		String tmp = "";

		List<String> allLines = new ArrayList<String>();
		long binOffset = -1;
		byte[] binHunk = new byte[0];

		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile( f, "r" );

			index++;
			tmp = raf.readLine();
			if ( !tmp.equals( "UHS" ) ) {
				UHSParseException pe = new UHSParseException( "Not a UHS file! (First bytes were not 'UHS')" );
				throw pe;
			}
			allLines.add( tmp );

			// There's a hunk of binary referenced by offset at the end of 91a and newer files
			// One can skip to it by searching for 0x1Ah.
			long skippedNulls = 0;
			byte tmpByte = -1;
			while ( (tmpByte = (byte)raf.read()) != -1 && tmpByte != 0x1a ) {
				// There should be no nulls prior to the binary hunk.
				if ( tmpByte == 0x00 ) {
					skippedNulls++;
					continue;  // A couple malformed 88a's have nulls at the end, skip.
				}

				raf.getChannel().position( raf.getChannel().position()-1 );  // Unread that byte.
				index++;
				tmp = raf.readLine();                 // RandomAccessFile sort of reads as ASCII/UTF-8?
				if ( tmp.indexOf( "\\x00" ) >= 0 ) {
					logger.warn( "Pruned extraneous null bytes from text" );
					tmp = tmp.replaceAll( "\\x00", "" );
				}
				allLines.add( tmp );
			}
			if ( skippedNulls > 0 ) logger.warn( "Skipped {} extraneous null bytes", skippedNulls );

			binOffset = raf.getChannel().position();  // Either after 0x1a, or EOF.
			long binSize = raf.length() - binOffset;
			if ( binSize > 0 && binSize <= Integer.MAX_VALUE ) {
				binHunk = new byte[(int)binSize];
				raf.readFully( binHunk );
			}
			else {
				binOffset = -1;
			}
		}
		catch ( NumberFormatException e ) {
			UHSParseException pe = new UHSParseException( String.format( "Could not parse header (last parsed line: %d)", index+1 ), e );
			throw pe;
		}
		finally {
			try {if ( raf != null ) raf.close();} catch ( IOException e ) {}
		}

		index = 0;  // Now increment after getting lines, and this will be the pending 0-based index.

		UHSParseContext context = new UHSParseContext();
		context.setFile( f );
		context.setAllLines( allLines );
		context.setBinaryHunk( binHunk );
		context.setBinaryHunkOffset( binOffset );

		index += parse88Format( context );
		UHSRootNode rootNode = context.getRootNode();

		// In 88a files, this would e the end.
		// In 9x files, index should be right after "** END OF 88A FORMAT **".

		if ( !force88a && context.getRootNode().isLegacy() ) {
			// That was a fake 88a format message, now comes the 9x format.

			context.setLineFudge( index-1 );  // Ignore all lines so far. Treat that END line as 0.

			rootNode = parse9xFormat( context, auxStyle );

			long storedSum = readChecksum( binHunk );
			long calcSum = calcChecksum( f );
			if ( storedSum == -1 ) {
				logger.warn( "Could not read the stored security checksum from this file" );
			}
			else if ( calcSum == -1 ) {
				logger.warn( "Could not calculate the security checksum for this file" );
			}
			else if ( storedSum != calcSum ) {
				logger.warn( "Calculated CRC differs from CRC stored in file: {} vs {} (off by: {})", calcSum, storedSum, (storedSum - calcSum) );
			}
		}
		return rootNode;
	}


	/**
	 * Generates a tree of UHSNodes from UHS 88a.
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
	 * <p>There are no hunk labels. The line index denotes meaning. Text of
	 * subjects, questions, and hints are encrypted.</p>
	 *
	 * <p>The file's 1-based line references begin at the first subject.
	 * This method will adjust the parse context's line fudge to ignore the
	 * first four lines after it reads them.</p>
	 *
	 * <p>A Version node will be added, since that was not natively reported
	 * in 88a.</p>
	 *
	 * <p>The official reader only honors line breaks in credit for lines with
	 * fewer than 20 characters. Otherwise, they're displayed as a space. No
	 * authors ever wrote with that in mind, so it's not worth enforcing.</p>
	 *
	 * <p>The generated root node will be added to the parse context. Its
	 * legacy flag will be set if "** END OF 88A FORMAT **" was found at the
	 * end.</p>
	 *
	 * @param context  the parse context
	 * @see #decryptString(CharSequence)
	 * @return  the number of lines consumed from the file in parsing children
	 */
	public int parse88Format( UHSParseContext context ) throws UHSParseException {
		try {
			int fudged = 0;
			int index = 0;

			String magicLine = context.getLine( index++ );
			if ( !"UHS".equals( magicLine ) ) {
				throw new UHSParseException( String.format( "The 88a parser encountered an unexpected first line (not 'UHS'): %s", magicLine ) );
			}

			String title = context.getLine( index++ );
			int headerFirstHint = Integer.parseInt( context.getLine( index++ ) );
			int headerLastHint = Integer.parseInt( context.getLine( index++ ) );

			UHSRootNode rootNode = new UHSRootNode();
				rootNode.setRawStringContent( title );
			context.setRootNode( rootNode );

			// Reset, ignoring 3 of those 4 lines, to make the next line (first subject) be 1.
			fudged = index-1;
			context.setLineFudge( context.getLineFudge() + fudged );
			index = 1;

			boolean firstSubject = true;
			int questionSectionStart = -1;

			List<UHSNode> subjectNodes = new ArrayList<UHSNode>();
			List<Integer> subjectsFirstQuestion = new ArrayList<Integer>();

			// Collect all subjects at once, while adding them to the root node along the way.
			while ( firstSubject || index < questionSectionStart ) {
				UHSNode currentSubject = new UHSNode( "Subject" );
					currentSubject.setRawStringContent( decryptString( context.getLine( index++ ) ) );
					rootNode.addChild( currentSubject );
					subjectNodes.add( currentSubject );

				int firstQuestion = Integer.parseInt( context.getLine( index++ ) );
				subjectsFirstQuestion.add( new Integer( firstQuestion ) );

				if ( firstSubject ) {
					questionSectionStart = firstQuestion;
					firstSubject = false;
				}
			}

			int s = 0;
			List<UHSNode> questionNodes = new ArrayList<UHSNode>();
			List<Integer> questionsFirstHint = new ArrayList<Integer>();

			// Collect all questions, while adding them to subjects along the way.
			while ( index < headerFirstHint ) {
				if ( s+1 < subjectNodes.size() && index == subjectsFirstQuestion.get( s+1 ) ) {
					s++;  // Reached the next subject's questions.
				}
				UHSNode currentSubject = subjectNodes.get( s );

				UHSNode currentQuestion = new UHSNode( "Question" );
					currentQuestion.setRawStringContent( decryptString( context.getLine( index++ ) ) +"?" );
					currentSubject.addChild( currentQuestion );
					questionNodes.add( currentQuestion );  // Keep a flat list as well, for counting.

				int firstHint = Integer.parseInt( context.getLine( index++ ) );
				questionsFirstHint.add( new Integer( firstHint ) );
			}

			int q = 0;

			// Collect all the hints, adding them to each question.
			while ( index <= headerLastHint ) {
				if ( q+1 < questionNodes.size() && index == questionsFirstHint.get( q+1 ) ) {
					q++;  // Reached the next question's hints.
				}
				UHSNode currentQuestion = questionNodes.get( q );

				UHSNode currentHint = new UHSNode( "Hint" );
					currentHint.setRawStringContent( decryptString( context.getLine( index++ ) ) );
					currentQuestion.addChild( currentHint );
			}
			// All subjects, questions, and hints have been collected.

			// Index should be at the end credit lines now.

			UHSNode blankNode = new UHSNode( "Blank" );
				blankNode.setRawStringContent( "--=File Info=--" );
				rootNode.addChild( blankNode );

			UHSNode fauxVersionNode = new UHSNode( "Version" );
				fauxVersionNode.setRawStringContent( "Version: 88a" );
				rootNode.addChild( fauxVersionNode );

			UHSNode fauxVersionDataNode = new UHSNode( "VersionData" );
				fauxVersionDataNode.setRawStringContent( "This version info was added by OpenUHS during parsing because the 88a format does not report it." );
				fauxVersionNode.addChild( fauxVersionDataNode );

			UHSNode creditNode = new UHSNode( "Credit" );
				creditNode.setRawStringContent( "Credits" );
				rootNode.addChild( creditNode );

			String breakChar = "^break^";
			StringBuilder creditDataBuf = new StringBuilder();
			while ( context.hasLine( index ) ) {
				String tmp = context.getLine( index++ );

				if ( "** END OF 88A FORMAT **".equals( tmp ) ) {
					rootNode.setLegacy( true );
					break;
				}

				if ( creditDataBuf.length() > 0 ) creditDataBuf.append( breakChar );
				creditDataBuf.append( tmp );
			}
			UHSNode creditDataNode = new UHSNode( "CreditData" );
				creditDataNode.setRawStringContent( creditDataBuf.toString() );
				creditDataNode.setStringContentDecorator( new Version88CreditDecorator() );
				creditNode.addChild( creditDataNode );

			return ( fudged + index );  // Index was reset after the header lines.
		}
		catch ( NumberFormatException e ) {
			UHSParseException pe = new UHSParseException( String.format( "Unable to parse nodes (last parsed line: %d)", context.getLastParsedLineNumber() ), e );
			throw pe;
		}
	}


	/**
	 * Generates a tree of UHSNodes from UHS 91a format onwards.
	 *
	 * <p>Versions 91a, 95a, and 96a have been seen in the wild.
	 * These UHS files are prepended with an 88a section containing an
	 * "upgrade your reader" notice. Additionally, they exploit the 88a format
	 * to include ciphertext that will become human-readable when encrypted,
	 * with a message for anyone opening the file in text editors.</p>
	 *
	 * <p>As shown below, the file's 1-based line references begin after
	 * "** END OF 88A FORMAT **". Before calling, set the context's line fudge
	 * to ignore all lines prior, so that context.getLine(0) will yield the
	 * END line.</p>
	 *
	 * <blockquote><pre>
	 * {@code
	 * ** END OF 88A FORMAT **
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
	 * {2-byte CRC16 of the entire file's bytes, excluding these two}
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
	 * @param context  the parse context
	 * @param auxStyle  AUX_NORMAL (canon), AUX_IGNORE (omit), or AUX_NEST (move inside the master subject and make that the new root).
	 * @return the root of a tree of nodes
	 * @see #buildNodes(UHSParseContext, UHSNode, int)
	 * @see #calcChecksum(File)
	 */
	public UHSRootNode parse9xFormat( UHSParseContext context, int auxStyle ) throws UHSParseException {
		if ( auxStyle != AUX_NORMAL && auxStyle != AUX_IGNORE && auxStyle != AUX_NEST ) {
			throw new IllegalArgumentException( String.format( "Invalid auxStyle: %d", auxStyle ) );
		}

		try {
			UHSRootNode rootNode = new UHSRootNode();
				rootNode.setRawStringContent( "Root" );
			context.setRootNode( rootNode );

			String title = context.getLine( 2 ); // This is the title of the master subject node
			int[] key = generate9xKey( title );
			context.setKey( key );

			int index = 1;
			index += buildNodes( context, rootNode, index );

			if ( rootNode.getChildCount() == 0 ) {
				throw new UHSParseException( String.format( "No nodes were parsed!? Started from this line: %s", context.getLine( 1 ) ) );
			}

			if ( auxStyle != AUX_IGNORE ) {
				if ( auxStyle == AUX_NEST ) {
					UHSNode tmpChildNode = rootNode.getChild( 0 );
						rootNode.setChildren( tmpChildNode.getChildren() );
						rootNode.setRawStringContent( title );
						rootNode.setStringContentDecorator( new Version9xTitleDecorator() );

					UHSNode blankNode = new UHSNode( "Blank" );
						blankNode.setRawStringContent( "--=File Info=--" );
						rootNode.addChild( blankNode );
				}
				while ( context.hasLine( index ) ) {
					index += buildNodes( context, rootNode, index );
				}
			}
			return rootNode;
		}
		catch ( NumberFormatException e ) {
			UHSParseException pe = new UHSParseException( String.format( "Unable to parse nodes (last parsed line: %d)", context.getLastParsedLineNumber() ), e );
			throw pe;
		}
	}


	/**
	 * Recursively parses UHS newer than 88a.
	 *
	 * <p>This recognizes various types of hints, and runs specialized methods to decode them.
	 * Unrecognized hints are harmlessly omitted.</p>
	 *
	 * @param context  the parse context
	 * @param currentNode  an existing node to add children to
	 * @param startIndex  the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int buildNodes( UHSParseContext context, UHSNode currentNode, int startIndex ) {
		int index = startIndex;

		String tmp = context.getLine( index );
		if ( tmp.matches( "[0-9]+ [A-Za-z]+$" ) ) {
			if (tmp.endsWith( " comment" )) {
				index += parseCommentNode( context, currentNode, index );
			}
			else if (tmp.endsWith( " credit" )) {
				index += parseCreditNode( context, currentNode, index );
			}
			else if (tmp.endsWith( " hint" )) {
				index += parseHintNode( context, currentNode, index );
			}
			else if (tmp.endsWith( " nesthint" )) {
				index += parseNestHintNode( context, currentNode, index );
			}
			else if (tmp.endsWith( " subject" )) {
				index += parseSubjectNode( context, currentNode, index );
			}
			else if (tmp.endsWith( " link" )) {
				index += parseLinkNode( context, currentNode, index );
			}
			else if (tmp.endsWith( " text" )) {
				index += parseTextNode( context, currentNode, index );
			}
			else if (tmp.endsWith( " hyperpng" )) {
				index += parseHyperImageNode( context, currentNode, index );
			}
			else if (tmp.endsWith( " gifa" )) {
				index += parseHyperImageNode( context, currentNode, index );
			}
			else if (tmp.endsWith( " sound" )) {
				index += parseSoundNode( context, currentNode, index );
			}
			else if (tmp.endsWith( " blank" )) {
				index += parseBlankNode( context, currentNode, index );
			}
			else if (tmp.endsWith( " version" )) {
				index += parseVersionNode( context, currentNode, index );
			}
			else if (tmp.endsWith( " info" )) {
				index += parseInfoNode( context, currentNode, index );
			}
			else if (tmp.endsWith( " incentive" )) {
				index += parseIncentiveNode( context, currentNode, index );
			}
			else {
				index += parseUnknownNode( context, currentNode, index );
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
	 * @param context  the parse context
	 * @param currentNode  an existing node to add children to
	 * @param startIndex  the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseSubjectNode( UHSParseContext context, UHSNode currentNode, int startIndex ) {
		int index = startIndex;
		String tmp = context.getLine( index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode newNode = new UHSNode( "Subject" );
			newNode.setRawStringContent( context.getLine( index ) );
			newNode.setStringContentDecorator( new Version9xTitleDecorator() );
			newNode.setId( startIndex );
			currentNode.addChild( newNode );
			context.getRootNode().addLink( newNode );
		index++;
		innerCount--;

		for ( int j=0; j < innerCount; ) {
			j += buildNodes( context, newNode, index+j );
		}

		index += innerCount;
		return index-startIndex;
	}

	/**
	 * Generates an irregularly revealed UHSNode and its contents.
	 *
	 * <p>UHSBatchNode was written to handle children that reveal in batches.</p>
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
	 * <p>A hyphen divider indicates the next batch, beginning with encrypted
	 * hint text. The encrypted text may span multiple lines.</p>
	 *
	 * <p>An equals divider indicates a nested node of any type (as if this were
	 * a Subject node), immediately followed by more encrypted hint text.</p>
	 *
	 * <p>The official reader reveals "partial hint", "embedded hunk", and
	 * "rest of hint" together as if they were one - each on a new line but
	 * displayed within a shared border.</p>
	 *
	 * <p>The hints surrounding an embedded hunk are optional.
	 * That is: a 'standalone' embedded hunk is preceeded by a hyphen and an equals sign.
	 * It is immediately followed by the hyphen indicating the next sepatate hint.</p>
	 *
	 * <p>Multiple embedded hunks can appear in a row, each preceeded by an equals.</p>
	 *
	 * <p>TODO: See if multiple embedded hunks can alternate with intervening text,
	 * not just appearing in clumps.</p>
	 *
	 * <p><ul>
	 * <li>Illustrative UHS: <i>The Longest Journey</i>: Chapter 1, What should I do with all the stuff outside my window?</li>
	 * </ul></p>
	 *
	 * @param context  the parse context
	 * @param currentNode  an existing node to add children to
	 * @param startIndex  the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 * @see #decryptNestString(CharSequence, int[])
	 * @see net.vhati.openuhs.core.UHSBatchNode
	 */
	public int parseNestHintNode( UHSParseContext context, UHSNode currentNode, int startIndex ) {
		String breakChar = "^break^";

		int index = startIndex;
		String tmp = context.getLine( index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSBatchNode hintNode = new UHSBatchNode( "NestHint" );
			hintNode.setRawStringContent( context.getLine( index ) );
			hintNode.setStringContentDecorator( new Version9xTitleDecorator() );
			hintNode.setId( startIndex );
			currentNode.addChild( hintNode );
			context.getRootNode().addLink( hintNode );
		index++;
		innerCount--;

		boolean firstInBatch = true;
		StringBuffer tmpContent = new StringBuffer();
		UHSNode newNode = new UHSNode( "HintData" );

		for ( int j=0; j < innerCount; j++ ) {
			tmp = context.getLine( index+j );
			if ( tmp.equals( "-" ) ) {
				// A hint, add pending content
				if ( tmpContent.length() > 0 ) {
					newNode.setRawStringContent( tmpContent.toString() );
					newNode.setStringContentDecorator( new Version9xHintDecorator() );
					hintNode.addChild( newNode );
					hintNode.setAddon( newNode, !firstInBatch );

					newNode = new UHSNode( "HintData" );
					tmpContent.delete( 0, tmpContent.length() );
				}
				firstInBatch = true;
			}
			else if ( tmp.equals( "=" ) ) {
				// Nested hunk, add pending content
				if ( tmpContent.length() > 0 ) {
					newNode.setRawStringContent( tmpContent.toString() );
					newNode.setStringContentDecorator( new Version9xHintDecorator() );
					hintNode.addChild( newNode );
					hintNode.setAddon( newNode, !firstInBatch );
					firstInBatch = false;  // There was content ahead of the equals sign.
				}

				int childrenBefore = hintNode.getChildCount();

				j += buildNodes( context, hintNode, index+j+1 );

				if ( hintNode.getChildCount() == childrenBefore+1 ) {
					UHSNode thatNode = hintNode.getChild( hintNode.getChildCount()-1 );
					hintNode.setAddon( thatNode, !firstInBatch );
					firstInBatch = false;  // Added a node just now.
				}

				if ( tmpContent.length() > 0 ) {
					newNode = new UHSNode( "HintData" );
					tmpContent.delete( 0, tmpContent.length() );
				}
			}
			else {
				// Accumulate hint content.
				if ( tmpContent.length() > 0 ) tmpContent.append( breakChar );
				tmpContent.append( decryptNestString( context.getLine( index+j ), context.getKey() ) );
			}

			if ( j == innerCount-1 && tmpContent.length() > 0 ) {
				newNode.setRawStringContent( tmpContent.toString() );
				newNode.setStringContentDecorator( new Version9xHintDecorator() );
				hintNode.addChild( newNode );
				hintNode.setAddon( newNode, !firstInBatch );
				firstInBatch = false;
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
	 * @param context  the parse context
	 * @param currentNode  an existing node to add children to
	 * @param startIndex  the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 * @see #decryptString(CharSequence)
	 */
	public int parseHintNode( UHSParseContext context, UHSNode currentNode, int startIndex ) {
		String breakChar = "^break^";

		int index = startIndex;
		String tmp = context.getLine( index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1 - 1;

		UHSNode hintNode = new UHSNode( "Hint" );
			hintNode.setRawStringContent( context.getLine( index ) );
			hintNode.setStringContentDecorator( new Version9xTitleDecorator() );
			hintNode.setId( startIndex );
			currentNode.addChild( hintNode );
			context.getRootNode().addLink( hintNode );
		index++;

		StringBuffer tmpContent = new StringBuffer();
		UHSNode newNode = new UHSNode( "HintData" );

		for ( int j=0; j < innerCount; j++ ) {
			tmp = context.getLine( index+j );
			if ( tmp.equals( "-" ) ) {
				if ( tmpContent.length() > 0 ) {
					newNode.setRawStringContent( tmpContent.toString() );
					newNode.setStringContentDecorator( new Version9xHintDecorator() );
					hintNode.addChild( newNode );
					newNode = new UHSNode( "HintData" );
					tmpContent.delete( 0, tmpContent.length() );
				}
			}
			else {
				if ( tmpContent.length() > 0 ) tmpContent.append( breakChar );

				tmp = context.getLine( index+j );
				tmpContent.append( decryptString( tmp ) );
			}

			if ( j == innerCount-1 && tmpContent.length() > 0 ) {
				newNode.setRawStringContent( tmpContent.toString() );
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
	 * @param context  the parse context
	 * @param currentNode  an existing node to add children to
	 * @param startIndex  the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseCommentNode( UHSParseContext context, UHSNode currentNode, int startIndex ) {
		String breakChar = "^break^";

		int index = startIndex;
		String tmp = context.getLine( index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode commentNode = new UHSNode( "Comment" );
			commentNode.setRawStringContent( context.getLine( index ) );
			commentNode.setStringContentDecorator( new Version9xTitleDecorator() );
			commentNode.setId( startIndex );
			currentNode.addChild( commentNode );
			context.getRootNode().addLink( commentNode );
		index++;
		innerCount--;

		StringBuffer tmpContent = new StringBuffer();
		UHSNode newNode = new UHSNode( "CommentData" );

		for ( int j=0; j < innerCount; j++ ) {
			if (tmpContent.length() > 0) tmpContent.append( breakChar );
			tmpContent.append( context.getLine( index+j ) );
		}
		newNode.setRawStringContent( tmpContent.toString() );
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
	 * @param context  the parse context
	 * @param currentNode  an existing node to add children to
	 * @param startIndex  the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseCreditNode( UHSParseContext context, UHSNode currentNode, int startIndex ) {
		String breakChar = "^break^";

		int index = startIndex;
		String tmp = context.getLine( index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode creditNode = new UHSNode( "Credit" );
			creditNode.setRawStringContent( context.getLine( index ) );
			creditNode.setStringContentDecorator( new Version9xTitleDecorator() );
			creditNode.setId( startIndex );
			currentNode.addChild( creditNode );
			context.getRootNode().addLink( creditNode );
		index++;
		innerCount--;

		StringBuffer tmpContent = new StringBuffer();
		UHSNode newNode = new UHSNode( "CreditData" );

		for ( int j=0; j < innerCount; j++ ) {
			if ( tmpContent.length() > 0 ) tmpContent.append( breakChar );
			tmpContent.append( context.getLine( index+j ) );
		}
		newNode.setRawStringContent( tmpContent.toString() );
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
	 * <p>Offset is counted from the beginning of the file.</p>
	 * <p>Offset and length are zero-padded to 6 or 7 digits.</p>
	 * <p>The binary content is encrypted.</p>
	 *
	 * @param context  the parse context
	 * @param currentNode  an existing node to add children to
	 * @param startIndex  the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 * @see #decryptTextHunk(CharSequence, int[])
	 */
	public int parseTextNode( UHSParseContext context, UHSNode currentNode, int startIndex ) {
		String breakChar = "^break^";

		int index = startIndex;
		String tmp = context.getLine( index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		tmp ="";
		UHSNode textNode = new UHSNode( "Text" );
			textNode.setRawStringContent( context.getLine( index ) );
			textNode.setStringContentDecorator( new Version9xTitleDecorator() );
			textNode.setId( startIndex );
			currentNode.addChild( textNode );
			context.getRootNode().addLink( textNode );
		index++;

		tmp = context.getLine( index );
		index++;
		long offset = Long.parseLong( tmp.substring( 9, tmp.lastIndexOf( " " ) ) ) - context.getBinaryHunkOffset();
		int length = Integer.parseInt( tmp.substring( tmp.lastIndexOf( " " )+1, tmp.length() ) );

		StringBuffer tmpContent = new StringBuffer();
		UHSNode newNode = new UHSNode( "TextData" );

		byte[] tmpBytes = context.readBinaryHunk( offset, length );
		if ( tmpBytes != null ) {
			tmp = new String( tmpBytes );
		}
		else {
			// This error would be at index-1, if not for context.getLine()'s memory.
			logger.error( "Could not read referenced raw bytes (last parsed line: {})", context.getLastParsedLineNumber() );
			tmp = "";
		}
		String[] lines = tmp.split( "(\r\n)|\r|\n", -1 );
		for ( int i=0; i < lines.length; i++ ) {
			if ( tmpContent.length() > 0 ) tmpContent.append( breakChar );
			tmpContent.append( decryptTextHunk( lines[i], context.getKey() ) );
		}
		newNode.setRawStringContent( tmpContent.toString() );
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
	 * @param context  the parse context
	 * @param currentNode  an existing node to add children to
	 * @param startIndex  the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseLinkNode( UHSParseContext context, UHSNode currentNode, int startIndex ) {
		int index = startIndex;
		String tmp = context.getLine( index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode newNode = new UHSNode( "Link" );
			newNode.setRawStringContent( context.getLine( index ) );
			newNode.setStringContentDecorator( new Version9xTitleDecorator() );
			newNode.setId( startIndex );
			currentNode.addChild( newNode );
			context.getRootNode().addLink( newNode );
		index++;

		int targetIndex = Integer.parseInt( context.getLine( index ) );
			newNode.setLinkTarget( targetIndex );
		index++;

		// Removed since it ran endlessly when nodes link in both directions.
		// buildNodes( context, newNode, targetIndex );

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
	 * <p>The "--not-a-gap--" lines are inserted here for visual clarity.</p>
	 * <p>Offset is counted from the beginning of the file.</p>
	 * <p>Offset and length are zero-padded to 6 or 7 digits.</p>
	 * <p>A gifa has the same structure, but might not officially contain regions.</p>
	 * <p>The main image gets an id, which may be referenced by an Incentive node.</p>
	 *
	 * </p>Line ids of nodes nested within a HyperImage are skewed
	 * because their initial line is the region coords, and the
	 * node type comes second.</p>
	 *
	 * <p>TODO: Nested HyperImages aren't expected to recurse further
	 * with additional nested HiperImage nodes. It is unknown whether such children
	 * would need their ids would be doubly skewed.</p>
	 *
	 * <p><ul>
	 * <li>Illustrative UHS: <i>The Longest Journey</i>: Chapter 7, the Stone Altar, Can you give me a picture of the solution?</li>
	 * <li>Illustrative UHS: <i>Deja Vu I</i>: Sewer, The Map</li>
	 * <li>Illustrative UHS: <i>Arcania: Gothic 4</i>: Jungle and Mountains, Jungle Map</li>
	 * <li>Illustrative UHS: <i>Nancy Drew 4: TitRT</i>: Shed, How can I solve, Finished Leaf Puzzle</li>
	 * <li>Illustrative UHS: <i>Elder Scrolls III: Tribunal</i>: Maps, Norenen-Dur, Details</li>
	 * <li>Illustrative UHS: <i>Dungeon Siege</i>: Chapter 6, Maps, Fire Village (Incentive'd HyperImage image)</li>
	 * </ul></p>
	 *
	 * @param context  the parse context
	 * @param currentNode  an existing node to add children to
	 * @param startIndex  the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 * @see net.vhati.openuhs.core.UHSHotSpotNode
	 */
	public int parseHyperImageNode( UHSParseContext context, UHSNode currentNode, int startIndex ) {
		int index = startIndex;
		String[] tokens = null;
		long offset = 0;
		int length = 0;
		byte[] tmpBytes = null;
		int x = 0;
		int y = 0;
		String tmp = context.getLine( index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		String mainType = null;  // It has to be one of these or the method wouldn't have been called.
		if ( tmp.indexOf( "hyperpng" ) != -1 ) {
			mainType = "Hyperpng";
		}
		else if ( tmp.indexOf( "gifa" ) != -1 ) {
			mainType = "Hypergif";
		}

		String mainTitle = context.getLine( index );
		index++;
		innerCount--;

		int mainImageIndex = index;  // Yeah, the number triplet gets an id. Weird.
		tokens = context.getLine( index ).split( " " );
		index++;
		innerCount--;
		if ( tokens.length != 3 ) {
			logger.error( "Unable to parse HyperImage's offset/length (last parsed line: {})", context.getLastParsedLineNumber() );
			return innerCount+3;
		}
		// Skip dummy zeroes.
		offset = Long.parseLong( tokens[1] ) - context.getBinaryHunkOffset();
		length = Integer.parseInt( tokens[2] );

		tmpBytes = context.readBinaryHunk( offset, length );
		if ( tmpBytes == null ) {
			logger.error( "Could not read referenced raw bytes (last parsed line: {})", context.getLastParsedLineNumber() );
		}

		UHSHotSpotNode hotspotNode = new UHSHotSpotNode( mainType );
			hotspotNode.setRawStringContent( mainTitle );
			hotspotNode.setRawImageContent( tmpBytes );
			hotspotNode.setId( startIndex );  // TODO: id = mainImageIndex and/or startIndex?
			currentNode.addChild( hotspotNode );
			context.getRootNode().addLink( hotspotNode );

		for ( int j=0; j < innerCount; ) {
			// Nested ids in HyperImage point to the zone line. Node type is at (zone line)+1.
			int nestedIndex = index+j;

			tokens = context.getLine( index+j ).split( " " );
			j++;
			if ( tokens.length != 4 ) {
				logger.error( "Unable to parse HyperImage's zone coordinates (last parsed line: {})", context.getLastParsedLineNumber() );
				return innerCount+3;
			}
			int zoneX1 = Integer.parseInt( tokens[0] )-1;
			int zoneY1 = Integer.parseInt( tokens[1] )-1;
			int zoneX2 = Integer.parseInt( tokens[2] )-1;
			int zoneY2 = Integer.parseInt( tokens[3] )-1;

			tmp = context.getLine( index+j );
			j++;
			if ( tmp.matches( "[0-9]+ [A-Za-z]+$" ) ) {
				int innerInnerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;
				if ( tmp.endsWith( " overlay" ) ) {
					String overlayTitle = context.getLine( index+j );
					j++;
					tokens = ( context.getLine( index+j ) ).split( " " );
					j++;

					if ( tokens.length != 5 ) {
						logger.error( "Unable to parse Overlay's offset/length/x/y (last parsed line: {})", context.getLastParsedLineNumber() );
						return innerCount+3;
					}
					// Skip dummy zeroes.
					offset = Long.parseLong( tokens[1] ) - context.getBinaryHunkOffset();
					length = Integer.parseInt( tokens[2] );
					int posX = Integer.parseInt( tokens[3] )-1;
					int posY = Integer.parseInt( tokens[4] )-1;

					tmpBytes = context.readBinaryHunk( offset, length );
					if ( tmpBytes == null ) {
						logger.error( "Could not read referenced raw bytes (last parsed line: {})", context.getLastParsedLineNumber() );
					}
					UHSImageNode overlayNode = new UHSImageNode( "Overlay" );
						overlayNode.setRawStringContent( overlayTitle );
						overlayNode.setRawImageContent( tmpBytes );
						overlayNode.setId( nestedIndex );
						hotspotNode.addChild( overlayNode );
						context.getRootNode().addLink( overlayNode );
						hotspotNode.setSpot( overlayNode, new HotSpot( zoneX1, zoneY1, zoneX2-zoneX1, zoneY2-zoneY1, posX, posY ) );
				}
				else if ( tmp.endsWith( " link" ) || tmp.endsWith( " hyperpng" ) || tmp.endsWith( " text" ) || tmp.endsWith( " hint" ) ) {
					int childrenBefore = hotspotNode.getChildCount();
					j--;  // Back up to the hunk type line.
					j += buildNodes( context, hotspotNode, index+j );
					if ( hotspotNode.getChildCount() == childrenBefore+1 ) {
						UHSNode newNode = hotspotNode.getChild( hotspotNode.getChildCount()-1 );
						newNode.shiftId( -1, context.getRootNode() );
						// It might be weird to recurse HyperImage id shifts.
						if ( tmp.endsWith( "hyperpng" ) && newNode.getChildCount() != 1 ) {
							logger.error( "Nested HyperImage has an unexpected child count (last parsed line: {})", context.getLastParsedLineNumber() );
						}
						hotspotNode.setSpot( newNode, new HotSpot( zoneX1, zoneY1, zoneX2-zoneX1, zoneY2-zoneY1, -1, -1 ) );
					}
					else {
						logger.error( "Failed to add nested hunk (last parsed line: {})", context.getLastParsedLineNumber() );
					}
				}
				else {
					logger.error( "Unexpected hunk in HyperImage: {} (last parsed line {})", tmp, context.getLastParsedLineNumber() );
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
	 * <blockquote><pre>
	 * {@code
	 * # sound
	 * title
	 * 000000 offset length
	 * }
	 * </pre></blockquote>
	 *
	 * <p>Offset is counted from the beginning of the file.</p>
	 * <p>Offset and length are zero-padded to 6 or 7 digits.</p>
	 *
	 * <p><ul>
	 * <li>Illustrative UHS: <i>Tex Murphy: Overseer</i>: Day Two, Bosworth Clark's Lab, How do I operate that keypad?</li>
	 * </ul></p>
	 *
	 * @param context  the parse context
	 * @param currentNode  an existing node to add children to
	 * @param startIndex  the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 * @see #decryptTextHunk(CharSequence, int[])
	 */
	public int parseSoundNode( UHSParseContext context, UHSNode currentNode, int startIndex ) {
		int index = startIndex;
		String tmp = context.getLine( index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		tmp ="";
		UHSAudioNode soundNode = new UHSAudioNode( "Sound" );
			soundNode.setRawStringContent( context.getLine( index ) );
			soundNode.setStringContentDecorator( new Version9xTitleDecorator() );
			soundNode.setId( startIndex );
			currentNode.addChild( soundNode );
			context.getRootNode().addLink( soundNode );
		index++;

		tmp = context.getLine( index );
		index++;
		long offset = Long.parseLong( tmp.substring( tmp.indexOf( " " )+1, tmp.lastIndexOf( " " ) ) ) - context.getBinaryHunkOffset();
		int length = Integer.parseInt( tmp.substring( tmp.lastIndexOf( " " )+1, tmp.length() ) );

		byte[] tmpBytes = context.readBinaryHunk( offset, length );
		if ( tmpBytes == null ) {
			// This error would be at index-1, if not for context.getLine()'s memory.
			logger.error( "Could not read referenced raw bytes (last parsed line: {})", context.getLastParsedLineNumber() );
		}

		soundNode.setRawAudioContent( tmpBytes );

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
	 * @param context  the parse context
	 * @param currentNode  an existing node to add children to
	 * @param startIndex  the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseBlankNode( UHSParseContext context, UHSNode currentNode, int startIndex ) {
		int index = startIndex;
		String tmp = context.getLine( index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode newNode = new UHSNode( "Blank" );
			newNode.setRawStringContent( "^^^" );
			newNode.setId( startIndex );
			currentNode.addChild( newNode );
			context.getRootNode().addLink( newNode );
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
	 * @param context  the parse context
	 * @param currentNode  an existing node to add children to
	 * @param startIndex  the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseVersionNode( UHSParseContext context, UHSNode currentNode, int startIndex ) {
		String breakChar = "^break^";

		int index = startIndex;
		String tmp = context.getLine( index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode versionNode = new UHSNode( "Version" );
			versionNode.setRawStringContent( "Version: "+ context.getLine( index ) );
			versionNode.setStringContentDecorator( new Version9xTitleDecorator() );
			versionNode.setId( startIndex );
			currentNode.addChild( versionNode );
			context.getRootNode().addLink( versionNode );
		index++;
		innerCount--;

		StringBuffer tmpContent = new StringBuffer();
		UHSNode newNode = new UHSNode( "VersionData" );

		for ( int j=0; j < innerCount; j++ ) {
			if ( tmpContent.length() > 0 ) tmpContent.append( breakChar );
			tmpContent.append( context.getLine( index+j ) );
		}
		newNode.setRawStringContent( tmpContent.toString() );
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
	 * @param context  the parse context
	 * @param currentNode  an existing node to add children to
	 * @param startIndex  the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseInfoNode( UHSParseContext context, UHSNode currentNode, int startIndex ) {
		String breakChar = "^break^";

		int index = startIndex;
		String tmp = context.getLine( index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode infoNode = new UHSNode( "Info" );
			infoNode.setRawStringContent( "Info: "+ context.getLine( index ) );
			infoNode.setStringContentDecorator( new Version9xTitleDecorator() );
			infoNode.setId( startIndex );
			currentNode.addChild( infoNode );
			context.getRootNode().addLink( infoNode );
		index++;
		innerCount--;

		if ( innerCount > 0 ) {
			StringBuffer tmpContent = new StringBuffer();

			UHSNode newNode = new UHSNode( "InfoData" );

			for ( int j=0; j < innerCount; j++ ) {
				tmp = context.getLine( index+j );
				if ( tmpContent.length() > 0 ) tmpContent.append( breakChar );
				tmpContent.append( tmp );
			}

			newNode.setRawStringContent( tmpContent.toString() );
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
	 * @param context  the parse context
	 * @param currentNode  an existing node to add children to
	 * @param startIndex  the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseIncentiveNode( UHSParseContext context, UHSNode currentNode, int startIndex ) {
		int index = startIndex;
		String tmp = context.getLine( index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode incentiveNode = new UHSNode( "Incentive" );
			incentiveNode.setRawStringContent( "Incentive: "+ context.getLine( index ) );
			incentiveNode.setId( startIndex );
			currentNode.addChild( incentiveNode );
			context.getRootNode().addLink( incentiveNode );
		index++;
		innerCount--;

		if ( innerCount > 0 ) {
			tmp = decryptNestString( context.getLine( index ), context.getKey() );
			index++;
			UHSNode newNode = new UHSNode( "IncentiveData" );
				newNode.setRawStringContent( tmp );
				incentiveNode.addChild( newNode );

			applyRestrictions( context.getRootNode(), tmp );
		}

		return index-startIndex;
	}

	/**
	 * Sets access restrictions on UHSNodes based on ids in an incentive UHSNode.
	 *
	 * @param rootNode  an existing root node
	 * @param incentiveString  a space-separated string of numbers, each with 'Z' or 'A' appended
	 * @see #parseIncentiveNode(UHSParseContext, UHSNode, int)
	 */
	public void applyRestrictions( UHSRootNode rootNode, String incentiveString ) {
		String[] tokens = incentiveString.split( " " );
		for ( int i=0; i < tokens.length; i++ ) {
			if ( tokens[i].matches( "[0-9]+[AZ]" ) ) {
				int tmpId = Integer.parseInt( tokens[i].substring( 0, tokens[i].length()-1 ) );
				UHSNode tmpNode = rootNode.getNodeByLinkId( tmpId );
				if ( tmpNode != null ) {
					if ( tokens[i].endsWith( "Z" ) ) {
						tmpNode.setRestriction( UHSNode.RESTRICT_NAG );
					} else if ( tokens[i].endsWith("A") ) {
						tmpNode.setRestriction( UHSNode.RESTRICT_REGONLY );
					}
				}
				else {
					logger.warn( "Incentive string referenced an unknown node id ({}): {}", tmpId, incentiveString );
				}
			}
		}
	}


	/**
	 * Generates a stand-in UHSNode for an unknown hunk.
	 *
	 * @param context  the parse context
	 * @param currentNode  an existing node to add children to
	 * @param startIndex  the line number to start parsing from
	 * @return the number of lines consumed from the file in parsing children
	 */
	public int parseUnknownNode( UHSParseContext context, UHSNode currentNode, int startIndex ) {
		int index = startIndex;
		String tmp = context.getLine( index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		logger.warn( "Unknown hunk: {} (last parsed line: {})", tmp, context.getLastParsedLineNumber() );

		UHSNode newNode = new UHSNode( "Unknown" );
			newNode.setRawStringContent( "^UNKNOWN HUNK^" );
			currentNode.addChild( newNode );

		index += innerCount;
		return index-startIndex;
	}


	/**
	 * Calculates the security checksum of a UHS file.
	 *
	 * <p>It's a CRC16 of the entire file (read as unsigned bytes),
	 * minus the last two bytes.</p>
	 */
	public long calcChecksum( File f ) throws IOException {
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
			throw new IOException( "Could not calculate checksum", e );
		}
		finally {
			try {if ( raf != null ) raf.close();} catch ( IOException e ) {}
		}

		return result;
	}

	/**
	 * Reads the security checksum stored in a UHS file.
	 */
	private long readChecksum( File f ) throws IOException {
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
			throw new IOException( String.format("Couldn't read stored checksum from: ", f.getAbsolutePath()) );
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
	 * @param a  any byte array that includes the end of a UHS file
	 */
	public long readChecksum( byte[] a ) {
		if ( a.length < 2 ) return -1;

		// Strip the signedness, if any
		int leastByte = a[a.length-2] & 0xFF;
		int mostByte = a[a.length-1] & 0xFF;

		return ( mostByte << 8 | leastByte );
	}
}
