package net.vhati.openuhs.core.markup;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.vhati.openuhs.core.markup.DecoratedFragment;
import net.vhati.openuhs.core.markup.StringDecorator;


/**
 * A StringDecorator ancestor.
 * <p>
 * This doesn't actually decorate anything.
 */
public class Version88StringDecorator extends StringDecorator {


	public Version88StringDecorator() {
		super();
	}


	@Override
	public DecoratedFragment[] getDecoratedString( String rawContent ) {
		String fragment = rawContent;
		String[] decoNames = new String[0];
		Map[] argMaps = new LinkedHashMap[0];
		DecoratedFragment[] result = new DecoratedFragment[] {new DecoratedFragment( fragment, decoNames, argMaps )};
		return result;
	}
}
