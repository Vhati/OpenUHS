package net.vhati.openuhs.core;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import net.vhati.openuhs.core.markup.DecoratedFragment;
import net.vhati.openuhs.core.markup.StringDecorator;


/**
 * A container for hierarchical content.
 *
 * <p>Each node has an optional id. The id is what the root node uses
 * to resolve link destinations. This is loosely based on the line
 * number the original hunk appeared at in a 9x format file. After
 * parsing, the id's value is arbitrary but must be unique.</p>
 *
 * <p>Each node has string content. Subclasses may offer additional
 * methods for images or audio.</p>
 *
 * <p>A node may optionally act as a group, containing nested
 * child nodes. A group can be visited, causing its own content to
 * appear as a title, with its children's content listed below.
 * The revealed amount attribute tracks the nth visible child.</p>
 *
 * <p>A node may optionally act as a hyperlink to another node.
 * A link points to an id, resolved by the root node upon clicking.</p>
 *
 * <p>Note: Link nodes cannot be group nodes, and vice versa.
 * Setting a non-negative link target will remove all children.
 * Adding children will reset the link target.</p>
 */
public class UHSNode {
	/** Any reader can see children or visit the link target. */
	public static final int RESTRICT_NONE = 0;

	/** This node is a nag message that should be hidden from registered readers. */
	public static final int RESTRICT_NAG = 1;

	/** Only registered readers can see this node's children or link target. */
	public static final int RESTRICT_REGONLY = 2;

	protected String type = "";
	protected int id = -1;
	protected int linkIndex = -1;                                // Either Link or group, not both
	protected int restriction = RESTRICT_NONE;
	protected List<UHSNode> children = null;
	protected int revealValue = 0;

	protected String rawStringContent = "";
	protected StringDecorator decorator = null;


	/**
	 * Constructs a node.
	 *
	 * @param type  an arbitrary string used to inform structure and decide special handling
	 */
	public UHSNode( String type ) {
		setType( type );
	}


	public void setType( String type ) {
		this.type = type;
	}

	public String getType() {
		return type;
	}


	/**
	 * Sets this node's id.
	 *
	 * <p>If altering an existing node, remember to
	 * call the rootNode's removeLink() before,
	 * and addLink() after.</p>
	 *
	 * @param n  a new id, or -1
	 * @see UHSRootNode#removeLink(UHSNode)
	 * @see UHSRootNode#addLink(UHSNode)
	 */
	public void setId( int n ) {
		if ( n < -1 ) n = -1;
		id = n;
	}

	/**
	 * Returns this node's id, or -1 if one is not set.
	 */
	public int getId() {
		return id;
	}

	/**
	 * Sets this node's id, offsetting the original by a relative amount.
	 *
	 * <p>This calls the rootNode's removeLink()/addLink()
	 * before and after changes. If the offset would
	 * result in a negative id, the id becomes -1. This
	 * does not affect the ids of children.</p>
	 *
	 * @param offset  an amount to add/subtract
	 * @param rootNode  an existing root node
	 * @see UHSRootNode#removeLink(UHSNode)
	 * @see UHSRootNode#addLink(UHSNode)
	 */
	public void shiftId( int offset, UHSRootNode rootNode ) {
		if ( id >= 0 && id + offset >= 0 ) {
			rootNode.removeLink( this );
			id = id + offset;
			rootNode.addLink( this );
		}
		else if ( id != -1 ){
			rootNode.removeLink( this );
			id = -1;
		}
	}


	/**
	 * Sets the id of another node to visit when this one is clicked, or -1 for none.
	 */
	public void setLinkTarget( int n ) {
		if ( n < 0 ) return;
		removeAllChildren();
		linkIndex = n;
	}

	public int getLinkTarget() {
		return linkIndex;
	}

	public boolean isLink() {
		if ( linkIndex != -1 ) {
			return true;
		}
		else {
			return false;
		}
	}


	/**
	 * Sets this node's viewing restriction.
	 *
	 * @param n  one of: UHSNode.RESTRICT_NONE, UHSNode.RESTRICT_NAG, or UHSNode.RESTRICT_REGONLY
	 */
	public void setRestriction( int n ) {
		if ( n != RESTRICT_NONE && n != RESTRICT_NAG && n != RESTRICT_REGONLY ) {
			throw new IllegalArgumentException( "Restriction must be RESTRICT_NONE, RESTRICT_NAG, or RESTRICT_REGONLY" );
		}
		restriction = n;
	}

	public int getRestriction() {
		return restriction;
	}


	public void setChildren( List<UHSNode> newChildren ) {
		if ( newChildren == null ) {
			this.removeAllChildren();
		}
		else {
			children = newChildren;
			linkIndex = -1;
			revealValue = 0;
		}
	}

	/**
	 * Returns this node's child nodes.
	 *
	 * @return a List of UHSNodes, or null
	 */
	public List<UHSNode> getChildren() {
		return children;
	}

	/**
	 * Returns this node's child nodes of a given type.
	 *
	 * @param type  a string to match node.getType() against
	 * @param c  a class to expect
	 * @return a List of UHSNodes, never null
	 */
	public <T extends UHSNode> List<T> getChildren( String type, Class<T> c ) {
		List<T> result = new ArrayList<T>();
		if ( children == null ) return result;

		for (int i=0; i < children.size(); i++) {
			UHSNode tmpNode = children.get( i );

			if ( tmpNode.getType().equals( type ) && c.isInstance( tmpNode ) ) {
				@SuppressWarnings("unchecked")
				T castNode = c.cast( tmpNode );
				result.add( castNode );
			}
		}
		return result;
	}

	/**
	 * Returns true if this node contains nested child nodes.
	 */
	public boolean isGroup() {
		if ( children != null ) {
			return true;
		} else {
			return false;
		}
	}


	/**
	 * Adds a child node to this one.
	 */
	public void addChild( UHSNode newChild ) {
		if ( children == null ) {
			linkIndex = -1;
			children = new ArrayList<UHSNode>();
		}
		if ( newChild != null ) {
			children.add( newChild );
		}
	}

	public void removeChild( UHSNode doomedNode ) {
		if ( children == null || !children.contains( doomedNode ) ) return;
		children.remove( doomedNode );
		revealValue = Math.min( revealValue, getMaximumReveal() );

		if ( children.size() == 0 ) removeAllChildren();
	}

	public void removeChild( int n ) {
		if ( children == null || getChildCount()-1 < n ) return;
		children.remove( n );
		revealValue = Math.min( revealValue, getMaximumReveal() );

		if ( children.size() == 0 ) removeAllChildren();
	}

	public void removeAllChildren() {
		if ( children == null ) return;
		children.clear();
		children = null;
		revealValue = 0;
	}

	/**
	 * Returns this node's first child node of a given type.
	 *
	 * @param type  a string to match node.getType() against
	 * @param c  a class to expect
	 * @return a UHSNode, or null
	 */
	public <T extends UHSNode> T getFirstChild( String type, Class<T> c ) {
		if ( children == null ) return null;

		for ( UHSNode tmpNode : children ) {
			if ( tmpNode.getType().equals( type ) && c.isInstance( tmpNode ) ) {
				@SuppressWarnings("unchecked")
				T result = c.cast( tmpNode );
				return result;
			}
		}
		return null;
	}

	/**
	 * Returns this node's nth child node.
	 *
	 * @param n  an index among children of this node
	 * @return a UHSNode, or null (if out of range)
	 */
	public UHSNode getChild( int n ) {
		if ( children == null || getChildCount()-1 < n ) return null;
		return children.get( n );
	}

	public int indexOfChild( UHSNode childNode ) {
		return children.indexOf( childNode );
	}

	public int getChildCount() {
		if ( children == null ) return 0;
		return children.size();
	}


	/**
	 * Returns the highest value that might be returned by getCurrentReveal().
	 *
	 * @return total child count, if any, or 0
	 * @see #setCurrentReveal(int)
	 * @see #getCurrentReveal()
	 */
	public int getMaximumReveal() {
		return getChildCount();
	}

	/**
	 * Sets the current reveal progress.
	 *
	 * @param n  a non-negative number less than or equal to the maximum reveal
	 * @see #getCurrentReveal()
	 * @see #getMaximumReveal()
	 */
	public void setCurrentReveal( int n ) {
		n = Math.min( Math.max( 0, n ), getMaximumReveal() );

		revealValue = n;
	}

	/**
	 * Returns the current reveal progress.
	 *
	 * @see #setCurrentReveal(int)
	 * @see #getMaximumReveal()
	 */
	public int getCurrentReveal() {
		return revealValue;
	}


	/**
	 * Sets this node's content, may not be null.
	 */
	public void setRawStringContent( String rawStringContent ) {
		if ( rawStringContent == null ) throw new IllegalArgumentException( "String content may not be null" );

		this.rawStringContent = rawStringContent;
	}

	public String getRawStringContent() {
		return rawStringContent;
	}


	public void setStringContentDecorator( StringDecorator d ) {
		decorator = d;
	}

	public StringDecorator getStringContentDecorator() {
		return decorator;
	}

	/**
	 * Returns content with markup parsed away.
	 *
	 * @return an array of DecoratedFragments, or null if no decorator is set
	 * @see #getDecoratedStringContent()
	 */
	public DecoratedFragment[] getDecoratedStringFragments() {
		if ( decorator != null ) {
			return decorator.getDecoratedString( rawStringContent );
		} else {
			return null;
		}
	}

	/**
	 * Returns content, with markup parsed if a decorator is set, raw otherwise.
	 *
	 * @return a non-null string
	 * @see #getDecoratedStringFragments()
	 */
	public String getDecoratedStringContent() {
		if ( decorator != null ) {
			DecoratedFragment[] frags = getDecoratedStringFragments();

			// Use raw length to hint at buffer capacity.
			StringBuilder buf = new StringBuilder( getRawStringContent().length() );
			for ( DecoratedFragment frag : frags ) {
				buf.append( frag.fragment );
			}
			return buf.toString();
		}
		else {
			return getRawStringContent();
		}
	}


	/**
	 * Recursively prints the indented contents of this node and its children.
	 *
	 * @param indent  indention prefix
	 * @param spacer  indention padding with each level
	 * @param outStream  a stream to print to
	 * @see #getPrintableContent()
	 */
	public void printNode( String indent, String spacer, PrintStream outStream ) {
		int id = getId();
		String idStr = (( id == -1 ) ? "" : "^"+ id +"^ " );
		String linkStr = ((!isLink()) ? "" : " (^Link to "+ getLinkTarget() +"^)");

		outStream.println( indent + idStr + getType() +": "+ getPrintableContent() + linkStr );

		for ( int i=0; i < getChildCount(); i++ ) {
			getChild( i ).printNode( indent+spacer, spacer, outStream );
		}
	}

	/**
	 * Returns a string representation of this node's content for printing.
	 *
	 * <p>This will be spliced into a message generated by printNode().</p>
	 *
	 * @see #printNode(String, String, PrintStream)
	 */
	public String getPrintableContent() {
		return rawStringContent;
	}
}
