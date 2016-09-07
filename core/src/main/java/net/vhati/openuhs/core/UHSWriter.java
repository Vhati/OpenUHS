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

import net.vhati.openuhs.core.UHSGenerationException;


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
			while ( tmpChar > 126 ) {
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

		if ( rootNode.getContentType() != UHSNode.STRING ) return false;
		if ( !rootNode.isGroup() ) return false;

		List<UHSNode> levelOne = rootNode.getChildren();
		for ( int o=0; o < levelOne.size(); o++ ) {
			UHSNode oNode = levelOne.get(o);
			String oType = oNode.getType();
			if ( oType.equals("Subject") ) {
				if ( oNode.getContentType() != UHSNode.STRING ) return false;
				if ( !oNode.isGroup() ) return false;

				// Check Question nodes
				List<UHSNode> levelTwo = oNode.getChildren();
				for ( int t=0; t < levelTwo.size(); t++ ) {
					UHSNode tNode = levelTwo.get(t);
					if ( tNode.getContentType() != UHSNode.STRING ) return false;
					if ( !tNode.isGroup() ) return false;

					// Check Hint nodes
					List<UHSNode> levelThree = tNode.getChildren();
					for ( int r=0; r < levelThree.size(); r++ ) {
						UHSNode rNode = levelThree.get( r );
						if ( rNode.getContentType() != UHSNode.STRING ) return false;
						if ( rNode.isGroup() ) return false;
					}
				}
			}
			else if (oType.equals( "Blank" )) {
			}
			else if (oType.equals( "Version" )) {
			}
			else if (oType.equals( "Credit" )) {
				if ( hasCredit ) return false;
				hasCredit = true;
				if ( oNode.getChildCount() != 1 ) return false;
				// Check CreditData node
				if ( oNode.getChild( 0 ).getContentType() != UHSNode.STRING ) return false;
				if ( oNode.getChild( 0 ).isGroup() ) return false;
			}
			else return false;
		}
		if ( !hasCredit ) return false;
		return true;
	}


	/**
	 * Writes the tree of a UHSRootnode in 88a format.
	 *
	 * <p>TODO: This method is broken and needs to be made decorator-aware.</p>
	 *
	 * <p>Newlines and "^break^" are replaced by " ".</p>
	 *
	 * @param rootNode  an existing root node
	 * @param os  an existing unwritten stream to write into
	 * @see #isValid88Format(UHSRootNode)
	 */
	public void write88Format( UHSRootNode rootNode, OutputStream os ) throws IOException, UHSGenerationException {
		if ( !isValid88Format( rootNode ) ) {
			throw new UHSGenerationException( "The node tree cannot be expressed in the 88a format" );
		}
		StringBuffer buf = new StringBuffer();

		String tmp = null;
		List<UHSNode> subjectNodes = rootNode.getChildren( "Subject" );
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

		UHSNode creditNode = rootNode.getChildren( "Credit" ).get( 0 );
		tmp = ( String )creditNode.getChild( 0 ).getContent();
		tmp = tmp.replaceAll( "\\^break\\^", "\r\n" );
		if ( tmp.length() > 0 ) buf.append( tmp ).append( "\r\n" );

		os.write( encodeAsciiBytes( buf.toString() ) );
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
		UHS9xInfo info = new UHS9xInfo();
		String uhsTitle = rootNode.getUHSTitle();
		info.encryptionKey = generate9xKey( uhsTitle );

		StringBuffer buf = new StringBuffer();
		getLinesAndBinData( rootNode, info, buf );

		buf.setLength( 0 );  // Null out the buffer's content, but the backing array's capacity remains.
		info.phase = 2;
		getLinesAndBinData( rootNode, info, buf );

		// Encode buf to ASCII and write. Flush.
		// Write the binary indicator byte. Flush?
		// Write binary. Flush.
		// Write CRC. Flush.

		CharsetEncoder asciiEncoder = Charset.forName( "ISO-8859-1" ).newEncoder();  // TODO: Really that charset?
		asciiEncoder.onMalformedInput( CodingErrorAction.REPLACE );
		asciiEncoder.onUnmappableCharacter( CodingErrorAction.REPORT );
		asciiEncoder.replaceWith("!".getBytes( "ISO-8859-1" ));

		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream( outFile );

			// Docs recommends wrapping with a BufferedWriter. Already got a buffer.
			Writer writer = new OutputStreamWriter( fos, asciiEncoder );
			writer.append( buf );
			writer.flush();

			fos.write( (byte)0x1a );
			fos.flush();

			info.binStream.writeTo( fos );
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
	 * appended to the parent's buffer. Then the parent prepends a total line count to itself.</p>
	 *
	 * <p>Phase 1:
	 * <ul>
	 * <li>Dry-run text encoding. (Critical information will be missing.)</li>
	 * <li>Count text lines, and build an ID-to-line map, for resolving link targets later.</li>
	 * <li>Count the total bytes of nodes' binary content, without collecting it. (for zero-padded character width)</li>
	 * </ul></p>
	 *
	 * <p>Phase 2:
	 * <ul>
	 * <li>Actually write encoded text.</li>
	 * <li>Use the ID-to-Line map to resolve links.</li>
	 * <li>Collect binary content (the growing size will resolve offsets)</li>
	 * <li>Write out all the binsry at the end.</li>
	 * </ul></p>
	 */
	private void getLinesAndBinData( UHSNode currentNode, UHS9xInfo info, StringBuffer parentBuf ) throws CharacterCodingException, UHSGenerationException, UnsupportedEncodingException {
		String type = currentNode.getType();
		boolean hasTitle = false;

		if ( info.phase == 1 ) {
			if ( currentNode.getId() != -1 ) {  // Associate id with current line
				info.putLine( currentNode.getId(), info.line );
			}
		}

		if ( "Root".equals( type ) ) {
			String fakeTitle = "Important Message!";

			String fakeSubject = "Important!";
			String ignoreQuestion = "Ignore this!";                        // Trailing question mark implied by format.
			String whyQuestion = "Why aren't there any more hints here?";  // Trailing question mark implied by format.

			List<String> ignoreHints = new ArrayList<String>();  // Encrypted.
			ignoreHints.add( "The following text will appear encrypted -- it is intended as instructions" );
			ignoreHints.add( "for people without UHS readers." );

			List<String> gapLines = new ArrayList<String>();  // Not encrypted.
			gapLines.add( "-------------------------------------------------------------------------" );
			gapLines.add( "This file is encoded in the Universal Hint System format.  The UHS is" );
			gapLines.add( "designed to show you only the hints that you need so your game is not" );
			gapLines.add( "spoiled.  You will need a UHS reader for your computer to view this" );
			gapLines.add( "file.  You can find UHS readers and more information on the UHS on the" );
			gapLines.add( "Internet at http://www.uhs-hints.com/ ." );
			gapLines.add( "-------------------------------------------------------------------------" );

			List<String> whyHints = new ArrayList<String>();  // Encrypted.
			whyHints.add( "This file has been written for a newer UHS format which the reader that" );
			whyHints.add( "you are using does not support.  Visit the UHS web site at" );
			whyHints.add( "http://www.uhs-hints.com/ to see if a newer reader is available." );

			int fakeSubjectCount = 1;
			int fakeQuestionCount = 2;
			int sSize = 2;
			int qSize = 2;
			int hSize = 1;
			int firstQ = 1 + ( sSize * fakeSubjectCount );        // First line is 1. Questions begin after last subject line.
			int firstH = firstQ + ( qSize * fakeQuestionCount );  // Hints begin after the last question line.

			// Final line of the final hint, not 1 line after.
			int lastH = firstH + ( hSize * ignoreHints.size() ) + gapLines.size() + ( hSize * whyHints.size() ) - 1;

			StringBuffer buf = new StringBuffer();
			buf.append( "UHS" ).append( "\r\n" );
			buf.append( fakeTitle ).append( "\r\n" );
			buf.append( firstH ).append( "\r\n" );
			buf.append( lastH ).append( "\r\n" );

			buf.append( encryptString( fakeSubject ) ).append( "\r\n" );
			buf.append( ( qSize * /* First question index */ 0 ) + firstQ ).append( "\r\n" );

			buf.append( encryptString( ignoreQuestion ) ).append( "\r\n" );
			buf.append( ( hSize * /* First hint index */ 0 ) + firstH ).append( "\r\n" );

			buf.append( encryptString( whyQuestion ) ).append( "\r\n" );
			buf.append( ( ( hSize * ignoreHints.size() ) + gapLines.size() ) + firstH ).append( "\r\n" );

			for ( String s : ignoreHints ) {
				buf.append( encryptString( s ) ).append( "\r\n" );
			}
			for ( String s : gapLines ) {
				buf.append( s ).append( "\r\n" );
			}
			for ( String s : whyHints ) {
				buf.append( encryptString( s ) ).append( "\r\n" );
			}
			buf.append( "** END OF 88A FORMAT **" ).append( "\r\n" );

			buf.append( "UHS" ).append( "\r\n" );
			if ( info.phase == 1 ) info.line += 1;  // The version 9x header.

			// Act like a Subject node.
			StringBuffer sBuf = new StringBuffer();
			sBuf.append( /* lineCount */ " subject" ).append( "\r\n" );
			sBuf.append( getEncryptedText( currentNode, info, ENCRYPT_NONE ) ).append( "\r\n" );

			if ( info.phase == 1 ) info.line += 2;  // Children will increment further.

			for ( UHSNode tmpNode : currentNode.getChildren() ) {
				getLinesAndBinData( tmpNode, info, sBuf );  // Recurse.
			}

			if ( info.phase == 2 ) {
				sBuf.insert( 0, crlfCount( sBuf.toString() ) );  // Insert lineCount at the beginning.
			}
			buf.append( sBuf );

			parentBuf.append( buf );
		}
		else if ( "Link".equals( type ) ) {  // lines: 3
			StringBuffer buf = new StringBuffer();
			buf.append( /* lineCount */ " link" ).append( "\r\n" );
			buf.append( getEncryptedText( currentNode, info, ENCRYPT_NONE ) ).append( "\r\n" );

			if ( info.phase == 1 ) {
				// Can't resolve a link target's line number yet.
			}
			else if ( info.phase == 2 ) {
				int targetLine = info.getLine( currentNode.getLinkTarget() );
				buf.append( targetLine );
			}
			buf.append( "\r\n" );

			if ( info.phase == 1 ) {
				info.line += 3;
			}
			else if ( info.phase == 2 ) {
				buf.insert( 0, crlfCount( buf.toString() ) );  // Insert lineCount at the beginning.
			}
			parentBuf.append( buf );
		}
		else if ( "Text".equals( type ) ) {  // lines: 3
			StringBuffer buf = new StringBuffer();
			buf.append( /* lineCount */ " text" ).append( "\r\n" );
			buf.append( getEncryptedText( currentNode, info, ENCRYPT_NONE ) ).append( "\r\n" );

			UHSNode tmpNode = currentNode.getFirstChild( "TextData" );
			if ( tmpNode == null ) {/* Throw an error */}
			byte[] tmpBytes = encodeAsciiBytes( getEncryptedText( tmpNode, info, ENCRYPT_TEXT ) );

			if ( info.phase == 1 ) {
				info.registerBinarySection( tmpBytes.length );
			}
			else if ( info.phase == 2 ) {
				String offsetString = zeroPad( info.binStream.size(), info.getOffsetNumberWidth() );
				String lengthString = zeroPad( tmpBytes.length, info.getLengthNumberWidth() );
				buf.append( "000000 0 " ).append( offsetString ).append( " " ).append( lengthString );

				info.binStream.write( tmpBytes, 0, tmpBytes.length );
			}
			buf.append( "\r\n" );

			if ( info.phase == 1 ) {
				info.line += 3;
			}
			else if ( info.phase == 2 ) {
				buf.insert( 0, crlfCount( buf.toString() ) );  // Insert lineCount at the beginning.
			}
			parentBuf.append( buf );
		}
		else if ( "Sound".equals( type ) ) {  // lines: 3
			StringBuffer buf = new StringBuffer();
			buf.append( /* lineCount */ " sound" ).append( "\r\n" );
			buf.append( getEncryptedText( currentNode, info, ENCRYPT_NONE ) ).append( "\r\n" );

			UHSNode tmpNode = currentNode.getFirstChild( "SoundData" );
			if ( tmpNode == null ) {/* Throw an error */}
			byte[] tmpBytes = (byte[])currentNode.getContent();

			if ( info.phase == 1 ) {
				info.registerBinarySection( tmpBytes.length );
			}
			else if ( info.phase == 2 ) {
				String offsetString = zeroPad( info.binStream.size(), info.getOffsetNumberWidth() );
				String lengthString = zeroPad( tmpBytes.length, info.getLengthNumberWidth() );
				buf.append( "000000 " ).append( offsetString ).append( " " ).append( lengthString );

				info.binStream.write( tmpBytes, 0, tmpBytes.length );
			}
			buf.append( "\r\n" );

			if ( info.phase == 1 ) {
				info.line += 3;
			}
			else if ( info.phase == 2 ) {
				buf.insert( 0, crlfCount( buf.toString() ) );  // Insert lineCount at the beginning.
			}
			parentBuf.append( buf );
		}
		else if ( "Blank".equals( type ) ) {  // lines: 2
			if ( "--=File Info=--".equals( currentNode.getContent() ) ) return;  // Meta: nested info node.

			StringBuffer buf = new StringBuffer();
			buf.append( /* lineCount */ " blank" ).append( "\r\n" );
			buf.append( "-" ).append( "\r\n" );

			if ( info.phase == 1 ) {
				info.line += 2;
			}
			else if ( info.phase == 2 ) {
				buf.insert( 0, crlfCount( buf.toString() ) );  // Insert lineCount at the beginning.
			}
			parentBuf.append( buf );
		}
		else if ( "Subject".equals( type ) ) {  // lines: 2 + various children's lines
			StringBuffer buf = new StringBuffer();
			buf.append( /* lineCount */ " subject" ).append( "\r\n" );
			buf.append( getEncryptedText( currentNode, info, ENCRYPT_NONE ) ).append( "\r\n" );

			if ( info.phase == 1 ) info.line += 2;  // Children will increment further.

			for ( UHSNode tmpNode : currentNode.getChildren() ) {
				getLinesAndBinData( tmpNode, info, buf );  // Recurse.
			}

			if ( info.phase == 2 ) {
				buf.insert( 0, crlfCount( buf.toString() ) );  // Insert lineCount at the beginning.
			}
			parentBuf.append( buf );
		}
		else if ( "Comment".equals( type ) ) {  // lines: 2 + content lines
			StringBuffer buf = new StringBuffer();
			buf.append( /* lineCount */ " comment" ).append( "\r\n" );
			buf.append( getEncryptedText( currentNode, info, ENCRYPT_NONE ) ).append( "\r\n" );

			UHSNode tmpNode = currentNode.getFirstChild( "CommentData" );
			if ( tmpNode == null ) {/* Throw an error */}

			buf.append( getEncryptedText( tmpNode, info, ENCRYPT_NONE ) ).append( "\r\n" );

			if ( info.phase == 1 ) {
				info.line += crlfCount( buf.toString() );
			}
			else if ( info.phase == 2 ) {
				buf.insert( 0, crlfCount( buf.toString() ) );  // Insert lineCount at the beginning.
			}
			parentBuf.append( buf );
		}
		else if ( "Credit".equals( type ) ) {  // lines: 2 + content lines
			StringBuffer buf = new StringBuffer();
			buf.append( /* lineCount */ " credit" ).append( "\r\n" );
			buf.append( getEncryptedText( currentNode, info, ENCRYPT_NONE ) ).append( "\r\n" );

			UHSNode tmpNode = currentNode.getFirstChild( "CreditData" );
			if ( tmpNode == null ) {/* Throw an error */}

			buf.append( getEncryptedText( tmpNode, info, ENCRYPT_NONE ) ).append( "\r\n" );

			if ( info.phase == 1 ) {
				info.line += crlfCount( buf.toString() );
			}
			else if ( info.phase == 2 ) {
				buf.insert( 0, crlfCount( buf.toString() ) );  // Insert lineCount at the beginning.
			}
			parentBuf.append( buf );
		}
		else if ( "Version".equals( type ) ) {  // lines: 2 + content lines
			StringBuffer buf = new StringBuffer();
			buf.append( /* lineCount */ " version" ).append( "\r\n" );
			buf.append( getEncryptedText( currentNode, info, ENCRYPT_NONE ) ).append( "\r\n" );

			UHSNode tmpNode = currentNode.getFirstChild( "VersionData" );
			if ( tmpNode == null ) {/* Throw an error */}

			buf.append( getEncryptedText( tmpNode, info, ENCRYPT_NONE ) ).append( "\r\n" );

			if ( info.phase == 1 ) {
				info.line += crlfCount( buf.toString() );
			}
			else if ( info.phase == 2 ) {
				buf.insert( 0, crlfCount( buf.toString() ) );  // Insert lineCount at the beginning.
			}
			parentBuf.append( buf );
		}
		else if ( "Info".equals( type ) ) {  // lines: 2 + content lines
			StringBuffer buf = new StringBuffer();
			buf.append( /* lineCount */ " info" ).append( "\r\n" );
			buf.append( "-" ).append( "\r\n" );

			UHSNode tmpNode = currentNode.getFirstChild( "InfoData" );
			if ( tmpNode == null ) {/* Throw an error */}

			buf.append( getEncryptedText( tmpNode, info, ENCRYPT_NONE ) ).append( "\r\n" );

			if ( info.phase == 1 ) {
				info.line += crlfCount( buf.toString() );
			}
			else if ( info.phase == 2 ) {
				buf.insert( 0, crlfCount( buf.toString() ) );  // Insert lineCount at the beginning.
			}
			parentBuf.append( buf );
		}
		else if ( "Incentive".equals( type ) ) {  // lines: 2 + content lines
			StringBuffer buf = new StringBuffer();
			buf.append( /* lineCount */ " incentive" ).append( "\r\n" );
			buf.append( "-" ).append( "\r\n" );

			UHSNode tmpNode = currentNode.getFirstChild( "IncentiveData" );
			if ( tmpNode == null ) {/* Throw an error */}

			buf.append( getEncryptedText( tmpNode, info, ENCRYPT_NEST ) ).append( "\r\n" );

			if ( info.phase == 1 ) {
				info.line += crlfCount( buf.toString() );
			}
			else if ( info.phase == 2 ) {
				buf.insert( 0, crlfCount( buf.toString() ) );  // Insert lineCount at the beginning.
			}
			parentBuf.append( buf );
		}
		else if ( "Hint".equals( type ) ) {  // TODO?  // lines: 2 + children's content lines + (N children)-1 dividers
			StringBuffer buf = new StringBuffer();
			buf.append( /* lineCount */ " hint" ).append( "\r\n" );
			buf.append( getEncryptedText( currentNode, info, ENCRYPT_NONE ) ).append( "\r\n" );

			boolean first = true;
			for ( UHSNode tmpNode : currentNode.getChildren( "HintData" ) ) {
				if ( !first ) {
					buf.append( "-" ).append( "\r\n" );  // Divider between successive children.
				}
				String encryptedChildContent = getEncryptedText( tmpNode, info, ENCRYPT_HINT );

				buf.append( encryptedChildContent ).append( "\r\n" );

				first = false;
			}

			if ( info.phase == 1 ) {
				info.line += crlfCount( buf.toString() );
			}
			else if ( info.phase == 2 ) {
				buf.insert( 0, crlfCount( buf.toString() ) );  // Insert lineCount at the beginning.
			}
			parentBuf.append( buf );
		}
		else if ( "NestHint".equals( type ) ) {  // TODO?  // lines: 2 + ugh
			StringBuffer buf = new StringBuffer();
			buf.append( /* lineCount */ " nesthint" ).append( "\r\n" );
			buf.append( getEncryptedText( currentNode, info, ENCRYPT_NONE ) ).append( "\r\n" );

			if ( info.phase == 1 ) info.line += 2;  // Children will increment further.

			// TODO: Use ((UHSBatchNode)currentNode).isAddon().
			// TODO: Insert empty "-" dividers when a non-HintData thinks it's not an addon.
			boolean first = true;
			for ( UHSNode tmpNode : currentNode.getChildren() ) {
				if ( tmpNode.getType().equals( "HintData" ) ) {
					if ( !first ) {
						buf.append( "-" ).append( "\r\n" );
						if ( info.phase == 1 ) info.line += 1;  // "-" divider
					}
					String childContent = getEncryptedText( tmpNode, info, ENCRYPT_NEST );
					if ( info.phase == 1 ) {
						info.line += crlfCount( childContent ) + 1;  // +1 for the final unterminated line.
					}
					buf.append( childContent ).append( "\r\n" );
				}
				else {
					if ( !first ) {
						buf.append( "=" ).append( "\r\n" );
						if ( info.phase == 1 ) info.line += 1;  // "=" divider
					}
					getLinesAndBinData( tmpNode, info, buf );  // Recurse.
				}
				first = false;
			}

			if ( info.phase == 2 ) {
				buf.insert( 0, crlfCount( buf.toString() ) );  // Insert lineCount at the beginning.
			}
			parentBuf.append( buf );
		}
		else if ( "HotSpot".equals( type ) ) {  // TODO?  // lines: 2 + 1 + ugh
			StringBuffer buf = new StringBuffer();
			// Current node has the title, and first child has the main image.

			buf.append( " " );
			if ( "Hyperpng".equals( currentNode.getChild( 0 ).getType() ) ) {
				buf.append( "hyperpng" );
			}
			else if ( "Hypergif".equals( currentNode.getChild( 0 ).getType() ) ) {
				buf.append( "gifa" );
			}
			else {/* Throw an error */}
			buf.append( "\r\n" );

			buf.append( getEncryptedText( currentNode, info, ENCRYPT_NONE ) ).append( "\r\n" );

			if ( info.phase == 1 ) info.line += 2;  // Chunk name and title.

			byte[] mainImageBytes = (byte[])(currentNode.getChild( 0 ).getContent());

			if ( info.phase == 1 ) {
				// Main images also get an id here, on the binary hunk reference line.
				if ( currentNode.getChild( 0 ).getId() != -1 ) {
					info.putLine( currentNode.getChild( 0 ).getId(), info.line );
				}

				info.registerBinarySection( mainImageBytes.length );
			}
			else if ( info.phase == 2 ) {
				String offsetString = zeroPad( info.binStream.size(), info.getOffsetNumberWidth() );
				String lengthString = zeroPad( mainImageBytes.length, info.getLengthNumberWidth() );
				buf.append( "000000 " ).append( offsetString ).append( " " ).append( lengthString );

				info.binStream.write( mainImageBytes, 0, mainImageBytes.length );
			}
			buf.append( "\r\n" );

			if ( info.phase == 1 ) info.line += 1;  // The binary hunk reference.

			boolean first = true;
			for ( UHSNode tmpNode : currentNode.getChildren() ) {
				if ( first ) {  // Skip the main image child, already handled.
					first = false;
					continue;
				}

				HotSpot spot = ((UHSHotSpotNode)currentNode).getSpot( tmpNode );
				buf.append( spot.zoneX ).append( " " ).append( spot.zoneY ).append( " " );
				buf.append( spot.zoneX+spot.zoneW ).append( spot.zoneY+spot.zoneH ).append( "\r\n" );

				if ( info.phase == 1 ) info.line += 1;  // Zone.

				if ( "Overlay".equals( tmpNode.getType() ) ) {  // lines: 3 (Technically it was preceeded by a zone in HyperImg.)
					// Overlays need the zone's x/y, so don't recurse.

					if ( info.phase == 1 && tmpNode.getId() != -1 ) {
						info.putLine( tmpNode.getId(), info.line-1 );  // Fudge the line map to point to zone.
					}

					StringBuffer oBuf = new StringBuffer();
					oBuf.append( /* lineCount */ " overlay" ).append( "\r\n" );
					oBuf.append( "^OVERLAY TITLE WAS EATEN^" ).append( "\r\n" );  // TODO: Move image binary into a child node.

					byte[] overlayImageBytes = (byte[])(tmpNode.getContent());

					if ( info.phase == 1 ) {
						info.registerBinarySection( overlayImageBytes.length );
					}
					else if ( info.phase == 2 ) {
						String offsetString = zeroPad( info.binStream.size(), info.getOffsetNumberWidth() );
						String lengthString = zeroPad( overlayImageBytes.length, info.getLengthNumberWidth() );
						oBuf.append( "000000 " ).append( offsetString ).append( " " ).append( lengthString );
						oBuf.append( spot.x ).append( " " ).append( spot.y );

						info.binStream.write( overlayImageBytes, 0, overlayImageBytes.length );
					}
					oBuf.append( "\r\n" );

					if ( info.phase == 1 ) {
						info.line += 3;
					}
					else if ( info.phase == 2 ) {
						oBuf.insert( 0, crlfCount( oBuf.toString() ) );  // Insert lineCount at the beginning.
					}
					buf.append( oBuf );
				}
				else {
					getLinesAndBinData( tmpNode, info, buf );  // Recurse.

					if ( info.phase == 1 ) {
						// Fudge the line map to point one line earlier, to zone.
						int badLine = info.getLine( tmpNode.getId() );
						info.putLine( tmpNode.getId(), badLine-1 );

						// TODO: It is assumed this child has no children. No further id shifting is done.
					}
				}
			}

			if ( info.phase == 2 ) {
				buf.insert( 0, crlfCount( buf.toString() ) );  // Insert lineCount at the beginning.
			}
			parentBuf.append( buf );
		}
		else {
			throw new IllegalArgumentException( "Unexpected version 9x node type: "+ currentNode.getType() );
		}
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

	private int crlfCount( String s ) {
		Matcher m = crlfPtn.matcher( s );
		int lineCount = 0;
		while ( m.find() ) lineCount++;
		return lineCount;
	}

	/**
	 * Encrypts the multiline string content of a node.
	 *
	 * <p>Linebreaks will be converted from "^break^" to "\r\n".</p>
	 */
	private String getEncryptedText( UHSNode currentNode, UHS9xInfo info, int encryption ) throws UHSGenerationException {
		if ( currentNode.getContentType() != UHSNode.STRING ) return null;  // !?

		String tmpString = (String)currentNode.getContent();
		tmpString = tmpString.replaceAll( "\\^break\\^", "\r\n" );
		if ( encryption == ENCRYPT_HINT ) {
			tmpString = encryptString( tmpString );
		}
		else if ( encryption == ENCRYPT_NEST || encryption == ENCRYPT_TEXT ) {
			if ( info.encryptionKey == null ) {
				throw new UHSGenerationException( "Attempted to encrypt before a key was set" );
			}
			StringBuffer buf = new StringBuffer( tmpString.length() );
			String[] lines = tmpString.split( "\r\n" );
			for ( int i=0; i < lines.length; i++ ) {
				if ( i > 0 ) buf.append( "\r\n" );
				if ( encryption == ENCRYPT_NEST ) {
					buf.append( encryptNestString( lines[i], info.encryptionKey ) );
				}
				else if ( encryption == ENCRYPT_TEXT ) {
					buf.append( encryptTextHunk( lines[i], info.encryptionKey ) );
				}
			}
			tmpString = buf.toString();
		}
		return tmpString;
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
	 * <p><ul>
	 * <li>Illustrative UHS: <i>Portal: Achievements</i> (hyperlink)</li>
	 * </ul></p>
	 *
	 * @param currentNode  the node to get content from
	 * @param plain  false to add markup, true to replace with ascii equivalent characters
	 * @return an escaped string, or null if the content wasn't text
	 */
	public String escapeText( UHSNode currentNode, boolean plain ) {
		if ( currentNode.getContentType() != UHSNode.STRING ) return null;

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
		char[] tmp = ((String)currentNode.getContent()).toCharArray();
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


	private byte[] encodeAsciiBytes( String s ) throws CharacterCodingException, UnsupportedEncodingException {
		CharsetEncoder asciiEncoder = Charset.forName( "ISO-8859-1" ).newEncoder();
			asciiEncoder.onMalformedInput( CodingErrorAction.REPLACE );
			asciiEncoder.onUnmappableCharacter( CodingErrorAction.REPORT );
			asciiEncoder.replaceWith("!".getBytes( "ISO-8859-1" ));
		CharBuffer sBuf = CharBuffer.wrap( s );
		ByteBuffer bBuf = asciiEncoder.encode( sBuf );
		return bBuf.array();
	}


	// An object to pass args around while recursively writing a 9x file.
	private class UHS9xInfo {
		// Pass 1
		public int line = -1;
		private Map<Integer, Integer> idToLineMap = new HashMap<Integer, Integer>();
		private int binHighestOffset = 0;
		private int binLength = 0;

		public int[] encryptionKey = null;
		public int phase = 1;

		// Pass 2
		private int offsetNumberWidth = 6;  // Might grow to 7 if binary hunk's too large
		private int lengthNumberWidth = 6;

		public ByteArrayOutputStream binStream = new ByteArrayOutputStream();

		public int getLine( int id ) {
			Integer resolvedLine = idToLineMap.get( new Integer( id ) );
			if ( resolvedLine != null ) {
				return resolvedLine.intValue();
			}
			else if ( phase == 2 ) {
				throw new NullPointerException( "No line was registered for node id: "+ id );
			}
			else {
				return -1;
			}
		}

		public void putLine( int id, int pendingLine ) {
			idToLineMap.put( new Integer( id ), new Integer( pendingLine ) );
		}

		public void registerBinarySection( int sectionLength ) {
			binHighestOffset = binLength;
			binLength += sectionLength;
		}

		/**
		 * Returns the amount of zero-padding needed for binary segment offsets.
		 *
		 * The default is 6, although it may be 7 when the binary hunk is large.
		 */
		public int getOffsetNumberWidth() {
			if ( offsetNumberWidth == -1 ) {
				int highestOffsetWidth = Integer.toString( binHighestOffset ).length();
				offsetNumberWidth = Math.max( highestOffsetWidth, 6 );
			}
			return offsetNumberWidth;
		}

		/**
		 * Returns the amount of zero-padding needed for binary segment lengths.
		 *
		 * The default is 6, although it may be 7 when the binary hunk is large.
		 */
		public int getLengthNumberWidth() {
			if ( lengthNumberWidth == -1 ) {
				int lengthWidth = Integer.toString( binLength ).length();
				lengthNumberWidth = Math.max( lengthWidth, 6 );
			}
			return lengthNumberWidth;
		}
	}

}
