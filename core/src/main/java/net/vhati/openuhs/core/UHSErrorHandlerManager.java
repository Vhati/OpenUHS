package net.vhati.openuhs.core;

import net.vhati.openuhs.core.*;


/**
 * A globally accessable place to find a logger.
 *
 * <p>A DefaultUHSErrorHandler(System.err) will be used initially.</p>
 */
public class UHSErrorHandlerManager {
  private static UHSErrorHandler errorHandler = new DefaultUHSErrorHandler(System.err);


  private UHSErrorHandlerManager() {}


  /**
   * Sets the global error handler (null for none).
   */
  public static void setErrorHandler(UHSErrorHandler eh) {errorHandler = eh;}

  /**
   * Returns the global error handler, or null.
   */
  public static UHSErrorHandler getErrorHandler() {return errorHandler;}
}
