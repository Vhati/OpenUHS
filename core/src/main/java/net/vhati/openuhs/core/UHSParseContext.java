package net.vhati.openuhs.core;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.ArrayByteReference;
import net.vhati.openuhs.core.ByteReference;
import net.vhati.openuhs.core.ExtraNodeId;
import net.vhati.openuhs.core.FileRegionByteReference;
import net.vhati.openuhs.core.UHSRootNode;


/**
 * A state-tracking object used internally by UHSParser.
 * <p>
 * There is no need to construct an instance of this directly.
 *
 * @see net.vhati.openuhs.core.UHSParser
 */
public class UHSParseContext {

	private final Logger logger = LoggerFactory.getLogger( UHSParseContext.class );

	protected boolean binaryDeferred = false;
	protected File file = null;
	protected long binHunkOffset = -1;
	protected UHSRootNode rootNode = null;
	protected int[] encryptionKey = null;

	protected List<String> allLines = null;
	protected byte[] binHunk = null;
	protected long binHunkLength = 0;

	protected List<ExtraNodeId> extraIds = new ArrayList<ExtraNodeId>();

	protected int lineIndexFudge = 0;
	protected int lastLineIndex = -1;


	public UHSParseContext() {
	}


	/**
	 * Sets whether to preload binary hunk segments into arrays or defer reads until needed.
	 */
	public void setBinaryDeferred( boolean b ) {
		binaryDeferred = b;
	}

	public boolean isBinaryDeferred() {
		return binaryDeferred;
	}


	/**
	 * Sets the file being parsed.
	 */
	public void setFile( File file ) {
		this.file = file;
	}

	public File getFile() {
		return file;
	}


	/**
	 * Sets the offset, from the beginning of the UHS file, to the binary hunk.
	 * <p>
	 * In the 88a format, there is no binary hunk.
	 * <p>
	 * In the 9x format, there's a special 0x1a byte in the file.
	 * Everything after that is the binary hunk.
	 */
	public void setBinaryHunkOffset( long binHunkOffset ) {
		this.binHunkOffset = binHunkOffset;
	}

	public long getBinaryHunkOffset() {
		return binHunkOffset;
	}

	/**
	 * Sets the binary hunk: either a preloaded array, or null.
	 *
	 * @param binHunk  the array, can be null if binaryDeferred is true
	 * @see #setBinaryHunkLength(long)
	 */
	public void setBinaryHunk( byte[] binHunk ) {
		this.binHunk = binHunk;
	}

	/**
	 * Sets the length of the binary hunk.
	 *
	 * @param binHunkLength  the length of the binary hunk (required even when binhunk is null)
	 * @see #setBinaryHunk(byte[])
	 */
	public void setBinaryHunkLength( long binHunkLength ) {
		this.binHunkLength = binHunkLength;
	}


	/**
	 * Sets the root node whose tree is under construction.
	 */
	public void setRootNode( UHSRootNode rootNode ) {
		this.rootNode = rootNode;
	}

	public UHSRootNode getRootNode() {
		return rootNode;
	}


	/**
	 * Sets the key for decrypting various hunks' text in the 9x format.
	 *
	 * @param key
	 * @see net.vhati.openuhs.core.UHSParser#decryptNestString(CharSequence, int[])
	 * @see net.vhati.openuhs.core.UHSParser#decryptTextHunk(CharSequence, int[])
	 * @see net.vhati.openuhs.core.UHSParser#generate9xKey(CharSequence)
	 */
	public void setEncryptionKey( int[] key ) {
		this.encryptionKey = key;
	}

	public int[] getEncryptionKey() {
		return encryptionKey;
	}


	/**
	 * An amount to add to any index passed into getLine().
	 * <p>
	 * For the 9x format, this is used to ignore the fake 88a header lines.
	 *
	 * @param lineIndexFudge  the number of lines to ignore
	 * @see #getLine(int)
	 */
	public void setLineFudge( int lineIndexFudge ) {
		this.lineIndexFudge = lineIndexFudge;
	}

	public int getLineFudge() {
		return lineIndexFudge;
	}

	/**
	 * Sets a list of all line break delimited strings decoded from the UHS file.
	 */
	public void setAllLines( List<String> allLines ) {
		this.allLines = allLines;
	}


	/**
	 * Registers an extraneous id to ignore.
	 * <p>
	 * Rather than track unnecessary ids, this will suppress warnings about
	 * unsatisfied Incentive hunk references.
	 * <p>
	 * HyperImage hunks sometimes have their main image line referenced in
	 * an Incentive hunk.
	 */
	public void registerExtraId( ExtraNodeId extraId ) {
		extraIds.add( extraId );
	}

	/**
	 * Returns an object representing an extraneous id, or null.
	 */
	public ExtraNodeId getExtraId( int id ) {
		for ( ExtraNodeId extraId : extraIds ) {
			if ( extraId.getId() == id ) return extraId;
		}
		return null;
	}


	/**
	 * Returns a reference to read bytes from the binary hunk.
	 * <p>
	 * Images, comments, sounds, etc., are stored there.
	 * <p>
	 * If the binary hunk has been set to an array, an ArrayByteReference
	 * will be returned. Otherwise, a FileRegionByteReference will be
	 * returned, as long as the binary hunk offset has been set.
	 * <p>
	 * The offset here is relative to the start of the binary hunk, NOT the beginning of the file.
	 *
	 * @param offset  the start offset of the data, within the binary hunk
	 * @param length  the number of bytes to read
	 * @return a ByteReference to retrieve the relevant bytes
	 * @see #setBinaryHunk(byte[])
	 * @see #setBinaryHunkOffset(long)
	 */
	public ByteReference readBinaryHunk( long offset, int length ) {
		if ( offset < 0 || length < 0 ) {
			throw new IllegalArgumentException( String.format( "Offset (%d) and length (%d) must not be negative (last parsed line: %d)", offset, length, getLastParsedLineNumber() ) );
		}
		if ( offset+length > binHunkLength ) {
			throw new ArrayIndexOutOfBoundsException( String.format( "Offset (%d) + length (%d) exceeded the binary hunk length (%d) (last parsed line: %d)", offset, length, binHunkLength, getLastParsedLineNumber() ) );
		}

		if ( isBinaryDeferred() ) {
			if ( binHunkOffset >= 0 ) {
				return new FileRegionByteReference( file, binHunkOffset+offset, length );
			}
			else {
				throw new IllegalStateException( "Binary hunk offset was not set" );
			}
		}
		else {
			if ( binHunk != null ) {
				byte[] data = new byte[length];
				System.arraycopy( binHunk, (int)offset, data, 0, length );
				return new ArrayByteReference( data );
			}
			else {
				throw new IllegalStateException( "Binary hunk array was not set" );
			}
		}
	}

	/**
	 * Returns the unsigned checksum value stored at the end of the file.
	 * <p>
	 * The last two bytes of a 9x format file are a little-endian 16-bit
	 * unsigned short CRC16 of the entire file, excluding those last two
	 * bytes.
	 * <p>
	 * Java doesn't have unsigned types, so the return value is a masked
	 * int.
	 */
	public int readStoredChecksumValue() {
		int result = -1;
		ByteReference crcRef = readBinaryHunk( binHunkLength-2, 2 );
		InputStream is = null;
		try {
			is = crcRef.getInputStream();

			ByteBuffer crcBuf = ByteBuffer.allocate( 2 );
			crcBuf.order( ByteOrder.LITTLE_ENDIAN );
			for ( int i=0; i < 2; i++ ) {
				int b = is.read();
				if ( b == -1 ) throw new IOException( "Unexpected end of binary hunk" );
				crcBuf.put( (byte)b );
			}
			crcBuf.flip();
			result = crcBuf.getShort() & 0xFFFF;
		}
		catch ( IOException e ) {
			logger.error( "Error reading stored checksum: {}", e );
		}

		return result;
	}

	/**
	 * Returns a string from allLines at a given index, while caching that index for logging purposes.
	 * <p>
	 * Note: While a list of lines is involved, the index is not synonymous
	 * with the line number seen in text editors. When parsing the 9x format,
	 * index is reset to 0 at the end of the fake 88a header.
	 *
	 * @param index  an index within allLines (0-based)
	 */
	public String getLine( int index ) {
		lastLineIndex = index;
		return allLines.get( lineIndexFudge + index );
	}

	/**
	 * Returns true if a given index would be valid in a call to getLine(), false otherwise.
	 */
	public boolean hasLine( int index ) {
		return ( (lineIndexFudge + index) > 0 && (lineIndexFudge + index) < allLines.size() );
	}

	/**
	 * Returns the 1-based line number of the index in the last call to getLine().
	 * <p>
	 * This will be counted from the very beginning of the file,
	 * including 9x format's fake 88a header.
	 */
	public int getLastParsedLineNumber() {
		return ( lineIndexFudge + lastLineIndex + 1 );
	}
}
