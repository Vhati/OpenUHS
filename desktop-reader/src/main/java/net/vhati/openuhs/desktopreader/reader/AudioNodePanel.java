package net.vhati.openuhs.desktopreader.reader;

import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.io.InputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.ByteReference;
import net.vhati.openuhs.core.UHSAudioNode;
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.desktopreader.reader.NodePanel;


/**
 * A reusable panel that represents a UHSAudioNode.
 *
 * @see net.vhati.openuhs.core.UHSAudioNode
 */
public class AudioNodePanel extends NodePanel {

	private final Logger logger = LoggerFactory.getLogger( AudioNodePanel.class );

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

		ByteReference audioRef = audioNode.getRawAudioContent();
		InputStream is = null;
		try {
			is = audioRef.getInputStream();
			byte[] audioBytes = new byte[(int)audioRef.length()];
			int offset = 0;
			int count = 0;
			while ( offset < audioBytes.length && (count=is.read( audioBytes, offset, audioBytes.length-offset )) >= 0 ) {
				offset += count;
			}
			if ( offset < audioBytes.length ) {
				throw new IOException( "Could not completely read audio content" );
			}

			playerPanel = new MinimalSoundPlayer( audioBytes );
			this.add( playerPanel, gridC );
			gridC.gridy++;
		}
		catch ( IOException e ) {
			logger.error( "Error loading binary content of {} node (\"{}\"): {}", audioNode.getType(), audioNode.getRawStringContent(), e );

			reset();
			return;
		}
		finally {
			try {if ( is != null ) is.close();} catch ( IOException e ) {}
		}

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
