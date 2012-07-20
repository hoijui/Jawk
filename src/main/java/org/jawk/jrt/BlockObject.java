package org.jawk.jrt;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * An item which blocks until something useful can be
 * done with the object.  The BlockManager multiplexes
 * BlockObjects such that unblocking one
 * BlockObject causes the BlockManager to dispatch
 * the notifier tag result of the BlockObject.
 * <p>
 * BlockObjects are chained.  The BlockManager
 * blocks on all chained BlockObjects until one
 * is unblocked.
 * <p>
 * Subclasses must provide meaningful block()
 * and getNotifierTag() routines.
 * <p>
 * BlockObjects do not actually perform the client
 * blocking.  This is done by the BlockManager at the
 * AVM (interpreted) or compiled runtime environment.
 * The AVM/compiled environments make special provision
 * to return the head block object to the BlockManager
 * (within _EXTENSION_ keyword processing).
 *
 * @see BlockManager
 * @see BulkBlockObject
 */
public abstract class BlockObject {

	protected BlockObject() {}

	/**
	 * Construct a meaningful notifier tag for this BlockObject.
	 */
	public abstract String getNotifierTag();

	/**
	 * Block until meaningful data is made available for
	 * the client application.  This is called by the BlockManager
	 * in a way such that the BlockManager waits for one
	 * BlockObject to unblock.
	 */
	public abstract void block() throws InterruptedException;

	private BlockObject next_block_object = null;

	/**
	 * Eliminate the rest of the BlockObject chain.
	 */
	public void clearNextBlockObject() {
		this.next_block_object = null;
	}

	/**
	 * Chain this BlockObject to another BlockObject.
	 * The chain is linear and there is no upper bounds on
	 * the number of BlockObjects that can be supported.
	 */
	public void setNextBlockObject(BlockObject bo) {
		this.next_block_object = bo;
	}

	/**
	 * Obtain all chained BlockObjects as a List,
	 * including this one.
	 * A BlockObject chain cycle causes a runtime exception
	 * to be thrown.
	 *
	 * @return A List of chained BlockObjects, including
	 * 	this one.
	 *
	 * @throws AwkRuntimeException if the BlockObject
	 * 	chain contains a cycle.
	 */
	public List<BlockObject> getBlockObjects()
			throws AwkRuntimeException
	{
		List<BlockObject> retval = new LinkedList<BlockObject>();
		Set<BlockObject> bo_set = new HashSet<BlockObject>();
		BlockObject ref = this;
		while (ref != null) {
			if (bo_set.contains(ref)) {
				throw new AwkRuntimeException("Block chain contains a cycle (duplicate) : " + ref.getClass().getName() + " / " + ref.getNotifierTag());
			} else {
				bo_set.add(ref);
			}
			retval.add(ref);
			ref = ref.next_block_object;
		}
		return retval;
	}

	/**
	 * Ensure non-evaluation of a BlockObject by throwing an AWK Runtime
	 * exception, in case it leaks into AWK evaluation space.
	 */
	@Override
	public final String toString() {
		throw new AwkRuntimeException("Extension Violation : Cannot AWK-evaluate a BlockObject.");
	}
}
