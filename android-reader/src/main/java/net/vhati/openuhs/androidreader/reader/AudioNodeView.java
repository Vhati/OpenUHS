package net.vhati.openuhs.androidreader.reader;

import java.io.InputStream;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.androidreader.AndroidUHSConstants;
import net.vhati.openuhs.androidreader.reader.NodeView;
import net.vhati.openuhs.androidreader.reader.AudioPlayerView;
import net.vhati.openuhs.core.ByteReference;
import net.vhati.openuhs.core.UHSAudioNode;
import net.vhati.openuhs.core.UHSNode;


public class AudioNodeView extends NodeView {

	private final Logger logger = LoggerFactory.getLogger( AndroidUHSConstants.LOG_TAG );

	protected UHSAudioNode audioNode = null;
	protected AudioPlayerView playerView = null;


	public AudioNodeView( Context context ) {
		super( context );
		this.setClickable( false );
		this.setFocusable( false );

		playerView = new AudioPlayerView( this.getContext() );
		playerView.setLabelText( "This is a sound hint." );
		this.addView( playerView );
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
			playerView.setAudio( audioBytes );
		}
		catch ( IOException e ) {
			logger.error( "Error loading binary content of {} node (\"{}\"): {}", audioNode.getType(), audioNode.getRawStringContent(), e );
			Toast.makeText( this.getContext(), "Error loading audio", Toast.LENGTH_LONG ).show();

			reset();
			return;
		}
		finally {
			try {if ( is != null ) is.close();} catch ( IOException e ) {}
		}
	}

	@Override
	public void reset() {
		audioNode = null;
		if ( playerView != null ) playerView.reset();
		super.reset();
	}
}
