package net.vhati.openuhs.desktopreader.reader;

import java.awt.Color;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.core.markup.DecoratedFragment;


/**
 * A JTextPane that understands UHS markup.
 */
public class UHSTextArea extends JTextPane {
	private static final String STYLE_NAME_REGULAR = "regular";
	private static final String STYLE_NAME_GROUP = "group";
	private static final String STYLE_NAME_LINK = "link";
	private static final String STYLE_NAME_HYPERLINK = "hyper";
	private static final String STYLE_NAME_MONOSPACED = "monospaced";

	public static Color GROUP_COLOR = Color.BLUE;
	public static Color LINK_COLOR = Color.GREEN.darker().darker();
	public static Color HYPER_COLOR = Color.MAGENTA.darker().darker();
	public static final StyleContext DEFAULT_STYLES = UHSTextArea.getDefaultStyleContext();

	private UHSTextArea pronoun = this;

	private UHSNode node = null;
	private StyledDocument doc = null;


	/**
	 * Constructs a text area with the class-default style context.
	 */
	public UHSTextArea( UHSNode n ) {
		this( n, DEFAULT_STYLES );
	}

	/**
	 * Constructor.
	 *
	 * @param n the UHSNode to display.
	 * @param styleContext a collection of font styles to use.
	 */
	public UHSTextArea( UHSNode n, StyleContext styleContext ) {
		super();
		node = n;

		doc = new DefaultStyledDocument( styleContext );
		pronoun.setStyledDocument( doc );
		updateContent();
	}


	/**
	 * Inserts the node's styled text into the document based on markup.
	 */
	public void updateContent() {
		try {doc.remove( 0, doc.getLength() );}
		catch ( BadLocationException e ) {}
		if ( node.getContentType() != UHSNode.STRING ) return;

		String normStyleName = null;
		if (node.isGroup()) normStyleName = STYLE_NAME_GROUP;
		else if (node.isLink()) normStyleName = STYLE_NAME_LINK;
		else normStyleName = STYLE_NAME_REGULAR;

		if (node.getStringContentDecorator() != null) {
			DecoratedFragment[] fragments = node.getDecoratedStringContent();
			for ( int i=0; i < fragments.length; i++ ) {
				String styleName = normStyleName;
				for ( int a=0; a < fragments[i].attributes.length; a++ ) {
					if ( fragments[i].attributes[a].equals( "Monospaced" ) ) {
						styleName = STYLE_NAME_MONOSPACED;
						break;
					}
					else if ( fragments[i].attributes[a].equals( "Hyperlink" ) ) {
						styleName = STYLE_NAME_HYPERLINK;
						break;
					}
				}

				try {
					doc.insertString( doc.getLength(), fragments[i].fragment, doc.getStyle( styleName ) );
				}
				catch ( BadLocationException e ) {e.printStackTrace();}
			}
		}
		else {
			try {
				doc.insertString( doc.getLength(), (String)node.getContent(), doc.getStyle( normStyleName ) );
			}
			catch ( BadLocationException e ) {e.printStackTrace();}
		}

		pronoun.validate();
		pronoun.repaint();
	}



	/**
	 * Returns a default-populated StyleContext for UHSTextAreas.
	 *
	 * <p>Changes to a style will immediately affect existing
	 * components using its descendants only. The style itself
	 * will not update for some reason.</p>
	 *
	 * <blockquote><pre>
	 * @{code
	 * Java's default
	 * - base
	 * - - regular
	 * - - - group
	 * - - - link
	 * - - - hyper
	 * - - - monospaced
	 * }
	 * </pre></blockquote>
	 */
	private static StyleContext getDefaultStyleContext() {
		StyleContext result = new StyleContext();
		Style defaultStyle = StyleContext.getDefaultStyleContext().getStyle( StyleContext.DEFAULT_STYLE );
			Style baseStyle = result.addStyle( "base", defaultStyle );
				Style regularStyle = result.addStyle( STYLE_NAME_REGULAR, baseStyle );

					Style groupStyle = result.addStyle( STYLE_NAME_GROUP, regularStyle );
						StyleConstants.setForeground( groupStyle, GROUP_COLOR );

					Style linkStyle = result.addStyle( STYLE_NAME_LINK, regularStyle );
						StyleConstants.setForeground( linkStyle, LINK_COLOR );

					Style hyperStyle = result.addStyle( STYLE_NAME_HYPERLINK, regularStyle );
						StyleConstants.setForeground( hyperStyle, HYPER_COLOR );
						StyleConstants.setUnderline( hyperStyle, true );

					Style monospacedStyle = result.addStyle( STYLE_NAME_MONOSPACED, regularStyle );
						StyleConstants.setFontFamily( monospacedStyle, "Monospaced" );

		return result;
	}
}
