package org.jawk;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import org.jawk.util.AwkParameters;
import org.jawk.util.AwkSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point into the parsing, analysis, and execution/compilation
 * of a Jawk script.
 * This entry point is used when Jawk is executed as a stand-alone application.
 * If you want to use Jawk as a library, please use {@see Awk}.
 */
public class Main {

	private static final Logger LOG = LoggerFactory.getLogger(Main.class);

	/**
	 * Prohibit the instantiation of this class, other than the
	 * way required by JSR 223.
	 */
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
		invoke(args);
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
	public static void main(String[] args)
			throws IOException, ClassNotFoundException
	{
		if (isWindows()) {
			// under windows, we do not want to pass on a Java Exception
			// to the OS (hoijui: why?)
			try {
				invoke(args);
			} catch (Error err) {
				LOG.error("Severe Error", err);
				System.exit(1);
			} catch (ExitException eex) {
				LOG.error("The application requested an exit", eex);
				System.exit(eex.getCode());
			} catch (RuntimeException re) {
				LOG.error("Unexpected state", re);
				System.exit(1);
			}
		} else {
			// ... under all other OS, we do
			try {
				invoke(args);
			} catch (ExitException eex) {
				LOG.error("The application requested an exit", eex);
				System.exit(eex.getCode());
			}
		}
	}

	/**
	 * An entry point to Jawk that provides the exit code of the script
	 * if interpreted or an compiler error status if compiled.
	 * If compiled, a non-zero exit status indicates that there
	 * was a compilation problem.
	 *
	 * @param args Command line arguments to the VM,
	 *   or JSR 223 scripting interface arguments.
	 *
	 * @throws IOException upon an IO error.
	 * @throws ClassNotFoundException if compilation is requested,
	 *	 but no compilation implementation class is found.
	 * @throws ExitException a specific exit code is requested.
	 */
	private static void invoke(String[] args)
			throws IOException, ClassNotFoundException, ExitException
	{
		AwkParameters parameters = new AwkParameters(Main.class, null); // null = NO extension description ==> require AWK script
		AwkSettings settings = parameters.parseCommandLineArguments(args);
		Awk awk = new Awk();
		awk.invoke(settings);
	}

	private static boolean isWindows() {
		return (System.getProperty("os.name").indexOf("Windows") >= 0);
	}
}
