package net.vhati.openuhs.desktopreader.reader;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;


/**
 * A simple swing component to play sounds.
 */
public class MinimalSoundPlayer extends JPanel {
	private static String playText = ">";
	private static String stopText = "X";

	private byte[] bytes = null;
	private Clip clip = null;
	private int duration = 0;
	private int position = 0;
	private Timer timer = null;

	private JButton playBtn = new JButton( playText );
	private JSlider slider = new JSlider( 0, 0, 0 );


	public MinimalSoundPlayer( byte[] b ) {
		super( new BorderLayout() );
		JPanel ctrlPanel = new JPanel();
			ctrlPanel.setLayout(new BoxLayout( ctrlPanel, BoxLayout.X_AXIS ));
				ctrlPanel.add( playBtn );
				ctrlPanel.add( Box.createHorizontalStrut( 10 ) );
				ctrlPanel.add( slider );
		this.add( ctrlPanel, BorderLayout.NORTH );


		try {
			InputStream is = new ByteArrayInputStream( b );
			AudioInputStream ain = AudioSystem.getAudioInputStream( is );
			try {
				//This used to be the entirety of the try{...}
				//DataLine.Info info = new DataLine.Info(Clip.class, ain.getFormat());
				//clip = (Clip) AudioSystem.getLine(info);
				//clip.open(ain);

				AudioFormat baseFormat = ain.getFormat();
				if ( baseFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED ) {
					AudioFormat decodedFormat = new AudioFormat( AudioFormat.Encoding.PCM_SIGNED, baseFormat.getSampleRate(), 16, baseFormat.getChannels(), baseFormat.getChannels() * 2, baseFormat.getSampleRate(), false );
					AudioInputStream decodedStream = AudioSystem.getAudioInputStream( decodedFormat, ain );
					int frameLength = (int)decodedStream.getFrameLength();
					int frameSize = decodedFormat.getFrameSize();
					DataLine.Info info = new DataLine.Info( Clip.class, decodedFormat, frameLength * frameSize );
					clip = (Clip)AudioSystem.getLine( info );
					clip.open( ain );
				}
				else {
					DataLine.Info info = new DataLine.Info( Clip.class, baseFormat, AudioSystem.NOT_SPECIFIED );
					clip = (Clip)AudioSystem.getLine( info );
					clip.open( ain );
				}
			}
			catch( LineUnavailableException e ) {
				e.printStackTrace();
			}
			finally {
				ain.close();
			}
			bytes = b;
			duration = (int)(clip.getMicrosecondLength() / 1000);
		}
		catch ( UnsupportedAudioFileException e ) {
			e.printStackTrace();
		}
		catch ( IOException e ) {
			e.printStackTrace();
		}


		if ( bytes != null ) {
			slider.setMaximum( duration );
			playBtn.addActionListener(new ActionListener() {
				public void actionPerformed( ActionEvent e ) {
					if ( !clip.isActive() ) start();
					else stop();
				}
			});
			slider.addChangeListener(new ChangeListener() {
				public void stateChanged( ChangeEvent e ) {
					if ( slider.getValue() != position ) {
						seek( slider.getValue() );
					}
				}
			});
			timer = new Timer(100, new ActionListener() {
				public void actionPerformed( ActionEvent e ) {
					if ( clip.isActive() ) {
						position = (int)(clip.getMicrosecondPosition() / 1000);
						slider.setValue( position );
					}
					else {
						stop();
						slider.setValue( 0 );
					}
				}
			});
		}
		else {
			playBtn.setEnabled( false );
		}
	}

	/**
	 * Halts playback.
	 */
	public void stop() {
		clip.stop();
		timer.stop();
		playBtn.setText( playText );
	}

	/**
	 * Begins/continues playback.
	 */
	public void start() {
		clip.start();
		timer.start();
		playBtn.setText( stopText );
	}

	/**
	 * Jumps to a new position in the sound.
	 *
	 * @param newPos  the desired position
	 */
	public void seek( int newPos ) {
		if ( newPos < 0 || newPos > duration ) return;
		position = newPos;
		clip.setMicrosecondPosition( position * 1000 );
		slider.setValue( position );
	}

	/**
	 * Gets the sound this component is playing.
	 *
	 * @return the sound
	 */
	public byte[] getSound() {
		return bytes;
	}
}
