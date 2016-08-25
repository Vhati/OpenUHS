package net.vhati.openuhs.core.markup;


public abstract class StringDecorator extends ContentDecorator {
  /** The sequence representing line breaks, as expected from the parser. */
  public static final char[] linebreak = new char[] {'^','b','r','e','a','k','^'};


  abstract DecoratedFragment[] getDecoratedString(String rawContent);

  public Object getDecoratedContent(Object rawContent) {
    if (rawContent instanceof String) {
      return getDecoratedString((String)rawContent);
    } else {
      return null;
    }
  }
}
