package net.vhati.openuhs.androidreader.reader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioTrack.OnPlaybackPositionUpdateListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.androidreader.R;
import net.vhati.openuhs.androidreader.AndroidUHSConstants;
import net.vhati.openuhs.core.UHSAudioNode;
import net.vhati.openuhs.core.UHSNode;


public class AudioPlayerView extends LinearLayout {

	private final Logger logger = LoggerFactory.getLogger( AndroidUHSConstants.LOG_TAG );

	private static final int WAV_HEADER_SIZE = 44;

	private Context context = null;
	private Button playBtn = null;
	private TextView playerLbl = null;

	private byte[] audioBytes = null;
	private WavFormat wavFormat = null;
	private TrackInfo trackInfo = null;

	private AudioTrack currentTrack = null;
	private AudioStreamThread currentStreamThread = null;


	public AudioPlayerView( Context context ) {
		super( context );
		this.context = context;

		this.setOrientation( LinearLayout.VERTICAL );
		this.setClickable( false );
		this.setFocusable( false );

		this.inflate( context, R.layout.audio_player_view, this );
		playBtn = (Button)findViewById( R.id.audioPlayBtn );
		playerLbl = (TextView)findViewById( R.id.audioPlayerLbl );

		playBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick( View v ) {
				play();
			}
		});
	}



	public void setAudio( byte[] audioBytes ) {
		reset();
		if ( audioBytes == null ) return;

		this.audioBytes = audioBytes;

		try {
			InputStream is = new ByteArrayInputStream( audioBytes );
			wavFormat = readHeader( is );
			trackInfo = createTrackInfo( wavFormat );
		}
		catch ( Exception e ) {
			Toast.makeText( this.getContext(), String.format( "Audio decoding failed: %s", e.getMessage() ), Toast.LENGTH_LONG ).show();
			logger.error( "Audio decoding failed", e );

			wavFormat = null;
			trackInfo = null;
		}
	}

	public void setLabelText( String s ) {
		playerLbl.setText( s );
	}

	public void reset() {
		abortPlayback();

		audioBytes = null;
		wavFormat = null;
		trackInfo = null;
	}

	/**
	 * Immediately halts and releases the AudioTrack and the thread feeding it.
	 *
	 * <p>Node content will still be available for playing again.</p>
	 */
	public void abortPlayback() {
		if ( currentStreamThread != null ) {
			currentStreamThread.abort();
			currentStreamThread = null;
		}
		if ( currentTrack != null ) {
			synchronized ( currentTrack ) {
				currentTrack.pause();
				currentTrack.flush();
				currentTrack.release();
				currentTrack = null;
			}
		}
	}


	/**
	 * Decodes WAV header info from an InputStream.
	 *
	 * <p>Afterward, the stream's position will be at the first data chunk.</p>
	 *
	 * @param is  a stream supporting mark()
	 * @return an object describing header info
	 */
	public WavFormat readHeader( InputStream is ) throws IOException, WavException {
		ByteOrder origOrder = null;

		byte[] magic = new byte[4];
		is.mark( magic.length );  // Setup backtracking.
		is.read( magic, 0, magic.length );
		is.reset();  // Unread.
		if ( magic[0] == 0x52 && magic[1] == 0x49 && magic[2] == 0x46 && magic[3] == 0x46 ) {
			origOrder = ByteOrder.LITTLE_ENDIAN;  // RIFF
		}
		else {
			// RIFX, rare byte-swapped counterpart.
			//origOrder = ByteOrder.BIG_ENDIAN;
			// Dunno how to translate the data chunk later. *shrug*
			throw new WavException( "Error: Sound bytes didn't begin with RIFF" );
		}

		ByteBuffer wavBuf = ByteBuffer.allocate( WAV_HEADER_SIZE );
		wavBuf.order( origOrder );
		is.read( wavBuf.array(), wavBuf.arrayOffset(), wavBuf.capacity() );
		wavBuf.rewind();

		wavBuf.order( ByteOrder.LITTLE_ENDIAN );
		wavBuf.position( 20 );
		int headerEncoding = wavBuf.getShort();
		int headerChannels = wavBuf.getShort();
		int headerRate = wavBuf.getInt();

		wavBuf.position( wavBuf.position() + 6 );
		int headerBits = wavBuf.getShort();

		/*
		 * Chunks begin with an id, 4 single-byte characters. Next comes the
		 * chunk's total size (4-byte int). The characters can be read as an
		 * int for testing.
		 *
		 * Data chunk begins with "data" (0x61746164).
		 */
		while ( wavBuf.getInt() != 0x61746164 ) {  // Skip ahead until the data chunk.
			int chunkSize = wavBuf.getInt();
			is.skip( chunkSize );

			wavBuf.rewind();
			is.read( wavBuf.array(), wavBuf.arrayOffset(), 8 );  // 4-byte id + 4-byte size.
			wavBuf.rewind();
		}
		// In the data chunk now.
		int dataSize = wavBuf.getInt();
		if ( dataSize <= 0 ) {
			throw new WavException( String.format( "Unsupported sound data chunk size: %d", dataSize ) );
		}

		WavFormat result = new WavFormat();
		result.headerEncoding = headerEncoding;
		result.headerChannels = headerChannels;
		result.headerRate = headerRate;
		result.headerBits = headerBits;
		result.dataChunkPos = wavBuf.position();
		result.dataChunkSize = dataSize;

		return result;
	}

	/**
	 * Returns an object holding AudioTrack values needed to play a WavFormat.
	 */
	private TrackInfo createTrackInfo( WavFormat wavFormat ) throws WavException {
		int trackChannelCfg = 0;
		if ( wavFormat.headerChannels == 1 ) {
			trackChannelCfg = AudioFormat.CHANNEL_OUT_MONO;
		}
		else if ( wavFormat.headerChannels == 2 ) {
			trackChannelCfg = AudioFormat.CHANNEL_OUT_STEREO;
		}
		else {
			throw new WavException( String.format( "Unsupported sound channels: %d", wavFormat.headerChannels ) );
		}

		if ( wavFormat.headerEncoding != 1 ) {  // 1 is uncompressed, Linear PCM.
			throw new WavException( String.format( "Unsupported sound encoding: %d", wavFormat.headerEncoding ) );
		}
		if ( wavFormat.headerBits != 16 ) {
			throw new WavException( String.format( "Unsupported sound bitness: %d", wavFormat.headerBits ) );
		}
		int trackEncoding = AudioFormat.ENCODING_PCM_16BIT;

		// This is a common range. Dunno how to ask Android for what it likes.
		if ( wavFormat.headerRate < 11025 || wavFormat.headerRate > 48000 ) {
			throw new WavException( String.format( "Unsupported sound rate: %d", wavFormat.headerRate ) );
		}
		int trackRate = wavFormat.headerRate;

		int trackBufferSize = AudioTrack.getMinBufferSize( trackRate, trackChannelCfg, trackEncoding );
		if ( trackBufferSize == AudioTrack.ERROR ) {
			throw new WavException( "AudioTrack.getMinBufferSize() returned ERROR" );
		}
		else if ( trackBufferSize == AudioTrack.ERROR_BAD_VALUE ) {
			throw new WavException( "AudioTrack.getMinBufferSize() returned BAD_VALUE" );
		}

		TrackInfo result = new TrackInfo();
		result.channelCfg = trackChannelCfg;
		result.encoding = trackEncoding;
		result.rate = trackRate;
		result.bufferSize = trackBufferSize;

		return result;
	}


	/**
	 * Plays the node's audio content.
	 *
	 * <p>A new AudioTrack is created, which will release() itself when done
	 * playing.</p>
	 *
	 * <p>A background thread will be started to feed bytes into the track as
	 * it plays.</p>
	 */
	public void play() {
		abortPlayback();

		AudioTrack track = null;
		Exception ex = null;
		try {
			// Avoid AudioTrack.MODE_STATIC. It's buggy.

			// Use AudioTrack.MODE_STREAM instead.

			track = new AudioTrack( AudioManager.STREAM_MUSIC, trackInfo.rate, trackInfo.channelCfg, trackInfo.encoding, trackInfo.bufferSize, AudioTrack.MODE_STREAM );

			track.setNotificationMarkerPosition( wavFormat.getTotalFrames()-1 );
			track.setPlaybackPositionUpdateListener(new OnPlaybackPositionUpdateListener() {
				@Override
				public void onPeriodicNotification( AudioTrack track ) {
				}
				@Override
				public void onMarkerReached( AudioTrack track ) {
					synchronized ( track ) {
						logger.info( "Sound track finished playing" );
						track.release();

						if ( track == AudioPlayerView.this.currentTrack ) {
							AudioPlayerView.this.currentTrack = null;
						}
						// Track finished, so the buffering thread should have already ended.
					}
				}
			});

			currentTrack = track;

			// With MODE_STREAM, play first, then fill the track buffer with write().
			logger.info( "Playing sound track" );
			track.play();
		}
		catch ( IllegalArgumentException e ) {
			ex = e;
		}
		catch ( IllegalStateException e ) {
			ex = e;
		}
		finally {
			if ( ex != null ) {
				Toast.makeText( this.getContext(), String.format( "Could not play audio: %s", ex.toString() ), Toast.LENGTH_LONG ).show();
				logger.error( "Could not play audio", ex );

				if ( track != null ) {
					synchronized ( track ) {
						track.release();
					}
				}
				currentTrack = null;
				return;
			}
		}

		AudioStreamThread t = new AudioStreamThread( wavFormat, audioBytes, track );
		currentStreamThread = t;
		t.start();
	}



	private class AudioStreamThread extends Thread {
		private volatile boolean aborting = false;

		private WavFormat wavFormat;
		private byte[] audioBytes;
		private AudioTrack track;


		public AudioStreamThread( WavFormat wavFormat, byte[] audioBytes, AudioTrack track ) {
			super();
			this.setPriority( Thread.MIN_PRIORITY );
			this.setDaemon( true );

			this.wavFormat = wavFormat;
			this.audioBytes = audioBytes;
			this.track = track;
		}

		public void abort() {
			aborting = true;
		}

		@Override
		public void run() {
			logger.info( "Audio stream thread started" );
			try {
				InputStream is = new ByteArrayInputStream( audioBytes );
				is.skip( wavFormat.dataChunkPos );  // Skip to the data chunk.

				byte[] buf = new byte[512];  // TODO: This should be half TrackInfo's buffer size.
				int chunkRemaining = wavFormat.dataChunkSize;
				int bytesRead = 0;
				while ( !aborting && chunkRemaining > 0 && (bytesRead=is.read( buf )) != -1 ) {
					int writeRemaining = Math.min( bytesRead, chunkRemaining );

					for ( int bytesWritten=0; !aborting && bytesWritten < writeRemaining; ) {
						int writeResult;

						synchronized ( track ) {
							writeResult = track.write( buf, bytesWritten, writeRemaining-bytesWritten );
						}
						if ( writeResult == AudioTrack.ERROR_INVALID_OPERATION ) {
							throw new IOException( "track.write() returned INVALID_OPERATION" );
						}
						else if ( writeResult == AudioTrack.ERROR_BAD_VALUE ) {
							throw new IOException( "track.write() returned BAD_VALUE" );
						}
						else if ( writeResult == AudioTrack.ERROR_DEAD_OBJECT ) {
							throw new IOException( "track.write() returned ERROR_DEAD_OBJECT" );
						}
						else if ( writeResult == AudioTrack.ERROR ) {
							throw new IOException( "track.write() returned ERROR" );
						}
						bytesWritten += writeResult;
					}
					chunkRemaining -= writeRemaining;
				}
				logger.info( "Audio bytes left unwritten ({}/{})", chunkRemaining, wavFormat.dataChunkSize );

				// With MODE_STATIC, post a runnable to the UI thread to play() after writing.
			}
			catch ( final IOException e ) {
				logger.error( "Could not play audio", e );

				AudioPlayerView.this.post(new Runnable() {
					@Override
					public void run() {
						Toast.makeText( AudioPlayerView.this.getContext(), String.format( "Error playing audio: %s", e.getMessage() ), Toast.LENGTH_LONG ).show();
					}
				});
			}
			finally {
				logger.info( "Audio stream thread ended" );
			}
		}
	}



	public static class WavFormat {
		public int headerEncoding = 0;
		public int headerChannels = 0;
		public int headerRate = 0;
		public int headerBits = 0;
		public long dataChunkPos = 0;
		public int dataChunkSize = 0;

		public int getBytesPerFrame() {
			return headerChannels * (headerBits / 8);
		}
		public int getTotalFrames() {
			return dataChunkSize / getBytesPerFrame();
		}
	}



	public static class TrackInfo {
		public int encoding = 0;
		public int channelCfg = 0;
		public int rate = 0;
		public int bufferSize = 0;
	}



	public static class WavException extends IOException {

		public WavException() {
		}

		public WavException( String message ) {
			super( message );
		}

		public WavException( Throwable cause ) {
			super( cause );
		}

		public WavException( String message, Throwable cause ) {
			super( message, cause );
		}
	}
}
