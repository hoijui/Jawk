package org.jawk.jrt;

/**
 * Differentiate from IllegalArgumentException to assist
 * in programmatic distinction between Jawk and other
 * argument exception issues.
 */
public class IllegalAwkArgumentException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	public IllegalAwkArgumentException(String msg) {
		super(msg);
	}
}
