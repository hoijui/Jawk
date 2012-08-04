package org.jawk;

/**
 * With this Exception, any part of the code may request a
 * <code>System.exit(code)</code> call with a specific code.
 */
public class ExitException extends Exception {

	public static final int EXIT_CODE_OK = 0;

	private final int code;

	/**
	 * Request exit with the <code>EXIT_CODE_OK</code>.
	 */
	public ExitException() {
		this(EXIT_CODE_OK);
	}

	public ExitException(int code) {
		this(code, "");
	}

	public ExitException(String message) {
		this(EXIT_CODE_OK, message);
	}

	public ExitException(int code, String message) {
		this(code, message, null);
	}

	public ExitException(int code, Throwable cause) {
		this(code, "", cause);
	}

	public ExitException(int code, String message, Throwable cause) {
		super(message + " (exit-code: " + code + ")", cause);
		this.code = code;
	}

	/**
	 * Returns the code to be passed to the <code>System.exit(code)</code> call.
	 */
	public int getCode() {
		return code;
	}
}
