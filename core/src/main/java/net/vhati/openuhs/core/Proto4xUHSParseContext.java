package net.vhati.openuhs.core;

import java.io.File;
import java.util.List;

import net.vhati.openuhs.core.UHSRootNode;


/**
 * A state-tracking object used internally by UHSProto4xParser.
 *
 * <p>There is no need to construct an instance of this directly.</p>
 *
 * @see net.vhati.openuhs.core.Proto4xUHSParser
 */
public class Proto4xUHSParseContext {
	protected File file = null;
	protected File workingDir = null;
	protected UHSRootNode rootNode = null;

	protected List<String> allLines = null;

	protected int lineIndexFudge = 0;
	protected int lastLineIndex = -1;


	public Proto4xUHSParseContext() {
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
	 * Sets the directory to use for resolving relative file paths.
	 */
	public void setWorkingDir( File workingDir ) {
		this.workingDir = workingDir;
	}

	public File getWorkingDir() {
		return workingDir;
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
	 * An amount to add to any index passed into getLine().
	 *
	 * <p>For the proto4x format, there are no ignored lines.</p>
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
	 * Returns a string from allLines at a given index, while caching that index for logging purposes.
	 *
	 * <p>Note: While a list of lines is involved, the index is not synonymous
	 * with the line number seen in text editors. When parsing the proto4x
	 * format, there happen to be no ignored lines, however.</p>
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
	 * <p>This will be counted from the very beginning of the file.</p>
	 */
	public int getLastParsedLineNumber() {
		return ( lineIndexFudge + lastLineIndex + 1 );
	}
}
