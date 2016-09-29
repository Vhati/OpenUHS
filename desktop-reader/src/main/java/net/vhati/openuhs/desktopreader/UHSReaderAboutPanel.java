package net.vhati.openuhs.desktopreader;

import java.awt.Desktop;
import java.awt.Insets;
import javax.swing.JScrollPane;
import javax.swing.JEditorPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.DefaultCaret;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UHSReaderAboutPanel extends JScrollPane {

	private final Logger logger = LoggerFactory.getLogger( UHSReaderAboutPanel.class );


	public UHSReaderAboutPanel() {
		super();

		String projectUrl = "https://github.com/vhati/OpenUHS";
		String licenseUrl = "http://www.gnu.org/licenses/";

		StringBuilder buf = new StringBuilder();

		buf.append( "<html>" );
		buf.append( "This software is distributed under the " );
		buf.append( "GPL open source license and its source code is available at\n" );
		buf.append( "<a href=\""+ projectUrl +"\">"+ projectUrl +"</a><br/>\n" );
		buf.append( "<br/>\n" );
		buf.append( "Copyright (C) 2007-2009, 2011, 2012, 2016 David Millis<br/>\n" );
		buf.append( "<br/>\n" );
		buf.append( "This program is free software; you can redistribute it and/or modify " );
		buf.append( "it under the terms of the GNU General Public License as published by " );
		buf.append( "the Free Software Foundation, either version 3 of the License, or " );
		buf.append( "(at your option) any later version.<br/>\n" );
		buf.append( "<br/>\n" );
		buf.append( "This program is distributed in the hope that it will be useful, " );
		buf.append( "but WITHOUT ANY WARRANTY; without even the implied warranty of " );
		buf.append( "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the " );
		buf.append( "GNU General Public License for more details.<br/>\n" );
		buf.append( "<br/>\n" );
		buf.append( "You should have received a copy of the GNU General Public License " );
		buf.append( "along with this program. If not, see " );
		buf.append( "<a href=\""+ licenseUrl +"\">"+ licenseUrl +"</a>.<br/>\n" );
		buf.append( "</html>" );

		JEditorPane aboutArea = new JEditorPane();
		aboutArea.setContentType( "text/html" );
		((DefaultCaret)aboutArea.getCaret()).setUpdatePolicy( DefaultCaret.NEVER_UPDATE );
		aboutArea.setText( buf.toString() );
		aboutArea.setEditable( false );
		aboutArea.setMargin( new Insets( 12, 12, 12, 12 ) );
		this.setViewportView( aboutArea );

		aboutArea.addHyperlinkListener(new HyperlinkListener() {
			@Override
			public void hyperlinkUpdate( HyperlinkEvent e ) {
				if ( HyperlinkEvent.EventType.ACTIVATED.equals( e.getEventType() ) ) {
					Desktop desktop = Desktop.getDesktop();
					try {
						desktop.browse( e.getURL().toURI() );
					}
					catch ( Exception f ) {
						logger.error( "Failed to launch browser for hyperlink: {}", e.getURL(), f );
					}
				}
			}
		});
	}
}
