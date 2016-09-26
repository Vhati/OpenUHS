package net.vhati.openuhs.core;

import net.vhati.openuhs.core.ByteReference;
import net.vhati.openuhs.core.UHSNode;


public class UHSImageNode extends UHSNode {
	protected ByteReference rawImageContent = null;


	public UHSImageNode( String type ) {
		super( type );
	}


	/**
	 * Sets this node's image content, or null.
	 */
	public void setRawImageContent( ByteReference rawImageContent ) {
		this.rawImageContent = rawImageContent;
	}

	public ByteReference getRawImageContent() {
		return rawImageContent;
	}


	@Override
	public String getPrintableContent() {
		return "^Image^";
	}
}
