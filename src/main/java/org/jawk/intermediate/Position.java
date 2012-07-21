package org.jawk.intermediate;

/**
 * Marks a position within the tuple list (queue).
 */
public interface Position {

	/**
	 * @return true whether we are at the end
	 *   of the tuple list, false otherwise
	 */
	boolean isEOF();

	/**
	 * Advances the position to the next tuple,
	 * as ordered within the tuple list (queue).
	 */
	void next();

	/**
	 * @return the opcode for the tuple at this
	 *	position
	 */
	int opcode();

	/**
	 * Get the integer representation for a particular
	 * element within the tuple.
	 *
	 * @param idx The item to retrieve from the tuple.
	 *
	 * @return the integer representation of the item.
	 */
	int intArg(int idx);

	/**
	 * Get the boolean representation for a particular
	 * element within the tuple.
	 *
	 * @param idx The item to retrieve from the tuple.
	 *
	 * @return the boolean representation of the item.
	 */
	boolean boolArg(int idx);

	/**
	 * Get a reference to a particular element
	 * within the tuple.
	 *
	 * @param idx The item to retrieve from the tuple.
	 *
	 * @return a reference to the item.
	 */
	Object arg(int idx);

	/**
	 * Obtain the address argument for this tuple.
	 * <p>
	 * This is a special form in that the tuple
	 * has only the address argument, and nothing else.
	 * </p>
	 */
	Address addressArg();

	/**
	 * Obtain the class argument for this tuple.
	 * <p>
	 * This is a special form in that the tuple
	 * has only the class argument, and nothing else.
	 * </p>
	 */
	Class classArg();

	/**
	 * Get the source line number for this position.
	 */
	int lineNumber();
}
