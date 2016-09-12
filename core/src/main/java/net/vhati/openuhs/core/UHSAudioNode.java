package net.vhati.openuhs.core;

import net.vhati.openuhs.core.UHSNode;


public class UHSAudioNode extends UHSNode {
	protected byte[] rawAudioContent = null;


	public UHSAudioNode( String type ) {
		super( type );
	}


	/**
	 * Sets this node's audio content, or null.
	 */
	public void setRawAudioContent( byte[] rawAudioContent ) {
		this.rawAudioContent = rawAudioContent;
	}

	public byte[] getRawAudioContent() {
		return rawAudioContent;
	}


	@Override
	public String getPrintableContent() {
		return "^Audio^";
	}
}
