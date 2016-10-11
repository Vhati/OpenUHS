package net.vhati.openuhs.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * A state-tracking object used internally by UHSWriter.
 *
 * <p>There is no need to construct an instance of this directly.</p>
 *
 * @see net.vhati.openuhs.core.UHSWriter
 */
public class UHSGenerationContext {
	private final int DEFAULT_OFFSET_NUMBER_WIDTH = 6;
	private final int DEFAULT_LENGTH_NUMBER_WIDTH = 6;

	private UHSRootNode legacyRootNode = null;
	private int[] encryptionKey = null;

	private int currentBinHunkLength = 0;

	private int phase = 1;

	// Phase 1.
	private Map<Integer, Integer> idToLineMap = new HashMap<Integer, Integer>();
	private List<UHSNode> restrictedNodes = new ArrayList<UHSNode>();

	private long highestBinSectionOffset = 0;
	private long highestBinSectionLength = 0;
	private long highestBinHunkLength = 0;

	private long binHunkOffset = 0;

	// Phase 2.
	private int offsetNumberWidth = -1;
	private int lengthNumberWidth = -1;

	// Phase 3.
	private ByteArrayOutputStream binStream = new ByteArrayOutputStream();


	public UHSGenerationContext() {
		setPhase( 1 );
	}


	public void setPhase( int n ) {
		phase = n;

		highestBinHunkLength = Math.max( currentBinHunkLength, highestBinHunkLength );
		currentBinHunkLength = 0;

		int highestOffsetWidth = Long.toString( binHunkOffset + highestBinSectionOffset ).length();
		offsetNumberWidth = Math.max( highestOffsetWidth, DEFAULT_OFFSET_NUMBER_WIDTH );

		int lengthWidth = Long.toString( highestBinSectionLength ).length();
		lengthNumberWidth = Math.max( lengthWidth, DEFAULT_LENGTH_NUMBER_WIDTH );
	}

	public boolean isPhaseOne() {
		return phase == 1;
	}

	public boolean isPhaseTwo() {
		return phase == 2;
	}

	public boolean isPhaseThree() {
		return phase == 3;
	}


	/**
	 * Sets the root node whose tree is under construction.
	 */
	public void setLegacyRootNode( UHSRootNode legacyRootNode ) {
		this.legacyRootNode = legacyRootNode;
	}

	public UHSRootNode getLegacyRootNode() {
		return legacyRootNode;
	}


	/**
	 * Sets the key for encrypting various hunks' text in the 9x format.
	 *
	 * @param key
	 * @see net.vhati.openuhs.core.UHSWriter#encryptNestString(CharSequence, int[])
	 * @see net.vhati.openuhs.core.UHSWriter#encryptTextHunk(CharSequence, int[])
	 */
	public void setEncryptionKey( int[] key ) {
		this.encryptionKey = key;
	}

	public int[] getEncryptionKey() {
		return encryptionKey;
	}


	public void putLine( int id, int pendingLine ) {
		idToLineMap.put( new Integer( id ), new Integer( pendingLine ) );
	}

	public int getLine( int id ) {
		Integer resolvedLine = idToLineMap.get( new Integer( id ) );
		if ( resolvedLine != null ) {
			return resolvedLine.intValue();
		}
		else if ( isPhaseOne() ) {
			return -1;
		}
		else {
			throw new NullPointerException( "No line was registered for node id: "+ id );
		}
	}


	/**
	 * Registers a node to include in the generated Incentive node.
	 *
	 * @param node  the node to register
	 * @see net.vhati.openuhs.core.UHSNode#setRestriction(int)
	 */
	public void registerRestrictedNode( UHSNode node ) {
		restrictedNodes.add( node );
	}

	public List<UHSNode> getRestrictedNodes() {
		return restrictedNodes;
	}


	public long getNextBinaryOffset() {
		return binHunkOffset + currentBinHunkLength;
	}

	public void registerBinarySection( long sectionLength ) {
		highestBinSectionOffset = Math.max( highestBinSectionOffset, currentBinHunkLength );
		highestBinSectionLength = Math.max( highestBinSectionLength, sectionLength );

		currentBinHunkLength += sectionLength;
	}


	public void setBinaryHunkOffset( long binHunkOffset ) {
		this.binHunkOffset = binHunkOffset;
	}


	public ByteArrayOutputStream getBinaryHunkOutputStream() {
		return binStream;
	}


	/**
	 * Writes data from a ByteReference to the binary hunk output stream.
	 */
	public void writeBinarySegment( ByteReference ref ) throws IOException {
		InputStream is = null;
		try {
			is = ref.getInputStream();
			byte[] buf = new byte[512];
			int count;
			while ( (count=is.read( buf )) != -1 ) {
				binStream.write( buf, 0, count );
			}
		}
		finally {
			try {if ( is != null ) is.close();} catch ( IOException e ) {}
		}
	}


	/**
	 * Returns the amount of zero-padding needed for binary segment offsets.
	 *
	 * The default is 6, although it may be 7 when the total text+binary is large.
	 */
	public int getOffsetNumberWidth() {
		return offsetNumberWidth;
	}

	/**
	 * Returns the amount of zero-padding needed for binary segment lengths.
	 *
	 * The default is 6, although it may be 7 when the binary hunk is large.
	 */
	public int getLengthNumberWidth() {
		return lengthNumberWidth;
	}


	/**
	 * Returns the current estimate for the file's total byte count.
	 *
	 * <p>This includes the 2-byte CRC16 checksum value at the end.</p>
	 *
	 * <p>The returned length will only be accurate after the binary hunk's
	 * length and offset have been determined from previous phases.</p>
	 */
	public long getExpectedFileLength() {
		return binHunkOffset + highestBinHunkLength + 2;
	}
}
