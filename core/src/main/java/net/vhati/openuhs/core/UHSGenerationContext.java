package net.vhati.openuhs.core;

import java.util.HashMap;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.Writer;


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

	// Pass 1
	private Map<Integer, Integer> idToLineMap = new HashMap<Integer, Integer>();
	private int binHunkOffset = 0;

	private int highestBinSectionOffset = 0;
	private int highestBinSectionLength = 0;

	// Pass 2
	private int offsetNumberWidth = -1;
	private int lengthNumberWidth = -1;

	private Writer textWriter = null;
	private ByteArrayOutputStream binStream = new ByteArrayOutputStream();


	public UHSGenerationContext() {
	}


	public void setPhase( int n ) {
		phase = n;

		currentBinHunkLength = 0;
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
		else if ( phase == 2 ) {
			throw new NullPointerException( "No line was registered for node id: "+ id );
		}
		else {
			return -1;
		}
	}

	public void setTextWriter( Writer textWriter ) {
		this.textWriter = textWriter;
	}

	public Writer getTextWriter() {
		return textWriter;
	}

	public int getNextBinaryOffset() {
		if ( isPhaseOne() || isPhaseTwo() ) return binHunkOffset + currentBinHunkLength;

		return binHunkOffset + binStream.size();
	}

	public void registerBinarySection( int sectionLength ) {
		highestBinSectionOffset = Math.max( highestBinSectionOffset, currentBinHunkLength );
		highestBinSectionLength = Math.max( highestBinSectionLength, sectionLength );

		currentBinHunkLength += sectionLength;
	}

	public ByteArrayOutputStream getBinaryHunkOutputStream() {
		return binStream;
	}


	public void setBinaryHunkOffset( int binHunkOffset ) {
		this.binHunkOffset = binHunkOffset;
	}


	/**
	 * Returns the amount of zero-padding needed for binary segment offsets.
	 *
	 * The default is 6, although it may be 7 when the total text+binary is large.
	 */
	public int getOffsetNumberWidth() {
		if ( isPhaseOne() ) return DEFAULT_OFFSET_NUMBER_WIDTH;

		if ( offsetNumberWidth == -1 ) {
			int highestOffsetWidth = Integer.toString( binHunkOffset + highestBinSectionOffset ).length();
			offsetNumberWidth = Math.max( highestOffsetWidth, DEFAULT_OFFSET_NUMBER_WIDTH );
		}
		return offsetNumberWidth;
	}

	/**
	 * Returns the amount of zero-padding needed for binary segment lengths.
	 *
	 * The default is 6, although it may be 7 when the binary hunk is large.
	 */
	public int getLengthNumberWidth() {
		if ( isPhaseOne() ) return DEFAULT_LENGTH_NUMBER_WIDTH;

		if ( lengthNumberWidth == -1 ) {
			int lengthWidth = Integer.toString( highestBinSectionLength ).length();
			lengthNumberWidth = Math.max( lengthWidth, DEFAULT_LENGTH_NUMBER_WIDTH );
		}
		return lengthNumberWidth;
	}
}
