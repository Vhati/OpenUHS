package net.vhati.openuhs.core;

import java.util.*;


/**
 * A node to hold all others.
 * <br />Additionally a root node is responsible for tracking nodes that are link targets.
 */
public class UHSRootNode extends UHSNode {
  private HashMap linkMap = new HashMap();


  public UHSRootNode() {
    super("Root");
  }


  /**
   * Makes a node available to target by link nodes.
   *
   * @param newLink the node to add
   */
  public void addLink(UHSNode newLink) {
    linkMap.put(newLink.getId()+"", newLink);
  }

  /**
   * Makes a node unavailable to target by link nodes.
   *
   * @param id ID of the node to remove
   */
  public void removeLinkById(int id) {
    if (!linkMap.containsKey(id+"")) return;
    linkMap.remove(id+"");
  }

  /**
   * Makes a node unavailable to target by link nodes.
   *
   * @param doomedLink the node to remove
   */
  public void removeLink(UHSNode doomedLink) {
    if (!linkMap.containsKey(doomedLink.getId()+"")) return;
    linkMap.remove(doomedLink.getId()+"");
  }

  /**
   * Makes all nodes unavailable to target by link nodes.
   */
  public void removeAllLinks() {
    linkMap.clear();
  }

  /**
   * Gets a node by its id.
   * The node itself will always be returned,
   * without any temporary group wrapping it.
   *
   * @param id ID of the node to get
   * @return the node, or null if not found
   */
  public UHSNode getNodeByLinkId(int id) {
    Object o = linkMap.get(id+"");
    if (o == null) return null;

    UHSNode newNode = (UHSNode)o;
    return newNode;
  }

  /**
   * Gets a link's target.
   * If the target is not a group, a temporary
   * encapsulating group will be created so that
   * the target's content will not be treated as
   * a title.
   *
   * @param id ID of the node to get
   * @return the node, or null if not found
   */
  public UHSNode getLink(int id) {
    Object o = linkMap.get(id+"");
    if (o == null) return null;

    UHSNode newNode = (UHSNode)o;
    if (newNode.isGroup()) return newNode;
    else {
      UHSNode tmpNode = new UHSNode("Temp");
      tmpNode.setContent("", UHSNode.STRING);
      tmpNode.addChild(newNode);
      return tmpNode;
    }
  }

  public int getLinkCount() {
    return linkMap.size();
  }

  public void setChildren(ArrayList inChildren) {
    super.setChildren(inChildren);
    if (this.getChildCount() > 0) this.setRevealedAmount(this.getChildCount());
  }


  public void addChild(UHSNode inChild) {
    super.addChild(inChild);
    if (this.getChildCount() > 0) this.setRevealedAmount(this.getChildCount());
  }


  /**
   * Returns the title of this hint tree.
   * It may be the root's content, or the content of the
   * first child, if it's a Subject node with String content.
   *
   * @return the title of the hint file, or null if absent or blank
   */
  public String getUHSTitle() {
    String result = null;
    if (this.getContentType() == UHSNode.STRING) {
      String tmp = (String)this.getContent();
      if (tmp.equals("Root")) {
        if (this.getChildCount() > 0) {
          UHSNode childNode = this.getChild(0);
          if (childNode.getType() == "Subject") {
            if (childNode.getContentType() == UHSNode.STRING) {
              result = (String)childNode.getContent();
            }
          }
        }
      } else {
        result = tmp;
      }
    }
    if (result != null && result.length() == 0) result = null;

    return result;
  }


  /**
   * Reverse-searches immediate children and returns the last Version node's content.
   * It may be inaccurate, blank, or conflict with what is claimed in the info node.
   *
   * <br />"Version: " will be stripped from the beginning.
   *
   * @return the reported hint version (e.g., "96a"), or null if absent or blank
   */
  public String getUHSVersion() {
    String result = null;

    for (int i=this.getChildCount()-1; result == null && i >= 0; i--) {
      UHSNode currentNode = this.getChild(i);
      if (currentNode.getType() == "Version") {
        if (currentNode.getContentType() == UHSNode.STRING) {
          result = (String)currentNode.getContent();
        }
      }
    }

    if (result != null) {
      if (result.startsWith("Version: ")) result = result.substring(9);
      if (result.length() == 0) result = null;
    }
    return result;
  }
}
