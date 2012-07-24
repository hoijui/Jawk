package org.jawk.util;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;

/**
 * Manages the command-line parameters accepted by Jawk.
 * The parameters and their meanings are provided below:
 *
 * <ul>
 * <li>-v name=val [-v name=val] ... <br/>
 *   Variable assignments prior to the execution of the script.</li>
 * <li>-F regexp <br/>
 *   Field separator (FS).</li>
 * <li>-f filename <br/>
 *   Use the text contained in filename as the script rather than
 *   obtaining it from the command-line.</li>
 * <li><i>Extension</i> -c <br/>
 *   Write intermediate file. Intermediate file can be used as
 *   an argument to -f.</li>
 * <li><i>Extension</i> -o filename <br/>
 *   Output filename for intermediate file, tuples, or syntax tree.</li>
 * <li><i>Extension</i> -z <br/>
 *   Compile to JVM rather than interpret it.</li>
 * <li><i>Extension</i> -Z <br/>
 *   Compile to JVM rather and execute it.</li>
 * <li><i>Extension</i> -d <br/>
 *   Compile results to destination directory instead of current working dir.</li>
 * <li><i>Extension</i> -s <br/>
 *   Dump the intermediate code.</li>
 * <li><i>Extension</i> -S <br/>
 *   Dump the syntax tree.</li>
 * <li><i>Extension</i> -x <br/>
 *   Enables _sleep, _dump, and exec keywords/functions.</li>
 * <li><i>Extension</i> -y <br/>
 *   Enables _INTEGER, _DOUBLE, and _STRING type casting keywords.</li>
 * <li><i>Extension</i> -t <br/>
 *   Maintain array keys in sorted order (using a TreeMap instead of a HashMap)</li>
 * <li><i>Extension</i> -r <br/>
 *   Do NOT error for <code>IllegalFormatException</code> when using
 *   <code>java.util.Formatter</code> for <code>sprintf</code>
 *   and <code>printf</code>.</li>
 * <li><i>Extension</i> -ext <br/>
 *   Enabled user-defined extensions. Works together with the
 *   -Djava.extensions property.
 *   It also disables blank rule as mapping to a print $0 statement.</li>
 * <li><i>Extension</i> -ni <br/>
 *   Do NOT consume stdin or files from ARGC/V through input rules.
 *   The motivation is to leave input rules for blocking extensions
 *   (i.e., Sockets, Dialogs, etc).</li>
 * </ul>
 * followed by the script (if -f is not provided), then followed
 * by a list containing zero or more of the following parameters:
 * <ul>
 * <li>name=val <br/>
 *   Variable assignments occurring just prior to receiving input
 *   (but after the BEGIN blocks, if any).</li>
 * <li>filename <br/>
 *   Filenames to treat as input to the script.</li>
 * </ul>
 * <p>
 * If no filenames are provided, stdin is used as input
 * to the script (but only if there are input rules).
 * </p>
 */
public class AwkParameters {

	private Class mainClass;

	private String extensionDescription;

	/**
	 * @param mainClass The main class to print when displaying usage.
	 * @param extensionDescription a text description of extensions that
	 *   are enabled (for compiled scripts)
	 */
	public AwkParameters(Class mainClass, String extensionDescription) {
		this.mainClass = mainClass;
		this.extensionDescription = extensionDescription;
	}

	/**
	 * Parses AWK command line parameters,
	 * for example from the VM entry point <code>main()</code>.
	 * <p>
	 * The command-line argument semantics are as follows:
	 * <ul>
	 * <li>First, "-" arguments are processed until first non-"-" argument
	 *   is encountered, or the "-" itself is provided.</li>
	 * <li>Next, a script is expected (unless the -f argument was provided).</li>
	 * <li>Then, subsequent parameters are passed into the script
	 *   via the ARGC/ARGV variables.</li>
	 * </ul>
	 * </p>
	 *
	 * @param args The command-line arguments provided by the user.
	 */
	public AwkSettings parseCommandLineArguments(String[] args) {

		AwkSettings settings = new AwkSettings();

		int arg_idx = 0;
		try {
			// optional parameter mode (i.e. args[i].charAt(0) == '-')
			while (arg_idx < args.length) {
				assert args[arg_idx] != null;
				if (args[arg_idx].length() == 0) {
					throw new IllegalArgumentException("zero-length argument at position " + (arg_idx + 1));
				}
				if (args[arg_idx].charAt(0) != '-') {
					// no more -X arguments
					break;
				} else if (args[arg_idx].equals("-")) {
					// no more -X arguments
					++arg_idx;
					break;
				} else if (args[arg_idx].equals("-v")) {
					checkParameterHasArgument(args, arg_idx);
					++arg_idx;
					checkInitialVariableFormat(args[arg_idx]);
					addVariable(settings, args[arg_idx]);
				} else if (args[arg_idx].equals("-f")) {
					checkParameterHasArgument(args, arg_idx);
					++arg_idx;
					settings.addScriptSource(new ScriptFileSource(args[arg_idx]));
				} else if (args[arg_idx].equals("-d")) {
					checkParameterHasArgument(args, arg_idx);
					++arg_idx;
					settings.setDestinationDirectory(args[arg_idx]);
				} else if (args[arg_idx].equals("-c")) {
					settings.setWriteIntermediateFile(true);
				} else if (args[arg_idx].equals("-o")) {
					checkParameterHasArgument(args, arg_idx);
					++arg_idx;
					settings.setOutputFilename(args[arg_idx]);
				} else if (args[arg_idx].equals("-z")) {
					settings.setCompile(true);
				} else if (args[arg_idx].equals("-Z")) {
					settings.setCompileRun(true);
				} else if (args[arg_idx].equals("-S")) {
					settings.setDumpSyntaxTree(true);
				} else if (args[arg_idx].equals("-s")) {
					settings.setDumpIntermediateCode(true);
				} else if (args[arg_idx].equals("-x")) {
					settings.setAdditionalFunctions(true);
				} else if (args[arg_idx].equals("-y")) {
					settings.setAdditionalTypeFunctions(true);
				} else if (args[arg_idx].equals("-t")) {
					settings.setUseSortedArrayKeys(true);
				} else if (args[arg_idx].equals("-r")) {
					settings.setCatchIllegalFormatExceptions(false);
				} else if (args[arg_idx].equals("-F")) {
					checkParameterHasArgument(args, arg_idx);
					++arg_idx;
					settings.setFieldSeparator(args[arg_idx]);
				} else if (args[arg_idx].equals("-ext")) {
					settings.setUserExtensions(true);
				} else if (args[arg_idx].equals("-ni")) {
					settings.setUseStdIn(true);
				} else if (args[arg_idx].equals("-h") || args[arg_idx].equals("-?")) {
					usage(System.out, extensionDescription);
				} else {
					throw new IllegalArgumentException("unknown parameter: "+args[arg_idx]);
				}

				++arg_idx;
			}

			if (extensionDescription == null) {
				// script mode (if -f is not provided)
				if (settings.getScriptSources().isEmpty()) {
					if (arg_idx >= args.length) {
						throw new IllegalArgumentException("Awk script not provided.");
					}
					String scriptContent = args[arg_idx++];
					settings.addScriptSource(new ScriptSource(
							ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT,
							new StringReader(scriptContent),
							false));
				} else {
					try {
						// XXX Maybe we should delay that to a later stage? The only difference would be, that errors (for example: File not found, or unable to read) would occure later
						// initialize the Readers or InputStreams
						for (ScriptSource scriptSource : settings.getScriptSources()) {
							if (scriptSource.isIntermediate()) {
								scriptSource.getInputStream();
							} else {
								scriptSource.getReader();
							}
						}
					} catch (IOException ex) {
						ex.printStackTrace();
						usage(System.err, extensionDescription);
					}
				}
			}
		} catch (IllegalArgumentException iae) {
			iae.printStackTrace(System.err);
			usage(System.err, extensionDescription);
			throw iae;
		}

		// name=val or filename mode
		while (arg_idx < args.length) {
			String name_value_filename = args[arg_idx++];
			settings.getNameValueOrFileNames().add(name_value_filename);
		}

		return settings;
	}

	/**
	 * Dump usage to stderr; exit with a non-zero error code.
	 */
	private void usage(PrintStream dest, String extension_description) {
		//String cls = Awk.class.getName();
		String cls = mainClass.getName();
		dest.println("usage:");
		dest.println(
				"java ... " + cls + " [-F fs_val]"
				+ (extension_description == null ? ""
				+ " [-f script-filename]"
				+ " [-o output-filename]"
				+ " [-c]"
				+ " [-z]"
				+ " [-Z]"
				+ " [-d dest-directory]"
				+ " [-S]"
				+ " [-s]"
				+ " [-x]"
				+ " [-y]"
				+ " [-r]"
				+ " [-ext]"
				+ " [-ni]"
				: "")
				+ " [-t]"
				+ " [-v name=val]..."
				+ (extension_description == null ? " [script]" : "")
				+ " [name=val | input_filename]...");
		dest.println();
		dest.println(" -F fs_val = Use fs_val for FS.");
		if (extension_description == null) {
			dest.println(" -f filename = Use contents of filename for script.");
		}
		dest.println(" -v name=val = Initial awk variable assignments.");
		dest.println();
		dest.println(" -t = (extension) Maintain array keys in sorted order.");
		if (extension_description == null) {
			dest.println(" -c = (extension) Compile to intermediate file. (default: a.ai)");
			dest.println(" -o = (extension) Specify output file.");
			dest.println(" -z = (extension) | Compile for JVM. (default: AwkScript.class)");
			dest.println(" -Z = (extension) | Compile for JVM and execute it. (default: AwkScript.class)");
			dest.println(" -d = (extension) | Compile to destination directory. (default: <CWD>)");
			dest.println(" -S = (extension) Write the syntax tree to file. (default: syntax_tree.lst)");
			dest.println(" -s = (extension) Write the intermediate code to file. (default: avm.lst)");
			dest.println(" -x = (extension) Enable _sleep, _dump as keywords, and exec as a builtin func.");
			//dest.println("                  (Note: exec not enabled in compiled mode.)");
			dest.println("                  (Note: exec enabled only in interpreted mode.)");
			dest.println(" -y = (extension) Enable _INTEGER, _DOUBLE, and _STRING casting keywords.");
			dest.println(" -r = (extension) Do NOT hide IllegalFormatExceptions for [s]printf.");
			dest.println("-ext= (extension) Enable user-defined extensions. (default: not enabled)");
			dest.println("-ni = (extension) Do NOT process stdin or ARGC/V through input rules.");
			dest.println("                  (Useful for blocking extensions.)");
			//dest.println("                  (Note: -ext & -ni not available in compiled mode.)");
			dest.println("                  (Note: -ext & -ni available only in interpreted mode.)");
		} else {
			// separate the extension description
			// from the -t argument description (above)
			// with a newline
			dest.println();
			dest.println(extension_description);
		}
		dest.println();
		dest.println(" -h or -? = (extension) This help screen.");
		if (dest == System.out) {
			System.exit(0);
		} else {
			// invalid usage ... return a non-zero error code
			System.exit(1);
		}
	}

	/**
	 * Validates that a required argument is provided with the parameter.
	 * This could have been done with a simple
	 * <code>if (arg_idx+1 &gt;= args.length) ...</code>.
	 * However,
	 * <ul>
	 * <li>this normalizes the implementation throughout the class.</li>
	 * <li>additional assertions are performed.</li>
	 * </ul>
	 */
	private static void checkParameterHasArgument(String[] args, int arg_idx)
			throws IllegalArgumentException
	{
		assert arg_idx < args.length;
		assert args[arg_idx].charAt(0) == '-';
		if (arg_idx + 1 >= args.length) {
			throw new IllegalArgumentException("Need additional argument for " + args[arg_idx]);
		}
	}

	/**
	 * Makes sure the argument is of the form name=value.
	 */
	private static void checkInitialVariableFormat(String v_arg)
			throws IllegalArgumentException
	{
		int equals_count = 0;
		int length = v_arg.length();
		for (int i = 0; equals_count <= 1 && i < length; i++) {
			if (v_arg.charAt(i) == '=') {
				++equals_count;
			}
		}
		if (equals_count != 1) {
			throw new IllegalArgumentException("v_arg \"" + v_arg + "\" must be of the form name=value");
		}
	}

	private static void addVariable(AwkSettings settings, String v_arg) {
		int eq_idx = v_arg.indexOf('=');
		assert eq_idx >= 0;
		String name = v_arg.substring(0, eq_idx);
		String value_string = v_arg.substring(eq_idx + 1);
		Object value;
		// deduce type
		try {
			value = Integer.parseInt(value_string);
		} catch (NumberFormatException nfe) {
			try {
				value = Double.parseDouble(value_string);
			} catch (NumberFormatException nfe2) {
				value = value_string;
			}
		}
		// note: this can overwrite previously defined variables
		settings.getVariables().put(name, value);
	}
}
