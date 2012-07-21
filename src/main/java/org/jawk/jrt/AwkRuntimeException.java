package org.jawk.jrt;

/**
 * A runtime exception thrown by Jawk. It is provided
 * to conveniently distinguish between Jawk runtime
 * exceptions and other runtime exceptions.
 */
public class AwkRuntimeException extends RuntimeException {

	public AwkRuntimeException(String msg) {
		super(msg);
	}

	public AwkRuntimeException(int lineno, String msg) {
		super(msg + " (line: " + lineno + ")");
	}
}
