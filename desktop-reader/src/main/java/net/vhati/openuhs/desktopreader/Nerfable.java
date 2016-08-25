package net.vhati.openuhs.desktopreader;


/*
 * An interface to en/disable user interaction.
 * It was written with JFrames and glassPanes in mind.
 *
 * It doesn't do anything vital, so one can safely use an empty method to become nerfable.
 */
public interface Nerfable {

  /*
   * Either nerf or restore user interaction.
   *
   * @param b the nerfed state
   */
  public void setNerfed(boolean b);
}
