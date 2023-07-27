package org.jawk;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import org.jawk.util.AwkParameters;
import org.jawk.util.AwkSettings;

/**
 * Entry point into the parsing, analysis, and execution/compilation
 * of a Jawk script.
 * This entry point is used when Jawk is executed as a stand-alone application.
 * If you want to use Jawk as a library, please use {@see Awk}.
 */
public class Main {

	/**
	 * Prohibit the instantiation of this class, other than the
	 * way required by JSR 223.
	 */
	@SuppressWarnings("unused")
	private Main() {}

	/**
	 * Class constructor to support the JSR 223 scripting interface
	 * already provided by Java SE 6.
	 *
	 * @param args String arguments from the command-line.
	 * @param is The input stream to use as stdin.
	 * @param os The output stream to use as stdout.
	 * @param es The output stream to use as stderr.
	 * @throws Exception enables exceptions to propagate to the callee.
	 */
	public Main(String[] args, InputStream is, PrintStream os, PrintStream es)
			throws Exception
	{
		System.setIn(is);
		System.setOut(os);
		System.setErr(es);
		
		AwkSettings settings = AwkParameters.parseCommandLineArguments(args);
		Awk awk = new Awk();
		awk.invoke(settings);
	}

	/**
	 * The entry point to Jawk for the VM.
	 * <p>
	 * The main method is a simple call to the invoke method.
	 * The current implementation is basically as follows:
	 * <blockquote>
	 * <pre>
	 * System.exit(invoke(args));
	 * </pre>
	 * </blockquote>
	 * </p>
	 *
	 * @param args Command line arguments to the VM.
	 *
	 * @throws IOException upon an IO error.
	 * @throws ClassNotFoundException if compilation is requested,
	 *	 but no compilation implementation class is found.
	 */
	public static void main(String[] args) {
		
		try {
			AwkSettings settings = AwkParameters.parseCommandLineArguments(args);
			Awk awk = new Awk();
			awk.invoke(settings);
		} catch (ExitException e) {
			System.exit(e.getCode());
		} catch (Exception e) {
			System.err.printf("%s: %s", e.getClass().getSimpleName(), e.getMessage());
			System.exit(1);
		}
		
	}


}
