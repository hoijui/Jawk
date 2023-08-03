package org.jawk.jrt;

/**
 * Thrown when exit() is called within a Jawk script.
 * <p>
 * Within Jawk, EndException is caught twice. The first
 * catch block executes when exit() is called within BEGIN
 * or action blocks. When invoked, the END blocks are
 * executed. The second catch block executes when exit() is
 * called within any of the END blocks. When invoked,
 * Jawk terminates with an exit code.
 * </p>
 */
public class EndException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public EndException(String s) {
		super(s);
	}

	public EndException() {
		super();
	}
}
