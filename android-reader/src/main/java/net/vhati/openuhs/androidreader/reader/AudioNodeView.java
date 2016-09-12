package net.vhati.openuhs.androidreader.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import net.vhati.openuhs.androidreader.reader.NodeView;
import net.vhati.openuhs.androidreader.reader.AudioPlayerView;
import net.vhati.openuhs.core.UHSAudioNode;
import net.vhati.openuhs.core.UHSNode;


public class AudioNodeView extends NodeView {
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

		playerView.setAudio( audioNode.getRawAudioContent() );
	}

	@Override
	public void reset() {
		audioNode = null;
		if ( playerView != null ) playerView.reset();
		super.reset();
	}
}
