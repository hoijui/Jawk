package org.jawk.jrt;

/**
 * A list of keys into an associative array.
 * <p>
 * KeyList is provided to differentiate between associative
 * array key lists and other types of lists on the operand stack
 * or as contained by variables. However, this is the only
 * List in used in this manner within Jawk at the time of
 * this writing.
 * </p>
 *
 * @see KeyListImpl
 */
//public interface KeyList extends java.util.List<Object>
public interface KeyList {

	/**
	 * Retrieve the number of elements in the KeyList.
	 */
	int size();

	Object getFirstAndRemove();
}
