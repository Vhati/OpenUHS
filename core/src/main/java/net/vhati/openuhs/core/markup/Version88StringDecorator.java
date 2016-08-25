package net.vhati.openuhs.core.markup;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;


/**
 * A StringDecorator ancestor.
 * This doesn't actually decorate anything.
 */
public class Version88StringDecorator extends StringDecorator {


  public Version88StringDecorator() {
    super();
  }


  public DecoratedFragment[] getDecoratedString(String rawContent) {
    String fragment = rawContent;
    String[] decoNames = new String[0];
    LinkedHashMap[] argMaps = new LinkedHashMap[0];
    DecoratedFragment[] result = new DecoratedFragment[] {new DecoratedFragment(fragment, decoNames, argMaps)};
    return result;
  }
}
