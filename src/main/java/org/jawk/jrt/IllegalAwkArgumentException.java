package org.jawk.jrt;

/**
 * Differentiate from IllegalArgumentException to assist
 * in programmatic distinction between Jawk and other
 * argument exception issues.
 */
public class IllegalAwkArgumentException extends IllegalArgumentException {

	public IllegalAwkArgumentException(String msg) {
		super(msg);
	}
}
