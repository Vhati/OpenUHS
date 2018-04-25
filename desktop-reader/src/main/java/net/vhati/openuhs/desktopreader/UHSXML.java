package net.vhati.openuhs.desktopreader;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.Element;
import org.jdom2.CDATA;
import org.jdom2.output.XMLOutputter;
import org.jdom2.output.Format;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.ByteReference;
import net.vhati.openuhs.core.HotSpot;
import net.vhati.openuhs.core.UHSAudioNode;
import net.vhati.openuhs.core.UHSBatchNode;
import net.vhati.openuhs.core.UHSHotSpotNode;
import net.vhati.openuhs.core.UHSImageNode;
import net.vhati.openuhs.core.UHSNode;


public class UHSXML {


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
	 * <p>
	 * Extensions are guessed.
	 *
	 * @param currentNode  a node to start extracting from
	 * @param basename  prefix for referenced binary files
	 * @param n  a number for uniqueness, incrementing with each file
	 * @return a new value for n
	 * @see net.vhati.openuhs.desktopreader.UHSUtil#guessFileExtension(InputStream)
	 */
	private static int exportNode( Element parentElement, UHSNode currentNode, String basename, int n ) throws IOException {
		Element currentElement = null;
		if ( currentNode instanceof UHSHotSpotNode ) {
			currentElement = new Element( "hotspot-node" );
		}
		else if ( currentNode instanceof UHSImageNode ) {
			currentElement = new Element( "image-node" );
		}
		else if ( currentNode instanceof UHSAudioNode ) {
			currentElement = new Element( "audio-node" );
		}
		else if ( currentNode instanceof UHSBatchNode ) {
			currentElement = new Element( "batch-node" );
		}
		else {
			currentElement = new Element( "node" );
		}

		currentElement.setAttribute( "type", currentNode.getType() );

		int id = currentNode.getId();
		currentElement.setAttribute( "id", (( id == -1 ) ? "" : id+"") );

		int restriction = currentNode.getRestriction();
		if ( restriction == UHSNode.RESTRICT_NAG ) {
			currentElement.setAttribute( "restriction", "nag" );
		}
		else if ( restriction == UHSNode.RESTRICT_REGONLY ) {
			currentElement.setAttribute( "restriction", "regonly" );
		}

		int linkId = currentNode.getLinkTarget();
		currentElement.setAttribute( "link-id", (( linkId == -1 ) ? "" : linkId+"") );

		// Content.
		if ( currentNode instanceof UHSNode ) {
			String contentTypeString = "string";

			String contentString = currentNode.getRawStringContent();

			Element contentElement = new Element( "content" );
				contentElement.setAttribute( "type", contentTypeString );
				contentElement.setContent( new CDATA( contentString ) );
				currentElement.addContent( contentElement );
		}
		if ( currentNode instanceof UHSAudioNode ) {
			UHSAudioNode audioNode = (UHSAudioNode)currentNode;
			String contentTypeString = "audio";

			ByteReference audioRef = audioNode.getRawAudioContent();
			InputStream is = null;
			String ext = null;
			try {
				is = audioRef.getInputStream();
				ext = UHSUtil.guessFileExtension( is );
			}
			catch ( IOException e ) {
				throw new IOException( String.format( "Error loading binary content of %s node (\"%s\")", audioNode.getType(), audioNode.getRawStringContent() ), e );
			}
			finally {
				try {if ( is != null ) is.close();} catch ( IOException e ) {}
			}

			String contentString = String.format( "%s%d%s.%s", basename, n, (( id == -1 ) ? "" : "_"+id), ext );
			n++;

			Element contentElement = new Element( "content" );
				contentElement.setAttribute( "type", contentTypeString );
				contentElement.setContent( new CDATA( contentString ) );
				currentElement.addContent( contentElement );
		}
		if ( currentNode instanceof UHSImageNode ) {
			UHSImageNode imageNode = (UHSImageNode)currentNode;
			String contentTypeString = "image";

			ByteReference imageRef = imageNode.getRawImageContent();
			InputStream is = null;
			String ext = null;
			try {
				is = imageRef.getInputStream();
				ext = UHSUtil.guessFileExtension( is );
			}
			catch ( IOException e ) {
				throw new IOException( String.format( "Error loading binary content of %s node (\"%s\")", imageNode.getType(), imageNode.getRawStringContent() ), e );
			}
			finally {
				try {if ( is != null ) is.close();} catch ( IOException e ) {}
			}

			String contentString = String.format( "%s%d%s.%s", basename, n, (( id == -1 ) ? "" : "_"+id), ext );
			n++;

			Element contentElement = new Element( "content" );
				contentElement.setAttribute( "type", contentTypeString );
				contentElement.setContent( new CDATA( contentString ) );
				currentElement.addContent( contentElement );
		}

		// Children.
		if ( currentNode instanceof UHSHotSpotNode ) {
			UHSHotSpotNode hotspotNode = (UHSHotSpotNode)currentNode;

			List<UHSNode> children = hotspotNode.getChildren();
			if ( children != null ) {
				for ( UHSNode tmpNode : children ) {
					HotSpot spot = hotspotNode.getSpot( tmpNode );
					Element childElement = new Element( "hotspot-child" );
						childElement.setAttribute( "zx", spot.zoneX+"" );
						childElement.setAttribute( "zy", spot.zoneY+"" );
						childElement.setAttribute( "zw", spot.zoneW+"" );
						childElement.setAttribute( "zh", spot.zoneH+"" );
						childElement.setAttribute( "px", spot.x+"" );
						childElement.setAttribute( "py", spot.y+"" );
						currentElement.addContent( childElement );
						n = exportNode( childElement, tmpNode, basename, n );
				}
			}
		}
		else if ( currentNode instanceof UHSBatchNode ) {
			List<UHSNode> children = currentNode.getChildren();
			if ( children != null ) {
				int childCount = children.size();
				for ( UHSNode tmpNode : children ) {
					Element childElement = new Element( "batch-child" );
						childElement.setAttribute( "addon", Boolean.toString( ((UHSBatchNode)currentNode).isAddon( tmpNode ) ) );
						currentElement.addContent( childElement );
						n = exportNode( childElement, tmpNode, basename, n );
				}
			}
		}
		else {
			List<UHSNode> children = currentNode.getChildren();
			if ( children != null ) {
				int childCount = children.size();
				for ( UHSNode tmpNode : children ) {
					Element childElement = new Element( "child" );
						currentElement.addContent( childElement );
						n = exportNode( childElement, tmpNode, basename, n );
				}
			}
		}

		parentElement.addContent( currentElement );
		return n;
	}
}
