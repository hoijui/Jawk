package org.jawk.intermediate;

/**
 * A pointer to a tuple within the list of tuples.
 * Addresses are used for jumps, especially in reaction to
 * conditional checks (i.e., if false, jump to else block, etc.).
 * <p>
 * Addresses have the following properties:
 * <ul>
 * <li>A name (label).</li>
 * <li>An index into the tuple queue.</li>
 * </ul>
 * An address may not necessarily have an index assigned upon creation.
 * However, upon tuple traversal, all address indexes must
 * point to a valid tuple.
 * </p>
 * <p>
 * All addresses should have a meaningful label.
 * </p>
 */
public interface Address {

	/**
	 * The label of the address.
	 * It is particularly useful when dumping tuples to an output stream.
	 *
	 * @return The label of the tuple.
	 */
	String label();

	/**
	 * Set the tuple index of this address.
	 * This can be deferred anytime after creation of the address,
	 * but the index must be assigned prior to traversing the tuples.
	 *
	 * @param idx The tuple location within the tuple list (queue)
	 *   for this address.
	 */
	void assignIndex(int idx);

	/**
	 * @return The index into the tuple queue/array.
	 */
	int index();
}
