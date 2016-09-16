package net.vhati.openuhs.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.HotSpot;
import net.vhati.openuhs.core.UHSAudioNode;
import net.vhati.openuhs.core.UHSGenerationContext;
import net.vhati.openuhs.core.UHSGenerationException;
import net.vhati.openuhs.core.UHSHotSpotNode;
import net.vhati.openuhs.core.UHSImageNode;
import net.vhati.openuhs.core.UHSNode;


/**
 * UHS file writer (88a only, 9x isn't implemented).
 */
public class UHSWriter {
	private static final int ENCRYPT_NONE = 0;
	private static final int ENCRYPT_HINT = 1;
	private static final int ENCRYPT_NEST = 2;
	private static final int ENCRYPT_TEXT = 3;

	private final Logger logger = LoggerFactory.getLogger( UHSWriter.class );

	private static final Pattern crlfPtn = Pattern.compile( "\r\n" );


	public UHSWriter() {
	}


	/**
	 * Generates an encryption key for version 9x formats.
	 *
	 * @param title  the title of the root node of the UHS document (not the filename, must be ASCII)
	 * @return the key
	 * @see #encryptNestString(CharSequence, int[])
	 * @see #encryptTextHunk(CharSequence, int[])
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
	 * Encrypts the content of standalone 'hint' hunks, and all 88a blocks.
	 *
	 * <p>This is only necessary when saving a file.</p>
	 *
	 * @param input  plaintext
	 * @return the encrypted text
	 */
	public String encryptString( CharSequence input ) {
		StringBuffer tmp = new StringBuffer( input.length() );

		for ( int i=0; i < input.length(); i++ ) {
			int mychar = (int)input.charAt( i );
			if ( mychar < 32 ) {
				// NOP
			}
			else if ( mychar%2 == 0 ) {
				mychar = ( mychar+32 ) / 2;
			}
			else {
				mychar = ( mychar+127 ) / 2;
			}

			tmp.append( (char)mychar );
		}

		return tmp.toString();
	}


	/**
	 * Encrypts the content of 'nesthint' and 'incentive' hunks.
	 *
	 * <p>This is only necessary when saving a file.</p>
	 *
	 * @param input  plaintext
	 * @param key  this file's hint decryption key
	 * @return the encrypted text
	 */
	public String encryptNestString( CharSequence input, int[] key ) {
		StringBuffer tmp = new StringBuffer( input.length() );
		int tmpChar = 0;

		for ( int i=0; i < input.length(); i++ ) {
			int codeoffset = i % key.length;
			tmpChar = input.charAt( i ) + ( key[codeoffset] ^ (i + 40) );
			while ( tmpChar > 127 ) {
				tmpChar -= 96;
			}
			tmp.append( (char)tmpChar );
		}

		return tmp.toString();
	}


	/**
	 * Encrypts the content of 'text' hunks.
	 *
	 * <p>This is only necessary when saving a file.</p>
	 *
	 * @param input  plaintext
	 * @param key  this file's hint decryption key
	 * @return the encrypted text
	 */
	public String encryptTextHunk( CharSequence input, int[] key ) {
		StringBuffer tmp = new StringBuffer( input.length() );
		int tmpChar = 0;

		for ( int i=0; i < input.length(); i++ ) {
			int codeoffset = i % key.length;
			tmpChar = input.charAt( i ) + ( key[codeoffset] ^ (codeoffset + 40) );
			while ( tmpChar > 127 ) {
				tmpChar -= 96;
			}
			tmp.append( (char)tmpChar );
		}

		return tmp.toString();
	}


	/**
	 * Tests whether a UHSRootNode can be expressed in 88a format.
	 *
	 * <p>The rootNode must contain...
	 * <blockquote><pre>
	 * {@code
	 * 0-or-more Subjects, containing:
	 * - 1-or-more Questions, containing:
	 * - - 1-or-more Hints
	 * 1 Credit, containing a text node
	 * }
	 * </pre></blockquote>
	 *
	 * <p>All the nodes' content must be strings.</p>
	 * <p>Newlines are not part of 88a, and will be stripped.</p>
	 * <p>Accented and exotic characters will be asciified.</p>
	 * <p>Markup within text will be stripped.</p>
	 * <p>Version and Blank nodes will be omitted.</p>
	 *
	 * @param rootNode  an existing root node
	 */
	public boolean isValid88Format( UHSRootNode rootNode ) {
		boolean hasCredit = false;

		if ( !rootNode.isGroup() ) return false;

		List<UHSNode> levelOne = rootNode.getChildren();
		for ( int o=0; o < levelOne.size(); o++ ) {
			UHSNode oNode = levelOne.get( o );
			String oType = oNode.getType();
			if ( "Subject".equals( oType ) ) {
				if ( !oNode.isGroup() ) return false;

				// Check Question nodes
				List<UHSNode> levelTwo = oNode.getChildren();
				for ( int t=0; t < levelTwo.size(); t++ ) {
					UHSNode tNode = levelTwo.get(t);
					if ( !tNode.isGroup() ) return false;

					// Check Hint nodes
					List<UHSNode> levelThree = tNode.getChildren();
					for ( int r=0; r < levelThree.size(); r++ ) {
						UHSNode rNode = levelThree.get( r );
						if ( rNode.isGroup() ) return false;
					}
				}
			}
			else if ( "Blank".equals( oType ) ) {
			}
			else if ( "Version".equals( oType ) ) {
			}
			else if ( "Credit".equals( oType ) ) {
				if ( hasCredit ) return false;
				hasCredit = true;
				if ( oNode.getChildCount() != 1 ) return false;
				// Check CreditData node
				if ( oNode.getChild( 0 ).isGroup() ) return false;
			}
			else return false;
		}
		if ( !hasCredit ) return false;
		return true;
	}


	public void write88Format( UHSRootNode rootNode, OutputStream os ) throws IOException, UHSGenerationException {
		CharsetEncoder asciiEncoder = Charset.forName( "US-ASCII" ).newEncoder();
		asciiEncoder.onMalformedInput( CodingErrorAction.REPORT );
		asciiEncoder.onUnmappableCharacter( CodingErrorAction.REPORT );

		Writer textWriter = new OutputStreamWriter( os, asciiEncoder );
		write88Format( rootNode, textWriter );
		textWriter.flush();
	}

	/**
	 * Writes the tree of a UHSRootnode in 88a format.
	 *
	 * <p>TODO: This method is broken and needs to be made decorator-aware.</p>
	 *
	 * <p>Newlines and "^break^" are replaced by " ".</p>
	 *
	 * @param rootNode  an existing root node
	 * @param writer  an existing Writer to receive text (encoding must be US-ASCII)
	 * @see #isValid88Format(UHSRootNode)
	 */
	public void write88Format( UHSRootNode rootNode, Writer writer ) throws IOException, UHSGenerationException {
		if ( !isValid88Format( rootNode ) ) {
			throw new UHSGenerationException( "The node tree cannot be expressed in the 88a format" );
		}
		StringBuffer buf = new StringBuffer();

		String tmp = null;
		List<UHSNode> subjectNodes = rootNode.getChildren( "Subject", UHSNode.class );
		List<UHSNode> questionNodes = new ArrayList<UHSNode>();
		List<UHSNode> hintNodes = new ArrayList<UHSNode>();
		for ( int s=0; s < subjectNodes.size(); s++ ) {
			UHSNode tmpS = subjectNodes.get( s );
			List<UHSNode> tmpQs = tmpS.getChildren();
			questionNodes.addAll( tmpQs );
			for ( int q=0; q < tmpQs.size(); q++ ) {
				hintNodes.addAll( (tmpQs.get( q )).getChildren() );
			}
		}

		int sSize = 2;
		int qSize = 2;
		int hSize = 1;
		int firstQ = 1 + ( sSize * subjectNodes.size() );        // First line is 1. Questions begin after last subject line.
		int firstH = firstQ + ( qSize * questionNodes.size() );  // Hints begin after the last question line.
		int lastH = firstH + ( hSize * hintNodes.size() ) - 1;   // Final line of the final hint, not 1 line after.

		buf.append( "UHS\r\n" );

		String title = "";
		title = escapeText( rootNode, true );
		buf.append( title ).append( "\r\n" );
		buf.append( firstH ).append( "\r\n" );
		buf.append( lastH ).append( "\r\n" );


		for ( int s=0; s < subjectNodes.size(); s++ ) {
			UHSNode tmpSubject = subjectNodes.get( s );
			tmp = escapeText( tmpSubject, true );
				tmp = tmp.replaceAll( "\\^break\\^", " " );  // 88a doesn't support newlines
				tmp = encryptString( tmp );
			int n = ( qSize * questionNodes.indexOf( tmpSubject.getChild( 0 ) ) ) + firstQ;
			buf.append( tmp ).append( "\r\n" ).append( n ).append( "\r\n" );
		}

		for ( int q=0; q < questionNodes.size(); q++ ) {
			UHSNode tmpQuestion = questionNodes.get( q );
			tmp = escapeText( tmpQuestion, true );
				if ( tmp.endsWith( "?" ) ) tmp = tmp.substring( 0, tmp.length()-1 );
				tmp = tmp.replaceAll( "\\^break\\^", " " );  // 88a doesn't support newlines
				tmp = encryptString( tmp );
			int n = ( hSize * hintNodes.indexOf( tmpQuestion.getChild( 0 ) ) ) + firstH;
			buf.append( tmp ).append( "\r\n" ).append( n ).append( "\r\n" );
		}

		for ( int h=0; h < hintNodes.size(); h++ ) {
			UHSNode tmpHint = hintNodes.get( h );
			tmp = escapeText( tmpHint, true );
				tmp = tmp.replaceAll( "\\^break\\^", " " );  // 88a doesn't support newlines
				tmp = encryptString( tmp );
			buf.append( tmp ).append( "\r\n" );
		}

		// TODO: Check for nulls.
		UHSNode creditNode = rootNode.getFirstChild( "Credit", UHSNode.class );
		tmp = creditNode.getFirstChild( "CreditData", UHSNode.class ).getRawStringContent();
		tmp = tmp.replaceAll( "\\^break\\^", "\r\n" );
		if ( tmp.length() > 0 ) buf.append( tmp ).append( "\r\n" );

		writer.append( buf );
	}


	/**
	 * Writes the tree of a UHSRootnode in 9x format.
	 *
	 * <p>TODO: This method it not yet functional.</p>
	 *
	 * @param rootNode  an existing root node
	 * @param outFile  a file to write
	 */
	public void write9xFormat( UHSRootNode rootNode, File outFile ) throws IOException, UHSGenerationException {
		CharsetEncoder asciiEncoder = Charset.forName( "US-ASCII" ).newEncoder();
		asciiEncoder.onMalformedInput( CodingErrorAction.REPORT );
		asciiEncoder.onUnmappableCharacter( CodingErrorAction.REPORT );

		ByteArrayOutputStream textStream;
		Writer textWriter;
		StringBuffer buf = new StringBuffer();

		UHSGenerationContext context = new UHSGenerationContext();
		context.setEncryptionKey( generate9xKey( rootNode.getUHSTitle() ) );

		context.setLegacyRootNode( rootNode.getLegacyRootNode() );

		if ( context.getLegacyRootNode() == null ) {
			context.setLegacyRootNode( createDefaultLegacyRootNode() );
		}

		// Phase One.
		textStream = new ByteArrayOutputStream();
		textWriter = new OutputStreamWriter( textStream, asciiEncoder );

		write88Format( context.getLegacyRootNode(), textWriter );  // Fake 88a section.
		textWriter.append( "** END OF 88A FORMAT **" + "\r\n" );

		getLinesAndBinData( context, rootNode, buf, 1 );
		textWriter.append( buf );
		textWriter.close();

		logger.debug( "Phase 1, binHunk offset: {}", textStream.size()+1 );
		context.setBinaryHunkOffset( textStream.size() + 1 );  // Include the indicator byte.

		// Phase Two.
		context.setPhase( 2 );
		buf.setLength( 0 );  // Null out the buffer's content, but the backing array's capacity remains.
		textStream.reset();  // Null out the stream's content, but the backing array's capacity remains.
		asciiEncoder.reset();
		textWriter = new OutputStreamWriter( textStream, asciiEncoder );

		write88Format( context.getLegacyRootNode(), textWriter );  // Fake 88a section.
		textWriter.append( "** END OF 88A FORMAT **" + "\r\n" );

		getLinesAndBinData( context, rootNode, buf, 1 );
		textWriter.append( buf );
		textWriter.close();

		logger.debug( "Phase 2, binHunk offset: {}", textStream.size()+1 );
		context.setBinaryHunkOffset( textStream.size() + 1 );  // Re-estimate the text byte count.

		context.setPhase( 3 );
		buf.setLength( 0 );
		textStream.reset();
		asciiEncoder.reset();
		textWriter = new OutputStreamWriter( textStream, asciiEncoder );

		write88Format( context.getLegacyRootNode(), textWriter );  // Fake 88a section.
		textWriter.append( "** END OF 88A FORMAT **" + "\r\n" );

		getLinesAndBinData( context, rootNode, buf, 1 );
		textWriter.append( buf );
		textWriter.close();

		logger.debug( "Phase 3, binHunk offset: {}", textStream.size()+1 );


		// Write encoded text bytes. Flush.
		// Write the binary indicator byte. Flush?
		// Write binary. Flush.
		// Write CRC. Flush.

		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream( outFile );

			// Docs recommends wrapping with a BufferedWriter. Already got a buffer.
			textStream.writeTo( fos );
			fos.flush();

			fos.write( (byte)0x1a );
			fos.flush();

			context.getBinaryHunkOutputStream().writeTo( fos );
			fos.flush();

			// TODO: CRC. Maybe CheckedOutputStream( fos, crc16 )?
		}
		finally {
			try {if ( fos != null ) fos.close();} catch ( IOException e ) {}
		}
	}

	/**
	 * ...
	 *
	 * <p>TODO: This method it not yet functional.</p>
	 *
	 * <p>This method recursively collects nested nodes' text in buffers,
	 * appended to the parent's buffer. Then the parent prepends a total line
	 * count to itself.</p>
	 *
	 * <p>Phase 1:
	 * <ul>
	 * <li>Dry-run text encoding. (Critical information will be missing.)</li>
	 * <li>Count text lines, and build an ID-to-line map, for resolving link
	 * targets later.</li>
	 * <li>Count the total bytes of nodes' binary content, without collecting
	 * it. (for zero-padded character width of offsets/lengths)</li>
	 * <li>Use total bytes of encoded text to estimate the binary hunk's
	 * offset.</li>
	 * </ul></p>
	 *
	 * <p>Phase 2:
	 * <ul>
	 * <li>Another dry-run encoding.</li>
	 * <li>Use the ID-to-Line map to resolve links.</li>
	 * <li>Collect binary content (the growing size will resolve offsets)</li>
	 * <li>Write out all the binsry at the end.</li>
	 * </ul></p>
	 */
	private int getLinesAndBinData( UHSGenerationContext context, UHSNode currentNode, StringBuffer parentBuf, int startIndex ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		String type = currentNode.getType();

		if ( context.isPhaseOne() ) {
			if ( currentNode.getId() != -1 ) {  // Associate id with current line.
				context.putLine( currentNode.getId(), startIndex );
			}
		}

		if ( "Root".equals( type ) ) {
			writeRootNode( context, currentNode, parentBuf, startIndex );
			return 0;
		}
		else if ( "Subject".equals( type ) ) {
			return writeSubjectNode( context, currentNode, parentBuf, startIndex );
		}
		else if ( "NestHint".equals( type ) ) {
			return writeNestHintNode( context, currentNode, parentBuf, startIndex );
		}
		else if ( "Hint".equals( type ) ) {
			return writeHintNode( context, currentNode, parentBuf, startIndex );
		}
		else if ( "Comment".equals( type ) ) {
			return writeCommentNode( context, currentNode, parentBuf, startIndex );
		}
		else if ( "Credit".equals( type ) ) {
			return writeCreditNode( context, currentNode, parentBuf, startIndex );
		}
		else if ( "Text".equals( type ) ) {
			return writeTextNode( context, currentNode, parentBuf, startIndex );
		}
		else if ( "Link".equals( type ) ) {
			return writeLinkNode( context, currentNode, parentBuf, startIndex );
		}
		else if ( "Hyperpng".equals( type ) || "Hypergif".equals( type ) ) {
			return writeHotSpotNode( context, currentNode, parentBuf, startIndex );
		}
		else if ( "Sound".equals( type ) ) {
			return writeSoundNode( context, currentNode, parentBuf, startIndex );
		}
		else if ( "Blank".equals( type ) ) {
			return writeBlankNode( context, currentNode, parentBuf, startIndex );
		}
		else if ( "Version".equals( type ) ) {
			return writeVersionNode( context, currentNode, parentBuf, startIndex );
		}
		else if ( "Info".equals( type ) ) {
			return writeInfoNode( context, currentNode, parentBuf, startIndex );
		}
		else if ( "Incentive".equals( type ) ) {
			return writeIncentiveNode( context, currentNode, parentBuf, startIndex );
		}
		else {
			throw new IllegalArgumentException( "Unexpected version 9x node type: "+ currentNode.getType() );
		}
	}

	/**
	 * Returns a common fake 88a section for the start of 9x format files.
	 *
	 * <p>Write this root in 88a, append "** END OF 88A FORMAT **", then
	 * write the real root in 9x.</p>
	 */
	public UHSRootNode createDefaultLegacyRootNode() {
		UHSRootNode rootNode = new UHSRootNode();
		rootNode.setRawStringContent( "Important Message!" );
		rootNode.setLegacy( true );

		UHSNode subjectNode = new UHSNode( "Subject" );
		subjectNode.setRawStringContent( "Important!" );
		rootNode.addChild( subjectNode );

		UHSNode ignoreNode = new UHSNode( "Question" );
		ignoreNode.setRawStringContent( "Ignore this!" );
		subjectNode.addChild( ignoreNode );

		UHSNode whyNode = new UHSNode( "Question" );
		whyNode.setRawStringContent( "Why aren't there any more hints here?" );
		subjectNode.addChild( whyNode );

		List<String> ignoreLines = new ArrayList<String>();
		ignoreLines.add( "The following text will appear encrypted -- it is intended as instructions" );
		ignoreLines.add( "for people without UHS readers." );

		// Pre-encrypted, to be encrypted again, and thus readable in text editors.
		ignoreLines.add( encryptString( "-------------------------------------------------------------------------" ) );
		ignoreLines.add( encryptString( "This file is encoded in the Universal Hint System format.  The UHS is" ) );
		ignoreLines.add( encryptString( "designed to show you only the hints that you need so your game is not" ) );
		ignoreLines.add( encryptString( "spoiled.  You will need a UHS reader for your computer to view this" ) );
		ignoreLines.add( encryptString( "file.  You can find UHS readers and more information on the UHS on the" ) );
		ignoreLines.add( encryptString( "Internet at http://www.uhs-hints.com/ ." ) );
		ignoreLines.add( encryptString( "-------------------------------------------------------------------------" ) );

		List<String> whyLines = new ArrayList<String>();
		whyLines.add( "This file has been written for a newer UHS format which the reader that" );
		whyLines.add( "you are using does not support.  Visit the UHS web site at" );
		whyLines.add( "http://www.uhs-hints.com/ to see if a newer reader is available." );

		for ( String line : ignoreLines ) {
			UHSNode hintNode = new UHSNode( "Hint" );
			hintNode.setRawStringContent( line );
			ignoreNode.addChild( hintNode );
		}

		for ( String line : whyLines ) {
			UHSNode hintNode = new UHSNode( "Hint" );
			hintNode.setRawStringContent( line );
			whyNode.addChild( hintNode );
		}

		return rootNode;
	}

	public void writeRootNode( UHSGenerationContext context, UHSNode currentNode, StringBuffer parentBuf, int startIndex ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		if ( currentNode instanceof UHSRootNode == false ) return;
		UHSRootNode rootNode = (UHSRootNode)currentNode;

		int innerCount = 0;
		String[] contentLines = null;
		StringBuffer buf = new StringBuffer();

		// Act like a Subject node.
		innerCount = 0;
		StringBuffer sBuf = new StringBuffer();

		sBuf.append( /* lineCount */ " subject" ).append( "\r\n" );
		contentLines = splitContentLines( rootNode, 1 );
		appendLines( sBuf, true, contentLines );
		innerCount += 2;

		for ( int i=0; i < rootNode.getChildCount(); i++ ) {
			UHSNode tmpNode = rootNode.getChild( i );
			innerCount += getLinesAndBinData( context, tmpNode, sBuf, startIndex + innerCount );  // Recurse.
		}

		sBuf.insert( 0, innerCount );  // Insert innerCount at the beginning.
		buf.append( sBuf );

		// TODO: Deal with aux nodes. =/

		parentBuf.append( buf );
	}

	public int writeSubjectNode( UHSGenerationContext context, UHSNode currentNode, StringBuffer parentBuf, int startIndex ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		// lines: 2 + various children's lines
		int innerCount = 0;

		UHSNode subjectNode = currentNode;
		String[] contentLines = null;

		StringBuffer buf = new StringBuffer();
		buf.append( /* lineCount */ " subject" ).append( "\r\n" );
		contentLines = splitContentLines( subjectNode, 1 );
		appendLines( buf, true, contentLines );
		innerCount += 2;

		for ( int i=0; i < subjectNode.getChildCount(); i++ ) {
			UHSNode tmpNode = subjectNode.getChild( i );
			innerCount += getLinesAndBinData( context, tmpNode, buf, startIndex + innerCount );  // Recurse.
		}

		buf.insert( 0, innerCount );  // Insert innerCount at the beginning.
		parentBuf.append( buf );
		return innerCount;
	}

	public int writeNestHintNode( UHSGenerationContext context, UHSNode currentNode, StringBuffer parentBuf, int startIndex ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		// TODO?  // lines: 2 + ugh
		int innerCount = 0;

		if ( currentNode instanceof UHSBatchNode == false ) return 0;
		UHSBatchNode nestNode = (UHSBatchNode)currentNode;
		String[] contentLines = null;

		StringBuffer buf = new StringBuffer();
		buf.append( /* lineCount */ " nesthint" ).append( "\r\n" );
		contentLines = splitContentLines( nestNode, 1 );
		appendLines( buf, true, contentLines );
		innerCount += 2;

		// TODO: Use nestNode.isAddon().

		boolean first = true;
		for ( int i=0; i < nestNode.getChildCount(); i++ ) {
			UHSNode tmpNode = nestNode.getChild( i );
			boolean addon = nestNode.isAddon( tmpNode );

			if ( first && addon ) {
				throw new UHSGenerationException( "NestHint's first child must not be an addon" );
			}

			if ( "HintData".equals( tmpNode.getType() ) ) {
				// First batch has no divider.
				// Successive batches begin with a divider and a HintData.
				// HintData addons do not get dividers when they follow other non-HintData addons.
				if ( !first && !addon) {
					buf.append( "-" ).append( "\r\n" );
					innerCount++;
				}
				contentLines = splitContentLines( tmpNode, -1 );
				innerCount += contentLines.length;
				encryptContentLines( context, ENCRYPT_NEST, contentLines );
				appendLines( buf, true, contentLines );
			}
			else {
				// Batches can only be started by HintData.
				// Insert an empty "-" HintData divider when any other node thinks it's not an addon.
				// Can't do this for the first batch.
				if ( !addon ) {
					if ( first ) {
						throw new UHSGenerationException( "NestHint's first child must be a HintData node" );
					}
					buf.append( "-" ).append( "\r\n" );
					innerCount++;
				}

				buf.append( "=" ).append( "\r\n" );
				innerCount++;

				innerCount += getLinesAndBinData( context, tmpNode, buf, startIndex + innerCount );  // Recurse.
			}
			first = false;
		}

		buf.insert( 0, innerCount );  // Insert innerCount at the beginning.
		parentBuf.append( buf );
		return innerCount;
	}

	public int writeHintNode( UHSGenerationContext context, UHSNode currentNode, StringBuffer parentBuf, int startIndex ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		// TODO?  // lines: 2 + children's content lines + (N children)-1 dividers
		int innerCount = 0;

		UHSNode hintNode = currentNode;
		String[] contentLines = null;

		StringBuffer buf = new StringBuffer();
		buf.append( /* lineCount */ " hint" ).append( "\r\n" );
		contentLines = splitContentLines( hintNode, 1 );
		appendLines( buf, true, contentLines );
		innerCount += 2;

		boolean first = true;
		for ( UHSNode tmpNode : hintNode.getChildren( "HintData", UHSNode.class ) ) {
			if ( !first ) {
				buf.append( "-" ).append( "\r\n" );  // Divider between successive children.
				innerCount++;
			}
			contentLines = splitContentLines( tmpNode, -1 );
			innerCount += contentLines.length;
			encryptContentLines( context, ENCRYPT_HINT, contentLines );
			appendLines( buf, true, contentLines );

			first = false;
		}

		buf.insert( 0, innerCount );  // Insert innerCount at the beginning.
		parentBuf.append( buf );
		return innerCount;
	}

	public int writeCommentNode( UHSGenerationContext context, UHSNode currentNode, StringBuffer parentBuf, int startIndex ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		// lines: 2 + content lines
		int innerCount = 0;

		UHSNode commentNode = currentNode;
		String[] contentLines = null;

		StringBuffer buf = new StringBuffer();
		buf.append( /* lineCount */ " comment" ).append( "\r\n" );
		contentLines = splitContentLines( commentNode, 1 );
		appendLines( buf, true, contentLines );
		innerCount += 2;

		UHSNode dataNode = commentNode.getFirstChild( "CommentData", UHSNode.class );
		if ( dataNode == null ) {/* Throw an error */}

		contentLines = splitContentLines( dataNode, -1 );
		innerCount += contentLines.length;
		appendLines( buf, true, contentLines );

		buf.insert( 0, innerCount );  // Insert innerCount at the beginning.
		parentBuf.append( buf );
		return innerCount;
	}

	public int writeCreditNode( UHSGenerationContext context, UHSNode currentNode, StringBuffer parentBuf, int startIndex ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		// lines: 2 + content lines
		int innerCount = 0;

		UHSNode creditNode = currentNode;
		String[] contentLines = null;

		StringBuffer buf = new StringBuffer();
		buf.append( /* lineCount */ " credit" ).append( "\r\n" );
		contentLines = splitContentLines( creditNode, 1 );
		appendLines( buf, true, contentLines );
		innerCount += 2;

		UHSNode dataNode = creditNode.getFirstChild( "CreditData", UHSNode.class );
		if ( dataNode == null ) {/* Throw an error */}

		contentLines = splitContentLines( dataNode, -1 );
		innerCount += contentLines.length;
		appendLines( buf, true, contentLines );

		buf.insert( 0, innerCount );  // Insert innerCount at the beginning.
		parentBuf.append( buf );
		return innerCount;
	}

	public int writeTextNode( UHSGenerationContext context, UHSNode currentNode, StringBuffer parentBuf, int startIndex ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		// lines: 3
		int innerCount = 0;

		UHSNode textNode = currentNode;
		String[] contentLines = null;

		StringBuffer buf = new StringBuffer();
		buf.append( /* lineCount */ " text" ).append( "\r\n" );
		contentLines = splitContentLines( textNode, 1 );
		appendLines( buf, true, contentLines );
		innerCount += 2;

		UHSNode dataNode = textNode.getFirstChild( "TextData", UHSNode.class );
		if ( dataNode == null ) {/* Throw an error */}
		contentLines = splitContentLines( dataNode, -1 );
		encryptContentLines( context, ENCRYPT_TEXT, contentLines );
		StringBuffer dataBuf = new StringBuffer();
		appendLines( dataBuf, false, contentLines );

		CharsetEncoder asciiEncoder = Charset.forName( "US-ASCII" ).newEncoder();
		asciiEncoder.onMalformedInput( CodingErrorAction.REPORT );
		asciiEncoder.onUnmappableCharacter( CodingErrorAction.REPORT );

		byte[] tmpBytes = asciiEncoder.encode( CharBuffer.wrap( dataBuf ) ).array();

		int bytesOffset = context.getNextBinaryOffset();
		if ( context.isPhaseOne() || context.isPhaseTwo() ) {
			context.registerBinarySection( tmpBytes.length );
		}
		else if ( context.isPhaseThree() ) {
			context.getBinaryHunkOutputStream().write( tmpBytes, 0, tmpBytes.length );
		}

		String offsetString = zeroPad( bytesOffset, context.getOffsetNumberWidth() );
		String lengthString = zeroPad( tmpBytes.length, context.getLengthNumberWidth() );
		buf.append( "000000 0 " ).append( offsetString ).append( " " ).append( lengthString );
		buf.append( "\r\n" );
		innerCount++;

		buf.insert( 0, innerCount );  // Insert innerCount at the beginning.
		parentBuf.append( buf );
		return innerCount;
	}

	public int writeLinkNode( UHSGenerationContext context, UHSNode currentNode, StringBuffer parentBuf, int startIndex ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		// lines: 3
		int innerCount = 0;

		UHSNode linkNode = currentNode;
		String[] contentLines = null;

		StringBuffer buf = new StringBuffer();
		buf.append( /* lineCount */ " link" ).append( "\r\n" );
		contentLines = splitContentLines( linkNode, 1 );
		appendLines( buf, true, contentLines );
		innerCount += 2;

		if ( context.isPhaseOne() ) {
			// Can't resolve a link target's line number yet.
		}
		else if ( context.isPhaseTwo() || context.isPhaseThree() ) {
			int targetLine = context.getLine( linkNode.getLinkTarget() );
			buf.append( targetLine );
		}
		buf.append( "\r\n" );
		innerCount++;

		buf.insert( 0, innerCount );  // Insert innerCount at the beginning.
		parentBuf.append( buf );
		return innerCount;
	}

	public int writeHotSpotNode( UHSGenerationContext context, UHSNode currentNode, StringBuffer parentBuf, int startIndex ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		// TODO?  // lines: 2 + 1 + ugh
		int innerCount = 0;

		if ( currentNode instanceof UHSHotSpotNode == false ) return 0;
		UHSHotSpotNode hotspotNode = (UHSHotSpotNode)currentNode;
		String[] contentLines = null;

		StringBuffer buf = new StringBuffer();
		// Current node has the title AND the main image.

		buf.append( " " );
		if ( "Hyperpng".equals( hotspotNode.getType() ) ) {
			buf.append( "hyperpng" );
		}
		else if ( "Hypergif".equals( hotspotNode.getType() ) ) {
			buf.append( "gifa" );
		}
		else {
			throw new UHSGenerationException( String.format( "Unexpected type of HotSpot node: %s", hotspotNode.getType() ) );
		}
		buf.append( "\r\n" );
		innerCount++;

		contentLines = splitContentLines( hotspotNode, 1 );
		appendLines( buf, true, contentLines );
		innerCount++;

		byte[] mainImageBytes = hotspotNode.getRawImageContent();

		int mainBytesOffset = context.getNextBinaryOffset();
		if ( context.isPhaseOne() || context.isPhaseTwo() ) {
			// TODO: main id vs main image id? Possibly register the other id here?

			context.registerBinarySection( mainImageBytes.length );
		}
		else if ( context.isPhaseThree() ) {
			context.getBinaryHunkOutputStream().write( mainImageBytes, 0, mainImageBytes.length );
		}

		String offsetString = zeroPad( mainBytesOffset, context.getOffsetNumberWidth() );
		String lengthString = zeroPad( mainImageBytes.length, context.getLengthNumberWidth() );
		buf.append( "000000 " ).append( offsetString ).append( " " ).append( lengthString );
		buf.append( "\r\n" );
		innerCount++;

		for ( int i=0; i < hotspotNode.getChildCount(); i++ ) {
			UHSNode tmpNode = hotspotNode.getChild( i );

			HotSpot spot = hotspotNode.getSpot( tmpNode );
			buf.append( zeroPad( spot.zoneX+1, 4 ) );  // X and Y need plus 1.
			buf.append( " " );
			buf.append( zeroPad( spot.zoneY+1, 4 ) );
			buf.append( " " );
			buf.append( zeroPad( spot.zoneX+1+spot.zoneW, 4 ) );
			buf.append( " " );
			buf.append( zeroPad( spot.zoneY+1+spot.zoneH, 4 ) );
			buf.append( "\r\n" );
			innerCount++;

			if ( "Overlay".equals( tmpNode.getType() ) ) {  // lines: 3 (Technically it was preceeded by a zone in HyperImage.)
				// Overlays need the zone's x/y, so don't recurse.

				if ( tmpNode instanceof UHSImageNode == false ) {/* Throw an error */}
				UHSImageNode overlayNode = (UHSImageNode)tmpNode;

				if ( context.isPhaseOne() && overlayNode.getId() != -1 ) {
					context.putLine( overlayNode.getId(), startIndex + innerCount - 1 );  // Fudge the line map to point to zone.
				}

				StringBuffer oBuf = new StringBuffer();
				oBuf.append( /* lineCount */ " overlay" ).append( "\r\n" );
				contentLines = splitContentLines( overlayNode, 1 );
				appendLines( oBuf, true, contentLines );
				innerCount += 2;

				byte[] overlayImageBytes = overlayNode.getRawImageContent();

				int overlayBytesOffset = context.getNextBinaryOffset();
				if ( context.isPhaseOne() || context.isPhaseTwo() ) {
					context.registerBinarySection( overlayImageBytes.length );
				}
				else if ( context.isPhaseThree() ) {
					context.getBinaryHunkOutputStream().write( overlayImageBytes, 0, overlayImageBytes.length );
				}
				String oOffsetString = zeroPad( overlayBytesOffset, context.getOffsetNumberWidth() );
				String oLengthString = zeroPad( overlayImageBytes.length, context.getLengthNumberWidth() );
				oBuf.append( "000000 " ).append( oOffsetString ).append( " " ).append( oLengthString );
				oBuf.append( " " );
				oBuf.append( zeroPad( spot.x+1, 4 ) );  // X and Y need plus 1.
				oBuf.append( " " );
				oBuf.append( zeroPad( spot.y+1, 4 ) );
				oBuf.append( "\r\n" );
				innerCount++;

				oBuf.insert( 0, 3 );  // Insert innerCount at the beginning.
				buf.append( oBuf );
			}
			else {
				innerCount += getLinesAndBinData( context, tmpNode, buf, startIndex + innerCount );  // Recurse.

				if ( context.isPhaseOne() ) {
					// Fudge the line map to point one line earlier, to zone.
					int badLine = context.getLine( tmpNode.getId() );
					context.putLine( tmpNode.getId(), badLine-1 );

					// TODO: No recursive id shifting is done.
				}
			}
		}

		buf.insert( 0, innerCount );  // Insert innerCount at the beginning.
		parentBuf.append( buf );
		return innerCount;
	}

	public int writeSoundNode( UHSGenerationContext context, UHSNode currentNode, StringBuffer parentBuf, int startIndex ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		// lines: 3
		int innerCount = 0;

		if ( currentNode instanceof UHSAudioNode == false ) return 0;
		UHSAudioNode soundNode = (UHSAudioNode)currentNode;
		String[] contentLines = null;

		StringBuffer buf = new StringBuffer();
		buf.append( /* lineCount */ " sound" ).append( "\r\n" );
		contentLines = splitContentLines( soundNode, 1 );
		appendLines( buf, true, contentLines );
		innerCount += 2;

		byte[] tmpBytes = soundNode.getRawAudioContent();

		int bytesOffset = context.getNextBinaryOffset();
		if ( context.isPhaseOne() || context.isPhaseTwo() ) {
			context.registerBinarySection( tmpBytes.length );
		}
		else if ( context.isPhaseThree() ) {
			context.getBinaryHunkOutputStream().write( tmpBytes, 0, tmpBytes.length );
		}

		String offsetString = zeroPad( bytesOffset, context.getOffsetNumberWidth() );
		String lengthString = zeroPad( tmpBytes.length, context.getLengthNumberWidth() );
		buf.append( "000000 " ).append( offsetString ).append( " " ).append( lengthString );
		buf.append( "\r\n" );
		innerCount++;

		buf.insert( 0, innerCount );  // Insert innerCount at the beginning.
		parentBuf.append( buf );
		return innerCount;
	}

	public int writeBlankNode( UHSGenerationContext context, UHSNode currentNode, StringBuffer parentBuf, int startIndex ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		// lines: 2
		int innerCount = 0;

		UHSNode blankNode = currentNode;

		if ( "--=File Info=--".equals( blankNode.getRawStringContent() ) ) {
			return 0;  // Meta: nested info node.
		}

		StringBuffer buf = new StringBuffer();
		buf.append( /* lineCount */ " blank" ).append( "\r\n" );
		buf.append( "-" ).append( "\r\n" );
		innerCount += 2;

		buf.insert( 0, innerCount );  // Insert innerCount at the beginning.
		parentBuf.append( buf );
		return innerCount;
	}

	public int writeVersionNode( UHSGenerationContext context, UHSNode currentNode, StringBuffer parentBuf, int startIndex ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		// lines: 2 + content lines
		int innerCount = 0;

		UHSNode versionNode = currentNode;
		String[] contentLines = null;

		StringBuffer buf = new StringBuffer();
		buf.append( /* lineCount */ " version" ).append( "\r\n" );
		contentLines = splitContentLines( versionNode, 1 );
		appendLines( buf, true, contentLines );
		innerCount += 2;

		UHSNode dataNode = versionNode.getFirstChild( "VersionData", UHSNode.class );
		if ( dataNode == null ) {/* Throw an error */}

		contentLines = splitContentLines( dataNode, -1 );
		innerCount += contentLines.length;
		appendLines( buf, true, contentLines );

		buf.insert( 0, innerCount );  // Insert innerCount at the beginning.
		parentBuf.append( buf );
		return innerCount;
	}

	public int writeInfoNode( UHSGenerationContext context, UHSNode currentNode, StringBuffer parentBuf, int startIndex ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		// lines: 2 + content lines
		int innerCount = 0;

		UHSNode infoNode = currentNode;
		String[] contentLines = null;

		StringBuffer buf = new StringBuffer();
		buf.append( /* lineCount */ " info" ).append( "\r\n" );
		buf.append( "-" ).append( "\r\n" );
		innerCount += 2;

		UHSNode dataNode = infoNode.getFirstChild( "InfoData", UHSNode.class );
		if ( dataNode == null ) {/* Throw an error */}

		contentLines = splitContentLines( dataNode, -1 );
		innerCount += contentLines.length;
		appendLines( buf, true, contentLines );

		buf.insert( 0, innerCount );  // Insert innerCount at the beginning.
		parentBuf.append( buf );
		return innerCount;
	}

	public int writeIncentiveNode( UHSGenerationContext context, UHSNode currentNode, StringBuffer parentBuf, int startIndex ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		// lines: 2 + content lines
		int innerCount = 0;

		UHSNode incentiveNode = currentNode;
		String[] contentLines = null;

		StringBuffer buf = new StringBuffer();
		buf.append( /* lineCount */ " incentive" ).append( "\r\n" );
		buf.append( "-" ).append( "\r\n" );
		innerCount += 2;

		UHSNode dataNode = incentiveNode.getFirstChild( "IncentiveData", UHSNode.class );
		if ( dataNode == null ) {/* Throw an error */}

		contentLines = splitContentLines( dataNode, -1 );
		innerCount += contentLines.length;
		encryptContentLines( context, ENCRYPT_NEST, contentLines );
		appendLines( buf, true, contentLines );

		buf.insert( 0, innerCount );  // Insert innerCount at the beginning.
		parentBuf.append( buf );
		return innerCount;
	}


	/**
	 * Adds zeroes to a number intil it has a minimum number of characters.
	 */
	private String zeroPad( long n, int width ) {
		String s = Long.toString( n );
		if ( s.length() >= width ) return s;

		StringBuilder padBuf = new StringBuilder( s );
		while ( padBuf.length() < width ) {
			padBuf.insert( 0, "0" );
		}
		return ( padBuf.toString() );
	}

	/**
	 * Returns the nimber of CRLF line breaks present in a string.
	 */
	private int crlfCount( CharSequence s ) {
		Matcher m = crlfPtn.matcher( s );
		int lineCount = 0;
		while ( m.find() ) lineCount++;
		return lineCount;
	}


	/**
	 * Returns the raw string content of a UHSNode, split into individual lines.
	 *
	 * <p>This will split on both "^break^" and CRLF.</p>
	 *
	 * @param currentNode  the node to get string content from
	 * @param maxCount  a maximum number of lines to allow, or -1 for no limit
	 * @return an array of lines
	 */
	private String[] splitContentLines( UHSNode currentNode, int maxCount ) throws UHSGenerationException {
		String[] result = currentNode.getRawStringContent().split( "\\^break\\^|\r\n", -1 );
		if ( maxCount != -1 && result.length > maxCount ) {
			throw new UHSGenerationException( String.format( "Node content exceeded max line count (%d): %s", maxCount, currentNode.getRawStringContent() ) );
		}
		return result;
	}

	/**
	 * Applies an encryption algorithm to an array of lines (modifying in-place).
	 *
	 * @param context  a context providing an encryption key, if needed
	 * @param encryption  one of: ENCRYPT_HINT, ENCRYPT_NEST, or ENCRYPT_TEXT
	 * @param lines  an arary of lines to encrypt
	 */
	private void encryptContentLines( UHSGenerationContext context, int encryption, String[] lines ) throws UHSGenerationException {
		if ( context.getEncryptionKey() == null ) {
			throw new UHSGenerationException( "Attempted to encrypt before a key was set in the generation context" );
		}

		for ( int i=lines.length-1; i >= 0; i-- ) {
			if ( encryption == ENCRYPT_HINT ) {
				lines[i] = encryptString( lines[i] );
			}
			else if ( encryption == ENCRYPT_NEST ) {
				lines[i] = encryptNestString( lines[i], context.getEncryptionKey() );
			}
			else if ( encryption == ENCRYPT_TEXT ) {
				lines[i] = encryptTextHunk( lines[i], context.getEncryptionKey() );
			}
		}
	}

	/**
	 * Appends lines to a buffer, adding a line break between each.
	 *
	 * @param buf  the buffer
	 * @param terminate  true to add a line break after the final line, false otherwise
	 * @param lines  an array of lines to append
	 */
	private void appendLines( StringBuffer buf, boolean terminate, String[] lines ) {
		for ( int i=0; i < lines.length; i++ ) {
			buf.append( lines[i] );
			if ( i < lines.length-1 || terminate ) buf.append( "\r\n" );
		}
	}


	/**
	 * Returns text from a UHSNode, escaped.
	 *
	 * <p>Unexpected non-ascii characters are replaced with <b>^?^</b>.</p>
	 *
	 * <p>Escapes have existed from version 88a onwards in most nodes' content and titles.
	 * The # character is the main escape char.</p>
	 *
	 * <p><ul>
	 * <li><b>##</b> a '#' character.</li>
	 * <li><b>#a+</b>[AaEeIiOoUu][:'`^]<b>#a-</b> accent enclosed letter; :=diaeresis,'=acute,`=grave,^=circumflex.</li>
	 * <li><b>#a+</b>[Nn]~<b>#a-</b> accent enclosed letter with a tilde.</li>
	 * <li><b>#a+</b>ae<b>#a-</b> an ash character.</li>
	 * <li><b>#a+</b>TM<b>#a-</b> a trademark character.</li>
	 * <li><b>#w.</b> raw newlines are spaces.</li>
	 * <li><b>#w+</b> raw newlines are spaces (default).</li>
	 * <li><b>#w-</b> raw newlines are newlines.</li>
	 * </ul></p>
	 *
	 * <p>The following are left for display code to handle (e.g., UHSTextArea).
	 * <ul>
	 * <li><b>#p+</b> proportional font (default).</li>
	 * <li><b>#p-</b> non-proportional font.</li>
	 * </ul></p>
	 *
	 * <p>This is displayed as a hyperlink, but it is not clickable.
	 * <ul>
	 * <li><b>#h+</b> through <b>#h-</b> is a hyperlink (http or email).</li>
	 * </ul></p>
	 *
	 * <p>TODO: Translate between decorated strings.</p>
	 *
	 * <p><ul>
	 * <li>Illustrative UHS: <i>Portal: Achievements</i> (hyperlink)</li>
	 * </ul></p>
	 *
	 * @param currentNode  the node to get content from
	 * @param plain  false to add markup, true to replace with ascii equivalent characters
	 * @return an escaped string, or null if the content wasn't text
	 */
	public String escapeText( UHSNode currentNode, boolean plain ) {
		CharsetEncoder asciiEncoder = Charset.forName( "US-ASCII" ).newEncoder();

		String accentPrefix = "#a+";
		String accentSuffix = "#a-";

		char[] diaeresisMarkup = new char[] {':'};
		char[] diaeresisAccent = new char[] {'Ä','Ë','Ï','Ö','Ü','ä','ë','ï','ö','ü'};
		char[] diaeresisNormal = new char[] {'A','E','I','O','U','a','e','i','o','u'};
		char[] acuteMarkup = new char[] {'\''};
		char[] acuteAccent = new char[] {'Á','É','Í','Ó','Ú','á','é','í','ó','ú'};
		char[] acuteNormal = new char[] {'A','E','I','O','U','a','e','i','o','u'};
		char[] graveMarkup = new char[] {'`'};
		char[] graveAccent = new char[] {'À','È','Ì','Ò','Ù','à','è','ì','ò','ù'};
		char[] graveNormal = new char[] {'A','E','I','O','U','a','e','i','o','u'};
		char[] circumflexMarkup = new char[] {'^'};
		char[] circumflexAccent = new char[] {'Â','Ê','Î','Ô','Û','â','ê','î','ô','û'};
		char[] circumflexNormal = new char[] {'A','E','I','O','U','a','e','i','o','u'};
		char[] tildeMarkup = new char[] {'~'};
		char[] tildeAccent = new char[] {'Ñ','ñ'};
		char[] tildeNormal = new char[] {'N','n'};

		char[][][] accents = new char[][][] {
			{diaeresisMarkup, diaeresisAccent, diaeresisNormal},
			{acuteMarkup, acuteAccent, acuteNormal},
			{graveMarkup, graveAccent, graveNormal},
			{circumflexMarkup, circumflexAccent, circumflexNormal},
			{graveMarkup, graveAccent, graveNormal}
		};

		StringBuffer buf = new StringBuffer();
		char[] tmp = currentNode.getRawStringContent().toCharArray();
		for ( int c=0; c < tmp.length; c++ ) {
			boolean escaped = false;

			for ( int i=0; !escaped && i < accents.length; i++ ) {
				char markup = accents[i][0][0]; char[] accent = accents[i][1]; char[] normal = accents[i][2];

				for ( int a=0; !escaped && a < accent.length; a++ ) {
					if ( tmp[c] == accent[a] ) {
						if ( plain ) {
							buf.append( normal[a] );
						} else {
							buf.append( accentPrefix ).append( normal[a] ).append( markup ).append( accentSuffix );
						}
						escaped = true;
					}
				}
			}

			if ( !escaped && tmp[c] == 'æ' ) {
				String normal = "ae";
				if ( plain ) {
					buf.append( normal );
				} else {
					buf.append( accentPrefix ).append( normal ).append( accentSuffix );
				}
				escaped = true;
			}
			if ( !escaped && tmp[c] == '™' ) {
				String normal = "TM";
				if ( plain ) {
					buf.append( normal );
				} else {
					buf.append( accentPrefix ).append( normal ).append( accentSuffix );
				}
				escaped = true;
			}
			if ( !escaped ) {
				if (asciiEncoder.canEncode( tmp[c] )) {
					buf.append( tmp[c] );
				} else {
					logger.warn( "No escape known for this non-ascii character: {}", tmp[c] );
					buf.append( "^?^" );
				}
			}
			// TODO: Linebreaks
		}

		return buf.toString();
	}
}
