package org.jawk;

/**
 * A placeholder for blocks of code which are not
 * implemented. If this is thrown, then it is the
 * author's intention to complete the section at
 * a later date.
 */
public class NotImplementedError extends Error {

	/**
	 * Construct the error without a message string.
	 */
	public NotImplementedError() {
		super();
	}

	/**
	 * Construct the error with a message string.
	 *
	 * @param msg The message string.
	 */
	public NotImplementedError(String msg) {
		super(msg);
	}
}
