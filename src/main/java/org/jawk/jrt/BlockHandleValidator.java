package org.jawk.jrt;

public interface BlockHandleValidator {

	/**
	 * @return null if valid, a reason string when invalid
	 */
	String isBlockHandleValid(String handle);
}
