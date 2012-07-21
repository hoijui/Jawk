package org.jawk.intermediate;

/**
 * An interface to a tuple position for compilation.
 * <p>
 * This is differentiated from a position interface for
 * interpretation because compilation requires linear
 * access (i.e., non-jumps) to the tuple list, while
 * interpretation requires this as well as jump capability.
 * </p>
 */
public interface PositionForCompilation extends Position {

	int index();
}
