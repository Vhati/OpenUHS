package net.vhati.openuhs.core;

import java.io.PrintStream;

import net.vhati.openuhs.core.*;


/**
 * An interface for wrapping loggers.
 */
public interface UHSErrorHandler {
  public static final int ERROR = 0;
  public static final int INFO = 1;


  /**
   * Logs an event.
   *
   * <p>It would be wise to synchronize on a lock object.</p>
   *
   * @param severity ERROR or INFO
   * @param source the responsible object, or null
   * @param message
   * @param line line number, or 0 for none
   * @param e an exception, or null
   */
  public void log(int severity, Object source, String message, int line, Exception e);
}
