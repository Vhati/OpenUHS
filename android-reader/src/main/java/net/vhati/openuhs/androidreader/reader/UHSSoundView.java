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

import net.vhati.openuhs.androidreader.R;
import net.vhati.openuhs.core.UHSErrorHandler;
import net.vhati.openuhs.core.UHSErrorHandlerManager;
import net.vhati.openuhs.core.UHSNode;


public class UHSSoundView extends LinearLayout {
	private static final int WAV_HEADER_SIZE = 44;
	private static final int trackMode = AudioTrack.MODE_STREAM;

	private Context context = null;
	private Button playBtn = null;
	private TextView errorLabel = null;
	private UHSNode node = null;

	private Object trackLock = new Object();
	private WavFormat fmt = null;
	private TrackInfo trackInfo = null;
	private AudioTrack track = null;

	private boolean keepAlive = false;       // Tells the write thread to stop.
	private boolean writeWorkerDone = true;  // Tells this the thread's stopped.


	public UHSSoundView(Context context) {
		super(context);
		this.context = context;

		this.setOrientation(LinearLayout.VERTICAL);
		setClickable(false);
		setFocusable(false);

		this.inflate(context, R.layout.uhs_sound_view, this);
		playBtn = (Button)findViewById(R.id.playBtn);
		errorLabel = (TextView)this.findViewById(R.id.errorText);

		playBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				android.util.Log.i("OpenUHS", "@!@ Sound play button clicked.");
				play();
			}
		});
	}



	public void setNode(UHSNode node) {
		synchronized (trackLock) {
			if (track != null) track.release();
			track = null;
			keepAlive = false;
		}

		this.node = node;

		if (node.getContentType() != UHSNode.AUDIO) {
			errorLabel.setText("^NON-AUDIO CONTENT^");
		}
		else {
			decodeBytes();
		}
	}


	private void decodeBytes() {
		byte[] soundBytes = (byte[])node.getContent();
		InputStream is = new ByteArrayInputStream(soundBytes);

		try {
			fmt = readHeader(is);

			int trackChannelCfg = 0;
			if (fmt.headerChannels == 1) {
				trackChannelCfg = AudioFormat.CHANNEL_OUT_MONO;
			} else if (fmt.headerChannels == 2) {
				trackChannelCfg = AudioFormat.CHANNEL_OUT_STEREO;
			} else {
				throw new WavException("Unsupported sound channels: "+ fmt.headerChannels +".");
			}

			if (fmt.headerEncoding != 1 /* Linear PCM*/)
				throw new WavException("Unsupported sound encoding: "+ fmt.headerEncoding +".");
			if (fmt.headerBits != 16)
				throw new WavException("Unsupported sound bitness: "+ fmt.headerBits +".");
			int trackEncoding = AudioFormat.ENCODING_PCM_16BIT;

			// This is a common range. Dunno how to ask Android for what it likes.
			if (fmt.headerRate < 11025 || fmt.headerRate > 48000)
				throw new WavException("Unsupported sound rate: "+ fmt.headerRate +".");
			int trackRate = fmt.headerRate;

			errorLabel.setText("This sound is playable.");

			int trackBufferSize = soundBytes.length;
			if (trackMode == AudioTrack.MODE_STREAM) {
				trackBufferSize = AudioTrack.getMinBufferSize(trackRate, trackChannelCfg, trackEncoding);
				if (trackBufferSize == AudioTrack.ERROR) {
					throw new WavException("getMinBufferSize() returned ERROR");
				} else if (trackBufferSize == AudioTrack.ERROR_BAD_VALUE) {
					throw new WavException("getMinBufferSize() returned BAD_VALUE");
				}
			}

			trackInfo = new TrackInfo();
			trackInfo.channelCfg = trackChannelCfg;
			trackInfo.encoding = trackEncoding;
			trackInfo.rate = trackRate;
			trackInfo.bufferSize = trackBufferSize;
		}
		catch (Exception e) {
			errorLabel.setText(e.toString());
		}
		finally {
			if (is != null) {
				try {is.close();}
				catch (Exception e) {}
			}
		}
	}


	/**
	 * Decodes WAV header info from a stream.
	 * Afterward, the stream's position will be at the first data chunk.
	 *
	 * @param is  a stream supporting mark()
	 * @return an object describing header info
	 */
	public WavFormat readHeader(InputStream is) throws IOException, WavException {
		ByteOrder origOrder = null;

		byte[] magic = new byte[4];
		is.mark(magic.length);  // Setup backtracking.
		is.read(magic, 0, magic.length);
		is.reset();  // Unread.
		if (magic[0] == 0x52 && magic[1] == 0x49 && magic[2] == 0x46 && magic[3] == 0x46) {
			origOrder = ByteOrder.LITTLE_ENDIAN;  // RIFF
		} else {
			// RIFX, rare byte-swapped counterpart.
			//origOrder = ByteOrder.BIG_ENDIAN;
			// Dunno how to translate the data chubk later. *shrug*
			throw new WavException("Error: Sound bytes didn't begin with RIFF.");
		}

		ByteBuffer soundBuf = ByteBuffer.allocate(WAV_HEADER_SIZE);
		soundBuf.order(origOrder);
		is.read(soundBuf.array(), soundBuf.arrayOffset(), soundBuf.capacity());
		soundBuf.rewind();

		soundBuf.order(ByteOrder.LITTLE_ENDIAN);
		soundBuf.position(20);
		int headerEncoding = soundBuf.getShort();
		int headerChannels = soundBuf.getShort();
		int headerRate = soundBuf.getInt();

		soundBuf.position(soundBuf.position() + 6);
		int headerBits = soundBuf.getShort();

		while (soundBuf.getInt() != 0x61746164) { // "data" marker.
			// Non-data chunk. Size follows, so read that and skip the chunk.
			int chunkSize = soundBuf.getInt();
			is.skip(chunkSize);

			soundBuf.rewind();
			is.read(soundBuf.array(), soundBuf.arrayOffset(), 8);
			soundBuf.rewind();
		}  // In the data chunk now.
		int dataSize = soundBuf.getInt();
		if (dataSize <= 0)
			throw new WavException("Unsupported sound data chunk size: "+ dataSize +".");

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
	 * An AudioTrack is created, which will release() when it's done playing.
	 */
	public void play() {
		synchronized(trackLock) {
			android.util.Log.i("OpenUHS", "@!@ Sound play(): existing track ("+ (track != null) +"), workerDone ("+ writeWorkerDone +").");
			if (track != null || !writeWorkerDone) return;

			keepAlive = true;
			writeWorkerDone = false;
			Exception err = null;
			try {
				track = new AudioTrack(AudioManager.STREAM_MUSIC, trackInfo.rate, trackInfo.channelCfg, trackInfo.encoding, trackInfo.bufferSize, trackMode);

				track.setNotificationMarkerPosition(fmt.getTotalFrames()-1);
				track.setPlaybackPositionUpdateListener(new OnPlaybackPositionUpdateListener() {
					@Override
					public void onPeriodicNotification(AudioTrack track) {
					}
					@Override
					public void onMarkerReached(AudioTrack track) {
						synchronized(trackLock) {
							android.util.Log.i("OpenUHS", "@!@ Sound track finished playing.");
							track.release();
							if (track == UHSSoundView.this.track)
								UHSSoundView.this.track = null;
							keepAlive = false;
						}
					}
				});

				// For AudioTrack.MODE_STATIC, use writeloop before play,
				// and reloadStaticData() before subsequent play() calls.
				// But you'd need to know when to release() if not when stopping.
				// And MODE_STATIC has a memory leak issue, so avoid it altogether.

				if (trackMode == AudioTrack.MODE_STREAM) {  // Write after play.
					android.util.Log.i("OpenUHS", "@!@ Playing sound track.");
					track.play();
				}
			}
			catch (IllegalArgumentException e) {err = e;}
			catch (IllegalStateException e) {err = e;}
			finally {
				if (err != null) {
					UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();
					errorHandler.log(UHSErrorHandler.ERROR, UHSSoundView.this, "Could not play sound.", 0, err);
					errorLabel.setText(err.toString());
					if (track != null) track.release();
					track = null;
					keepAlive = false;
					writeWorkerDone = true;
					return;
				}
			}
		}

		final long bytesToSkip = fmt.dataChunkPos;
		final int bytesToWrite = fmt.dataChunkSize;
		final byte[] soundBytes = (byte[])node.getContent();   // Not an immutable copy. *shrug*
		Runnable r = new Runnable() {
			public void run() {
				try {
					InputStream is = new ByteArrayInputStream(soundBytes);
					is.skip(bytesToSkip);

					byte[] tmpBytes = new byte[512];
					int n = 0;
					int bytesLeft = bytesToWrite;
					while (keepAlive && bytesLeft > 0 && (n = is.read(tmpBytes)) != -1) {
						n = Math.min(n, bytesLeft);
						for (int bytesWritten=0; keepAlive && bytesWritten < n;) {
							int tmpInt = track.write(tmpBytes, bytesWritten, n-bytesWritten);

							if (tmpInt == AudioTrack.ERROR_INVALID_OPERATION)
								throw new IOException("track.write() returned INVALID_OPERATION");
							else if (tmpInt == AudioTrack.ERROR_BAD_VALUE)
								throw new IOException("track.write() returned BAD_VALUE");
							bytesWritten += tmpInt;
						}
						bytesLeft -= n;
					}
					android.util.Log.i("OpenUHS", "@!@ Sound bytes left unwritten ("+ bytesLeft +"/"+ bytesToWrite +").");

					// With MODE_STATIC, post a runnable to the UI thread to play() after writing.
				}
				catch (final IOException e) {
					UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();
					errorHandler.log(UHSErrorHandler.ERROR, UHSSoundView.this, "Could not play sound.", 0, e);

					UHSSoundView.this.post(new Runnable() {
						@Override
						public void run() {
							errorLabel.setText(e.toString());
						}
					});
				}
				finally {
					writeWorkerDone = true;
					android.util.Log.i("OpenUHS", "@!@ Sound worker thread ended.");
				}
			}
		};
		android.util.Log.i("OpenUHS", "@!@ Sound worker thread started.");
		Thread t = new Thread(r);
		t.setPriority(Thread.MIN_PRIORITY);
		t.setDaemon(true);
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



	public static class WavException extends Exception {
		public WavException(String s) {
			super(s);
		}
	}
}
