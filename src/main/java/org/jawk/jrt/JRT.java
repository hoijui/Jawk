package org.jawk.jrt;

// There must be NO imports to org.jawk.*,
// other than org.jawk.jrt which occurs by
// default. We wish to house all
// required runtime classes in jrt.jar,
// not have to refer to jawk.jar!

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Jawk runtime coordinator.
 * The JRT services interpreted and compiled Jawk scripts, mainly
 * for IO and other non-CPU bound tasks. The goal is to house
 * service functions into a Java-compiled class rather than
 * to hand-craft service functions in byte-code, or cut-paste
 * compiled JVM code into the compiled AWK script. Also,
 * since these functions are non-CPU bound, the need for
 * inlining is reduced.
 * <p>
 * Variable access is achieved through the VariableManager interface.
 * The constructor requires a VariableManager instance (which, in
 * this case, is the compiled Jawk class itself).
 * </p>
 * <p>
 * Main services include:
 * <ul>
 * <li>File and command output redirection via print(f).
 * <li>File and command input redirection via getline.
 * <li>Most built-in AWK functions, such as system(), sprintf(), etc.
 * <li>Automatic AWK type conversion routines.
 * <li>IO management for input rule processing.
 * <li>Random number engine management.
 * <li>Input field ($0, $1, ...) management.
 * </ul>
 * </p>
 * <p>
 * All static and non-static service methods should be package-private
 * to the resultant AWK script class rather than public. However,
 * the resultant script class is not in the <code>org.jawk.jrt</code> package
 * by default, and the user may reassign the resultant script class
 * to another package. Therefore, all accessed methods are public.
 * </p>
 *
 * @see VariableManager
 */
public class JRT {

	private static final Logger LOG = LoggerFactory.getLogger(JRT.class);

	private static final boolean IS_WINDOWS = (System.getProperty("os.name").indexOf("Windows") >= 0);

	/**
	 * The default regular expression for setRecordSeparator.
	 * The AVM refers to this field, so that the field exists
	 * in one place.
	 */
	public static final String DEFAULT_RS_REGEX = System.getProperty("line.separator", null);
	//public static final String DEFAULT_RS_REGEX = "(\n)|(\r\n)";

	static {
		assert (DEFAULT_RS_REGEX != null) : "line.separator not found in System properties ?!";
	}

	private VariableManager vm;

	private Map<String, Process> output_processes = new HashMap<String, Process>();
	private Map<String, PrintStream> output_streams = new HashMap<String, PrintStream>();

	// Paritioning reader for stdin.
	private PartitioningReader partitioningReader = null;
	// Current input line ($0).
	private String inputLine = null;
	// Current input fields ($0, $1, $2, ...).
	private List<String> input_fields = new ArrayList<String>(100);
	private AssocArray arglist_aa = null;
	private int arglist_idx;
	private boolean has_filenames = false;
	private static final String BLANK = "";

	private static final Integer ONE = Integer.valueOf(1);
	private static final Integer ZERO = Integer.valueOf(0);
	private static final Integer MINUS_ONE = Integer.valueOf(-1);
	private String jrt_input_string;

	private Map<String, PartitioningReader> file_readers = new HashMap<String, PartitioningReader>();
	private Map<String, PartitioningReader> command_readers = new HashMap<String, PartitioningReader>();
	private Map<String, Process> command_processes = new HashMap<String, Process>();
	private Map<String, PrintStream> outputFiles = new HashMap<String, PrintStream>();

	/**
	 * Create a JRT with a VariableManager
	 *
	 * @param vm The VariableManager to use with this JRT.
	 */
	public JRT(VariableManager vm) {
		this.vm = vm;
	}

	/**
	 * Assign all -v variables.
	 *
	 * @param initial_var_map A map containing all initial variable
	 *   names and their values.
	 */
	public final void assignInitialVariables(Map<String, Object> initial_var_map) {
		assert initial_var_map != null;
		for (Map.Entry<String, Object> var : initial_var_map.entrySet()) {
			vm.assignVariable(var.getKey(), var.getValue());
		}
	}

	/**
	 * Called by AVM/compiled modules to assign local
	 * environment variables to an associative array
	 * (in this case, to ENVIRON).
	 *
	 * @param aa The associative array to populate with
	 *   environment variables. The module asserts that
	 *   the associative array is empty prior to population.
	 */
	public static void assignEnvironmentVariables(AssocArray aa) {
		assert aa.keySet().isEmpty();
		Map<String, String> env = System.getenv();
		for (Map.Entry<String, String> var : env.entrySet()) {
			aa.put(var.getKey(), var.getValue());
		}
	}

	/**
	 * Convert Strings, Integers, and Doubles to Strings
	 * based on the CONVFMT variable contents.
	 *
	 * @param o Object to convert.
	 * @param convfmt The contents of the CONVFMT variable.
	 *
	 * @return A String representation of o.
	 */
	public static String toAwkString(Object o, String convfmt) {
		StringBuffer convfmt_sb = new StringBuffer();
		//private String convfmt = "%.2g";
		Formatter convfmt_formatter = new Formatter(convfmt_sb);

		if (o instanceof Number) {
			double d = ((Number) o).doubleValue();
			if (d == (int) d) {
				return Integer.toString((int) d);
			} else {
				convfmt_sb.setLength(0);
				try {
					//convfmt_formatter.format(getCONVFMT(), d);
					convfmt_formatter.format(convfmt, d);
					//return Double.toString(d);
					return convfmt_sb.toString();
				} catch (java.util.UnknownFormatConversionException ufce) {
					return "";
				}
			}
		} else {
			return o.toString();
		}
	}

	// not static to use CONVFMT (& possibly OFMT later)
	/**
	 * Convert a String, Integer, or Double to String
	 * based on the OFMT variable contents. Jawk will
	 * subsequently use this String for output via print().
	 *
	 * @param o Object to convert.
	 * @param ofmt The contents of the OFMT variable.
	 *
	 * @return A String representation of o.
	 */
	public static String toAwkStringForOutput(Object o, String ofmt) {
		if (o instanceof Number) {
			double d = ((Number) o).doubleValue();
			if (d == (int) d) {
				return Integer.toString((int) d);
			} else {
				//ofmt_sb.setLength(0);
				try {
					StringBuffer ofmt_sb = new StringBuffer();
					Formatter ofmt_formatter = new Formatter(ofmt_sb);
					ofmt_formatter.format(ofmt, d);
					return ofmt_sb.toString();
				} catch (java.util.UnknownFormatConversionException ufce) {
					return "";
				}
			}
		} else {
			return o.toString();
		}
	}

	/*
	 * Convert a String, Integer, or Double to Double.
	 *
	 * @param o Object to convert.
	 * @param convfmt The contents of the CONVFMT variable.
	 *
	 * @return A String representation of o.
	 */
	public static double toDouble(Object o) {
		if (o instanceof Number) {
			return ((Number) o).doubleValue();
		} else {
			try {
				return Double.parseDouble(o.toString());
			} catch (NumberFormatException nfe) {
				return 0;
			}
		}
	}

	/**
	 * Compares two objects. Whether to employ less-than, equals, or
	 * greater-than checks depends on the mode chosen by the callee.
	 * It handles Awk variable rules and type conversion semantics.
	 *
	 * @param o1 The 1st object.
	 * @param o2 the 2nd object.
	 * @param mode <ul>
	 *   <li>&lt; 0 - Return true if o1 &lt; o2.</li>
	 *   <li>0 - Return true if o1 == o2.</li>
	 *   <li>&gt; 0 - Return true if o1 &gt; o2.</li>
	 *   </ul>
	 */
	public static boolean compare2(Object obj1, Object obj2, int mode) {

		// TODO check for hybrid analysis

		Object o1 = obj1;
		Object o2 = obj2;

		if (!(o1 instanceof Number)) {
			try {
				o1 = Double.parseDouble(o1.toString());
			} catch (NumberFormatException nfe) {
				// Empty variable treated as 0
				if (o1.toString().length() == 0)
					o1 = 0.0;
			}
		}
		if (!(o2 instanceof Number)) {
			try {
				o2 = Double.parseDouble(o2.toString());
			} catch (NumberFormatException nfe) {
				// Empty variable treated as 0
				if (o2.toString().length() == 0)
					o2 = 0.0;
			}
		}

		if ((o1 instanceof Number) && (o2 instanceof Number)) {
			if (mode < 0) {
				return (((Number) o1).doubleValue() < ((Number) o2).doubleValue());
			} else if (mode == 0) {
				return (((Number) o1).doubleValue() == ((Number) o2).doubleValue());
			} else {
				return (((Number) o1).doubleValue() > ((Number) o2).doubleValue());
			}
		} else {

			// string equality usually occurs more often than natural ordering comparison
			if (mode == 0) {
				return (o1.toString().equals(o2.toString()));
			} else if (mode < 0) {
				return (o1.toString().compareTo(o2.toString()) < 0);
			} else {
				return (o1.toString().compareTo(o2.toString()) > 0);
			}
		}
	}

	/**
	 * Return an object which is numerically equivalent to
	 * one plus a given object. For Integers and Doubles,
	 * this is similar to o+1. For Strings, attempts are
	 * made to convert it to a double first. If the
	 * String does not represent a valid Double, 1 is returned.
	 *
	 * @param o The object to increase.
	 *
	 * @return o+1 if o is an Integer or Double object, or
	 *   if o is a String object and represents a double.
	 *   Otherwise, 1 is returned. If the return value
	 *   is an integer, an Integer object is returned.
	 *   Otherwise, a Double object is returned.
	 */
	public static Object inc(Object o) {
		assert (o != null);
		double ans;
		if (o instanceof Number) {
			ans = ((Number) o).doubleValue() + 1;
		} else {
			try {
				ans = Double.parseDouble(o.toString()) + 1;
			} catch (NumberFormatException nfe) {
				ans = 1;
			}
		}
		if (ans == (int) ans) {
			return (int) ans;
		} else {
			return ans;
		}
	}

	/**
	 * Return an object which is numerically equivalent to
	 * one minus a given object. For Integers and Doubles,
	 * this is similar to o-1. For Strings, attempts are
	 * made to convert it to a double first. If the
	 * String does not represent a valid Double, -1 is returned.
	 *
	 * @param o The object to increase.
	 *
	 * @return o-1 if o is an Integer or Double object, or
	 *   if o is a String object and represents a double.
	 *   Otherwise, -1 is returned. If the return value
	 *   is an integer, an Integer object is returned.
	 *   Otherwise, a Double object is returned.
	 */
	public static Object dec(Object o) {
		double ans;
		if (o instanceof Number) {
			ans = ((Number) o).doubleValue() - 1;
		} else {
			try {
				ans = Double.parseDouble(o.toString()) - 1;
			} catch (NumberFormatException nfe) {
				ans = 1;
			}
		}
		if (ans == (int) ans) {
			return (int) ans;
		} else {
			return ans;
		}
	}

	// non-static to reference "inputLine"
	/**
	 * Converts an Integer, Double, String, Pattern,
	 * or PatternPair to a boolean.
	 *
	 * @param o The object to convert to a boolean.
	 *
	 * @return For the following class types for o:
	 * 	<ul>
	 * 	<li><strong>Integer</strong> - o.intValue() != 0
	 * 	<li><strong>Double</strong> - o.doubleValue() != 0
	 * 	<li><strong>String</strong> - o.length() &gt; 0
	 * 	<li><strong>Pattern</strong> - $0 ~ o
	 * 	<li><strong>PatternPair</strong> - $0 ~ o (with some state information)
	 * 	</ul>
	 * 	If o is none of these types, an error is thrown.
	 *
	 * @see PatternPair
	 */
	public final boolean toBoolean(Object o) {
		boolean val;
		if (o instanceof Integer) {
			val = ((Integer)o).intValue() != 0;
		} else if (o instanceof Double) {
			val = ((Double)o).doubleValue() != 0;
		} else if (o instanceof String) {
			val = (o.toString().length() > 0);
		} else if (o instanceof Pattern) {
			// match against $0
			// ...
			Pattern pattern = (Pattern) o;
			String s = inputLine == null ? BLANK : inputLine;
			Matcher matcher = pattern.matcher(s);
			val = matcher.find();
		} else if (o instanceof PatternPair) {
			String s = inputLine == null ? BLANK : inputLine;
			val = ((PatternPair) o).matches(s);
		} else {
			throw new Error("Unknown operand_stack type: " + o.getClass() + " for value " + o);
		}
		return val;
	}

	/**
	 * Splits the string into parts separated by one or more spaces;
	 * blank first and last fields are eliminated.
	 * This conforms to the 2-argument version of AWK's split function.
	 *
	 * @param array The array to populate.
	 * @param string The string to split.
	 * @param convfmt Contents of the CONVFMT variable.
	 *
	 * @return The number of parts resulting from this split operation.
	 */
	public static int split(Object array, Object string, String convfmt) {
		return splitWorker(new StringTokenizer(toAwkString(string, convfmt)), (AssocArray) array);
	}
	/**
	 * Splits the string into parts separated the regular expression fs.
	 * This conforms to the 3-argument version of AWK's split function.
	 * <p>
	 * If fs is blank, it behaves similar to the 2-arg version of
	 * AWK's split function.
	 *
	 * @param fs Field separator regular expression.
	 * @param array The array to populate.
	 * @param string The string to split.
	 * @param convfmt Contents of the CONVFMT variable.
	 *
	 * @return The number of parts resulting from this split operation.
	 */
	public static int split(Object fs, Object array, Object string, String convfmt) {
		String fs_string = toAwkString(fs, convfmt);
		if (fs_string.equals(" ")) {
			return splitWorker(new StringTokenizer(toAwkString(string, convfmt)), (AssocArray) array);
		} else if (fs_string.equals("")) {
			return splitWorker(new CharacterTokenizer(toAwkString(string, convfmt)), (AssocArray) array);
		} else if (fs_string.length() == 1) {
			return splitWorker(new SingleCharacterTokenizer(toAwkString(string, convfmt), fs_string.charAt(0)), (AssocArray) array);
		} else {
			return splitWorker(new RegexTokenizer(toAwkString(string, convfmt), fs_string), (AssocArray) array);
		}
	}

	private static int splitWorker(Enumeration<Object> e, AssocArray aa) {
		int cnt = 0;
		aa.clear();
		while (e.hasMoreElements()) {
			aa.put(++cnt, e.nextElement());
		}
		return cnt;
	}

	public PartitioningReader getPartitioningReader() {
		return partitioningReader;
	}

	public String getInputLine() {
		return inputLine;
	}

	public void setInputLine(String inputLine) {
		this.inputLine = inputLine;
	}

	/**
	 * Attempt to consume one line of input, either from stdin
	 * or from filenames passed in to ARGC/ARGV via
	 * the command-line.
	 *
	 * @param for_getline true if call is for getline, false otherwise.
	 *
	 * @return true if line is consumed, false otherwise.
	 *
	 * @throws IOException upon an IO error.
	 */
	public boolean jrtConsumeInput(final InputStream input, boolean for_getline) throws IOException {
		// first time!
		if (arglist_aa == null) {
			Object arglist_obj = vm.getARGV(); // vm.getVariable("argv_field", true);
			arglist_aa = (AssocArray) arglist_obj;
			arglist_idx = 1;

			// calculate has_filenames

			int argc = (int) toDouble(vm.getARGC()); //(vm.getVariable("argc_field", true));
			// 1 .. argc doesn't make sense
			// 1 .. argc-1 does since arguments of:
			// a b c
			// result in:
			// ARGC=4
			// ARGV[0]="java Awk"
			// ARGV[1]="a"
			// ARGV[2]="b"
			// ARGV[3]="c"
			for (int i = 1; i < argc; i++) {
				if (arglist_aa.isIn(i)) {
					Object namevalue_or_filename_object = arglist_aa.get(i);
					String namevalue_or_filename = toAwkString(namevalue_or_filename_object, vm.getCONVFMT().toString());
					if (namevalue_or_filename.indexOf('=') == -1) {
						// filename!
						has_filenames = true;
						break;
					}
				}
			}
		}

		// initial: pr == null
		// subsequent: pr != null, but eof

		while (true) {
			try {
				if (partitioningReader == null) {
					int argc = (int) toDouble(vm.getARGC()); // (vm.getVariable("argc_field", true));
					Object o = BLANK;
					while(arglist_idx <= argc) {
						o = arglist_aa.get(arglist_idx);
						++arglist_idx;
						if (!o.toString().equals(BLANK)) {
							break;
						}
					}
					if (!o.equals(BLANK)) {
						String name_value_or_filename = toAwkString(o, vm.getCONVFMT().toString());
						if (name_value_or_filename.indexOf('=') == -1) {
							partitioningReader = new PartitioningReader(new FileReader(name_value_or_filename), vm.getRS().toString(), true);
							vm.setFILENAME(name_value_or_filename);
							vm.resetFNR();
						} else {
							setFilelistVariable(name_value_or_filename);
							if (!has_filenames) {
								// stdin with a variable!
								partitioningReader = new PartitioningReader(new InputStreamReader(input), vm.getRS().toString());
								vm.setFILENAME("");
							} else {
								continue;
							}
						}
					} else if (!has_filenames) {
						partitioningReader = new PartitioningReader(new InputStreamReader(input), vm.getRS().toString());
						vm.setFILENAME("");
					} else {
						return false;
					}
				} else if (inputLine == null) {
					if (has_filenames) {
						int argc = (int) toDouble(vm.getARGC());
						Object o = BLANK;
						while(arglist_idx <= argc) {
							o = arglist_aa.get(arglist_idx);
							++arglist_idx;
							if (!o.toString().equals(BLANK)) {
								break;
							}
						}
						if (!o.equals(BLANK)) {
							String name_value_or_filename = toAwkString(o, vm.getCONVFMT().toString());
							if (name_value_or_filename.indexOf('=') == -1) {
								// true = from filename list
								partitioningReader = new PartitioningReader(new FileReader(name_value_or_filename), vm.getRS().toString(), true);
								vm.setFILENAME(name_value_or_filename);
								vm.resetFNR();
							} else {
								setFilelistVariable(name_value_or_filename);
								vm.incNR();
								continue;
							}
						} else {
							return false;
						}
					} else {
						return false;
					}
				}


				// when active_input == false, usually means
				// to instantiate "pr" (PartitioningReader for $0, etc)
				// for Jawk extensions
				//if (!active_input)
				//	return false;

				inputLine = partitioningReader.readRecord();
				if (inputLine == null) {
					continue;
				} else {
					if (for_getline) {
						// TRUE
						// leave result on the stack
						// DO NOTHING! The callee will re-acquire $0
					} else {
						// FALSE
						// leave the stack alone ...
						jrtParseFields();
					}
					vm.incNR();
					if (partitioningReader.fromFilenameList()) {
						vm.incFNR();
					}
					return true;
				}
			} catch (IOException ioe) {
				LOG.warn("IO Exception", ioe);
				continue;
			}
		}
	}

	private void setFilelistVariable(String name_value) {
		int eq_idx = name_value.indexOf('=');
		// variable name should be non-blank
		assert eq_idx >= 0;
		if (eq_idx == 0) {
			throw new IllegalArgumentException("Must have a non-blank variable name in a name=value variable assignment argument.");
		}
		String name = name_value.substring(0, eq_idx);
		String value = name_value.substring(eq_idx + 1);
		Object obj;
		try {
			obj = Integer.parseInt(value);
		} catch (NumberFormatException nfe) {
			try {
				obj = Double.parseDouble(value);
			} catch (NumberFormatException nfe2) {
				obj = value;
			}
		}
		vm.assignVariable(name, obj);
	}

	/**
	 * Splits $0 into $1, $2, etc.
	 * Called when an update to $0 has occurred.
	 */
	public void jrtParseFields() {
		String fs_string = vm.getFS().toString();
		Enumeration<Object> tokenizer;
		if (fs_string.equals(" ")) {
			tokenizer = new StringTokenizer(inputLine);
		} else if (fs_string.length() == 1) {
			tokenizer = new SingleCharacterTokenizer(inputLine, fs_string.charAt(0));
		} else if (fs_string.equals("")) {
			tokenizer = new CharacterTokenizer(inputLine);
		} else {
			tokenizer = new RegexTokenizer(inputLine, fs_string);
		}

		assert inputLine != null;
		input_fields.clear();
		input_fields.add(inputLine); // $0
		while (tokenizer.hasMoreElements()) {
			input_fields.add((String) tokenizer.nextElement());
		}
		// recalc NF
		recalculateNF();
	}

	private void recalculateNF() {
		vm.setNF(Integer.valueOf(input_fields.size() - 1));
	}

	private static int toFieldNumber(Object o) {
		int fieldnum;
		if (o instanceof Number) {
			fieldnum = ((Number) o).intValue();
		} else {
			try {
				fieldnum = (int) Double.parseDouble(o.toString());
			} catch (NumberFormatException nfe) {
				throw new RuntimeException("Field $(" + o.toString() + ") is incorrect.");
			}
		}
		return fieldnum;
	}

	/**
	 * Retrieve the contents of a particular input field.
	 *
	 * @param fieldnum_obj Object referring to the field number.
	 *
	 * @return Contents of the field.
	 */
	public Object jrtGetInputField(Object fieldnum_obj) {
		return jrtGetInputField(toFieldNumber(fieldnum_obj));
	}

	public Object jrtGetInputField(int fieldnum) {
		if (fieldnum < input_fields.size()) {
			String retval = input_fields.get(fieldnum);
			assert retval != null;
			return retval;
		} else {
			return BLANK;
		}
	}

	/**
	 * Stores value_obj into an input field.
	 *
	 * @param value_obj The RHS of the assignment.
	 * @param field_num Object referring to the field number.
	 *
	 * @return A string representation of value_obj.
	 */
	public String jrtSetInputField(Object value_obj, int field_num) {
		assert field_num >= 1;
		assert value_obj != null;
		String value = value_obj.toString();
		// if the value is BLANK
		if (value.equals(BLANK)) {
			if (field_num < input_fields.size()) {
				input_fields.set(field_num, BLANK);
			}
		} else {
			// append the list to accommodate the new value
			for (int i = input_fields.size() - 1; i < field_num; i++) {
				input_fields.add(BLANK);
			}
			input_fields.set(field_num, value);
		}
		// rebuild $0
		rebuildDollarZeroFromFields();
		// recalc NF
		recalculateNF();
		return value;
	}

	private void rebuildDollarZeroFromFields() {
		StringBuilder new_dollar_zero_sb = new StringBuilder();
		String ofs = vm.getOFS().toString();
		for (int i = 1; i < input_fields.size(); i++) {
			if (i > 1) {
				new_dollar_zero_sb.append(ofs);
			}
			new_dollar_zero_sb.append(input_fields.get(i));
		}
		input_fields.set(0, new_dollar_zero_sb.toString());
	}

	public Integer jrtConsumeFileInputForGetline(String filename) {
		try {
			if (jrtConsumeFileInput(filename)) {
				return ONE;
			} else {
				jrt_input_string = "";
				return ZERO;
			}
		} catch (IOException ioe) {
			jrt_input_string = "";
			return MINUS_ONE;
		}
	}

	/**
	 * Retrieve the next line of output from a command, executing
	 * the command if necessary and store it to $0.
	 *
	 * @param cmd_string The command to execute.
	 *
	 * @return Integer(1) if successful, Integer(0) if no more
	 * 	input is available, Integer(-1) upon an IO error.
	 */
	public Integer jrtConsumeCommandInputForGetline(String cmd_string) {
		try {
			if (jrtConsumeCommandInput(cmd_string)) {
				return ONE;
			} else {
				jrt_input_string = "";
				return ZERO;
			}
		} catch (IOException ioe) {
			jrt_input_string = "";
			return MINUS_ONE;
		}
	}

	/**
	 * Retrieve $0.
	 *
	 * @return The contents of the $0 input field.
	 */
	public String jrtGetInputString() {
		return jrt_input_string;
	}

	public Map<String, PrintStream> getOutputFiles() {
		return outputFiles;
	}

	/**
	 * Retrieve the PrintStream which writes to a particular file,
	 * creating the PrintStream if necessary.
	 *
	 * @param filename The file which to write the contents of the PrintStream.
	 * @param append true to append to the file, false to overwrite the file.
	 */
	public final PrintStream jrtGetPrintStream(String filename, boolean append) {
		PrintStream ps = outputFiles.get(filename);
		if (ps == null) {
			try {
				outputFiles.put(filename, ps = new PrintStream(new FileOutputStream(filename, append), true));	// true = autoflush
			} catch (IOException ioe) {
				throw new AwkRuntimeException("Cannot open " + filename + " for writing: " + ioe);
			}
		}
		assert ps != null;
		return ps;
	}

	public boolean jrtConsumeFileInput(String filename) throws IOException {
		PartitioningReader pr = file_readers.get(filename);
		if (pr == null) {
			try {
				file_readers.put(filename, pr = new PartitioningReader(new FileReader(filename), vm.getRS().toString()));
				vm.setFILENAME(filename);
			} catch (IOException ioe) {
				LOG.warn("IO Exception", ioe);
				file_readers.remove(filename);
				throw ioe;
			}
		}

		inputLine = pr.readRecord();
		if (inputLine == null) {
			return false;
		} else {
			jrt_input_string = inputLine;
			vm.incNR();
			return true;
		}
	}

	private static Process spawnProcess(String cmd) throws IOException {

		Process p;

		if (IS_WINDOWS) {
			// spawn the process!
			ProcessBuilder pb = new ProcessBuilder(("cmd.exe /c " + cmd).split("[ \t]+"));
			p = pb.start();
		} else {
			// spawn the process!
			ProcessBuilder pb = new ProcessBuilder(cmd.split("[ \t]+"));
			p = pb.start();
		}

		return p;
	}

	@SuppressWarnings("UseOfSystemOutOrSystemErr")
	public boolean jrtConsumeCommandInput(String cmd) throws IOException {
		PartitioningReader pr = command_readers.get(cmd);
		if (pr == null) {
			try {
				Process p = spawnProcess(cmd);
				// no input to this process!
				p.getOutputStream().close();
				DataPump.dump(cmd, p.getErrorStream(), System.err);
				command_processes.put(cmd, p);
				command_readers.put(cmd, pr = new PartitioningReader(new InputStreamReader(p.getInputStream()), vm.getRS().toString()));
				vm.setFILENAME("");
			} catch (IOException ioe) {
				LOG.warn("IO Exception", ioe);
				command_readers.remove(cmd);
				Process p = command_processes.get(cmd);
				command_processes.remove(cmd);
				if (p != null) {
					p.destroy();
				}
				throw ioe;
			}
		}

		inputLine = pr.readRecord();
		if (inputLine == null) {
			return false;
		} else {
			jrt_input_string = inputLine;
			vm.incNR();
			return true;
		}
	}

	/**
	 * Retrieve the PrintStream which shuttles data to stdin for a process,
	 * executing the process if necessary. Threads are created to shuttle the
	 * data to/from the process.
	 *
	 * @param cmd The command to execute.
	 *
	 * @return The PrintStream which to write to provide
	 *   input data to the process.
	 */
	@SuppressWarnings("UseOfSystemOutOrSystemErr")
	public PrintStream jrtSpawnForOutput(String cmd) {
		PrintStream ps = output_streams.get(cmd);
		if (ps == null) {
			Process p;
			try {
				p = spawnProcess(cmd);
				DataPump.dump(cmd, p.getErrorStream(), System.err);
				DataPump.dump(cmd, p.getInputStream(), System.out);
			} catch (IOException ioe) {
				throw new AwkRuntimeException("Can't spawn " + cmd + ": " + ioe);
			}
			output_processes.put(cmd, p);
			output_streams.put(cmd, ps = new PrintStream(p.getOutputStream(), true));	// true = auto-flush
		}
		return ps;
	}

	/**
	 * Attempt to close an open stream, whether it is
	 * an input file, output file, input process, or output
	 * process.
	 * <p>
	 * The specification did not describe AWK behavior
	 * when attempting to close streams/processes with
	 * the same file/command name. In this case,
	 * <em>all</em> open streams with this name
	 * are closed.
	 * </p>
	 *
	 * @param filename The filename/command process to close.
	 *
	 * @return Integer(0) upon a successful close, Integer(-1)
	 *   otherwise.
	 */
	public Integer jrtClose(String filename) {
		boolean b1 = jrtCloseFileReader(filename);
		boolean b2 = jrtCloseCommandReader(filename);
		boolean b3 = jrtCloseOutputFile(filename);
		boolean b4 = jrtCloseOutputStream(filename);
		// either close will do
		return (b1 || b2 || b3 || b4) ? ZERO : MINUS_ONE;
	}

	public void jrtCloseAll() {
		Set<String> set = new HashSet<String>();
		for (String s : file_readers.keySet()) {
			set.add(s);
		}
		for (String s : command_readers.keySet()) {
			set.add(s);
		}
		for (String s : outputFiles.keySet()) {
			set.add(s);
		}
		for (String s : output_streams.keySet()) {
			set.add(s);
		}
		for (String s : set) {
			jrtClose(s);
		}
	}

	private boolean jrtCloseOutputFile(String filename) {
		PrintStream ps = outputFiles.get(filename);
		if (ps != null) {
			ps.close();
			outputFiles.remove(filename);
		}
		return ps != null;
	}

	private boolean jrtCloseOutputStream(String cmd) {
		Process p = output_processes.get(cmd);
		PrintStream ps = output_streams.get(cmd);
		if (ps == null) {
			return false;
		}
		assert p != null;
		output_processes.remove(cmd);
		output_streams.remove(cmd);
		ps.close();
		// if windows, let the process kill itself eventually
		if (!IS_WINDOWS) {
			try {
				// causes a hard exit ?!
				p.waitFor();
				p.exitValue();
			} catch (InterruptedException ie) {
				throw new AwkRuntimeException("Caught exception while waiting for process exit: " + ie);
			}
		}
		return true;
	}

	private boolean jrtCloseFileReader(String filename) {
		PartitioningReader pr = file_readers.get(filename);
		if (pr == null) {
			return false;
		}
		file_readers.remove(filename);
		try {
			pr.close();
			return true;
		} catch (IOException ioe) {
			return false;
		}
	}

	private boolean jrtCloseCommandReader(String cmd) {
		Process p = command_processes.get(cmd);
		PartitioningReader pr = command_readers.get(cmd);
		if (pr == null) {
			return false;
		}
		assert p != null;
		command_readers.remove(cmd);
		command_processes.remove(cmd);
		try {
			pr.close();
			// if windows, let the process kill itself eventually
			if (!IS_WINDOWS) {
				try {
					// causes a hard die ?!
					p.waitFor();
					p.exitValue();
				} catch (InterruptedException ie) {
					throw new AwkRuntimeException("Caught exception while waiting for process exit: " + ie);
				}
			}
			return true;
		} catch (IOException ioe) {
			return false;
		}
	}

	/**
	 * Executes the command specified by cmd and waits
	 * for termination, returning an Integer object
	 * containing the return code.
	 * stdin to this process is closed while
	 * threads are created to shuttle stdout and
	 * stderr of the command to stdout/stderr
	 * of the calling process.
	 *
	 * @param cmd The command to execute.
	 *
	 * @return Integer(return_code) of the created
	 *   process. Integer(-1) is returned on an IO error.
	 */
	@SuppressWarnings("UseOfSystemOutOrSystemErr")
	public static Integer jrtSystem(String cmd) {
		try {
			Process p = spawnProcess(cmd);
			// no input to this process!
			p.getOutputStream().close();
			DataPump.dump(cmd, p.getErrorStream(), System.err);
			DataPump.dump(cmd, p.getInputStream(), System.out);
			try {
				int retcode = p.waitFor();
				return Integer.valueOf(retcode);
			} catch (InterruptedException ie) {
				return Integer.valueOf(p.exitValue());
			}
		} catch (IOException ioe) {
			LOG.warn("IO Exception", ioe);
			return MINUS_ONE;
		}
	}

	/**
	 * Applies a format string to a set of parameters and
	 * returns the formatted result.
	 * String.format() is used to perform the formatting.
	 * Thus, an IllegalFormatException can be thrown.
	 * If so, a blank string ("") is returned.
	 *
	 * @param arr Arguments to format.
	 * @param fmt_arg The format string to apply.
	 *
	 * @return The formatted string; a blank string
	 *   if the format argument is invalid.
	 *
	 * @see #sprintfFunctionNoCatch(Object[],String)
	 */
	public static String sprintfFunction(Object[] arr, String fmt_arg) {
		try {
			return String.format(fmt_arg, arr);
		} catch (IllegalFormatException ife) {
			return "";
		}
	}

	/**
	 * Applies a format string to a set of parameters and
	 * prints the result to stdout.
	 * The implementation is a simple call to sprintfFunction:
	 * <blockquote>
	 * <pre>
	 * System.out.print(sprintfFunction(arr, fmt_arg));
	 * </pre>
	 * </blockquote>
	 * String.format() is used to perform the formatting.
	 * Thus, an IllegalFormatException can be thrown.
	 * If so, a blank string ("") is printed.
	 *
	 * @param arr Arguments to format.
	 * @param fmt_arg The format string to apply.
	 *
	 * @see #printfFunctionNoCatch(Object[],String)
	 */
	@SuppressWarnings("UseOfSystemOutOrSystemErr")
	public static void printfFunction(Object[] arr, String fmt_arg) {
		System.out.print(sprintfFunction(arr, fmt_arg));
	}

	/**
	 * Applies a format string to a set of parameters and
	 * prints the result to a PrintStream.
	 * The implementation is a simple call to sprintfFunction:
	 * <blockquote>
	 * <pre>
	 * ps.print(sprintfFunction(arr, fmt_arg));
	 * </pre>
	 * </blockquote>
	 * String.format() is used to perform the formatting.
	 * Thus, an IllegalFormatException can be thrown.
	 * If so, a blank string ("") is printed.
	 *
	 * @param ps The PrintStream to use for printing.
	 * @param arr Arguments to format.
	 * @param fmt_arg The format string to apply.
	 *
	 * @see #printfFunctionNoCatch(PrintStream,Object[],String)
	 */
	public static void printfFunction(PrintStream ps, Object[] arr, String fmt_arg) {
		ps.print(sprintfFunction(arr, fmt_arg));
	}

	public static String sprintfFunctionNoCatch(Object[] arr, String fmt_arg)
			throws IllegalFormatException
	{
		return String.format(fmt_arg, arr);
	}

	@SuppressWarnings("UseOfSystemOutOrSystemErr")
	public static void printfFunctionNoCatch(Object[] arr, String fmt_arg) {
		System.out.print(sprintfFunctionNoCatch(arr, fmt_arg));
	}

	public static void printfFunctionNoCatch(PrintStream ps, Object[] arr, String fmt_arg) {
		ps.print(sprintfFunctionNoCatch(arr, fmt_arg));
	}

	public static Integer replaceFirst(Object orig_value_obj, Object repl_obj, Object ere_obj, StringBuffer sb, String convfmt) {
		String orig_value = toAwkString(orig_value_obj, convfmt);
		String repl = toAwkString(repl_obj, convfmt);
		String ere = toAwkString(ere_obj, convfmt);
		// remove special meaning for backslash and dollar signs
		repl = Matcher.quoteReplacement(repl);
		sb.setLength(0);
		sb.append(orig_value.replaceFirst(ere, repl));
		if (sb.toString().equals(orig_value)) {
			return ZERO;
		} else {
			return ONE;
		}
	}

	public static Integer replaceAll(Object orig_value_obj, Object repl_obj, Object ere_obj, StringBuffer sb, String convfmt) {
		String orig_value = toAwkString(orig_value_obj, convfmt);
		String repl = toAwkString(repl_obj, convfmt);
		String ere = toAwkString(ere_obj, convfmt);
		// remove special meaning for backslash and dollar signs
		repl = Matcher.quoteReplacement(repl);
		sb.setLength(0);

		Pattern p = Pattern.compile(ere);
		Matcher m = p.matcher(orig_value);
		int cnt = 0;
		while (m.find()) {
			++cnt;
			m.appendReplacement(sb, repl);
		}
		m.appendTail(sb);
		return Integer.valueOf(cnt);
	}

	public static String substr(Object startpos_obj, String str) {
		int startpos = (int) toDouble(startpos_obj);
		if (startpos <= 0) {
			throw new AwkRuntimeException("2nd arg to substr must be a positive integer");
		}
		if (startpos > str.length()) {
			return "";
		} else {
			return str.substring(startpos - 1);
		}
	}

	public static String substr(Object size_obj, Object startpos_obj, String str) {
		int startpos = (int) toDouble(startpos_obj);
		if (startpos <= 0) {
			throw new AwkRuntimeException("2nd arg to substr must be a positive integer");
		}
		if (startpos > str.length()) {
			return "";
		}
		int size = (int) toDouble(size_obj);
		if (size < 0) {
			throw new AwkRuntimeException("3nd arg to substr must be a non-negative integer");
		}
		if (startpos + size > str.length()) {
			return str.substring(startpos - 1);
		} else {
			return str.substring(startpos - 1, startpos + size - 1);
		}
	}

	public static int timeSeed() {
		long l = (new Date()).getTime();
		long l2 = (l % (1000 * 60 * 60 * 24));
		int seed = (int) l2;
		return seed;
	}

	public static Random newRandom(int seed) {
		return new Random(seed);
	}

	public void applyRS(Object rs_obj) {
//	if (rs_obj.toString().equals(BLANK))
//		rs_obj = DEFAULT_RS_REGEX;
		if (partitioningReader != null) {
			partitioningReader.setRecordSeparator(rs_obj.toString());
		}
	}
}
