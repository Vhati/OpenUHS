package net.vhati.openuhs.desktopreader.reader;

import net.vhati.openuhs.core.UHSNode;


/**
 * An interface NodePanels use on ancestors to replace themselves in a larger UHS reader GUI.
 */
public interface UHSReaderNavCtrl {

  /**
   * Displays a new node within the current tree.
   *
   * @param newNode the new node
   */
  public void setReaderNode(UHSNode newNode);

  /**
   * Displays a new node within the current tree.
   *
   * @param id ID of the new node
   */
  public void setReaderNode(int id);


  /**
   * Sets the reader's title to the specified string.
   *
   * @param s the title to be displayed in the reader. A null value is treated as an empty string, "".
   */
  public void setReaderTitle(String s);

  /**
   * Gets the title of the reader.
   *
   * @return the title of the reader
   */
  public String getReaderTitle();
}
