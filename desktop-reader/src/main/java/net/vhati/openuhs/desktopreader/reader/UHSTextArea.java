package net.vhati.openuhs.desktopreader.reader;

import java.awt.Color;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.core.markup.DecoratedFragment;


/**
 * A JTextPane that understands UHS markup.
 */
public class UHSTextArea extends JTextPane {
	private static final String STYLE_NAME_REGULAR = "regular";
	private static final String STYLE_NAME_VISITABLE = "visitable";
	private static final String STYLE_NAME_LINK = "link";
	private static final String STYLE_NAME_HYPERLINK = "hyper";
	private static final String STYLE_NAME_MONOSPACED = "monospaced";

	public static Color VISITABLE_COLOR = Color.BLUE;
	public static Color LINK_COLOR = Color.GREEN.darker().darker();
	public static Color HYPER_COLOR = Color.MAGENTA.darker().darker();
	public static final StyleContext DEFAULT_STYLES = UHSTextArea.getDefaultStyleContext();

	private final Logger logger = LoggerFactory.getLogger( UHSTextArea.class );

	private StyledDocument doc = null;
	private UHSNode node = null;
	private boolean visitable = false;
	private String overrideText = null;


	/**
	 * Constructs a text area with the class-default style context.
	 */
	public UHSTextArea() {
		this( DEFAULT_STYLES );
	}

	/**
	 * Constructor.
	 *
	 * @param styleContext  a collection of font styles to use.
	 */
	public UHSTextArea( StyleContext styleContext ) {
		super();

		doc = new DefaultStyledDocument( styleContext );
		this.setStyledDocument( doc );
	}

	/**
	 * Sets a node to represent.
	 *
	 * @param node  the node
	 * @param visitable  true if the reader supports visiting this node, false otherwise
	 */
	public void setNode( UHSNode node, boolean visitable ) {
		this.node = node;
		this.visitable = visitable;
		contentChanged();
	}

	/**
	 * Sets a style indicating to the user that this node can be visited.
	 */
	public void setVisitable( boolean b ) {
		if ( visitable == b ) return;

		visitable = b;
		contentChanged();
	}

	public boolean isVisitable() {
		return visitable;
	}

	/**
	 * Sets a string to display instead of a UHSNode's content.
	 *
	 * <p>The string will be styled /as if/ that were the node's content.</p>
	 *
	 * @param s  the text, or null for none
	 */
	public void setOverrideText( String s ) {
		overrideText = s;
		contentChanged();
	}

	public String getOverrideText() {
		return overrideText;
	}


	/**
	 * Clears and inserts styled text into the document based on node content.
	 */
	public void contentChanged() {
		try {doc.remove( 0, doc.getLength() );}
		catch ( BadLocationException e ) {}

		String normStyleName = null;
		if ( isVisitable() ) {
			normStyleName = STYLE_NAME_VISITABLE;
		}
		else if ( node != null && node.isLink() ) {
			normStyleName = STYLE_NAME_LINK;
		}
		else {
			normStyleName = STYLE_NAME_REGULAR;
		}

		try {
			if ( overrideText != null ) {
				doc.insertString( doc.getLength(), overrideText, doc.getStyle( normStyleName ) );
			}
			else if ( node != null ) {
				DecoratedFragment[] fragments = node.getDecoratedStringFragments();

				if ( fragments != null ) {
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

						doc.insertString( doc.getLength(), fragments[i].fragment, doc.getStyle( styleName ) );
					}
				}
				else {
					doc.insertString( doc.getLength(), node.getRawStringContent(), doc.getStyle( normStyleName ) );
				}
			}
		}
		catch ( BadLocationException e ) {
			logger.error( "Error updating text", e );
		}

		this.validate();
		this.repaint();
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
	 * - - - visitable
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

					Style visitableStyle = result.addStyle( STYLE_NAME_VISITABLE, regularStyle );
						StyleConstants.setForeground( visitableStyle, VISITABLE_COLOR );

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
