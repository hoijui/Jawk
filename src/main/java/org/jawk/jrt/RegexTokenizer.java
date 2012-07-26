package org.jawk.jrt;

import java.util.Enumeration;

/**
 * Similar to StringTokenizer, except that tokens are delimited
 * by a regular expression.
 */
public class RegexTokenizer implements Enumeration<Object> {

	private String[] array;
	private int idx = 0;

	/**
	 * Construct a RegexTokenizer.
	 *
	 * @param input The input string to tokenize.
	 * @param delimitterRegexPattern The regular expression delineating tokens
	 *   within the input string.
	 */
	public RegexTokenizer(String input, String delimitterRegexPattern) {
		array = input.split(delimitterRegexPattern, -2);
	}

	@Override
	public boolean hasMoreElements() {
		return idx < array.length;
	}

	@Override
	public Object nextElement() {
		return array[idx++];
	}
}
