package net.vhati.openuhs.desktopreader;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import net.vhati.openuhs.desktopreader.FieldEditorPanel;
import net.vhati.openuhs.desktopreader.FieldEditorPanel.ContentType;
import net.vhati.openuhs.desktopreader.UHSReaderConfig;


public class UHSReaderConfigPanel extends JPanel implements ActionListener {
	protected static final String FONT_SIZE = "font_size";
	protected static final String HTTP_PROXY = "http_proxy";
	protected static final String SOCKS_PROXY = "socks_proxy";

	protected UHSReaderConfig appConfig = null;
	protected Runnable applyCallback = null;

	protected FieldEditorPanel editorPanel;
	private JButton applyBtn = null;


	public UHSReaderConfigPanel() {
		super( new BorderLayout() );

		editorPanel = new FieldEditorPanel( false );
		editorPanel.setBorder( BorderFactory.createEmptyBorder( 10, 10, 0, 10 ) );
		editorPanel.setNameWidth( 200 );
		editorPanel.addRow( FONT_SIZE, ContentType.INTEGER );
		editorPanel.addTextRow( "The font size for hints in the reader" );
		editorPanel.addSeparatorRow();
		editorPanel.addRow( HTTP_PROXY, ContentType.STRING );
		editorPanel.addTextRow( "An optional network proxy (example: 127.0.0.1:1234)" );
		editorPanel.addSeparatorRow();
		editorPanel.addRow( SOCKS_PROXY, ContentType.STRING );
		editorPanel.addTextRow( "An optional network proxy (example: 127.0.0.1:1234)" );
		editorPanel.addBlankRow();
		editorPanel.addFillRow();

		editorPanel.setVisible( false );  // Show it later when appConfig != null.

		final JScrollPane editorScroll = new JScrollPane( editorPanel );
		editorScroll.setVerticalScrollBarPolicy( ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS );
		editorScroll.getVerticalScrollBar().setUnitIncrement( 10 );
		int vbarWidth = editorScroll.getVerticalScrollBar().getPreferredSize().width;
		editorScroll.setPreferredSize( new Dimension( editorPanel.getPreferredSize().width+vbarWidth+5, 400 ) );
		this.add( editorScroll, BorderLayout.CENTER );

		JPanel applyPanel = new JPanel();
		applyPanel.setLayout( new BoxLayout( applyPanel, BoxLayout.X_AXIS ) );
		applyPanel.add( Box.createHorizontalGlue() );
		applyBtn = new JButton( "Apply" );
		applyPanel.add( applyBtn );
		applyPanel.add( Box.createHorizontalGlue() );
		this.add( applyPanel, BorderLayout.SOUTH );

		applyBtn.addActionListener( this );

		editorScroll.addAncestorListener(new AncestorListener() {
			@Override
			public void ancestorAdded( AncestorEvent e ) {
				editorScroll.getViewport().setViewPosition( new Point(0, 0) );
			}
			@Override
			public void ancestorMoved( AncestorEvent e ) {
			}
			@Override
			public void ancestorRemoved( AncestorEvent e ) {
			}
		});
	}


	public void setConfig( UHSReaderConfig newConfig ) {
		appConfig = newConfig;
		if ( appConfig == null ) {
			editorPanel.setVisible( false );
			editorPanel.reset();
			applyBtn.setEnabled( false );

			this.validate();
			this.repaint();
			return;
		}
		editorPanel.setVisible( true );
		applyBtn.setEnabled( true );

		editorPanel.getInt( FONT_SIZE ).setText( Integer.toString( appConfig.getPropertyAsInt( FONT_SIZE, 12 ) ) );
		editorPanel.getString( HTTP_PROXY ).setText( appConfig.getProperty( HTTP_PROXY, "" ) );
		editorPanel.getString( SOCKS_PROXY ).setText( appConfig.getProperty( SOCKS_PROXY, "" ) );

		this.validate();
		this.repaint();
	}


	public void setApplyCallback( Runnable r ) {
		applyCallback = r;
	}


	@Override
	public void actionPerformed( ActionEvent e ) {
		Object source = e.getSource();

		if ( source == applyBtn ) {
			if ( appConfig == null ) return;

			String tmp;

			tmp = editorPanel.getInt( FONT_SIZE ).getText();
			try {
				int n = Integer.parseInt( tmp );
				if ( n > 0 ) {
					appConfig.setProperty( FONT_SIZE, Integer.toString( n ) );
				}
			}
			catch ( NumberFormatException f ) {}

			Pattern hostPortPtn = Pattern.compile( "^([a-z\\d](?:[a-z\\d\\-]{0,61}[a-z\\d])?(\\.[a-z\\d](?:[a-z\\d\\-]{0,61}‌​[a-z\\d])?)*):([0-9]+)$" );
			Matcher m = null;

			if ( (m=hostPortPtn.matcher( editorPanel.getString( HTTP_PROXY ).getText() )).matches() ) {
				appConfig.setProperty( HTTP_PROXY, m.group( 0 ) );
			}
			if ( (m=hostPortPtn.matcher( editorPanel.getString( SOCKS_PROXY ).getText() )).matches() ) {
				appConfig.setProperty( SOCKS_PROXY, m.group( 0 ) );
			}

			setConfig( appConfig );

			if ( applyCallback != null ) applyCallback.run();
		}
	}
}
