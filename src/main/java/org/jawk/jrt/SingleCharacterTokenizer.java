package org.jawk.jrt;

import java.util.Enumeration;

/**
 * Similar to StringTokenizer, except that tokens are delimited
 * by a single character.
 */
public class SingleCharacterTokenizer implements Enumeration<Object> {

	private String input;
	private int split_char;
	private int idx = 0;

	/**
	 * Construct a RegexTokenizer.
	 *
	 * @param input The input string to tokenize.
	 * @param split_char The character which deliniates tokens
	 *   within the input string.
	 */
	public SingleCharacterTokenizer(String input, int split_char) {
		// input + sentinel
		this.input = input + ((char) split_char);
		this.split_char = split_char;
	}

	public boolean hasMoreElements() {
		return idx < input.length();
	}

	private StringBuffer sb = new StringBuffer();

	public Object nextElement() {
		sb.setLength(0);
		while (idx < input.length()) {
			if (input.charAt(idx) == split_char) {
				++idx;
				break;
			} else {
				sb.append(input.charAt(idx++));
			}
		}

		return sb.toString();
	}
}
