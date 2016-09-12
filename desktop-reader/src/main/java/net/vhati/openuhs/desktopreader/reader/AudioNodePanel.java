package net.vhati.openuhs.desktopreader.reader;

import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;

import net.vhati.openuhs.core.UHSAudioNode;
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.desktopreader.reader.NodePanel;


/**
 * A reusable panel that represents a UHSAudioNode.
 *
 * @see net.vhati.openuhs.core.UHSAudioNode
 */
public class AudioNodePanel extends NodePanel {

	protected UHSAudioNode audioNode = null;
	protected MinimalSoundPlayer playerPanel = null;


	public AudioNodePanel() {
		super();

		this.setLayout( new GridBagLayout() );
	}


	@Override
	public boolean accept( UHSNode node ) {
		if ( node instanceof UHSAudioNode ) return true;
		return false;
	}

	@Override
	public void setNode( UHSNode node, boolean showAll ) {
		reset();
		if ( !accept( node ) ) return;

		super.setNode( node, showAll );
		audioNode = (UHSAudioNode)node;

		GridBagConstraints gridC = new GridBagConstraints();
		gridC.gridy = 0;
		gridC.gridwidth = GridBagConstraints.REMAINDER;  //End Row
		gridC.weightx = 1.0;
		gridC.weighty = 0;
		gridC.fill = GridBagConstraints.HORIZONTAL;
		gridC.insets = new Insets( 1, 2, 1, 2 );

		playerPanel = new MinimalSoundPlayer( audioNode.getRawAudioContent() );
		this.add( playerPanel, gridC );
		gridC.gridy++;

		this.revalidate();
		this.repaint();
	}

	@Override
	public void reset() {
		if ( playerPanel != null ) playerPanel.stop();
		audioNode = null;
		playerPanel = null;
		this.removeAll();
		super.reset();

		this.revalidate();
		this.repaint();
	}
}
