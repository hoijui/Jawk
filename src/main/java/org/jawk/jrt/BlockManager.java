package org.jawk.jrt;

import java.util.LinkedList;
import java.util.List;

/**
 * Manages multiple blocking code segments simultaneously such that
 * unblocking one block condition releases the block of all other
 * block code segments.
 *
 * @see BlockObject
 * @see BulkBlockObject
 */
public class BlockManager {
  /**
   * Executes all block segments simultaneously, waiting for
   * one block release.
   * <p>
   * The algorithm is as follows:
   * <ul>
   * <li>Collect linked block objects into a List.
   * <li>Spawn a BlockThread for each block object.
   * <li>Wait for notification from any of the BlockThreads.
   * <li>Interrupt remaining block threads.
   * <li>Wait for each BlockThread to die.
   * <li>Return the block object notifier which satisified their block condition.
   * </ul>
   * <p>
   * And, the BlockThread algorithm is as follows:
   * <p>
   * <ul>
   * <li>try, catch for InterruptedException ...
   * 	<ul>
   * 	<li>Execute the BlockObject block segment.
   * 	<li>Assign the notifier from this BlockObject
   * 		if one isn't already assigned (to mitigate
   *	 	a race condition).
   * 	<li>Notify the BlockManager.
   * 	</ul>
   * <li>If interrupted, do nothing and return.
   * </ul>
   *
   * @param bo BlockObject to employ.  Other block objects
   *	may be linked to this block object.  In this event,
   *	employ all block objects simultaneously.
   */
  public String block(BlockObject bo) {
	// get all block objects
	List<BlockObject> bos = bo.getBlockObjects();
	// each block object contains a wait statement
	// (either indefinite or timed)

	// for each block object
	// 	spawn a thread (preferably using a threadpool)
	// 	do the wait
	//	signal a break in the block
	// interrupt all other threads, resulting in InterruptedExceptions

		List<Thread> thread_list = new LinkedList<Thread>();
	synchronized(BlockManager.this) {
		for(BlockObject blockobj : bos) {
			// spawn a thread
			Thread t = new BlockThread(blockobj);
			t.start();
			thread_list.add(t);
		}

		// now, wait for notification from one of the BlockThreads
		try {
			BlockManager.this.wait();
		} catch (InterruptedException ie) {}
	}

	// block successful, interrupt other blockers
	// and wait for thread deaths
	for(Thread t : thread_list) {
		t.interrupt();
		try {
			t.join();
		} catch (InterruptedException ie) {}
	}

	// return who was the notifier
	assert notifier != null;
	return notifier;
  }

  private Object NOTIFIER_LOCK = "NOTIFIER_LOCK";
  private String notifier = null;

  private class BlockThread extends Thread {
	private BlockObject bo;
	private BlockThread(BlockObject bo) {
		setName("BlockThread for "+bo.getNotifierTag());
		this.bo = bo;
	}
	public final void run() {
		try {
			bo.block();
			synchronized(NOTIFIER_LOCK) {
				if (notifier == null)
					notifier = bo.getNotifierTag();
			}
			synchronized(BlockManager.this) {
				BlockManager.this.notify();
			}
		} catch (InterruptedException ie) {
		} catch (RuntimeException re) {
			re.printStackTrace();
			System.err.println("(exitting)");
			System.exit(1);
		}
	}
  }
}
