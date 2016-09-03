package net.vhati.openuhs.desktopreader;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.Element;
import org.jdom.CDATA;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;

import net.vhati.openuhs.core.DefaultUHSErrorHandler;
import net.vhati.openuhs.core.HotSpot;
import net.vhati.openuhs.core.UHSErrorHandler;
import net.vhati.openuhs.core.UHSHotSpotNode;
import net.vhati.openuhs.core.UHSNode;


public class UHSXML {
	private static UHSErrorHandler errorHandler = new DefaultUHSErrorHandler( System.err );


	/**
	 * Sets the error handler to notify of exceptions.
	 *
	 * <p>This is a convenience for logging/muting.</p>
	 *
	 * <p>The default handler prints to System.err.</p>
	 *
	 * @param eh  the error handler, or null, for quiet parsing
	 */
	public static void setErrorHandler( UHSErrorHandler eh ) {
		errorHandler = eh;
	}


	/**
	 * Exports a node and its children to xml.
	 *
	 * @param currentNode  a node to start exporting from
	 * @param basename  prefix for referenced binary files
	 * @param out  a stream to print to (e.g., System.out)
	 */
	public static void exportTree( UHSNode currentNode, String basename, OutputStream out ) throws IOException {
		Element rootElement = new Element( "uhs" );
		rootElement.setAttribute( "openuhs-xml-version", "1" );
		Document doc = new Document( rootElement );

		exportNode( rootElement, currentNode, basename, 1 );

		XMLOutputter outputter = new XMLOutputter( Format.getPrettyFormat() );
		outputter.output( doc, out );
	}


	/**
	 * Recursively exports a node and its children to xml Elements.
	 *
	 * <p>Extensions are guessed.</p>
	 *
	 * @param currentNode  a node to start extracting from
	 * @param basename  prefix for referenced binary files
	 * @param n  a number for uniqueness, incrementing with each file
	 * @return a new value for n
	 * @see net.vhati.openuhs.desktopreader.UHSUtil#getFileExtension(byte[])
	 */
	private static int exportNode( Element parentElement, UHSNode currentNode, String basename, int n ) {
		Element currentElement = null;
		if ( currentNode instanceof UHSHotSpotNode ) currentElement = new Element( "hotspot-node" );
		else currentElement = new Element( "node" );

		currentElement.setAttribute( "type", currentNode.getType() );

		int id = currentNode.getId();
		currentElement.setAttribute( "id", (( id == -1 ) ? "" : id+"") );

		int restriction = currentNode.getRestriction();
		if ( restriction == UHSNode.RESTRICT_NAG )
			currentElement.setAttribute( "restriction", "nag" );
		else if ( restriction == UHSNode.RESTRICT_REGONLY )
			currentElement.setAttribute( "restriction", "regonly" );

		int contentType = currentNode.getContentType();
		String contentTypeString = "";
		String contentString = "";
		if ( contentType == UHSNode.STRING ) {
			contentTypeString = "string";
			contentString = (String)currentNode.getContent();
		} else {
			if ( contentType == UHSNode.IMAGE ) {
				contentTypeString = "image";
			}
			else if ( contentType == UHSNode.AUDIO ) {
				contentTypeString = "audio";
			}
			else {
				contentTypeString = "unknown";
			}
			contentString = basename + n + (( id == -1 ) ? "" : "_"+id) +"."+ UHSUtil.getFileExtension( (byte[])(currentNode.getContent()) );
			n++;
		}
		Element contentElement = new Element( "content" );
			contentElement.setAttribute( "type", contentTypeString );
			contentElement.setContent( new CDATA( contentString ) );
			currentElement.addContent( contentElement );


		if ( currentNode instanceof UHSHotSpotNode ) {
			List<UHSNode> children = currentNode.getChildren();
			if ( children != null ) {
				int childCount = children.size();
				for ( int i=0; i < childCount; i++ ) {
					HotSpot spot = ((UHSHotSpotNode)currentNode).getSpot( children.get( i ) );
					Element childElement = new Element( "hotspot-child" );
						childElement.setAttribute( "zx", spot.zoneX+"" );
						childElement.setAttribute( "zy", spot.zoneY+"" );
						childElement.setAttribute( "zw", spot.zoneW+"" );
						childElement.setAttribute( "zh", spot.zoneH+"" );
						childElement.setAttribute( "px", spot.x+"" );
						childElement.setAttribute( "py", spot.y+"" );
						currentElement.addContent( childElement );
						n = exportNode( childElement, children.get( i ), basename, n );
				}
			}
		}
		else {
			int linkId = currentNode.getLinkTarget();
			currentElement.setAttribute( "link-id", (( linkId == -1 ) ? "" : linkId+"") );

			List<UHSNode> children = currentNode.getChildren();
			if ( children != null ) {
				int childCount = children.size();
				for ( int i=0; i < childCount; i++ ) {
					Element childElement = new Element( "child" );
						currentElement.addContent( childElement );
						n = exportNode( childElement, children.get( i ), basename, n );
				}
			}
		}

		parentElement.addContent( currentElement );
		return n;
	}
}
