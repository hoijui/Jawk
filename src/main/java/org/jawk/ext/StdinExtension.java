
package org.jawk.ext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.jawk.NotImplementedError;
import org.jawk.jrt.BlockObject;

/**
 * Enable stdin processing in Jawk, to be used in conjunction with the -ni parameter.
 * Since normal input processing is turned off via -ni, this is provided to enable a way
 * to read input from stdin.
 * <p>
 * To use:
 * <blockquote><pre>
 * StdinGetline() == 1 { print "--&gt; " $0 }
 * </pre></blockquote>
 * <p>
 * The extension functions are as follows:
 * <ul>
 * <hr>
 * <li><strong><em><font size=+1>StdinHasInput</font></em></strong> -<br>
 * Returns 1 when StdinGetline() does not block (i.e., when input is available
 * or upon an EOF), 0 otherwise.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>none
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>1 when StdinGetline() does not block, 0 otherwise.
 * </ul><p>
 * <li><strong><em><font size=+1>StdinGetline</font></em></strong> -<br>
 * Retrieve a line of input from stdin.  The operation
 * will block until input is available, EOF, or an IO error.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>none
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>1 upon successful read of a line of input from stdin,
 * 	0 upon an EOF, and -1 when an IO error occurs.
 * </ul><p>
 * <li><strong><em><font size=+1>StdinBlock</font></em></strong> -<br>
 * Block until a call to StdinGetline() would not block.
 * <strong>Parameters:</strong>
 * <ul>
 * <li>chained block function - optional
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>"Stdin" if this block object is triggered
 * </ul><p>
 * <hr>
 * </ul>
 */
public class StdinExtension extends AbstractExtension implements JawkExtension {

  private final BlockingQueue<Object> getline_input = new LinkedBlockingQueue<Object>();
  private static final Object DONE = new Object();

  public StdinExtension()
  throws IOException {

	Thread getline_input_thread = new Thread("getline_input_thread") {
	  public final void run() {
		try {
			BufferedReader br = new BufferedReader(
					new InputStreamReader(System.in));
			String line;
			while((line = br.readLine()) != null) {
				getline_input.put(line);
				synchronized(blocker) {
					blocker.notify();
				}
			}
		} catch (InterruptedException ie) {
			// do nothing ... the thread death will signal an issue
			ie.printStackTrace();
		} catch (IOException ioe) {
			// do nothing ... the thread death will signal an issue
			ioe.printStackTrace();
		}
		try {
			getline_input.put(DONE);
		} catch (InterruptedException ie) {
			ie.printStackTrace();
			System.err.println("Should never be interrupted.");
			System.exit(1);
		}
		synchronized(blocker) {
			blocker.notify();
		}
	  }
	};
	getline_input_thread.setDaemon(true);
	getline_input_thread.start();
  }

  public String getExtensionName() { return "Stdin Support"; }
  public String[] extensionKeywords() {
	return new String[] {
		// keyboard stuff
		"StdinHasInput",	// i.e.  b = StdinHasInput()
		"StdinGetline",	// i.e.  retcode = StdinGetline() # $0 = the input
		"StdinBlock",	// i.e.  StdinBlock(...)
	};
  }

  public Object invoke(String keyword, Object[] args) {
	if (false)
		;
	else if (keyword.equals("StdinHasInput")) {
		checkNumArgs(args, 0);
		return stdinhasinput();
	}
	else if (keyword.equals("StdinGetline")) {
		checkNumArgs(args, 0);
		return stdingetline();
	}
	else if (keyword.equals("StdinBlock"))
		if (args.length == 0)
			return stdinblock();
		else if (args.length == 1)
			return stdinblock((BlockObject) args[0]);
		else
			throw new IllegalArgumentException("StdinBlock accepts 0 or 1 args.");
	else
		throw new NotImplementedError(keyword);
	// never reached
	return null;
  }

  private boolean is_eof = false;

  private final int stdinhasinput() {
	if (is_eof)
		// upon eof, always "don't block" !
		return 1;
	else if (getline_input.size() == 0)
		// nothing in the queue
		return 0;
	else if (getline_input.size() == 1 && getline_input.peek() == DONE)
		// DONE indicator in the queue
		return 0;
	else
		// otherwise, something to read
		return 1;
  }

  /**
   * @return 1 upon successful read,
   * 	0 upon EOF, and -1 if an IO error occurs
   */
  private Object stdingetline() {
	try {
		if (is_eof)
			return 0;
		Object line_obj = getline_input.take();
		if (line_obj == DONE) {
			is_eof = true;
			return 0;
		}
		jrt.input_line = (String) line_obj;
		jrt.jrtParseFields();
		return 1;
	} catch (InterruptedException ie) {
		ie.printStackTrace();
		return -1;
	}
  }

  private BlockObject blocker = new BlockObject() {
	public String getNotifierTag() { return "Stdin"; }
	public final void block()
	throws InterruptedException {
		synchronized(blocker) {
			if (stdinhasinput() == 0)
				blocker.wait();
		}
	}
  };

  private final BlockObject stdinblock() {
	blocker.clearNextBlockObject();
	return blocker;
  }
  private final BlockObject stdinblock(BlockObject bo) {
	assert bo != null;
	blocker.setNextBlockObject(bo);
	return blocker;
  }
}

