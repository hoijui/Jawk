package org.jawk.jrt;

/**
 * An item that blocks.
 */
public interface Blockable {

	/**
	 * Polls whether the item will block or not.
	 * The blocking mechanism should perform this once.
	 * If blocking will occur, then the blocking mechanism
	 * will enter into a wait() state. Otherwise,
	 * the item is in a non-blocking state, waiting to
	 * be processed by the client.
	 *
	 * @param bo The block object querying whether to block or not.
	 *
	 * @return true if it will block (i.e., no data is
	 *   available), false otherwise
	 */
	boolean willBlock(BlockObject bo);
}
