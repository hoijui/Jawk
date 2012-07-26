package org.jawk.jrt;

import java.util.Enumeration;

/**
 * Similar to StringTokenizer, except that tokens are delimited
 * by a single character.
 */
public class SingleCharacterTokenizer implements Enumeration<Object> {

	private String input;
	private int splitChar;
	private int idx = 0;

	/**
	 * Construct a RegexTokenizer.
	 *
	 * @param input The input string to tokenize.
	 * @param splitChar The character which delineates tokens
	 *   within the input string.
	 */
	public SingleCharacterTokenizer(String input, int splitChar) {
		// input + sentinel
		this.input = input + ((char) splitChar);
		this.splitChar = splitChar;
	}

	@Override
	public boolean hasMoreElements() {
		return idx < input.length();
	}

	private StringBuffer sb = new StringBuffer();

	@Override
	public Object nextElement() {
		sb.setLength(0);
		while (idx < input.length()) {
			if (input.charAt(idx) == splitChar) {
				++idx;
				break;
			} else {
				sb.append(input.charAt(idx++));
			}
		}

		return sb.toString();
	}
}
