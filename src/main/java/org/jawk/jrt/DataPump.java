package org.jawk.jrt;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

/**
 * Relay data from an input stream to an output stream.
 * A thread is created to do the work.
 * <p>
 * Jawk uses data pumps to relay stdin, stdout, and stderr
 * of a spawned process (by, for example, system() or
 * "cmd" | getline) to the stdin, stdout, and/or stderr
 * of the calling process (the interpreter itself).
 */
public class DataPump extends Thread {

	private InputStream is;
	private PrintStream os;

	/**
	 * Allocate the data pump and start the thread.
	 *
	 * @param s A human-readable description of this data pump.
	 *   It is part of the thread name, and, therefore, visible
	 *   upon a VM thread dump.
	 * @param is The input stream.
	 * @param os The output stream.
	 */
	public DataPump(String s, InputStream is, PrintStream os) {
		super("DataPump for " + s);
		this.is = is;
		this.os = os;
		//setDaemon(true);
		start();
	}

	/**
	 * VM entry point for the thread.  It performs the data
	 * relay.
	 */
	public final void run() {
		try {
			byte[] b = new byte[4096];
			int len;
			while ((len = is.read(b, 0, b.length)) >= 0) {
				os.write(b, 0, len);
			}
		} catch (IOException ioe) {
			// ignore
		}
		try {
			is.close();
		} catch (IOException ioe) {}
	}
} // public class DataPump {Thread}
