package net.vhati.openuhs.core.markup;

import net.vhati.openuhs.core.markup.DecoratedFragment;


public abstract class StringDecorator {
  /** The sequence representing line breaks, as expected from the parser. */
  public static final char[] linebreak = new char[] {'^','b','r','e','a','k','^'};


  public abstract DecoratedFragment[] getDecoratedString(String rawContent);
}
