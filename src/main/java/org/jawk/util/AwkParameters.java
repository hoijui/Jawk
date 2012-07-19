package org.jawk.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Awk Parameters.  It manages the command-line parameters accepted by Jawk.
 * The parameters and their meanings are provided below:
 *
 * <ul>
 * <li>-v name=val [-v name=val] ... <br>
 * Variable assignments prior to execution of the script.
 * <li>-F regexp <br>
 * Field separator (FS).
 * <li>-f filename <br>
 * Use the text contained in filename as the script rather than
 * obtaining it from the command-line.
 * <li><i>Extension</i> -c <br>
 * Write intermediate file.  Intermediate file can be used as
 * an argument to -f.
 * <li><i>Extension</i> -o filename <br>
 * Output filename for intermediate file, tuples, or syntax tree.
 * <li><i>Extension</i> -z <br>
 * Compile to JVM rather than interpret it.
 * <li><i>Extension</i> -Z <br>
 * Compile to JVM rather and execute it.
 * <li><i>Extension</i> -d <br>
 * Compile results to destination directory instead of current working dir.
 * <li><i>Extension</i> -s <br>
 * Dump the intermediate code.
 * <li><i>Extension</i> -S <br>
 * Dump the syntax tree.
 * <li><i>Extension</i> -x <br>
 * Enables _sleep, _dump, and exec keywords/functions.
 * <li><i>Extension</i> -y <br>
 * Enables _INTEGER, _DOUBLE, and _STRING type casting keywords.
 * <li><i>Extension</i> -t <br>
 * Maintain array keys in sorted order (using a TreeMap instead of a HashMap)
 * <li><i>Extension</i> -r <br>
 * Do NOT trap for IllegalFormatException when using java.util.Formatter
 * for sprintf and printf.
 * <li><i>Extension</i> -ext <br>
 * Enabled user-defined extensions.  Works together with the
 * -Djava.extensions property.
 * It also disables blank rule as mapping to a print $0 statement.
 * <li><i>Extension</i> -ni <br>
 * Do NOT consume stdin or files from ARGC/V through input rules.
 * The motivation is to leave input rules for blocking extensions
 * (i.e., Sockets, Dialogs, etc).
 * </ul>
 * followed by the script (if -f is not provided), then followed
 * by a list containing zero or more of the following parameters:
 * <ul>
 * <li>name=val <br>
 * Variable assignments occurring just prior to receiving input
 * (but after the BEGIN blocks, if any).
 * <li>filename <br>
 * Filenames to treat as input to the script.
 * </ul>
 * <p>
 * If no filenames are provided, stdin is used as input
 * to the script (but only if there are input rules).
 */
public class AwkParameters {

	private Class mainclass;

	/**
	 * Dump usage to stderr; exit with a non-zero error code.
	 */
	private void usage(PrintStream dest, String extension_description) {
		//String cls = Awk.class.getName();
		String cls = mainclass.getName();
		dest.println("usage:");
		dest.println(
				"java ... "+cls+" [-F fs_val]"
				+(extension_description==null?""
				+" [-f script-filename]"
				+" [-o output-filename]"
				+" [-c]"
				+" [-z]"
				+" [-Z]"
				+" [-d dest-directory]"
				+" [-S]"
				+" [-s]"
				+" [-x]"
				+" [-y]"
				+" [-r]"
				+" [-ext]"
				+" [-ni]"
				:"")
				+" [-t]"
				+" [-v name=val]..."
				+(extension_description==null?" [script]":"")
				+" [name=val | input_filename]...");
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
			dest.println(" -d = (extension) | Compile to destination directory.  (default: pwd)");
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
		} else { // invalid usage ... return a non-zero error code
			System.exit(1);
		}
	}

	/**
	 * Provide text representation of the parameters to stdout.
	 */
	void dump() {

		PrintStream out = System.out;

		out.println("Awk Parameters");
		out.println("--------------");
		out.println("initial_variables = " + initial_variables);
		out.println("name_value_filename_list = " + name_value_filename_list);
		out.println("script_filename = " + script_filename);
		out.println("script_reader = " + script_reader);
		out.println("should_compile = " + should_compile);
		out.println("should_compile_and_run = " + should_compile_and_run);
		out.println("initial_fs_value = " + initial_fs_value);
		out.println("dump_syntax_tree = " + dump_syntax_tree);
		out.println("dump_intermediate_code = " + dump_intermediate_code);
		out.println("additional_functions = " + additional_functions);
		out.println("additional_type_functions = " + additional_type_functions);
		out.println("sorted_array_keys = " + sorted_array_keys);
		out.println("trap_illegal_format_exceptions = " + trap_illegal_format_exceptions);
		out.println("write_to_intermediate_file = " + write_to_intermediate_file);
		out.println("output_filename = " + output_filename);
		out.println("dest_directory = " + dest_directory);
		out.println();
	}

	/**
	 * Contains variable assignments which are applied prior to
	 * executing the script (-v assignments).
	 */
	public Map<String, Object> initial_variables = new HashMap<String, Object>();
	/**
	 * Contains name=value or filename entries.
	 * Order is important, which is why name=value and filenames
	 * are listed in the same List container.
	 */
	public List<String> name_value_filename_list = new ArrayList<String>();
	/**
	 * Script filename (if provided).
	 * If null, using the first non-"-" parameter.
	 */
	public String script_filename = null;
	/**
	 * Reader providing the script.
	 * If script comes from the command-line, a StringReader is used.
	 */
	private Reader script_reader;
	/**
	 * Whether to interpret or compile the script.
	 * Initial value is set to false (interpret).
	 */
	public boolean should_compile = false;
	/**
	 * Whether to compile and execute the script.
	 * Initial value is set to false (interpret).
	 */
	public boolean should_compile_and_run = false;
	/**
	 * Initial FS value.
	 * Initially set to null (default FS value).
	 */
	public String initial_fs_value = null;
	/**
	 * Whether to dump the syntax tree; false by default.
	 */
	public boolean dump_syntax_tree = false;
	/**
	 * Whether to dump the intermediate code; false by default.
	 */
	public boolean dump_intermediate_code = false;
	/**
	 * Whether to enable additional functions (_sleep/_dump); false by default.
	 */
	public boolean additional_functions = false;
	/**
	 * Whether to enable additional functions (_INTEGER/_DOUBLE/_STRING); false
	 * by default.
	 */
	public boolean additional_type_functions = false;
	/**
	 * Whether to maintain array keys in sorted order; false by default.
	 */
	public boolean sorted_array_keys = false;
	/**
	 * Whether to trap IllegalFormatExceptions for [s]printf; true by default.
	 */
	public boolean trap_illegal_format_exceptions = true;

	/**
	 * Whether user extensions are enabled; false by default.
	 */
	public boolean user_extensions = false;
	/**
	 * Whether Jawk consumes stdin or ARGV file input; false by default.
	 */
	public boolean no_input = false;

	/**
	 * Write to intermediate file.
	 * Initially set to false.
	 */
	public boolean write_to_intermediate_file = false;
	/**
	 * Output filename.
	 * Initially set to null (use appropriate default filename).
	 */
	public String output_filename = null;
	/**
	 * Compiled destination directory (if provided).
	 * If null, using null (i.e., present working directory).
	 */
	private String dest_directory = null;

	/**
	 * Allocate the parameters, using the command line parameters
	 * from the VM entry point (main).
	 * <p>
	 * The command-line argument semantics are as follows:
	 * <ul>
	 * <li>First, "-" arguments are processed until first non-"-" argument
	 *	is encountered, or the "-" itself is provided.
	 * <li>Next, a script is expected (unless the -f argument was provided).
	 * <li>Then, subsequent parameters are passed into the script
	 *	via the ARGC/ARGV variables.
	 * </ul>
	 *
	 * @param mainclass The main class to print when displaying usage.
	 * @param args The command-line arguments provided by the user.
	 * @param extension_description a text description of extensions that
	 *	are enabled (for compiled scripts)
	 */
	public AwkParameters(Class mainclass, String args[], String extension_description) {
		this.mainclass = mainclass;
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
					addInitialVariable(args[arg_idx]);
				} else if (args[arg_idx].equals("-f")) {
					checkParameterHasArgument(args, arg_idx);
					++arg_idx;
					script_filename = args[arg_idx];
				} else if (args[arg_idx].equals("-d")) {
					checkParameterHasArgument(args, arg_idx);
					++arg_idx;
					dest_directory = args[arg_idx];
				} else if (args[arg_idx].equals("-c")) {
					write_to_intermediate_file = true;
				} else if (args[arg_idx].equals("-o")) {
					checkParameterHasArgument(args, arg_idx);
					++arg_idx;
					output_filename = args[arg_idx];
				} else if (args[arg_idx].equals("-z")) {
					should_compile = true;
				} else if (args[arg_idx].equals("-Z")) {
					should_compile_and_run = true;
				} else if (args[arg_idx].equals("-S")) {
					dump_syntax_tree = true;
				} else if (args[arg_idx].equals("-s")) {
					dump_intermediate_code = true;
				} else if (args[arg_idx].equals("-x")) {
					additional_functions = true;
				} else if (args[arg_idx].equals("-y")) {
					additional_type_functions = true;
				} else if (args[arg_idx].equals("-t")) {
					sorted_array_keys = true;
				} else if (args[arg_idx].equals("-r")) {
					trap_illegal_format_exceptions = false;
				} else if (args[arg_idx].equals("-F")) {
					checkParameterHasArgument(args, arg_idx);
					++arg_idx;
					initial_fs_value = args[arg_idx];
				} else if (args[arg_idx].equals("-ext")) {
					user_extensions = true;
				} else if (args[arg_idx].equals("-ni")) {
					no_input = true;
				} else if (args[arg_idx].equals("-h") || args[arg_idx].equals("-?")) {
					usage(System.out, extension_description);
				} else {
					throw new IllegalArgumentException("unknown parameter: "+args[arg_idx]);
				}

				++arg_idx;
			}

			if (extension_description == null) {
				// script mode (if -f not provided)
				if (script_filename == null) {
					if (arg_idx >= args.length) {
						throw new IllegalArgumentException("Awk script not provided.");
					}
					String script_filename = args[arg_idx++];
					script_reader = new StringReader(script_filename);
				} else {
					try {
						is_intermediate_file = script_filename.endsWith(".ai");
						if (is_intermediate_file) {
							if_input_stream = new FileInputStream(script_filename);
						} else { // read from file
							script_reader = new FileReader(script_filename);
						}
					} catch (FileNotFoundException fnfe) {
						fnfe.printStackTrace();
						usage(System.err, extension_description);
					}
				}
			}
		} catch (IllegalArgumentException iae) {
			iae.printStackTrace(System.err);
			usage(System.err, extension_description);
			throw iae;
		}

		// name=val or filename mode
		while (arg_idx < args.length) {
			String name_value_filename = args[arg_idx++];
			name_value_filename_list.add(name_value_filename);
		}
	}

	/**
	 * Validates that a required argument is provided with the parameter.
	 * This could have been done with a simple if (arg_idx+1 &gt;= args.length) ...
	 * However,
	 * <ul>
	 * <li>this normalizes the implementation throughout the class.
	 * <li>additional assertions are performed.
	 * </ul>
	 */
	private static void checkParameterHasArgument(String args[], int arg_idx)
			throws IllegalArgumentException {
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
			throws IllegalArgumentException {
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

	private void addInitialVariable(String v_arg) {
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
		// note: can overwrite previously defined variables
		initial_variables.put(name, value);
	}

	private boolean is_intermediate_file;
	private InputStream if_input_stream;

	/**
	 * Determine if the -f optarg is an intermediate file.
	 * Intermediate files end with the .ai extension.  No other
	 * determination is made whether the file is an intermediate
	 * file or not.  That is, the content of the file is not checked.
	 *
	 * @return true if the -f optarg is an intermediate file (a file
	 *   ending in .ai), false otherwise.
	 */
  public boolean isIntermediateFile() { return is_intermediate_file; }

	/**
	 * Obtain the Reader containing the script.  This returns non-null
	 * only if the -f argument is utilized.
	 *
	 * @return The reader which contains the script, null if no script
	 *	file is provided.
	 */
	public Reader scriptReader() {
		return script_reader;
	}

	/**
	 * Obtain the InputStream containing the intermediate file.
	 * This returns non-null only if the -f argument is utilized and it
	 * refers to an intermediate file.
	 *
	 * @return The reader which contains the intermediate file, null if
	 *   either the -f argument is not used, or the argument does not
	 *   refer to an intermediate file.
	 */
	public InputStream ifInputStream() {
		return if_input_stream;
	}
	/**
	 * @param default_filename The filename to return if -o argument
	 *   is not used.
	 *
	 * @return the optarg for the -o parameter, or the default_filename
	 *   parameter if -o is not utilized.
	 */
	public String outputFilename(String default_filename) {
		if (output_filename == null) {
			return default_filename;
		} else {
			return output_filename;
		}
	}

	/**
	 * @return the optarg for the -d parameter, or null
	 *   if -d is not utilized.
	 */
	public String destDirectory() {
		return dest_directory;
	}

	/**
	 * Provides a description of extensions that are enabled/disabled.
	 * The default compiler implementation uses this method
	 * to describe extensions which are compiled into the script.
	 * The description is then provided to the user within the usage.
	 *
	 * @return A description of the extensions which are enabled/disabled.
	 */
	public String extensionDescription() {
		// additional_functions (_sleep, _dump)
		// additional_type_functions (_INTEGER, _DOUBLE, _STRING)
		// sorted_array_keys
		// trap_illegal_format_exceptions (for FALSE)
		String retval = ""
				+ (additional_functions ? ", _sleep & _dump enabled" : "")
				+ (additional_type_functions ? ", _INTEGER, _DOUBLE, _STRING enabled" : "")
				+ (sorted_array_keys ? ", associative array keys are sorted" : "")
				+ (!trap_illegal_format_exceptions ? ", IllegalFormatExceptions NOT trapped" : "");
		if (retval.length() > 0) {
			return "{extensions: " + retval.substring(2) + "}";
		} else {
			return "{no compiled extensions utilized}";
		}
	}
}
