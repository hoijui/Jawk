package org.jawk.intermediate;

/**
 * An interface to a tuple position for interpretation.
 * <p>
 * This is differentiated from a position interface for
 * compilation because compilation requires linear
 * access (i.e., non-jumps) to the tuple list, while
 * interpretation requires this as well as jump capability.
 * </p>
 */
public interface PositionForInterpretation extends Position {

	/**
	 * Reposition to the tuple located at a particular address.
	 * This is usually done in a response to an if condition.
	 * However, this is also done to perform loops, etc.
	 *
	 * @param address The target address for the jump.
	 */
	void jump(Address address);

	/**
	 * @return The current index into the tuple list (queue)
	 *	of the tuple located at the current position.
	 */
	int current();

	/**
	 * Reposition to the tuple located at a particular index
	 * into the tuple list (queue)..
	 *
	 * @param idx The target index for the jump.
	 */
	void jump(int idx);
}
