package net.vhati.openuhs.core;


/**
 * Coordinates for use with a UHSHotSpotNode.
 *
 * Children are initially invisible and associated with clickable zones.
 * When the zone is clicked, that child node is revealed, at a given position.
 *
 * The position may be -1 for children like link nodes that trigger an action.
 *
 * @see net.vhati.openuhs.core.UHSHotSpotNode
 */
public class HotSpot {
  public int zoneX;
  public int zoneY;
  public int zoneW;
  public int zoneH;
  public int x;
  public int y;


  public HotSpot(int zX, int zY, int zW, int zH, int pX, int pY) {
    zoneX = zX;
    zoneY = zY;
    zoneW = zW;
    zoneH = zH;
    x = pX;
    y = pY;
  }

  public HotSpot(int zX, int zY, int zW, int zH) {
    this(zX, zY, zW, zH, -1, -1);
  }

  public HotSpot() {
    this(0, 0, 10, 10, -1, -1);
  }
}
