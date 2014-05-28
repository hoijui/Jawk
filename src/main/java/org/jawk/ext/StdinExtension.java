
package org.jawk.ext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.jawk.NotImplementedError;
import org.jawk.jrt.BlockObject;
import org.jawk.jrt.JRT;
import org.jawk.jrt.VariableManager;
import org.jawk.util.AwkSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enable stdin processing in Jawk, to be used in conjunction with the -ni parameter.
 * Since normal input processing is turned off via -ni, this is provided to enable a way
 * to read input from stdin.
 *
 * <p>
 * To use:
 * <blockquote><pre>
 * StdinGetline() == 1 { print "--&gt; " $0 }
 * </pre></blockquote>
 * </p>
 *
 * <p>
 * The extension functions are as follows:
 * <ul>
 * <hr/>
 * <li>
 *   <p>
 *   <strong><em><font size=+1>StdinHasInput</font></em></strong> -<br/>
 *   Returns 1 when StdinGetline() does not block (i.e., when input is available
 *   or upon an EOF), 0 otherwise.<br/>
 *   <strong>Parameters:</strong>
 *   <ul>
 *   <li>none</li>
 *   </ul>
 *   <strong>Returns:</strong>
 *   <ul>
 *   <li>1 when StdinGetline() does not block, 0 otherwise.</li>
 *   </ul>
 *   </p>
 * <li>
 *   <p>
 *   <strong><em><font size=+1>StdinGetline</font></em></strong> -<br/>
 *   Retrieve a line of input from stdin. The operation
 *   will block until input is available, EOF, or an IO error.<br/>
 *   <strong>Parameters:</strong>
 *   <ul>
 *   <li>none</li>
 *   </ul>
 *   <strong>Returns:</strong>
 *   <ul>
 *   <li>1 upon successful read of a line of input from stdin,
 *     0 upon an EOF, and -1 when an IO error occurs.</li>
 *   </ul>
 *   </p>
 * <li>
 *   <p>
 *   <strong><em><font size=+1>StdinBlock</font></em></strong> -<br/>
 *   Block until a call to StdinGetline() would not block.<br/>
 *   <strong>Parameters:</strong>
 *   <ul>
 *   <li>chained block function - optional</li>
 *   </ul>
 *   <strong>Returns:</strong>
 *   <ul>
 *   <li>"Stdin" if this block object is triggered</li>
 *   </ul>
 *   </p></li>
 * <hr/>
 * </ul>
 * </p>
 */
public class StdinExtension extends AbstractExtension implements JawkExtension {

	private static final Logger LOG = LoggerFactory.getLogger(StdinExtension.class);

	private static final Object DONE = new Object();

	private final BlockingQueue<Object> getLineInput = new LinkedBlockingQueue<Object>();

	private final BlockObject blocker = new BlockObject() {

		@Override
		public String getNotifierTag() {
			return "Stdin";
		}

		@Override
		public final void block()
				throws InterruptedException
		{
			synchronized (blocker) {
				if (stdInHasInput() == 0) {
					blocker.wait();
				}
			}
		}
	};

	private boolean isEof = false;

	@Override
	public void init(VariableManager vm, JRT jrt, final AwkSettings settings) {
		super.init(vm, jrt, settings);

		Thread getLineInputThread = new Thread("getLineInputThread") {
			@Override
			public final void run() {
				try {
					BufferedReader br = new BufferedReader(
							new InputStreamReader(settings.getInput()));
					String line;
					while ((line = br.readLine()) != null) {
						getLineInput.put(line);
						synchronized (blocker) {
							blocker.notify();
						}
					}
				} catch (InterruptedException ie) {
					LOG.error("", ie);
					// do nothing ... the thread death will signal an issue
				} catch (IOException ioe) {
					LOG.error("", ioe);
					// do nothing ... the thread death will signal an issue
				}
				try {
					getLineInput.put(DONE);
				} catch (InterruptedException ie) {
					LOG.error("Should never be interrupted.", ie);
					System.exit(1);
				}
				synchronized (blocker) {
					blocker.notify();
				}
			}
		};
		getLineInputThread.setDaemon(true);
		getLineInputThread.start();
	}

	@Override
	public String getExtensionName() {
		return "Stdin Support";
	}

	@Override
	public String[] extensionKeywords() {
		return new String[] {
					// keyboard stuff
					"StdinHasInput", // i.e. b = StdinHasInput()
					"StdinGetline", // i.e. retcode = StdinGetline() # $0 = the input
					"StdinBlock", // i.e. StdinBlock(...)
				};
	}

	@Override
	public Object invoke(String keyword, Object[] args) {
		if        (keyword.equals("StdinHasInput")) {
			checkNumArgs(args, 0);
			return stdInHasInput();
		} else if (keyword.equals("StdinGetline")) {
			checkNumArgs(args, 0);
			return stdInGetLine();
		} else if (keyword.equals("StdinBlock")) {
			if (args.length == 0) {
				return stdInBlock();
			} else if (args.length == 1) {
				return stdInBlock((BlockObject) args[0]);
			} else {
				throw new IllegalArgumentException("StdinBlock accepts 0 or 1 args.");
			}
		} else {
			throw new NotImplementedError(keyword);
		}
	}

	private int stdInHasInput() {
		if (isEof) {
			// upon eof, always "don't block" !
			return 1;
		} else if (getLineInput.size() == 0) {
			// nothing in the queue
			return 0;
		} else if (getLineInput.size() == 1 && getLineInput.peek() == DONE) {
			// DONE indicator in the queue
			return 0;
		} else {
			// otherwise, something to read
			return 1;
		}
	}

	/**
	 * @return 1 upon successful read,
	 *   0 upon EOF, and -1 if an IO error occurs
	 */
	private Object stdInGetLine() {
		try {
			if (isEof) {
				return 0;
			}
			Object lineObj = getLineInput.take();
			if (lineObj == DONE) {
				isEof = true;
				return 0;
			}
			getJrt().setInputLine((String) lineObj);
			getJrt().jrtParseFields();
			return 1;
		} catch (InterruptedException ie) {
			LOG.warn("", ie);
			return -1;
		}
	}

	private BlockObject stdInBlock() {
		blocker.clearNextBlockObject();
		return blocker;
	}

	private BlockObject stdInBlock(BlockObject bo) {
		assert bo != null;
		blocker.setNextBlockObject(bo);
		return blocker;
	}
}
