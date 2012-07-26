package org.jawk.jrt;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * An item which blocks until something useful can be
 * done with the object. The BlockManager multiplexes
 * BlockObjects such that unblocking one
 * BlockObject causes the BlockManager to dispatch
 * the notifier tag result of the BlockObject.
 * <p>
 * BlockObjects are chained. The BlockManager
 * blocks on all chained BlockObjects until one
 * is unblocked.
 * </p>
 * <p>
 * Subclasses must provide meaningful block()
 * and getNotifierTag() routines.
 * </p>
 * <p>
 * BlockObjects do not actually perform the client
 * blocking. This is done by the BlockManager at the
 * AVM (interpreted) or compiled runtime environment.
 * The AVM/compiled environments make special provision
 * to return the head block object to the BlockManager
 * (within _EXTENSION_ keyword processing).
 * </p>
 *
 * @see BlockManager
 * @see BulkBlockObject
 */
public abstract class BlockObject {

	private BlockObject nextBlockObject = null;

	protected BlockObject() {}

	/**
	 * Construct a meaningful notifier tag for this BlockObject.
	 */
	public abstract String getNotifierTag();

	/**
	 * Block until meaningful data is made available for
	 * the client application. This is called by the BlockManager
	 * in a way such that the BlockManager waits for one
	 * BlockObject to unblock.
	 */
	public abstract void block() throws InterruptedException;


	/**
	 * Eliminate the rest of the BlockObject chain.
	 */
	public final void clearNextBlockObject() {
		this.nextBlockObject = null;
	}

	/**
	 * Chain this BlockObject to another BlockObject.
	 * The chain is linear and there is no upper bounds on
	 * the number of BlockObjects that can be supported.
	 */
	public void setNextBlockObject(BlockObject bo) {
		this.nextBlockObject = bo;
	}

	protected final BlockObject getNextBlockObject() {
		return nextBlockObject;
	}

	/**
	 * Obtain all chained BlockObjects as a List,
	 * including this one.
	 * A BlockObject chain cycle causes a runtime exception
	 * to be thrown.
	 *
	 * @return A List of chained BlockObjects, including
	 *   this one.
	 *
	 * @throws AwkRuntimeException if the BlockObject
	 *   chain contains a cycle.
	 */
	public List<BlockObject> getBlockObjects() {
		List<BlockObject> retval = new LinkedList<BlockObject>();
		Set<BlockObject> blockObjects = new HashSet<BlockObject>();
		BlockObject ref = this;
		while (ref != null) {
			if (blockObjects.contains(ref)) {
				throw new AwkRuntimeException("Block chain contains a cycle (duplicate) : " + ref.getClass().getName() + " / " + ref.getNotifierTag());
			} else {
				blockObjects.add(ref);
			}
			retval.add(ref);
			ref = ref.getNextBlockObject();
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
