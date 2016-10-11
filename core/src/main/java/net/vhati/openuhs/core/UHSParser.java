package net.vhati.openuhs.core;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.ByteReference;
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
import net.vhati.openuhs.core.markup.Version88CreditsDecorator;
import net.vhati.openuhs.core.markup.Version9xCommentDecorator;
import net.vhati.openuhs.core.markup.Version9xCreditDecorator;
import net.vhati.openuhs.core.markup.Version9xHintDecorator;
import net.vhati.openuhs.core.markup.Version9xIncentiveDecorator;
import net.vhati.openuhs.core.markup.Version9xInfoDecorator;
import net.vhati.openuhs.core.markup.Version9xStringDecorator;
import net.vhati.openuhs.core.markup.Version9xTextDecorator;
import net.vhati.openuhs.core.markup.Version9xTitleDecorator;
import net.vhati.openuhs.core.markup.Version9xVersionDecorator;


/**
 * A parser for 88a format and 9x format UHS files.
 */
public class UHSParser {

	private final Logger logger = LoggerFactory.getLogger( UHSParser.class );

	private boolean binaryDeferred = false;
	private boolean force88a = false;


	public UHSParser() {
	}


	/**
	 * Sets whether to preload binary hunk segments into arrays or defer reads until needed.
	 *
	 * <p>This will be passed along to UHSParseContexts as they're created during parsing.</p>
	 *
	 * @param b  true to defer, false to preload (default is false)
	 * @see net.vhati.openuhs.core.UHSParseContext#setBinaryDeferred(boolean)
	 */
	public void setBinaryDeferred( boolean b ) {
		binaryDeferred = b;
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
	 * Reads bytes into a parse context using a RandomAccessFile.
	 *
	 * <p>This method is unbuffered and thus inefficient. It is also somewhat
	 * reckless in decoding bytes into characters. On the plus side, the code
	 * is uncomplicated.</p>
	 *
	 * @param context  the parse context
	 * @see #readBytesUsingStream(File)
	 */
	private void readBytesUsingRAF( UHSParseContext context ) throws IOException, UHSParseException {
		int index = -1;  // Increment before reads, and this will be the last read 0-based index.
		String tmp = "";

		List<String> allLines = new ArrayList<String>();
		long binHunkOffset = -1;
		byte[] binHunk = new byte[0];

		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile( context.getFile(), "r" );

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
				if ( tmp.indexOf( "\0" ) >= 0 ) {
					logger.warn( "Pruned extraneous null bytes from text" );
					tmp = tmp.replaceAll( "\0", "" );
				}
				allLines.add( tmp );
			}
			if ( skippedNulls > 0 ) logger.warn( "Skipped {} extraneous null bytes", skippedNulls );

			binHunkOffset = raf.getChannel().position();  // Either after 0x1a, or EOF.
			long binSize = raf.length() - binHunkOffset;
			if ( binSize > 0 && binSize <= Integer.MAX_VALUE ) {
				binHunk = new byte[(int)binSize];
				raf.readFully( binHunk );
			}
			else {
				binHunkOffset = -1;
			}
		}
		finally {
			try {if ( raf != null ) raf.close();} catch ( IOException e ) {}
		}

		context.setAllLines( allLines );
		context.setBinaryHunk( binHunk );
		context.setBinaryHunkOffset( binHunkOffset );
		if ( binHunkOffset >= 0 ) context.setBinaryHunkLength( context.getFile().length() - binHunkOffset );
	}

	/**
	 * Cuts CRLF terminated strings out of a StringBuilder.
	 *
	 * <p>Afterward, the StringBuilder will contain only the remaining
	 * unterminated characters.</p>
	 *
	 * @param buf  a source buffer to remove substrings from
	 * @param results  a list to add collected strings into
	 */
	private void carveLines( StringBuilder buf, List<String> results ) {
		String sep = "\r\n";
		int sepLen = sep.length();
		int lastBreak = -sep.length();
		int nextBreak = -sep.length();
		while ( (nextBreak=buf.indexOf( sep, (lastBreak + sepLen) )) >= 0 ) {
			results.add( buf.substring( (lastBreak + sepLen), nextBreak ) );
			lastBreak = nextBreak;
		}
		if ( lastBreak > 0 ) buf.delete( 0, (lastBreak + sepLen) );
	}

	/**
	 * Reads bytes into a parse context using an InputStream, a CharsetDecoder, and buffers.
	 *
	 * <p>Bytes are read from the file a little at a time. Any bytes prior to
	 * the 9x format binary indicator byte (0x1a), if present, are decoded to
	 * ascii characters. Everything after that is collected as bytes.</p>
	 *
	 * <p>This method is elborate, but in an informal benchmark, it was over
	 * six times faster than RandomAccessFile at parsing a directory of every
	 * UHS file.</p>
	 *
	 * @param context  the parse context
	 * @see #readBytesUsingRAF(File)
	 */
	private void readBytesUsingStream( UHSParseContext context ) throws IOException, UHSParseException {
		CharsetDecoder decoder = Charset.forName( "US-ASCII" ).newDecoder();
		decoder.onMalformedInput( CodingErrorAction.REPORT );
		decoder.onUnmappableCharacter( CodingErrorAction.REPORT );

		boolean binWanted = !context.isBinaryDeferred();
		ByteBuffer bb = ByteBuffer.allocate( 8192 );
		CharBuffer cb = CharBuffer.allocate( 8192 );
		StringBuilder builder = new StringBuilder();
		List<String> carvedLines = new ArrayList<String>();
		ByteArrayOutputStream binStream = new ByteArrayOutputStream();
		boolean binFound = false;
		boolean eofFound = false;
		CoderResult decodeResult = null;
		int binPos = -1;  // Intra-buffer position of binary hunk.
		long binHunkOffset = -1;  // Offset from beginning of file to the byte after 0x1a.
		boolean prunedNulls = false;

		FileInputStream fin = null;
		try {
			fin = new FileInputStream( context.getFile() );
			FileChannel fChan = fin.getChannel();
			int count;
			while ( !binFound && !eofFound ) {
				int readStartedAt = bb.position();  // Possibly appending to leftover bytes from last loop.
				count = fChan.read( bb );
				eofFound = ( count == -1 );
				bb.flip();  // Set limit at current pos, set new pos to 0.

				// Peek ahead for binary.
				while ( !binFound && bb.hasRemaining() ) {
					if ( bb.get() == 0x1a ) {
						binPos = bb.position();
						binHunkOffset = (fChan.position() - count) + (binPos - readStartedAt);
						binFound = true;

						//logger.debug( "Binary hunk found: {}", binHunkOffset );

						while ( binWanted && bb.hasRemaining() ) {  // Dump the binary part.
							binStream.write( bb.get() );
						}
						bb.limit( binPos );            // Prepare to decode just the text part.
					}
				}
				bb.rewind();  // Back to 0, where we were after flipping.

				cb.clear();
				decodeResult = decoder.decode( bb, cb, (binFound || eofFound) );

				if ( decodeResult.isError() ) decodeResult.throwException();  // Bad byte.
				cb.flip();
				builder.append( cb );
				for ( int n; (n=builder.lastIndexOf( "\0" )) >= 0; ) {
					prunedNulls = true;
					builder.deleteCharAt( n );
				}
				carveLines( builder, carvedLines );

				while ( decodeResult.isOverflow() ) {  // cb filled.
					cb.clear();
					decodeResult = decoder.decode( bb, cb, (binFound || eofFound) );

					if ( decodeResult.isError() ) decodeResult.throwException();
					cb.flip();
					builder.append( cb );
					for ( int n; (n=builder.lastIndexOf( "\0" )) >= 0; ) {
						prunedNulls = true;
						builder.deleteCharAt( n );
					}
					carveLines( builder, carvedLines );
				}

				// Slide any leftover bytes to the beginning,
				// with position set to just after them, so the next read appends.
				if ( !binFound ) bb.compact();
			}

			//logger.debug( "File channel finished reading text" );

			// Collect any characters lingering in the decoder.
			cb.clear();
			while ( (decodeResult = decoder.flush( cb )).isOverflow() ) {
				cb.flip();
				builder.append( cb );
				for ( int n; (n=builder.lastIndexOf( "\0" )) >= 0; ) {
					prunedNulls = true;
					builder.deleteCharAt( n );
				}
				carveLines( builder, carvedLines );

				cb.clear();
			}

			// Collect the final unterminated line, if any.
			if ( builder.length() > 0 ) carvedLines.add( builder.toString() );

			if ( prunedNulls ) logger.warn( "Pruned extraneous null bytes from text" );

			//logger.debug( "Decoder flushed, lines: {}, final line: {}", carvedLines.size(), carvedLines.get(carvedLines.size()-1) );

			// Collect the rest of the binary lingering in the file.
			if ( binWanted && binFound ) {
				while ( !eofFound ) {
					bb.clear();
					eofFound = ( (count=fChan.read( bb )) == -1 );
					bb.flip();

					while ( bb.hasRemaining() ) {
						binStream.write( bb.get() );
					}
				}
			}

			//logger.debug( "File channel finished reading binary, binHunk length: {}", binStream.size() );

			//for ( int i=0; i < 20; i++ ) logger.debug( "Line {}: {}", i, carvedLines.get( i ) );

			context.setAllLines( carvedLines );
			if ( binWanted ) context.setBinaryHunk( binStream.toByteArray() );
			context.setBinaryHunkOffset( binHunkOffset );
			if ( binHunkOffset >= 0 ) context.setBinaryHunkLength( context.getFile().length() - binHunkOffset );
		}
		finally {
			try {if ( fin != null ) fin.close();} catch ( IOException e ) {}
		}
	}


	/**
	 * Reads a UHS file into a List of text lines and an array of bytes (for binary content).
	 * Then calls an appropriate parser to construct a UHSRootNode and a tree of UHSNodes.
	 *
	 * <p>This is likely the only method you'll need.</p>
	 *
	 * @param f  a file to read
	 * @return the root of a tree of nodes representing the hint file
	 * @see #parse88Format(UHSParseContext)
	 * @see #parse9xFormat(UHSParseContext)
	 */
	public UHSRootNode parseFile( File f ) throws IOException, UHSParseException {
		int index = 0;  // Increment after getting lines, and this will be the pending 0-based index.

		UHSParseContext context = new UHSParseContext();
		context.setBinaryDeferred( binaryDeferred );
		context.setFile( f );
		readBytesUsingStream( context );

		UHSRootNode rootNode = null;
		try {
			index += parse88Format( context );
			rootNode = context.getRootNode();

			// In 88a files, this would e the end.
			// In 9x files, index should be right after "** END OF 88A FORMAT **".

			if ( !force88a && rootNode.isLegacy() ) {
				// That was a fake 88a format message, now comes the 9x format.
				UHSRootNode legacyRootNode = rootNode;

				context.setLineFudge( index-1 );  // Ignore all lines so far. Treat that END line as 0.

				rootNode = parse9xFormat( context );
				rootNode.setLegacyRootNode( legacyRootNode );

				int storedSum = context.readStoredChecksumValue();
				int calcSum = calcChecksum( f );

				if ( storedSum != calcSum ) {
					logger.warn( "Calculated CRC differs from CRC stored in file: {} vs {} (off by: {})", calcSum, storedSum, (storedSum - calcSum) );
				}
			}
		}
		catch ( ArrayIndexOutOfBoundsException e ) {
			throw new UHSParseException( String.format( "Parsing failed: %s", e.getMessage() ), e );
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
	 * credits sentences (several lines)
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
				rootNode.setRawStringContent( "Root" );
			context.setRootNode( rootNode );

			UHSNode masterSubjectNode = new UHSNode( "Subject" );
				masterSubjectNode.setRawStringContent( title );
				rootNode.addChild( masterSubjectNode );

			// Reset, ignoring 3 of those 4 lines, to make the next line (first subject) be 1.
			fudged = index-1;
			context.setLineFudge( context.getLineFudge() + fudged );
			index = 1;

			boolean firstSubject = true;
			int questionSectionStart = -1;

			List<UHSNode> subjectNodes = new ArrayList<UHSNode>();
			List<Integer> subjectsFirstQuestion = new ArrayList<Integer>();

			// Collect all subjects at once, while adding them to the master subject node along the way.
			while ( firstSubject || index < questionSectionStart ) {
				UHSNode currentSubject = new UHSNode( "Subject" );
					currentSubject.setRawStringContent( decryptString( context.getLine( index++ ) ) );
					masterSubjectNode.addChild( currentSubject );
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

			// Build auxiliary nodes.
			UHSNode fauxVersionNode = new UHSNode( "Version" );
				fauxVersionNode.setRawStringContent( "88a" );
				rootNode.addChild( fauxVersionNode );

			UHSNode fauxVersionDataNode = new UHSNode( "VersionData" );
				fauxVersionDataNode.setRawStringContent( "This version info was added by OpenUHS during parsing because the 88a format does not report it." );
				fauxVersionNode.addChild( fauxVersionDataNode );

			UHSNode creditsNode = new UHSNode( "Credits" );
				creditsNode.setRawStringContent( "-" );
				rootNode.addChild( creditsNode );

			String breakChar = "^break^";
			StringBuilder creditsDataBuf = new StringBuilder();
			while ( context.hasLine( index ) ) {
				String tmp = context.getLine( index++ );

				if ( "** END OF 88A FORMAT **".equals( tmp ) ) {
					rootNode.setLegacy( true );
					break;
				}

				if ( creditsDataBuf.length() > 0 ) creditsDataBuf.append( breakChar );
				creditsDataBuf.append( tmp );
			}
			UHSNode creditsDataNode = new UHSNode( "CreditsData" );
				creditsDataNode.setRawStringContent( creditsDataBuf.toString() );
				creditsDataNode.setStringContentDecorator( new Version88CreditsDecorator() );
				creditsNode.addChild( creditsDataNode );

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
	 * <p>The root node will normally contain up to four children.
	 * <ul>
	 * <li>A 'subject', containing all the subjects, hints, etc., that users
	 * care about. Official readers will begin inside this node.</li>
	 * <li>A 'version', mentioning the UHS compiler that made the file.</li>
	 * <li>An 'info', mentioning the author, publisher, etc.</li>
	 * <li>And an 'incentive', listing nodes to show/block if the reader is
	 * unregistered.</li>
	 * </ul></p>
	 *
	 * <p>Official readers merge the content of the auxiliary nodes to appear
	 * together as "Credits and File Information".</p>
	 *
	 * @param context  the parse context
	 * @return the root of a tree of nodes
	 * @see #parseNode(UHSParseContext, UHSNode, int)
	 * @see #calcChecksum(File)
	 */
	public UHSRootNode parse9xFormat( UHSParseContext context ) throws UHSParseException {
		try {
			UHSRootNode rootNode = new UHSRootNode();
				rootNode.setRawStringContent( "Root" );
			context.setRootNode( rootNode );

			String title = context.getLine( 2 ); // This is the title of the master subject node
			int[] key = generate9xKey( title );
			context.setEncryptionKey( key );

			int index = 1;
			// Build the master subject node.
			index += parseNode( context, rootNode, index );

			if ( rootNode.getChildCount() == 0 ) {
				throw new UHSParseException( String.format( "No nodes were parsed!? Started from this line: %s", context.getLine( 1 ) ) );
			}

			// Build auxiliary nodes: version, info, incentive.
			while ( context.hasLine( index ) ) {
				index += parseNode( context, rootNode, index );
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
	public int parseNode( UHSParseContext context, UHSNode currentNode, int startIndex ) {
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
			j += parseNode( context, newNode, index+j );
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

				j += parseNode( context, hintNode, index+j+1 );

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
				tmpContent.append( decryptNestString( context.getLine( index+j ), context.getEncryptionKey() ) );
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
	 * <p>Unlike the 88a format's credits, 9x format credit hunks appear under
	 * a Subject. They are not auxiliary nodes.</p>
	 *
	 * <p><ul>
	 * <li>Illustrative UHS: <i>Alone in the Dark</i>: About this UHS File, Who wrote this file?</li>
	 * </ul></p>
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

		ByteReference textRef = context.readBinaryHunk( offset, length );
		try {
			List<String> decodedLines = decodeByteReference( textRef );
			for ( String line : decodedLines ) {
				if ( tmpContent.length() > 0 ) tmpContent.append( breakChar );
				tmpContent.append( decryptTextHunk( line, context.getEncryptionKey() ) );
			}

		}
		catch ( IOException e ) {
			// This error would be at index-1, if not for context.getLine()'s memory.
			logger.error( "Could not read referenced raw bytes (last parsed line: {}): e", context.getLastParsedLineNumber(), e );
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
		// parseNode( context, newNode, targetIndex );

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
	 *
	 * <p>Offset is counted from the beginning of the file.</p>
	 *
	 * <p>Offset and length are zero-padded to 6 or 7 digits.</p>
	 *
	 * <p>The zone (x, y, x+w, y+h) and overlay (x, y) numbers are zero-padded
	 * to 4 digits. Both x's and y's seem to be 0-based.</p>
	 *
	 * <p>A gifa has the same structure, but might not officially contain
	 * regions.</p>
	 *
	 * <p>The HyperImage itself gets an id, as usual. The main image also gets
	 * an id, which may be referenced by an Incentive chunk. There are no
	 * examples in the wild of a restricted main image inside an unrestricted
	 * HyperImage chunk. Testing with an edited file indicated that official
	 * readers ignore main image restrictions, and that this is likely an
	 * error the authors made.</p>
	 *
	 * </p>Line ids of nodes nested within a HyperImage are skewed because
	 * their initial line is the region coords, and the node type comes
	 * second.</p>
	 *
	 * <p>HyperImages can contain additional HyperImage nodes that have
	 * their own children. Shifting a nested HyperImage should not recurse
	 * into its children.</p>
	 *
	 * <p><ul>
	 * <li>Illustrative UHS: <i>Arcania: Gothic 4</i>: Jungle and Mountains, Jungle Map</li>
	 * <li>Illustrative UHS: <i>Deja Vu I</i>: Sewer, The Map</li>
	 * <li>Illustrative UHS: <i>Dungeon Siege</i>: Chapter 6, Maps, Fire Village (Incentive'd HyperImage and main image)</li>
	 * <li>Illustrative UHS: <i>Elder Scrolls III: Tribunal</i>: Maps, Norenen-Dur, Details</li>
	 * <li>Illustrative UHS: <i>Nancy Drew 4: TitRT</i>: Shed, How can I solve, Finished Leaf Puzzle (Incentive'd HyperImage and main image)</li>
	 * <li>Illustrative UHS: <i>The Longest Journey</i>: Chapter 7, the Stone Altar, Can you give me a picture of the solution? (Overlay and Link)</li>
	 * <li>Illustrative UHS: <i>Trinity</i>: Trinity Site, Map of the Trinity Site, Map Key (Link targeting a nested HyperImage erroneously points to the node type line, instead of shifting to the zone line)</li>
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

		ByteReference mainImageRef = context.readBinaryHunk( offset, length );

		UHSHotSpotNode hotspotNode = new UHSHotSpotNode( mainType );
			hotspotNode.setRawStringContent( mainTitle );
			hotspotNode.setRawImageContent( mainImageRef );
			hotspotNode.setId( startIndex );  // TODO: The id can be mainImageIndex and/or startIndex.
			currentNode.addChild( hotspotNode );
			context.getRootNode().addLink( hotspotNode );
			context.getRootNode().addLink( hotspotNode, mainImageIndex );  // TODO: Ugly. See UHSRootNode's javadoc.

		for ( int j=0; j < innerCount; ) {
			// Nested ids in HyperImage point to the zone line. Node type is at (zone line)+1.
			int nestedIndex = index+j;

			tokens = context.getLine( index+j ).split( " " );
			j++;
			if ( tokens.length != 4 ) {
				logger.error( "Unable to parse HyperImage's zone coordinates (last parsed line: {})", context.getLastParsedLineNumber() );
				return innerCount+3;
			}
			int zoneX1 = Integer.parseInt( tokens[0] );
			int zoneY1 = Integer.parseInt( tokens[1] );
			int zoneX2 = Integer.parseInt( tokens[2] );
			int zoneY2 = Integer.parseInt( tokens[3] );

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
					int posX = Integer.parseInt( tokens[3] );
					int posY = Integer.parseInt( tokens[4] );

					ByteReference overlayImageRef = context.readBinaryHunk( offset, length );

					UHSImageNode overlayNode = new UHSImageNode( "Overlay" );
						overlayNode.setRawStringContent( overlayTitle );
						overlayNode.setRawImageContent( overlayImageRef );
						overlayNode.setId( nestedIndex );
						hotspotNode.addChild( overlayNode );
						context.getRootNode().addLink( overlayNode );
						hotspotNode.setSpot( overlayNode, new HotSpot( zoneX1, zoneY1, zoneX2-zoneX1, zoneY2-zoneY1, posX, posY ) );
				}
				else if ( tmp.endsWith( " link" ) || tmp.endsWith( " hyperpng" ) || tmp.endsWith( " text" ) || tmp.endsWith( " hint" ) ) {
					int childrenBefore = hotspotNode.getChildCount();
					j--;  // Back up to the hunk type line.
					j += parseNode( context, hotspotNode, index+j );
					if ( hotspotNode.getChildCount() == childrenBefore+1 ) {
						UHSNode newNode = hotspotNode.getChild( hotspotNode.getChildCount()-1 );
						newNode.shiftId( -1, context.getRootNode() );
						// Do not recurse HyperImage id shifts.

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

		ByteReference audioRef = context.readBinaryHunk( offset, length );

		soundNode.setRawAudioContent( audioRef );

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
	 * <p>The title is the version, like "96a". The sentences describe the compiler.</p>
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
			versionNode.setRawStringContent( context.getLine( index ) );
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
			infoNode.setRawStringContent( context.getLine( index ) );
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
	 * <p>The list is a space-separated string of numbers, each with 'Z' or
	 * 'A' appended. 'Z' means the node is a nag message that should be hidden
	 * from registered readers. 'A' means only registered readers can see the
	 * node's children or link target. In some files, there is no list, and
	 * this node only occupies 2 lines.</p>
	 *
	 * <blockquote><pre>
	 * {@code
	 * # incentive
	 * -
	 * ID list (encrypted)
	 * More ids
	 * More ids
	 * }
	 * </pre></blockquote>
	 *
	 * <p>Upon parsing this node, all referenced ids will be looked up by
	 * calling getLink(id) on the rootNode. The nodes will have their
	 * restriction attribute set, but it is up to readers to actually honor
	 * them.</p>
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
		String breakChar = "^break^";

		int index = startIndex;
		String tmp = context.getLine( index );
		index++;
		int innerCount = Integer.parseInt( tmp.substring( 0, tmp.indexOf( " " ) ) ) - 1;

		UHSNode incentiveNode = new UHSNode( "Incentive" );
			incentiveNode.setRawStringContent( context.getLine( index ) );
			incentiveNode.setId( startIndex );
			currentNode.addChild( incentiveNode );
			context.getRootNode().addLink( incentiveNode );
		index++;
		innerCount--;

		if ( innerCount > 0 ) {
			StringBuffer tmpContent = new StringBuffer();

			UHSNode newNode = new UHSNode( "IncentiveData" );

			for ( int j=0; j < innerCount; j++ ) {
				tmp = decryptNestString( context.getLine( index+j ), context.getEncryptionKey() );
				if ( tmpContent.length() > 0 ) tmpContent.append( breakChar );
				tmpContent.append( tmp );
			}
			newNode.setRawStringContent( tmpContent.toString() );
			newNode.setStringContentDecorator( new Version9xIncentiveDecorator() );
			incentiveNode.addChild( newNode );

			applyRestrictions( context.getRootNode(), newNode.getDecoratedStringContent() );
		}

		index += innerCount;
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
	 * Returns a list of lines, as decoded ascii from the InputStream of a ByteReference.
	 */
	public List<String> decodeByteReference( ByteReference ref ) throws IOException {
		List<String> results = new ArrayList<String>();
		InputStream is = null;
		try {
			CharsetDecoder decoder = Charset.forName( "US-ASCII" ).newDecoder();
			decoder.onMalformedInput( CodingErrorAction.REPORT );
			decoder.onUnmappableCharacter( CodingErrorAction.REPORT );

			is = ref.getInputStream();
			BufferedReader br = new BufferedReader( new InputStreamReader( is, decoder ) );
			String line;
			while ( (line=br.readLine()) != null ) {
				results.add( line );
			}
		}
		finally {
			try {if ( is != null ) is.close();} catch ( IOException e ) {}
		}

		return results;
	}


	/**
	 * Calculates the security checksum of a UHS file.
	 *
	 * <p>It's a CRC16 of the entire file (read as unsigned bytes),
	 * minus the last two bytes.</p>
	 */
	public int calcChecksum( File f ) throws IOException {
		int result = -1;

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
			result = (int)crc.getValue();
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
	private int readChecksum( File f ) throws IOException {
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
				ByteBuffer crcBuf = ByteBuffer.allocate( 2 );
				crcBuf.order( ByteOrder.LITTLE_ENDIAN );
				while ( raf.getChannel().read( crcBuf ) != -1 ) {
					if ( crcBuf.position() == 2 ) {
						crcBuf.flip();
						result = crcBuf.getShort( 0 ) & 0xFFFF;
						break;
					}
				}

			}
		}
		catch ( IOException e ) {
			throw new IOException( String.format( "Couldn't read stored checksum from: ", f.getAbsolutePath() ) );
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
	public int readChecksum( byte[] a ) {
		if ( a.length < 2 ) return -1;

		// Strip the signedness, if any
		int leastByte = a[a.length-2] & 0xFF;
		int mostByte = a[a.length-1] & 0xFF;

		return ( mostByte << 8 | leastByte );
	}
}
