package org.jawk.jrt;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A convenience class that blocks
 * until any Blockable in the handles set is ready
 * with data (i.e., will not block).
 * It's like BlockObject in that this is returned
 * to the AVM/AwkScript for execution by
 * the BlockManager. Unlike the BlockObject, however,
 * it implements the block() and getNotifierTag()
 * to manage blocking for a collection of Blockables.
 *
 * @see BlockManager
 * @see BlockObject
 */
public final class BulkBlockObject extends BlockObject {

	/**
	 * When set true, all empty (AWK-null) handles are treated
	 * as blocked blockables, allowing other blockers to block.
	 */
	private static final boolean BYPASS_ALL_BLANK_HANDLES = true;

	private static final String ALL_HANDLES_ARE_BLANK = "ALL_HANDLES_ARE_BLANK";
	private static final String ALL_HANDLES_ARE_BLOCKED = "ALL_HANDLES_ARE_BLOCKED";

	private final String prefix;
	private final Set<String> handles;
	private final Map<String, ? extends Blockable> blockables;
	private final VariableManager vm;
	private String blockResult = null;

	/**
	 * Construct a block object which waits for any Blockable
	 * within the set of Blockables to "unblock".
	 *
	 * @param prefix First part of the return string for the block operation.
	 * @param blockables All universe of handles and their associated blockables.
	 * @param vm Required to obtain OFS (used in the construction of the
	 *   return string / notifier tag).
	 */
	public BulkBlockObject(String prefix, Map<String, ? extends Blockable> blockables, VariableManager vm) {
		if (prefix == null) {
			throw new IllegalArgumentException("prefix argument cannot be null");
		}
		if (blockables == null) {
			throw new IllegalArgumentException("blockables argument cannot be null");
		}
		if (vm == null) {
			throw new IllegalArgumentException("vm argument cannot be null");
		}
		this.prefix = prefix;
		this.handles = new LinkedHashSet<String>();
		this.blockables = blockables;
		this.vm = vm;
	}

	public boolean containsHandle(String handle) {
		return handles.contains(handle);
	}

	/**
	 * What to return to the client code when a handle is non-blocking.
	 * <p>
	 * The format is as follows :
	 * <blockquote>
	 * <pre>
	 * prefix OFS handle
	 * </pre>
	 * </p>
	 *
	 * @return The client string containing the handle of the
	 * non-blocking object.
	 */
	@Override
	public String getNotifierTag() {
		assert prefix != null;
		assert vm != null;
		return prefix
				+ JRT.toAwkString(vm.getOFS(), vm.getCONVFMT().toString())
				+ blockResult;
	}

	@Override
	public void block()
			throws InterruptedException
	{
		synchronized (this) {
			String handle = checkForNonblockHandle();

			if (handle.equals(ALL_HANDLES_ARE_BLANK)) {
				this.wait();
				throw new Error("Should never be notified.");
			}

			if (handle.equals(ALL_HANDLES_ARE_BLOCKED)) {
				this.wait();
				handle = checkForNonblockHandle();
			}
			assert (handle != null);
			assert !handle.equals(ALL_HANDLES_ARE_BLOCKED) : "handle == ALL_HANDLES_ARE_BLOCKED is an invalid return value ... willBlock() could be of issue";
			blockResult = handle;
		}
	}

	private String checkForNonblockHandle() {
		boolean allHandlesAreBlank = true;
		// cycle through all block_handles
		// check if any of them has accepted sockets
		for (String handle : handles) {
			if (BYPASS_ALL_BLANK_HANDLES && handle.equals("")) {
				continue;
			}
			allHandlesAreBlank = false;
			Blockable blockable = blockables.get(handle);
			if (blockable == null) {
				/*
				 * throw new AwkRuntimeException("handle '"+handle+"' not a
				 * valid blockable -- "+ "if these are dialog handles, perhaps
				 * you didn't clear out the dialog handle after it has been
				 * destroyed via DialogDestroy()?");
				 */
				throw new AwkRuntimeException("handle '" + handle + "' doesn't map to a valid blockable");
			}
			if (!blockable.willBlock(this)) {
				return handle;
			}
		}
		//return null;
		if (allHandlesAreBlank) {
			return ALL_HANDLES_ARE_BLANK;
		} else {
			return ALL_HANDLES_ARE_BLOCKED;
		}
	}

	private static final BlockHandleValidator NO_BLOCK_HANDLE_VALIDATION = new BlockHandleValidator() {

		@Override
		public String isBlockHandleValid(String handle) {
			// always valid
			return null;
		}
	};

	public BlockObject setHandles(Object[] args, VariableManager vm) {
		return setHandles(args, vm, NO_BLOCK_HANDLE_VALIDATION);
	}

	public BlockObject setHandles(Object[] args, VariableManager vm, BlockHandleValidator validator) {
		BlockObject blocker = this;

		if (args.length == 0) {
			throw new IllegalArgumentException(prefix + " blocker requires at least one argument.");
		}
		int numArgs = args.length;
		Object lastArg = args[numArgs - 1];
		if (lastArg instanceof BlockObject) {
			BlockObject bo = (BlockObject) lastArg;
			blocker.setNextBlockObject(bo);
			if (args.length == 1) {
				throw new IllegalArgumentException(prefix + " blocker requires at least one item to close-block on.");
			}
			--numArgs;
		} else {
			blocker.clearNextBlockObject();
		}

		// what we know
		// 0 .. numArgs-1 = items to close-block
		// numArgs >= 1

		handles.clear();

		for (int i = 0; i < numArgs; ++i) {
			Object o = args[i];
			if (o instanceof AssocArray) {
				AssocArray aa = (AssocArray) o;
				for (Object oo : aa.keySet()) {
					String handle = JRT.toAwkString(oo, vm.getCONVFMT().toString());
					String reason = validator.isBlockHandleValid(handle);
					if (reason != null) {
						throw new AwkRuntimeException(handle + ": invalid handle: " + reason);
					}
					// otherwise...
					handles.add(handle);
				}
			} else {
				String handle = JRT.toAwkString(o, vm.getCONVFMT().toString());
				String reason = validator.isBlockHandleValid(handle);
				if (reason != null) {
					throw new AwkRuntimeException(handle + ": invalid handle: " + reason);
				}
				// otherwise...
				handles.add(handle);
			}
		}

		return blocker;
	}
}
