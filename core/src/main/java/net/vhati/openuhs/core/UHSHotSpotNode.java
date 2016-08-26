package net.vhati.openuhs.core;

import java.util.List;
import java.util.Vector;

import net.vhati.openuhs.core.HotSpot;
import net.vhati.openuhs.core.UHSNode;


/**
 * A container for UHSNodes that have clickable regions.
 *
 * Children are initially invisible and associated with clickable zones.
 * When the zone is clicked, that child node is revealed, at a given position.
 *
 * @see net.vhati.openuhs.core.HotSpot
 */
public class UHSHotSpotNode extends UHSNode {
  private List<HotSpot> spots = new Vector<HotSpot>();


  public UHSHotSpotNode(String inType) {
    super(inType);
  }


  /**
   * Returns the zone/position of a child.
   *
   * @param inChild a child node
   * @return a HotSpot
   */
  public HotSpot getSpot(UHSNode inChild) {
    int index = super.indexOfChild(inChild);
    if (index == -1) return null;
    return spots.get(index);
  }

  /**
   * Gets the zone/position of a child.
   *
   * @param n index of a child node
   * @return a HotSpot
   */
  public HotSpot getSpot(int n) {
    if (super.getChildCount()-1 < n) return null;
    return spots.get(n);
  }

  /**
   * Sets the zone/position of a child.
   *
   * @param inChild a child node
   * @param spot a HotSpot
   */
  public void setSpot(UHSNode inChild, HotSpot spot) {
    int index = super.indexOfChild(inChild);
    if (index == -1) return;

    spots.set(index, spot);
  }

  /**
   * Sets the zone/position of a child.
   *
   * @param n index of a child node
   * @param spot a HotSpot
   */
  public void setSpot(int n, HotSpot spot) {
    if (super.getChildCount()-1 < n) return;
    spots.set(n, spot);
  }


  public Object getContent() {
    return super.getContent();
  }


  /**
   * Overridden to make linking impossible.
   *
   * @param n ID of the node to target
   * @see net.vhati.openuhs.core.UHSNode#setLinkTarget(int) UHSNode.setLinkTarget(int)
   */
  public void setLinkTarget(int n) {
    return;
  }


  /**
   * Replace or initialize the current children.
   * <br />This method gives the new nodes default zones/positions.
   *
   * @param inChildren a List of new child UHSNodes
   */
  public void setChildren(List<UHSNode> inChildren) {
    if (inChildren == null || inChildren.size() == 0) {
      this.removeAllChildren();
    }
    else {
      super.setChildren(inChildren);
      spots.clear();
      for (int i=0; i < inChildren.size(); i++) {
        spots.add(new HotSpot());
      }
    }
  }


  public void addChild(UHSNode inChild) {
    super.addChild(inChild);
    spots.add(new HotSpot());
  }

  public void removeChild(UHSNode inChild) {
    int index = super.indexOfChild(inChild);
    if (index == -1) return;
    super.removeChild(index);
    spots.remove(index);
  }


  public void removeAllChildren() {
    super.removeAllChildren();
    spots.clear();
  }
}
