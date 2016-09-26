package net.vhati.openuhs.core;

import net.vhati.openuhs.core.ByteReference;
import net.vhati.openuhs.core.UHSNode;


public class UHSAudioNode extends UHSNode {
	protected ByteReference rawAudioContent = null;


	public UHSAudioNode( String type ) {
		super( type );
	}


	/**
	 * Sets this node's audio content, or null.
	 */
	public void setRawAudioContent( ByteReference rawAudioContent ) {
		this.rawAudioContent = rawAudioContent;
	}

	public ByteReference getRawAudioContent() {
		return rawAudioContent;
	}


	@Override
	public String getPrintableContent() {
		return "^Audio^";
	}
}
