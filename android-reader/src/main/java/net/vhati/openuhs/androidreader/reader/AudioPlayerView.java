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
	private static final int trackMode = AudioTrack.MODE_STREAM;

	private Context context = null;
	private Button playBtn = null;
	private TextView playerLbl = null;

	private Object trackLock = new Object();
	private byte[] soundBytes = null;
	private WavFormat fmt = null;
	private TrackInfo trackInfo = null;
	private AudioTrack track = null;

	private volatile boolean keepAlive = false;       // Tells the write thread to stop.
	private volatile boolean writeWorkerDone = true;  // Tells this the thread's stopped.


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



	public void setAudio( byte[] soundBytes ) {
		reset();
		if ( soundBytes == null ) return;

		this.soundBytes = soundBytes;

		decodeBytes();
	}

	public void setLabelText( String s ) {
		playerLbl.setText( s );
	}

	public void reset() {
		synchronized ( trackLock ) {
			if ( track != null ) track.release();
			track = null;
			keepAlive = false;
		}
		soundBytes = null;
	}


	private void decodeBytes() {
		InputStream is = new ByteArrayInputStream( soundBytes );

		try {
			fmt = readHeader( is );

			int trackChannelCfg = 0;
			if ( fmt.headerChannels == 1 ) {
				trackChannelCfg = AudioFormat.CHANNEL_OUT_MONO;
			}
			else if ( fmt.headerChannels == 2 ) {
				trackChannelCfg = AudioFormat.CHANNEL_OUT_STEREO;
			}
			else {
				throw new WavException( "Unsupported sound channels: "+ fmt.headerChannels );
			}

			if ( fmt.headerEncoding != 1 /* Linear PCM*/ ) {
				throw new WavException( "Unsupported sound encoding: "+ fmt.headerEncoding );
			}
			if ( fmt.headerBits != 16 ) {
				throw new WavException( "Unsupported sound bitness: "+ fmt.headerBits );
			}
			int trackEncoding = AudioFormat.ENCODING_PCM_16BIT;

			// This is a common range. Dunno how to ask Android for what it likes.
			if ( fmt.headerRate < 11025 || fmt.headerRate > 48000 ) {
				throw new WavException( "Unsupported sound rate: "+ fmt.headerRate );
			}
			int trackRate = fmt.headerRate;

			int trackBufferSize = soundBytes.length;
			if ( trackMode == AudioTrack.MODE_STREAM ) {
				trackBufferSize = AudioTrack.getMinBufferSize( trackRate, trackChannelCfg, trackEncoding );
				if ( trackBufferSize == AudioTrack.ERROR ) {
					throw new WavException( "getMinBufferSize() returned ERROR" );
				}
				else if ( trackBufferSize == AudioTrack.ERROR_BAD_VALUE ) {
					throw new WavException( "getMinBufferSize() returned BAD_VALUE" );
				}
			}

			trackInfo = new TrackInfo();
			trackInfo.channelCfg = trackChannelCfg;
			trackInfo.encoding = trackEncoding;
			trackInfo.rate = trackRate;
			trackInfo.bufferSize = trackBufferSize;
		}
		catch ( Exception e ) {
			Toast.makeText( this.getContext(), String.format( "Audio decoding failed: %s", e.toString() ), Toast.LENGTH_LONG ).show();
			logger.error( "Audio decoding failed", e );
		}
		finally {
			try {if ( is != null ) is.close();} catch ( Exception e ) {}
		}
	}


	/**
	 * Decodes WAV header info from a stream.
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
			// Dunno how to translate the data chubk later. *shrug*
			throw new WavException( "Error: Sound bytes didn't begin with RIFF" );
		}

		ByteBuffer soundBuf = ByteBuffer.allocate( WAV_HEADER_SIZE );
		soundBuf.order( origOrder );
		is.read( soundBuf.array(), soundBuf.arrayOffset(), soundBuf.capacity() );
		soundBuf.rewind();

		soundBuf.order( ByteOrder.LITTLE_ENDIAN );
		soundBuf.position( 20 );
		int headerEncoding = soundBuf.getShort();
		int headerChannels = soundBuf.getShort();
		int headerRate = soundBuf.getInt();

		soundBuf.position( soundBuf.position() + 6 );
		int headerBits = soundBuf.getShort();

		while ( soundBuf.getInt() != 0x61746164 ) { // "data" marker.
			// Non-data chunk. Size follows, so read that and skip the chunk.
			int chunkSize = soundBuf.getInt();
			is.skip( chunkSize );

			soundBuf.rewind();
			is.read( soundBuf.array(), soundBuf.arrayOffset(), 8 );
			soundBuf.rewind();
		}  // In the data chunk now.
		int dataSize = soundBuf.getInt();
		if ( dataSize <= 0 ) {
			throw new WavException( "Unsupported sound data chunk size: "+ dataSize );
		}

		WavFormat result = new WavFormat();
		result.headerEncoding = headerEncoding;
		result.headerChannels = headerChannels;
		result.headerRate = headerRate;
		result.headerBits = headerBits;
		result.dataChunkPos = soundBuf.position();
		result.dataChunkSize = dataSize;

		return result;
	}


	/**
	 * Plays the node's sound.
	 *
	 * <p>An AudioTrack is created, which will release() when it's done playing.</p>
	 */
	public void play() {
		synchronized ( trackLock ) {
			logger.info( "Sound play(): trackExists ({}), workerDone ({})", (track != null), writeWorkerDone );
			if ( track != null || !writeWorkerDone ) return;

			keepAlive = true;
			writeWorkerDone = false;
			Exception ex = null;
			try {
				track = new AudioTrack( AudioManager.STREAM_MUSIC, trackInfo.rate, trackInfo.channelCfg, trackInfo.encoding, trackInfo.bufferSize, trackMode );

				track.setNotificationMarkerPosition( fmt.getTotalFrames()-1 );
				track.setPlaybackPositionUpdateListener(new OnPlaybackPositionUpdateListener() {
					@Override
					public void onPeriodicNotification( AudioTrack track ) {
					}
					@Override
					public void onMarkerReached( AudioTrack track ) {
						synchronized ( trackLock ) {
							logger.info( "Sound track finished playing" );
							track.release();
							if ( track == AudioPlayerView.this.track ) {
								AudioPlayerView.this.track = null;
							}
							keepAlive = false;
						}
					}
				});

				// For AudioTrack.MODE_STATIC, use writeloop before play,
				// and reloadStaticData() before subsequent play() calls.
				// But you'd need to know when to release() if not when stopping.
				// And MODE_STATIC has a memory leak issue, so avoid it altogether.

				if ( trackMode == AudioTrack.MODE_STREAM ) {  // Write after play.
					logger.info( "Playing sound track" );
					track.play();
				}
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

					if ( track != null ) track.release();
					track = null;
					keepAlive = false;
					writeWorkerDone = true;
					return;
				}
			}
		}

		final long bytesToSkip = fmt.dataChunkPos;
		final int bytesToWrite = fmt.dataChunkSize;
		final byte[] workerBytes = new byte[this.soundBytes.length];   // Not an immutable copy. *shrug*
		System.arraycopy( this.soundBytes, 0, workerBytes, 0, this.soundBytes.length );  // Grr, race condition.

		Runnable r = new Runnable() {
			public void run() {
				logger.info( "Sound worker thread started" );
				try {
					InputStream is = new ByteArrayInputStream( workerBytes );
					is.skip( bytesToSkip );

					byte[] tmpBytes = new byte[512];
					int n = 0;
					int bytesLeft = bytesToWrite;
					while ( keepAlive && bytesLeft > 0 && (n=is.read( tmpBytes )) != -1 ) {
						n = Math.min( n, bytesLeft );
						for ( int bytesWritten=0; keepAlive && bytesWritten < n; ) {
							int tmpInt = track.write( tmpBytes, bytesWritten, n-bytesWritten );

							if ( tmpInt == AudioTrack.ERROR_INVALID_OPERATION ) {
								throw new IOException( "track.write() returned INVALID_OPERATION" );
							}
							else if ( tmpInt == AudioTrack.ERROR_BAD_VALUE ) {
								throw new IOException( "track.write() returned BAD_VALUE" );
							}
							bytesWritten += tmpInt;
						}
						bytesLeft -= n;
					}
					logger.info( "Sound bytes left unwritten ({}/{})", bytesLeft, bytesToWrite );

					// With MODE_STATIC, post a runnable to the UI thread to play() after writing.
				}
				catch ( final IOException e ) {
					logger.error( "Could not play sound", e );

					AudioPlayerView.this.post(new Runnable() {
						@Override
						public void run() {
							Toast.makeText( AudioPlayerView.this.getContext(), String.format( "Error playing audio: %s", e.toString() ), Toast.LENGTH_LONG ).show();
						}
					});
				}
				finally {
					writeWorkerDone = true;
					logger.info( "Sound worker thread ended" );
				}
			}
		};
		Thread t = new Thread( r );
		t.setPriority( Thread.MIN_PRIORITY );
		t.setDaemon( true );
		t.start();
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
