package net.vhati.openuhs.core;

import java.io.File;
import java.util.List;

import net.vhati.openuhs.core.UHSRootNode;


/**
 * A state-tracking object used internally by UHSParser.
 *
 * <p>There is no need to construct an instance of this directly.</p>
 *
 * @see net.vhati.openuhs.core.UHSParser
 */
public class UHSParseContext {
	protected File file = null;
	protected long binOffset = -1;
	protected UHSRootNode rootNode = null;
	protected int[] encryptionKey = null;

	protected List<String> allLines = null;
	protected byte[] binHunk = null;

	protected int lineIndexFudge = 0;
	protected int lastLineIndex = -1;


	public UHSParseContext() {
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
	 *
	 * <p>In the 88a format, there is no binary hunk.</p>
	 *
	 * <p>In the 9x format, there's a special 0x1a byte in the file.
	 * Everything after that is the binary hunk.</p>
	 */
	public void setBinaryHunkOffset( long binOffset ) {
		this.binOffset = binOffset;
	}

	public long getBinaryHunkOffset() {
		return binOffset;
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
	 *
	 * <p>For the 9x format, this is used to ignore the fake 88a header lines.</p>
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

	public void setBinaryHunk( byte[] binHunk ) {
		this.binHunk = binHunk;
	}


	/**
	 * Reads some raw bytes originally from the end of the UHS file.
	 *
	 * <p>Images, comments, sounds, etc., are stored there.</p>
	 *
	 * <p>This offset here is relative to the start of the binary hunk, NOT the beginning of the file.</p>
	 *
	 * @param offset  the start offset of the data, within the binary hunk
	 * @param length  the number of bytes to read
	 * @return the relevant bytes, or null if the offset or length is invalid
	 */
	public byte[] readBinaryHunk( long offset, int length ) {
		if ( offset < 0 || length < 0 || offset+length > binHunk.length )
			return null;
		byte[] result = new byte[length];
		System.arraycopy( binHunk, (int)offset, result, 0, length );

		return result;
	}

	/**
	 * Returns a string from allLines at a given index, while caching that index for logging purposes.
	 *
	 * <p>Note: While a list of lines is involved, the index is not synonymous
	 * with the line number seen in text editors. When parsing the 9x format,
	 * index is reset to 0 at the end of the fake 88a header.</p>
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
	 *
	 * <p>This will be counted from the very beginning of the file,
	 * including 9x format's fake 88a header.</p>
	 */
	public int getLastParsedLineNumber() {
		return ( lineIndexFudge + lastLineIndex + 1 );
	}
}
