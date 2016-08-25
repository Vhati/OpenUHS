package net.vhati.openuhs.core;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import net.vhati.openuhs.core.markup.DecoratedFragment;
import net.vhati.openuhs.core.markup.StringDecorator;


/**
 * A container for hierarchical content.
 * <br />A UHSNode has two attributes: a String indicating its
 * type and an optional id. The id is what the root node uses
 * to determine link destinations.
 * <br />Each node has content: STRING, IMAGE, or AUDIO. Non-String
 * content is stored in raw byte[] form.
 * <br />A node may additionally act as a group, containing nested
 * child nodes. In this case, this node's content should be
 * considered a title. The revealed amount attribute tracks the nth
 * visible child.
 * <br />A non-group node may act as a hyperlink to another node.
 * A link points to an id, resolved by the root node upon clicking.
 */
public class UHSNode {
  public static final int STRING = 0;
  public static final int IMAGE = 1;
  public static final int AUDIO = 2;

  public static final int RESTRICT_NONE = 0;
  public static final int RESTRICT_NAG = 1;
  public static final int RESTRICT_REGONLY = 2;

  private String type = "";
  private int contentType = STRING;
  private Object content = null;
  private StringDecorator decorator = null;
  private int id = -1;
  private int linkIndex = -1;                                // Either Link or group, not both
  private int restriction = RESTRICT_NONE;
  private List<UHSNode> children = null;
  private int revealedAmt = -1;


  public UHSNode(String inType) {
    setType(inType);
  }


  public String getType() {
    return type;
  }

  public void setType(String inType) {
    type = inType;
  }


  public Object getContent() {
    return content;
  }

  /**
   * Sets this node's content.
   *
   * @param inContent raw content (e.g., String or byte[])
   * @param inContentType one of STRING, IMAGE, or AUDIO
   */
  public void setContent(Object inContent, int inContentType) {
    if (inContentType != STRING && inContentType != IMAGE && inContentType != AUDIO) {
      throw new IllegalArgumentException("Content type must be STRING, IMAGE, or AUDIO");
    }
    content = inContent;
    contentType = inContentType;
  }

  public int getContentType() {
    return contentType;
  }


  /**
   * Returns this node's id, or -1 if one is not set.
   */
  public int getId() {
    return id;
  }

  /**
   * Sets this node's id.
   * If altering an existing node, remember to
   * call the rootNode's removeLink() before,
   * and addLink() after.
   *
   * @param input a new id, or -1
   * @see UHSRootNode#removeLink(UHSNode) UHSRootNode.removeLink(UHSNode)
   * @see UHSRootNode#addLink(UHSNode) UHSRootNode.addLink(UHSNode)
   */
  public void setId(int input) {
    if (input < -1) input = -1;
    id = input;
  }

  /**
   * Offsets the id this node.
   * This calls the rootNode's removeLink()/addLink()
   * before and after changes. If the offset would
   * result in a negative id, the id becomes -1. This
   * does not affect the ids of children.
   *
   * @param offset an amount to add/subtract
   * @param rootNode an existing root node
   * @see UHSRootNode#removeLink(UHSNode) UHSRootNode.removeLink(UHSNode)
   * @see UHSRootNode#addLink(UHSNode) UHSRootNode.addLink(UHSNode)
   */
  public void shiftId(int offset, UHSRootNode rootNode) {
    if (id >= 0 && id + offset >= 0) {
      rootNode.removeLink(this);
      id = id + offset;
      rootNode.addLink(this);
    }
    else if (id != -1){
      rootNode.removeLink(this);
      id = -1;
    }
  }


  public boolean isLink() {
    if (linkIndex != -1) return true;
    else return false;
  }

  public int getLinkTarget() {
    return linkIndex;
  }

  public void setLinkTarget(int input) {
    if (input < 0) return;
    this.removeAllChildren();
    linkIndex = input;
  }


  /**
   * Returns this node's viewing restriction.
   * <br />RESTRICT_NONE - Any reader can see children or the link target.
   * <br />RESTRICT_NAG - This node is a nag message that should be hidden from registered readers.
   * <br />RESTRICT_REGONLY - Only registered readers can see this node's children or link target.
   */
  public int getRestriction() {
    return restriction;
  }

  public void setRestriction(int n) {
    if (n != RESTRICT_NONE && n != RESTRICT_NAG && n != RESTRICT_REGONLY) {
      throw new IllegalArgumentException("Restriction must be RESTRICT_NONE, RESTRICT_NAG, or RESTRICT_REGONLY");
    }
    restriction = n;
  }


  /**
   * Returns true if this node contains nested child nodes.
   */
  public boolean isGroup() {
    if (children != null) return true;
    else return false;
  }

  /**
   * Returns this node's child nodes of a given type.
   *
   * @return a List of UHSNodes, never null
   */
  public List<UHSNode> getChildren(String type) {
    List result = new ArrayList();
    if (children == null) return result;

    for (int i=0; i < children.size(); i++) {
      UHSNode tmpNode = children.get(i);
      if (tmpNode.getType().equals(type)) result.add(tmpNode);
    }
    return result;
  }

  /**
   * Returns this node's child nodes.
   *
   * @return a List of UHSNodes, or null
   */
  public List<UHSNode> getChildren() {
    return children;
  }

  public void setChildren(List<UHSNode> newChildren) {
    if (newChildren == null) {
      this.removeAllChildren();
    }
    else {
      children = newChildren;
      linkIndex = -1;
      revealedAmt = 1;
    }
  }


  public void addChild(UHSNode inChild) {
    if (children == null) {
      linkIndex = -1;
      children = new ArrayList<UHSNode>();
    }
    if (inChild != null) {
      children.add(inChild);
      if (revealedAmt < 1) revealedAmt = 1;
    }
  }

  public void removeChild(UHSNode inChild) {
    if (children == null || !children.contains(inChild)) return;
    children.remove(inChild);
    revealedAmt--;
    if (revealedAmt <= 0) revealedAmt = -1;
    if (children.size() == 0) removeAllChildren();
  }

  public void removeChild(int input) {
    if (children == null || this.getChildCount()-1 < input) return;
    children.remove(input);
    revealedAmt--;
    if (revealedAmt <= 0) revealedAmt = -1;
    if (children.size() == 0) removeAllChildren();
  }

  public void removeAllChildren() {
    if (children == null) return;
    children.clear();
    children = null;
    revealedAmt = -1;
  }

  /**
   * Returns this node's first child node of a given type.
   *
   * @return a UHSNode, or null
   */
  public UHSNode getFirstChild(String type) {
    UHSNode result = null;
    if (children == null) return result;

    for (int i=0; i < children.size(); i++) {
      UHSNode tmpNode = children.get(i);
      if (tmpNode.getType().equals(type)) {
        result = tmpNode;
        break;
      }
    }
    return result;
  }

  /**
   * Returns this node's nth child node.
   *
   * @return a UHSNode, or null (if out of range)
   */
  public UHSNode getChild(int input) {
    if (children == null || this.getChildCount()-1 < input) return null;
    return children.get(input);
  }

  public int indexOfChild(UHSNode inChild) {
    return children.indexOf(inChild);
  }

  public int getChildCount() {
    if (children == null) return 0;
    return children.size();
  }


  /**
   * Sets the number of revealed children.
   *
   * @param n a number greater than 1 and less than or equal to the child count
   */
  public void setRevealedAmount(int n) {
    if (this.getChildCount() < n || n < 1) return;
    revealedAmt = n;
  }

  /**
   * Returns the number of revealed children.
   * <br />Or -1 if there are no children.
   */
  public int getRevealedAmount() {
    return revealedAmt;
  }


  /**
   * Recursively prints the indented contents of this node and its children.
   *
   * @param indent indention prefix
   * @param spacer indention padding with each level
   * @param outStream a stream to print to
   */
  public void printNode(String indent, String spacer, PrintStream outStream) {
    int id = this.getId();
    String idStr = (id==-1?"":"^"+id+"^ ");
    String linkStr = (!this.isLink()?"":" (^Link to "+ this.getLinkTarget() +"^)");

    if (this.getContentType() == UHSNode.STRING)
      outStream.println(indent + idStr + getType() +": "+ this.getContent() + linkStr);
    else if (this.getContentType() == UHSNode.IMAGE)
      outStream.println(indent + idStr + getType() +": "+"^IMAGE^"+ linkStr);
    else if (this.getContentType() == UHSNode.AUDIO)
      outStream.println(indent + idStr + getType() +": "+"^AUDIO^"+ linkStr);

    for (int i=0; i < this.getChildCount(); i++) {
      this.getChild(i).printNode(indent+spacer, spacer, outStream);
    }
  }


  public void setStringContentDecorator(StringDecorator d) {
    decorator = d;
  }

  public StringDecorator getStringContentDecorator() {
    return decorator;
  }

  /**
   * Returns content with markup parsed away.
   *
   * @return an array of DecoratedFragments for STRING nodes, or null
   */
  public DecoratedFragment[] getDecoratedStringContent() {
    if (decorator != null) {
      return decorator.getDecoratedString((String)content);
    } else {
      return null;
    }
  }
}
