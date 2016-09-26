package net.vhati.openuhs.desktopreader.reader;

import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.io.InputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.ByteReference;
import net.vhati.openuhs.core.UHSImageNode;
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.desktopreader.reader.NodePanel;


/**
 * A reusable panel that represents a UHSImageNode.
 *
 * @see net.vhati.openuhs.core.UHSImageNode
 */
public class ImageNodePanel extends NodePanel {

	private final Logger logger = LoggerFactory.getLogger( ImageNodePanel.class );

	protected UHSImageNode imageNode = null;


	public ImageNodePanel() {
		super();

		this.setLayout( new GridBagLayout() );
	}


	@Override
	public boolean accept( UHSNode node ) {
		if ( node instanceof UHSImageNode ) return true;
		return false;
	}

	@Override
	public void setNode( UHSNode node, boolean showAll ) {
		reset();
		if ( !accept( node ) ) return;

		super.setNode( node, showAll );
		imageNode = (UHSImageNode)node;

		GridBagConstraints gridC = new GridBagConstraints();
		gridC.gridy = 0;
		gridC.gridwidth = GridBagConstraints.REMAINDER;  //End Row
		gridC.weightx = 1.0;
		gridC.weighty = 0;
		gridC.fill = GridBagConstraints.HORIZONTAL;
		gridC.insets = new Insets( 1, 2, 1, 2 );

		ByteReference imageRef = imageNode.getRawImageContent();
		InputStream is = null;
		try {
			is = imageRef.getInputStream();

			JLabel imageLbl = new JLabel( new ImageIcon( ImageIO.read( is ) ) );
			this.add( imageLbl, gridC );
			gridC.gridy++;
		}
		catch ( IOException e ) {
			logger.error( "Error loading binary content of {} node (\"{}\"): {}", imageNode.getType(), imageNode.getRawStringContent(), e );

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
		imageNode = null;
		this.removeAll();
		super.reset();

		this.revalidate();
		this.repaint();
	}


	/**
	 * Returns false, meaning the panel's width is unconstrained by its viewport (horizontal scrolling enabled).
	 */
	@Override
	public boolean getScrollableTracksViewportWidth() {
		return false;
	}
}
