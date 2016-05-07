package org.jawk.backend;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.bcel.Constants;
import static org.apache.bcel.Constants.*;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.BranchHandle;
import org.apache.bcel.generic.BranchInstruction;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.CompoundInstruction;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.FieldGen;
import org.apache.bcel.generic.GOTO;
import org.apache.bcel.generic.IFEQ;
import org.apache.bcel.generic.IFLE;
import org.apache.bcel.generic.IFNE;
import org.apache.bcel.generic.IFNONNULL;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionConstants;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.LocalVariableGen;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.PUSH;
import org.apache.bcel.generic.Type;
import org.jawk.intermediate.AwkTuples;
import org.jawk.intermediate.PositionForCompilation;
import org.jawk.jrt.AssocArray;
import org.jawk.jrt.AwkRuntimeException;
import org.jawk.jrt.EndException;
import org.jawk.jrt.JRT;
import org.jawk.jrt.KeyList;
import org.jawk.jrt.KeyListImpl;
import org.jawk.jrt.PatternPair;
import org.jawk.jrt.VariableManager;
import org.jawk.util.AwkParameters;
import org.jawk.util.AwkSettings;
import org.jawk.util.ScriptSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The reference implementation of the Jawk compiler.
 * Jawk intermediate code is analyzed and converted to
 * appropriate Java byte-code for execution on a modern
 * JVM.
 * <a href="http://jakarta.apache.org/bcel/" target=_TOP>The Apache Byte Code Engineering Library (BCEL)</a>
 * is used to manage the construction
 * of the compiled byte-code.
 * <p>
 * Since this reference implementation relies on the
 * BCEL to execute, Jawk employs reflection to
 * class-load this compiler implementation. If the
 * reflection fails, it is most likely because
 * the BCEL library cannot be found in the class-path.
 * Reflection was used to ensure that Jawk will
 * build and execute, even without the presence of
 * the BCEL.
 * </p>
 * <p>
 * The architecture of the resultant class is nearly
 * identical to the following Java code
 * (assuming AwkScript as the class-name and no package
 * name is provided via the -d argument):
 * <blockquote>
 * <pre>
 *
 * import org.jawk.jrt.*;
 * import org.jawk.util.AwkParameters;
 *
 * import java.util.*;
 * import java.util.regex.*;
 * import java.io.*;
 *
 * public class AwkScript implements VariableManager {
 *
 *	<strong>// use this field as the third argument to the AwkParameters
 *	// constructor when executing ScriptMain directly</strong>
 *
 *	public static final String EXTENSION_DESCRIPTION = <i><strong>extension-description-string</strong></i>;
 *
 *	private static final Integer ZERO = new Integer(0);
 *	private static final Integer ONE = new Integer(1);
 *	private static final Integer MINUS_ONE = new Integer(-1);
 *
 *	public static void main(String args[]) {
 *		AwkScript as = new AwkScript();
 *		// this is why org.jawk.util.AwkParameters is in jrt.jar ...
 *		AwkParameters ap = new AwkParameters(AwkScript.class, EXTENSION_DESCRIPTION);
 *		AwkSettings settings = ap.parseCommandLineArguments(args);
 *		// to send the error code back to the calling process
 *		System.exit(as.ScriptMain(settings));
 *	}
 *
 *	<strong>// to satisfy the VariableManager interface
 *
 *	// Note: field names here correspond to a global_N field
 *	// which are assigned upon compilation of the script.</strong>
 *
 *	public final Object getARGC() { if (<i><strong>argc_field</strong></i> == null) return ""; else return <i><strong>argc_field</strong></i>; }
 *	public final Object getCONVFMT() { if (<i><strong>convfmt_field</strong></i> == null) return ""; else return <i><strong>convfmt_field</strong></i>; }
 *	public final Object getFS() { if (<i><strong>fs_field</strong></i> == null) return ""; else return <i><strong>fs_field</strong></i>; }
 *	public final Object getARGV() { return <i><strong>argv_field</strong></i>; }
 *	public final Object getOFS() { if (<i><strong>ofs_field</strong></i> == null) return ""; else return <i><strong>ofs_field</strong></i>; }
 *	public final Object getRS() { if (<i><strong>rs_field</strong></i> == null) return ""; else return <i><strong>rs_field</strong></i>; }
 *	public final void setFILENAME(String arg) { <i><strong>filename_field</strong></i> = arg; }
 *	public final void setNF(String arg) { <i><strong>nf_field</strong></i> = arg; }
 *
 *	private Object getNR() { if (<i><strong>nr_field</strong></i> == null) return ""; else return <i><strong>nr_field</strong></i>; }
 *	private Object getFNR() { if (<i><strong>fnr_field</strong></i> == null) return ""; else return <i><strong>fnr_field</strong></i>; }
 *
 *	public final void incNR() { <i><strong>nr_field</strong></i> = (int) JRT.toDouble(JRT.inc(getNR())); }
 *	public final void incFNR() { <i><strong>fnr_field</strong></i> = (int) JRT.toDouble(JRT.inc(getFNR())); }
 *	public final void resetFNR() { <i><strong>fnr_field</strong></i> = ZERO; }
 *
 *	public final void assignField(String name, Object value) {
 *		if (name.equals("<i><strong>scalar1</strong></i>")) <i><strong>scalar1_field</strong></i> = value;
 *		else if (name.equals("<i><strong>scalar2</strong></i>")) <i><strong>scalar2_field</strong></i> = value;
 *		else if (name.equals("<i><strong>scalar3</strong></i>")) <i><strong>scalar3_field</strong></i> = value;
 *		...
 *		else if (name.equals("<i><strong>scalarN</strong></i>")) <i><strong>scalarN_field</strong></i> = value;
 *		else if (name.equals("<i><strong>funcName1</strong></i>")) <i>throw an exception</i>;
 *		else if (name.equals("<i><strong>funcName2</strong></i>")) <i>throw an exception</i>;
 *		...
 *		else if (name.equals("<i><strong>funcNameX</strong></i>")) <i>throw an exception</i>;
 *		else if (name.equals("<i><strong>assocArrayName1</strong></i>")) <i>throw an exception</i>;
 *		else if (name.equals("<i><strong>assocArrayName2</strong></i>")) <i>throw an exception</i>;
 *		...
 *		else if (name.equals("<i><strong>assocArrayNameM</strong></i>")) <i>throw an exception</i>;
 *	}
 *
 *	private JRT input_runtime;
 *	private HashMap regexps;
 *	private HashMap pattern_pairs;
 *	private int exit_code;
 *
 *	private int oldseed;
 *
 *	private Random random_number_generator;
 *
 *	<strong>// global_N refers to all the global variables,
 *	// those which are defined by default
 *	// (i.e., ARGC, ARGV, ENVIRON, NF, etc.)
 *	// and vars declared by the script.
 *	// The _SET_NUM_GLOBALS_ opcode allocates
 *	// these fields.</strong>
 *
 *	private Object global_0;
 *	private Object global_1;
 *	private Object global_2;
 *	...
 *	private Object global_N;
 *
 * 	<strong>// Call this method to invoke the Jawk script.
 *	// Refer to the static main method implementation
 *	// and Javadocs on how to build the AwkParameters.
 *	// Use the public static String EXTENSION_DESCRIPTION
 *	// field (within AwkScript) as the third parameter
 *	// to the AwkParameters constructor to ensure proper
 *	// extension description in the usage statement.</strong>
 *
 *	public final int ScriptMain(AwkSettings settings) {
 *
 *		// local variables
 *
 *		double dregister;
 *		StringBuffer sb = new StringBuffer();
 *
 *		// Field Allocation
 *		// ----------------
 *		// Could have be done in the class constructor,
 *		// but placed here to ensure proper repeat initialization
 *		// if repeat execution is required
 *		// within the same JVM instance. Because
 *		// if these were within the class constructor, each of these
 *		// data structures / int values would have to be
 *		// reinitialized in some way anyway.
 *
 *		input_runtime = JRT(this);	// this = VariableManager
 *		regexps = new HashMap();
 *		pattern_pairs = new HashMap();
 *		oldseed = 0;
 *		random_number_generator = new Random(null);
 *		exit_code = 0;
 *
 *		// script execution
 *
 *		try {
 *			///
 *			/// Compiled BEGIN and input rule blocks code here.
 *			/// (EndException is thrown when exit() is encountered.)
 *			///
 *		} catch (EndException ee) {
 *			// do nothing
 *		}
 *
 *		try {
 *			runEndBlocks();
 *		} catch (EndException ee) {
 *			// do nothing
 *		}
 *
 *		return exit_code;
 *	}
 *
 *	public void runEndBlocks() {
 *		double dregister;
 *		StringBuffer sb = new StringBuffer();
 *
 *		///
 *		/// Compiled END blocks code here.
 *		/// (EndException is thrown when exit() is encountered.)
 *		///
 *	}
 *
 *	<strong>// One of these exists for every function definition.
 *	// Arguments are reversed from its Jawk source.</strong>
 *	public Object FUNC_<i><strong>function_name</strong></i>(Object oN, Object oN-1, ... Object o2, Object o1) {
 *		Object _return_value_ = null;
 *		StringBuffer sb = new StringBuffer();
 *		double dregister = 0.0;
 *
 *		///
 *		/// Compiled function <i><strong>function_name</strong></i> code here.
 *		/// (A return() sets the _return_value_ and falls out of this
 *		/// function code block.)
 *		/// (EndException is thrown when exit() is encountered.)
 *		///
 *
 *		return _return_value_;
 *	}
 *
 *	<strong>// The following is created for every optarg version of the function
 *	// call that exists within the script for this function_name.
 *	// X > 0 && N > X</strong>
 *	public final Object FUNC_<i><strong>function_name</strong></i>(Object oX, Object oX-1, ... Object o2, Object o1) {
 *		return FUNC_<i><strong>function_name</strong></i>(null, null, ..., null, oX, oX-1, ..., o2, o1);
 *	}
 * }
 * </pre>
 * </blockquote>
 * </p>
 *
 * @see org.jawk.backend.AwkCompiler
 * @see org.jawk.backend.AVM
 * @see org.jawk.jrt.VariableManager
 *
 * @author Danny Daglas
 */
public class AwkCompilerImpl implements AwkCompiler {

	private static final Logger LOG = LoggerFactory.getLogger(AwkCompilerImpl.class);

	// These classes should exist in the jrt package because
	// the jrt.jar file contains the jrt package.

	private static final Class AssocArrayClass = AssocArray.class;
	private static final Class PatternPairClass = PatternPair.class;
	private static final Class KeyListImplClass = KeyListImpl.class;
	private static final Class EndExceptionClass = EndException.class;
	private static final Class AwkRuntimeExceptionClass = AwkRuntimeException.class;
	private static final Class VariableManagerClass = VariableManager.class;
	private static final Class JRT_Class = JRT.class;

	static {
		assert assertStaticClassVarsAreFromPackage();
	}

	private static boolean assertStaticClassVarsAreFromPackage() {
		//String packagename = System.getProperty("jawk.rtPackgeName", "org.jawk.jrt");
		String packagename = "org.jawk.jrt";
		if (packagename != null) {
			// use reflection to get all static Class definitions
			// and verify that they are members of the
			// runtime package
			Class c = AwkCompilerImpl.class;
			// foreach field in declared in the class...
			for (Field f : c.getDeclaredFields()) {
				int mod = f.getModifiers();
				// if a "private static final" member...
				if (       (mod & Modifier.PRIVATE) > 0
						&& (mod & Modifier.STATIC) > 0
						&& (mod & Modifier.FINAL) > 0
						&& f.getType() == Class.class)
				{
					try {
						// obtain the value of the field
						// and apply it here!
						Object o = f.get(null);
						Class cls = (Class) o;
						if (!cls.getPackage().getName().equals(packagename)) {
							throw new AssertionError("class " + c.toString() + " is not contained within '" + packagename + "' package. Field = " + f.toString());
						}
					} catch (IllegalAccessException iae) {
						throw new AssertionError(iae); // NOTE Thought there is an AssertionError#AssertionError(String, Throwable) ctor aswell, it was only introduced in Java 1.7, so we should not yet use it.
					}
				}
			}
		}
		// all's well
		return true;
	}

	private String classname;
	private ClassGen cg;
	private InstructionFactory factory;
	private ConstantPoolGen cp;
	private MyInstructionList il_main;
	private MethodGen mg_main;
	private Map<String, Integer> lv_main = new HashMap<String,Integer>();
	private MyInstructionList il_reb;	// reb = runEndBlocks
	private MethodGen mg_reb;	// reb = runEndBlocks
	private Map<String, Integer> lv_reb;	// reb = runEndBlocks

	private MyInstructionList il;	// active instructionlist
	private MethodGen mg;	// active methodgen
	private Map<String, Integer> local_vars;	// active localvars

	private MethodGen mg_temp = null;
	private MyInstructionList il_temp = null;
	private Map<org.jawk.intermediate.Address, List<BranchHandle>> bhs_temp = null;
	private Map<Integer, InstructionHandle> ihs_temp = null;
	private Map<String, Integer> lvs_temp = null;

	private AwkSettings settings;

	/**
	 * Creates the compiler implementation.
	 * Jawk invokes this via reflection.
	 *
	 * @param parameters Parameters to the script compiler.
	 *
	 * @see org.jawk.Awk
	 */
	public AwkCompilerImpl(AwkSettings settings) {
		this.settings = settings;
	}

	/**
	 * Overrides BCEL's InstructionList to provide instruction marking
	 * services.
	 * <p>
	 * Instruction marking aids in retrieving the first instruction
	 * of a group of instructions that represents one Jawk opcode.
	 * </p>
	 * <p>
	 * A mark must occur prior to appending an instruction
	 * to the instruction list. Then, the marked instruction
	 * must be retrieved before another instruction is marked.
	 * In other words, the instruction list keeps state of
	 * the most recent marked instruction. The application
	 * code must retrieve the marked instruction as soon
	 * as possible to do it's Jawk opcode to JVm instruction
	 * management.
	 * </p>
	 */
	private static final class MyInstructionList extends InstructionList {

		private InstructionHandle marked_handle = null;
		private boolean marked = false;

		public void mark() {
			marked = true;
		}

		@Override
		public InstructionHandle append(Instruction i) {
			InstructionHandle retval = super.append(i);
			if (marked) {
				marked_handle = retval;
				marked = false;
			}
			return retval;
		}

		@Override
		public BranchHandle append(BranchInstruction i) {
			BranchHandle retval = super.append(i);
			if (marked) {
				marked_handle = retval;
				marked = false;
			}
			return retval;
		}

		@Override
		public InstructionHandle append(CompoundInstruction i) {
			InstructionHandle retval = super.append(i);
			if (marked) {
				marked_handle = retval;
				marked = false;
			}
			return retval;
		}

		public InstructionHandle markedHandle() {
			InstructionHandle ih = marked_handle;
			marked_handle = null;
			return ih;
		}
	}

	/**
	 * Checks whether a string is a valid class-name
	 * (validity for Jawk, not necessarily for the JVM).
	 * Validity is defined as:
	 * <ul>
	 * <li>className is a non-null, non-empty string</li>
	 * <li>A <strong>className unit</strong> is defined
	 * as text between the start-of-string and a period,
	 * a period and the end-of-string, or between
	 * two periods. className units must be non-empty strings.</li>
	 * <li>className units must adhere to Java identifier rules.
	 * See {@see java.lang.Character.isJavaIdentifierStart}
	 * and {@see java.lang.Character.isJavaIdentifierPart} for rules
	 * relating to Java identifiers.</li>
	 * <li>className must not contain dollar signs ($).
	 * Even though dollar signs are common within class names
	 * to indicate inner classes, the Jawk compiler cannot
	 * process these types of names.</li>
	 * </ul>
	 * <p>
	 * If all of these tests pass, nothing occurs and the
	 * method exits. Otherwise, an IllegalArgumentException
	 * is thrown
	 * </p>
	 *
	 * @param className The className to verify.
	 *
	 * @throws IllegalArgumentException if the className does
	 * not conform to the rules described above.
	 */
	private static void validateClassname(String className)
			throws IllegalArgumentException
	{
		// - check for non-null
		assert className != null;
		// - check for non-blank classname
		if (className.length() == 0) {
			throw new IllegalArgumentException("classname cannot be black");
		}
		// - check for a valid java identifier
		if (className.charAt(0) != '.' && !Character.isJavaIdentifierStart(className.charAt(0))) {
			throw new IllegalArgumentException("classname is not a valid java identifier");
		}
		for (int i = 1; i < className.length(); i++) {
			if (className.charAt(i) != '.' && !Character.isJavaIdentifierPart(className.charAt(i))) {
				throw new IllegalArgumentException("classname is not a valid java identifier");
			}
		}
		// - check for $
		if (className.indexOf('$') >= 0) {
			throw new IllegalArgumentException("classname cannot contain a $");
		}
		// - check for no ..'s in classname
		if (className.indexOf("..") >= 0) {
			throw new IllegalArgumentException("null-package (..) found in classname");
		}

		// otherwise, all is good
	}

	/**
	 * Retrieves the directory-name, if there is one in the given className.
	 * It uses the separator parameter as class "path" separators.
	 *
	 * @param className The class-name to analyze.
	 * @param separator The separator denoting components
	 *   of the class "path".
	 *
	 * @return The "dir" part of the class "path".
	 *   If no separators exist, <code>null</code> is returned.
	 */
	private static String extractDirname(String className, String separator) {
		// converts
		// a.b.c.d
		// to
		// a.b.c
		// (if no periods exist, return null)
		assert className != null && className.length() > 0;
		int dot_idx = className.lastIndexOf(separator);
		if (dot_idx == -1) {
			return null;
		} else {
			String dirname = className.substring(0, dot_idx);
			// convert all "."'s to File.separator's
			return dirname.replace(separator, File.separator);
		}
	}

	/**
	 * Retrieves the name portion of the class "path" contained
	 * within className.
	 * If no separators (periods) exist, className is returned.
	 *
	 * @param className The className to analyze.
	 *
	 * @return The "name" part of the class "path".
	 *   If no separators (periods) exist, className is returned.
	 */
	private static String extractClassname(String className) {
		// converts
		// a.b.c.d
		// to
		// d
		assert className != null && className.length() > 0;
		int dot_idx = className.lastIndexOf('.');
		if (dot_idx == -1) {
			return className;
		} else {
			return className.substring(dot_idx + 1);
		}
	}

	/**
	 * Prepares for compilation.
	 * Actions are as follows:
	 * <ul>
	 * <li>Allocate the ScriptMain and runEndBlocks method objects.</li>
	 * <li>Allocate local variables (dregister and sb) for both methods.</li>
	 * <li>Allocate static helper objects (i.e., ZERO, ONE, and MINUS_ONE).</li>
	 * <li>Allocate some global helper objects (i.e., random_seed_generator).</li>
	 * </ul>
	 * <p>
	 * <strong>TODO:</strong> analyze tuples to check for
	 * <ul>
	 * <li>_CONCAT_ - if not found, no need to allocate sb</li>
	 * <li>_RANDOM_ - if not found, no need to allocate random_seed_generator</li>
	 * <li>arithmetic opcodes - if not found, no need to allocate dregister</li>
	 * <li><i>etc...</i></li>
	 * </ul>
	 * </p>
	 */
	private void precompile() {
		// setup JVM compilation stuff
		//classname = "AwkScript";
		classname = settings.getOutputFilename("AwkScript");
		validateClassname(classname);

		// This string is only used as decorative/descriptive thing.
		// No actual data is tried to be read from "the file"
		// it supposedly points to.
		String scriptFilename = "";
		for (ScriptSource scriptSource : settings.getScriptSources()) {
			scriptFilename = scriptFilename + " " + scriptSource.getDescription();
		}
		scriptFilename = scriptFilename.trim();

		cg = new ClassGen(classname, "java.lang.Object", scriptFilename,
				ACC_PUBLIC | ACC_SUPER, new String[] { VariableManagerClass.getName() });
		factory = new InstructionFactory(cg);
		cp = cg.getConstantPool();

		il_main = new MyInstructionList();
		mg_main = new MethodGen(ACC_PUBLIC | ACC_FINAL,
				Type.INT,
				new Type[] { getObjectType(AwkSettings.class) },
				new String[] { "settings" },
				"ScriptMain",
				classname,
				il_main, cp);

		// ScriptMain is the main method!
		il = il_main;
		mg = mg_main;
		local_vars = lv_main;

		il_reb = new MyInstructionList();
		mg_reb = new MethodGen(ACC_PUBLIC,
				Type.VOID,
				new Type[] {}, // no args
				new String[] {},
				"runEndBlocks",
				classname,
				il_reb, cp);
		lv_reb = new HashMap<String, Integer>();

		// assign some local variables to runEndBlocks
		LocalVariableGen dregister_reb = mg_reb.addLocalVariable("dregister", getObjectType(Double.TYPE), null, null);
		LocalVariableGen sb_reb = mg_reb.addLocalVariable("sb", new ObjectType("java.lang.StringBuffer"), null, null);
		lv_reb.put("dregister", dregister_reb.getIndex());
		lv_reb.put("sb", sb_reb.getIndex());
		InstructionHandle ih =
				il_reb.append(factory.createNew("java.lang.StringBuffer"));
		il_reb.append(InstructionConstants.DUP);
		il_reb.append(factory.createInvoke("java.lang.StringBuffer", "<init>", Type.VOID, buildArgs(new Class[] {}), INVOKESPECIAL));
		il_reb.append(InstructionFactory.createStore(new ObjectType("java.lang.StringBuffer"), sb_reb.getIndex()));
		dregister_reb.setStart(ih);
		sb_reb.setStart(ih);


		// assign some local variables to MAIN

		// for il_main
		JVMTools_allocateLocalVariable(Double.TYPE, "dregister");

		// for CONCAT
		JVMTools_allocateLocalVariable(StringBuffer.class, "sb");
		JVMTools_new("java.lang.StringBuffer");
		JVMTools_storeToLocalVariable(StringBuffer.class, "sb");


		/// STATIC CONSTRUCTOR STUFF

		InstructionList static_il = new InstructionList();
		MethodGen static_mg = new MethodGen(ACC_STATIC, Type.VOID, Type.NO_ARGS, new String[] {}, "<clinit>", classname, static_il, cp);

		JVMTools_allocateStaticField(String.class, "EXTENSION_DESCRIPTION", ACC_PUBLIC);
		static_il.append(new PUSH(cp, settings.toExtensionDescription()));
		static_il.append(factory.createFieldAccess(classname, "EXTENSION_DESCRIPTION", getObjectType(String.class), Constants.PUTSTATIC));

		JVMTools_allocateStaticField(Integer.class, "ZERO");
		static_il.append(new PUSH(cp, 0));
		static_il.append(factory.createInvoke(Integer.class.getName(), "valueOf", getObjectType(Integer.class), buildArgs(new Class[] {Integer.TYPE}), INVOKESTATIC));
		static_il.append(factory.createFieldAccess(classname, "ZERO", getObjectType(Integer.class), Constants.PUTSTATIC));

		JVMTools_allocateStaticField(Integer.class, "ONE");
		static_il.append(new PUSH(cp, 1));
		static_il.append(factory.createInvoke(Integer.class.getName(), "valueOf", getObjectType(Integer.class), buildArgs(new Class[] {Integer.TYPE}), INVOKESTATIC));
		static_il.append(factory.createFieldAccess(classname, "ONE", getObjectType(Integer.class), Constants.PUTSTATIC));

		JVMTools_allocateStaticField(Integer.class, "MINUS_ONE");
		static_il.append(new PUSH(cp, -1));
		static_il.append(factory.createInvoke(Integer.class.getName(), "valueOf", getObjectType(Integer.class), buildArgs(new Class[] {Integer.TYPE}), INVOKESTATIC));
		static_il.append(factory.createFieldAccess(classname, "MINUS_ONE", getObjectType(Integer.class), Constants.PUTSTATIC));

		static_il.append(InstructionFactory.createReturn(Type.VOID));

		static_mg.setMaxStack();
		static_mg.setMaxLocals();
		cg.addMethod(static_mg.getMethod());
		static_il.dispose();


		JVMTools_allocateField(JRT_Class, "input_runtime");
		il.append(InstructionConstants.ALOAD_0);
		JVMTools_new(JRT_Class.getName(), VariableManagerClass);
		JVMTools_storeField(JRT_Class, "input_runtime");

		JVMTools_allocateField(Map.class, "regexps");
		JVMTools_new("java.util.HashMap");
		JVMTools_storeField(Map.class, "regexps");

		JVMTools_allocateField(Map.class, "pattern_pairs");
		JVMTools_new("java.util.HashMap");
		JVMTools_storeField(Map.class, "pattern_pairs");

		// for EXIT
		JVMTools_allocateField(Integer.TYPE, "exit_code");
		il.append(new PUSH(cp, 0));
		JVMTools_storeField(Integer.TYPE, "exit_code");

		// for SRAND/RAND
		JVMTools_allocateField(Integer.TYPE, "oldseed");
		il.append(new PUSH(cp, 0));
		JVMTools_storeField(Integer.TYPE, "oldseed");

		JVMTools_allocateField(Random.class, "random_number_generator");
		il.append(InstructionConstants.ACONST_NULL);
		JVMTools_storeField(Random.class, "random_number_generator");
	}

	/**
	 * Entry point to the compiler.
	 * The compiler traverses the AwkTuples produced by
	 * the intermediate step, translating each tuple
	 * into JVM code. Some post-processing occurs
	 * to resolve branching (jumps to Addresses).
	 *
	 * @param tuples The tuples which are traversed
	 * 	to produce the compiled result.
	 *
	 * @see org.jawk.intermediate.Address
	 */
	@Override
	public final void compile(AwkTuples tuples) {
		precompile();
		getOffsets(tuples);

		PositionForCompilation position = (PositionForCompilation) tuples.top();

		// to keep track of line numbers that have changed
		int previous_lineno = -2;

		while (!position.isEOF()) {
			il.mark();

			int opcode = position.opcode();

			translateToJVM(position, opcode, tuples);

			InstructionHandle ih = il.markedHandle();
			if (ih != null) {
				instruction_handles.put(position.index(), ih);
				// only add line numbers when they have changed ...
				int lineno = position.lineNumber();
				if (previous_lineno != lineno) {
					mg.addLineNumber(ih, lineno);
					previous_lineno = lineno;
				}
			}

			position.next();
		}

		assert mg == mg_reb;
		JVMTools_returnVoid();

		resolveBranchHandleTargets();

		postcompile(tuples);
	}

	private void addExitCode(InstructionList il, MethodGen mg) {
		// strategy:
		//
		// try {
		// 	//code - already contained in InstructionList//
		// } catch (EndException ee) {}
		// try {
		// 	runEndBlocks();
		// } catch (EndException ee) {}

		// try1
		InstructionHandle ih1_start = il.getStart();
		InstructionHandle ih1_end = il.getEnd();
		BranchHandle bh1 = il.append(new GOTO(null));
		// catch1
		// (do nothing)
		InstructionHandle ih1_catch =
				il.append(InstructionConstants.POP);

		// try2
		// run end blocks here!
		InstructionHandle ih_reb =
				il.append(InstructionConstants.ALOAD_0);
		il.append(factory.createInvoke(classname, "runEndBlocks", Type.VOID, Type.NO_ARGS, Constants.INVOKEVIRTUAL));
		InstructionHandle ih2_end =
				il.append(InstructionConstants.ALOAD_0);
		il.append(factory.createFieldAccess(classname, "exit_code", Type.INT, Constants.GETFIELD));
		il.append(InstructionFactory.createReturn(Type.INT));
		bh1.setTarget(ih_reb);

		// catch2
		// (again, do nothing)
		InstructionHandle ih2_catch =
				il.append(InstructionConstants.POP);
		il.append(InstructionConstants.ALOAD_0);
		il.append(factory.createFieldAccess(classname, "exit_code", Type.INT, Constants.GETFIELD));
		il.append(InstructionFactory.createReturn(Type.INT));

		mg.addExceptionHandler(ih1_start, ih1_end, ih1_catch, new ObjectType(EndExceptionClass.getName()));
		mg.addExceptionHandler(ih_reb, ih2_end, ih2_catch, new ObjectType(EndExceptionClass.getName()));
	}

	private void postcompile(AwkTuples tuples) {
		mg_main.setMaxStack();
		mg_main.setMaxLocals();
		cg.addMethod(mg_main.getMethod());
		il_main.dispose();

		mg_reb.setMaxStack();
		mg_reb.setMaxLocals();
		cg.addMethod(mg_reb.getMethod());
		il_reb.dispose();

		FileOutputStream fos = null;
		try {
			cg.addEmptyConstructor(ACC_PUBLIC);
			addMainMethod();
			createMethods_VariableManager(tuples);
			createPartialParamCalls(tuples);
			String destDir = settings.getDestinationDirectory();
			String dirname = extractDirname(classname, ".");
			String clsname = extractClassname(classname);
			if (dirname != null) {
				clsname = dirname + File.separator + clsname;
			}
			if (new File(destDir).exists()) {
				clsname = destDir + File.separator + clsname;
			} else {
				throw new IOException("Output directory for the AWK compiled script \"" + destDir + "\" does not exist.");
			}
			String path = extractDirname(clsname, File.separator);
			if (path != null) {
				final File classFileDir = new File(path);
				if (!classFileDir.exists()) {
					if (classFileDir.mkdirs()) {
						LOG.info("Created output directory for the AWK compiled script \"{}\"", path);
					} else {
						throw new IOException("Failed to create output directory for the AWK compiled script \"" + path + "\".");
					}
				}
			}

			fos = new FileOutputStream(clsname + ".class");
			cg.getJavaClass().dump(fos);
			LOG.trace("wrote: {}.class", clsname);
		} catch (IOException ioe) {
			LOG.error("IO Problem", ioe);
			System.exit(1);
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException ex) {
					LOG.warn("Failed to close the script class file output stream", ex);
				}
			}
		}
	}

	private void addMainMethod() {
		InstructionList tmpIl = new InstructionList();

		MethodGen tmpMg = new MethodGen(ACC_PUBLIC | ACC_STATIC,
				Type.VOID,
				new Type[] {new ArrayType(getObjectType(String.class), 1)},
				new String[] {"args"},
				"main",
				classname,
				tmpIl, cp);
		tmpIl.append(factory.createNew(classname));
		tmpIl.append(InstructionConstants.DUP);
		tmpIl.append(InstructionConstants.DUP);

		// ..., mainclass, mainclass, mainclass
		tmpIl.append(factory.createInvoke(classname, "<init>",
				Type.VOID,
				new Type[] {},
				INVOKESPECIAL));
		// ..., mainclass, mainclass

		tmpIl.append(InstructionConstants.DUP);
		LocalVariableGen mainclass_arg = tmpMg.addLocalVariable("mainclass_", new ObjectType(classname), null, null);
		InstructionHandle ih =
				tmpIl.append(InstructionFactory.createStore(new ObjectType(classname), mainclass_arg.getIndex()));
		mainclass_arg.setStart(ih);


		tmpIl.append(factory.createNew(AwkParameters.class.getName()));
		// ..., mainclass, mainclass, AwkParameters
		tmpIl.append(InstructionConstants.DUP_X1);
		// ..., mainclass, AwkParameters, mainclass, AwkParameters
		tmpIl.append(InstructionConstants.SWAP);
		// ..., mainclass, AwkParameters, AwkParameters, mainclass
		tmpIl.append(factory.createInvoke(Object.class.getName(), "getClass", getObjectType(Class.class), new Type[] {}, INVOKEVIRTUAL));
		// ..., mainclass, AwkParameters, AwkParameters, mainclass.class

		tmpIl.append(factory.createFieldAccess(classname, "EXTENSION_DESCRIPTION", getObjectType(String.class), Constants.GETSTATIC));
		// ..., mainclass, AwkParameters, AwkParameters, mainclass.class, args, desc
		tmpIl.append(factory.createInvoke(AwkParameters.class.getName(), "<init>",
				Type.VOID,
				new Type[] {
					getObjectType(Class.class),
					getObjectType(String.class) },
				INVOKESPECIAL));
		// ..., mainclass, AwkParameters
		tmpIl.append(InstructionFactory.createLoad(Type.OBJECT, 0));
		// ..., mainclass, AwkParameters, args
		tmpIl.append(factory.createInvoke(AwkParameters.class.getName(), "parseCommandLineArguments",
				getObjectType(AwkSettings.class),
				new Type[] { new ArrayType(getObjectType(String.class), 1) },
				INVOKEVIRTUAL));
		// ..., mainclass, AwkSettings

		tmpIl.append(factory.createInvoke(classname, "ScriptMain", Type.INT, new Type[] { getObjectType(AwkSettings.class) }, INVOKEVIRTUAL));
		tmpIl.append(factory.createInvoke(System.class.getName(), "exit", Type.VOID, new Type[] {Type.INT}, INVOKESTATIC));
		// ??? the return (below) is required, even though we're exit()ing (above) ???
		// ??? (missing return results in a VerifyError) ???
		tmpIl.append(InstructionFactory.createReturn(Type.VOID));

		tmpMg.setMaxStack();
		tmpMg.setMaxLocals();
		cg.addMethod(tmpMg.getMethod());
		tmpIl.dispose();
	}

	private void createMethods_VariableManager(AwkTuples tuples) {
		createGetMethod("getARGC", argc_field);
		createGetMethod("getCONVFMT", convfmt_field);
		createGetMethod("getFS", fs_field);

		createGetMethod(ACC_PRIVATE, "getNR", nr_field);
		createGetMethod(ACC_PRIVATE, "getFNR", fnr_field);
		createMethod_getARGV();
		createGetMethod("getRS", rs_field);
		createGetMethod("getOFS", ofs_field);
		createGetMethod("getSUBSEP", subsep_field);
		createSetMethod("setFILENAME", filename_field, String.class);
		createSetMethod("setNF", nf_field, Integer.class);

		createIncMethod("incNR", "getNR", nr_field);
		createIncMethod("incFNR", "getFNR", fnr_field);

		createResetMethod("resetFNR", fnr_field);

		createAssignVariableMethod(tuples);
	}

	private void createAssignVariableMethod(AwkTuples tuples) {

		InstructionList tmpIl = new InstructionList();
		MethodGen method = new MethodGen(
				ACC_PUBLIC | ACC_FINAL,
				getObjectType(Void.TYPE),
				buildArgs(new Class[] {String.class, Object.class}),
				new String[] {"name", "value"},
				"assignVariable",
				classname,
				tmpIl, cp);

		// basically:
		// if (name.equals("field1")) field1 = value;
		// else if (name.equals("field2")) field2 = value;
		// else if (name.equals("field3")) field3 = value;
		// ...
		// else if (name.equals("fieldN")) fieldN = value;
		// else if (name.equals("funcName1")) throw exception;
		// else if (name.equals("funcName2")) throw exception;
		// ...
		// else if (name.equals("funcNameX")) throw exception;
		// // otherwise, fall through the method

		Map<String, Integer> global_var_offset_map = tuples.getGlobalVariableOffsetMap();
		Map<String, Boolean> global_var_aarray_map = tuples.getGlobalVariableAarrayMap();
		Set<String> function_name_set = tuples.getFunctionNameSet();

		assert function_name_set != null;

		Set<String> all_symbols = new HashSet<String>(global_var_offset_map.keySet());
		all_symbols.addAll(function_name_set);

		// used to just loop over the global_var_offset_map,
		// but need to loop over vars AND function names
		// to report errors when trying to use function names
		// as variable names
		for (String varname : all_symbols) {

			// ...

			tmpIl.append(InstructionConstants.ALOAD_1);
			// ..., name
			tmpIl.append(new PUSH(cp, varname));
			// ..., name, fieldname
			tmpIl.append(factory.createInvoke(String.class.getName(), "equals", Type.BOOLEAN, buildArgs(new Class[] {Object.class}), Constants.INVOKEVIRTUAL));
			// ..., 0-or-1
			BranchHandle bh = tmpIl.append(new IFEQ(null));

			// do the assignment!

			boolean is_function = function_name_set.contains(varname);

			if (is_function) {
				// THROW AN EXCEPTION
				// ...
				JVMTools_throwNewException(tmpIl, IllegalArgumentException.class, "Cannot assign a scalar to a function name (" + varname + ").");
			} else {
				int offset = global_var_offset_map.get(varname);
				boolean is_aarray = global_var_aarray_map.get(varname);

				if (is_aarray) {
					// THROW AN EXCEPTION
					// ...
					JVMTools_throwNewException(tmpIl, IllegalArgumentException.class, "Cannot assign a scalar to a non-scalar variable (" + varname + ").");
				} else {
					// ...
					tmpIl.append(InstructionConstants.ALOAD_0);
					// ..., this
					tmpIl.append(InstructionConstants.ALOAD_2);
					// ..., this, value
					tmpIl.append(factory.createFieldAccess(classname, "global_" + offset, getObjectType(Object.class), Constants.PUTFIELD));
					// ...
					tmpIl.append(InstructionFactory.createReturn(Type.VOID));
				}
			}

			// otherwise, fall to the next global field to check

			InstructionHandle ih = tmpIl.append(InstructionConstants.NOP);
			bh.setTarget(ih);
		}

		// or, fall through the method by doing nothing
		// This occurs when a variable assignment happens
		// to a var that does not exist within the script.

		tmpIl.append(InstructionFactory.createReturn(Type.VOID));

		method.setMaxStack();
		method.setMaxLocals();
		cg.addMethod(method.getMethod());
		tmpIl.dispose();
	}

	private void createGetMethod(String method_name, String field_name) {
		createGetMethod(ACC_PUBLIC, method_name, field_name);
	}

	private void createGetMethod(int method_access, String method_name, String field_name) {
		InstructionList tmpIl = new InstructionList();
		MethodGen method = new MethodGen(method_access | ACC_FINAL, getObjectType(Object.class), new Type[] {}, new String[] {}, method_name, classname, tmpIl, cp);

		tmpIl.append(InstructionConstants.ALOAD_0);
		tmpIl.append(factory.createFieldAccess(classname, field_name, getObjectType(Object.class), Constants.GETFIELD));
		tmpIl.append(InstructionConstants.DUP);
		BranchHandle bh =
				tmpIl.append(new IFNONNULL(null));

		// NULL!
		tmpIl.append(InstructionConstants.POP);	// pop the null
		tmpIl.append(new PUSH(cp, ""));
		InstructionHandle ih =
				tmpIl.append(InstructionFactory.createReturn(getObjectType(Object.class)));
		bh.setTarget(ih);

		method.setMaxStack();
		method.setMaxLocals();
		cg.addMethod(method.getMethod());
		tmpIl.dispose();
	}

	private void createIncMethod(String method_name, String field_method, String field_name) {
		InstructionList tmpIl = new InstructionList();
		// no-arg method
		MethodGen method = new MethodGen(ACC_PUBLIC | ACC_FINAL, Type.VOID, buildArgs(new Class[] {}), new String[] {}, method_name, classname, tmpIl, cp);

		// implement: setXX((int) toDouble(inc(getXX())))

		tmpIl.append(InstructionConstants.ALOAD_0);
		tmpIl.append(InstructionConstants.ALOAD_0);
		// get field
		tmpIl.append(factory.createInvoke(classname, field_method, getObjectType(Object.class), buildArgs(new Class[] {}), INVOKEVIRTUAL));
		// inc
		tmpIl.append(factory.createInvoke(JRT_Class.getName(), "inc", getObjectType(Object.class), buildArgs(new Class[] {Object.class}), INVOKESTATIC));
		// toDouble
		tmpIl.append(factory.createInvoke(JRT_Class.getName(), "toDouble", getObjectType(Double.TYPE), buildArgs(new Class[] {Object.class}), INVOKESTATIC));
		// (int)
		tmpIl.append(InstructionConstants.D2I);
		// { convert int to Integer }
		tmpIl.append(factory.createInvoke("java.lang.Integer", "valueOf", getObjectType(Integer.class), buildArgs(new Class[] {Integer.TYPE}), Constants.INVOKESTATIC));
		// set field
		tmpIl.append(factory.createFieldAccess(classname, field_name, getObjectType(Object.class), Constants.PUTFIELD));

		tmpIl.append(InstructionFactory.createReturn(Type.VOID));

		method.setMaxStack();
		method.setMaxLocals();
		cg.addMethod(method.getMethod());
		tmpIl.dispose();
	}

	private void createResetMethod(String method_name, String field_name) {
		InstructionList tmpIl = new InstructionList();
		// no-arg method
		MethodGen method = new MethodGen(ACC_PUBLIC | ACC_FINAL, Type.VOID, buildArgs(new Class[] {}), new String[] {}, method_name, classname, tmpIl, cp);

		// implement: setXX(ZERO)

		tmpIl.append(InstructionConstants.ALOAD_0);
		tmpIl.append(factory.createFieldAccess(classname, "ZERO", getObjectType(Integer.class), Constants.GETSTATIC));
		tmpIl.append(factory.createFieldAccess(classname, field_name, getObjectType(Object.class), Constants.PUTFIELD));

		tmpIl.append(InstructionFactory.createReturn(Type.VOID));

		method.setMaxStack();
		method.setMaxLocals();
		cg.addMethod(method.getMethod());
		tmpIl.dispose();
	}

	private void createSetMethod(String method_name, String field_name, Class field_type) {
		InstructionList tmpIl = new InstructionList();
		MethodGen method = new MethodGen(ACC_PUBLIC | ACC_FINAL, Type.VOID, buildArgs(new Class[] {field_type}), new String[] {"arg"}, method_name, classname, tmpIl, cp);

		tmpIl.append(InstructionConstants.ALOAD_0);
		tmpIl.append(InstructionConstants.ALOAD_1);
		tmpIl.append(factory.createFieldAccess(classname, field_name, getObjectType(Object.class), Constants.PUTFIELD));
		tmpIl.append(InstructionFactory.createReturn(Type.VOID));

		method.setMaxStack();
		method.setMaxLocals();
		cg.addMethod(method.getMethod());
		tmpIl.dispose();
	}

	private void createMethod_getARGV() {
		InstructionList tmpIl = new InstructionList();
		MethodGen method = new MethodGen(ACC_PUBLIC | ACC_FINAL, getObjectType(Object.class), new Type[] {}, new String[] {}, "getARGV", classname, tmpIl, cp);

		tmpIl.append(InstructionConstants.ALOAD_0);
		tmpIl.append(factory.createFieldAccess(classname, argv_field, getObjectType(Object.class), Constants.GETFIELD));

		tmpIl.append(InstructionFactory.createReturn(getObjectType(Object.class)));

		method.setMaxStack();
		method.setMaxLocals();
		cg.addMethod(method.getMethod());
		tmpIl.dispose();
	}

	private void getOffsets(AwkTuples tuples) {
		PositionForCompilation position = (PositionForCompilation) tuples.top();
		while (!position.isEOF()) {
			int opcode = position.opcode();
			switch (opcode) {
				case AwkTuples._NF_OFFSET_: {
					nf_field = "global_" + position.intArg(0);
					break;
				}
				case AwkTuples._NR_OFFSET_: {
					nr_field = "global_" + position.intArg(0);
					il.append(factory.createFieldAccess(classname, "ZERO", getObjectType(Integer.class), Constants.GETSTATIC));
					JVMTools_storeField(Object.class, nr_field);
					break;
				}
				case AwkTuples._FNR_OFFSET_: {
					fnr_field = "global_" + position.intArg(0);
					il.append(factory.createFieldAccess(classname, "ZERO", getObjectType(Integer.class), Constants.GETSTATIC));
					JVMTools_storeField(Object.class, fnr_field);
					break;
				}
				case AwkTuples._FS_OFFSET_: {
					fs_field = "global_" + position.intArg(0);
					JVMTools_pushString(" ");
					JVMTools_storeField(Object.class, fs_field);
					break;
				}
				case AwkTuples._RS_OFFSET_: {
					rs_field = "global_" + (rs_offset = position.intArg(0));
					il.append(factory.createFieldAccess(JRT_Class.getName(), "DEFAULT_RS_REGEX", getObjectType(String.class), Constants.GETSTATIC));
					JVMTools_storeField(Object.class, rs_field);
					break;
				}
				case AwkTuples._OFS_OFFSET_: {
					ofs_field = "global_" + position.intArg(0);
					JVMTools_pushString(" ");
					JVMTools_storeField(Object.class, ofs_field);
					break;
				}
				case AwkTuples._RSTART_OFFSET_: {
					rstart_field = "global_" + position.intArg(0);
					break;
				}
				case AwkTuples._RLENGTH_OFFSET_: {
					rlength_field = "global_" + position.intArg(0);
					break;
				}
				case AwkTuples._FILENAME_OFFSET_: {
					filename_field = "global_" + position.intArg(0);
					break;
				}
				case AwkTuples._SUBSEP_OFFSET_: {
					subsep_field = "global_" + (subsep_offset = position.intArg(0));
					JVMTools_pushString(new String(new byte[] {28}));
					JVMTools_storeField(Object.class, subsep_field);
					break;
				}
				case AwkTuples._CONVFMT_OFFSET_: {
					convfmt_offset = position.intArg(0);
					convfmt_field = "global_" + convfmt_offset;
					JVMTools_pushString("%.6g");
					JVMTools_storeField(Object.class, convfmt_field);
					break;
				}
				case AwkTuples._OFMT_OFFSET_: {
					ofmt_field = "global_" + (ofmt_offset = position.intArg(0));
					JVMTools_pushString("%.6g");
					JVMTools_storeField(Object.class, ofmt_field);
					break;
				}
				case AwkTuples._ENVIRON_OFFSET_: {
					environ_field = "global_" + (environ_offset = position.intArg(0));

					// ...
					JVMTools_getVariable(environ_offset, true, true);	// true = global, true = assoc_array
					// ... ENVIRON
					JVMTools_cast(AssocArrayClass);
					// ... (AssocArray) ENVIRON
					JVMTools_invokeStatic(Void.TYPE, JRT_Class, "assignEnvironmentVariables", AssocArrayClass);
					// ...
					break;
				}
				case AwkTuples._ARGC_OFFSET_: {
					argc_field = "global_" + (argc_offset = position.intArg(0));
					break;
				}
				case AwkTuples._ARGV_OFFSET_: {
					argv_field = "global_" + (argv_offset = position.intArg(0));
					// access settings.getNameValueOrFileNames()
					// and cycle from 1 to ARGC, populating ARGV
					// with the contents of this list as:
					// for (...)
					//	ARGV[i] = settings.getNameValueOrFileNames().get(i)
					//
					// (NVFL = nameValueOrFileNames)

					// ...
					JVMTools_getVariable(argv_offset, true, true);	// true = is global, true = it is an array
					JVMTools_cast(AssocArrayClass);
					// ..., Argv
					il.append(InstructionConstants.ALOAD_1);	// 1st parameter is (AwkSettings) "settings"
					JVMTools_invokeVirtual(List.class, AwkSettings.class, "getNameValueOrFileNames");
					// ..., Argv, NVFL	 (name_value_filename_list)
					JVMTools_DUP_X1();
					// ..., NVFL, Argv, NVFL
					JVMTools_DUP();
					JVMTools_invokeInterface(Integer.TYPE, List.class, "size");

					// ..., NVFL, Argv, NVFL, argc

					// *** set argc here ***
					JVMTools_DUP();
					// Add one to account for ARGV[0] = "java Awk"
					il.append(InstructionConstants.ICONST_1);
					JVMTools_IADD();
					JVMTools_invokeStatic(Integer.class, Integer.class, "valueOf", Integer.TYPE);
					JVMTools_setVariable(argc_offset, true);
					// *** done ***

					// ..., NVFL, Argv, NVFL, argc

// loop
					InstructionHandle ih2 =
							JVMTools_DUP();
//JVMTools_DEBUG("Within loop!");
//JVMTools_DEBUG(argc_offset, true);	// true = is global
					// ..., NVFL, Argv, NVFL, argc, argc
					BranchHandle bh = JVMTools_IFEQ();

					// argc != 0!
					// ..., NVFL, Argv, NVFL, argc
					JVMTools_DUP_X2();
					JVMTools_DUP_X1();
					// ..., NVFL, argc, Argv, argc, NVFL, argc
					il.append(new PUSH(cp, 1));
					il.append(InstructionConstants.ISUB);
					JVMTools_invokeInterface(Object.class, List.class, "get", Integer.TYPE);
					// ..., NVFL, argc, Argv, argc, item
					JVMTools_invokeVirtual(Object.class, AssocArrayClass, "put", Integer.TYPE, Object.class);
					JVMTools_POP();
					// ..., NVFL, argc
					JVMTools_SWAP();
					// ..., argc, NVFL
					JVMTools_DUP_X1();
					// ..., NVFL, argc, NVFL
					JVMTools_getVariable(argv_offset, true, true);	// true = is global, true = it is an array
					JVMTools_cast(AssocArrayClass);
					// ..., NVFL, argc, NVFL, Argv
					JVMTools_DUP_X2();
					// ..., NVFL, Argv, argc, NVFL, Argv
					JVMTools_POP();
					// ..., NVFL, Argv, argc, NVFL
					JVMTools_SWAP();
					// ..., NVFL, Argv, NVFL, argc
					il.append(new PUSH(cp, 1));
					il.append(InstructionConstants.ISUB);
					BranchHandle bh2 =
							JVMTools_GOTO();
					bh2.setTarget(ih2);

					// ..., NVFL, Argv, NVFL, argc
					InstructionHandle ih =
							JVMTools_POP();
					JVMTools_POP();
					JVMTools_POP();
					JVMTools_POP();
					bh.setTarget(ih);

					// set ARGV[0] = "java "+classname

					JVMTools_getVariable(argv_offset, true, true);
					JVMTools_cast(AssocArrayClass);
					il.append(new PUSH(cp, 0));
					il.append(new PUSH(cp, "java " + classname));
					JVMTools_invokeVirtual(Object.class, AssocArrayClass, "put", Integer.TYPE, Object.class);
					JVMTools_POP();

					break;
				}
			}
			position.next();
		}
	}

	/**
	 * Construct functions that accept a subset of the formal
	 * parameters and pass in null (blank) values for
	 * parameters that are missing.
	 * <p>
	 * The intermediate code (tuples) is traversed one time
	 * to discover all partial parameter calls that are
	 * made.
	 * </p>
	 */
	private void createPartialParamCalls(AwkTuples tuples) {

		Map<String, Set<Integer>> visited_funcs = new HashMap<String, Set<Integer>>();

		PositionForCompilation position = (PositionForCompilation) tuples.top();
		while (!position.isEOF()) {
			int opcode = position.opcode();
			if (opcode == AwkTuples._CALL_FUNCTION_) {
				String func_name = position.arg(1).toString();
				int num_formal_params = position.intArg(2);
				int num_actual_params = position.intArg(3);
				assert num_formal_params >= num_actual_params;

				if (num_formal_params > num_actual_params) {
					Set<Integer> visited_arg_count = visited_funcs.get(func_name);
					if (visited_arg_count == null) {
						visited_funcs.put(func_name, visited_arg_count = new HashSet<Integer>());
					}
					if (!visited_arg_count.contains(num_actual_params)) {
						visited_arg_count.add(num_actual_params);
						addPartialParamCall(func_name, num_formal_params, num_actual_params);
					}
				}
			}
			position.next();
		}
	}

	private static String[] toStringArray(List<String> list) {
		String[] retval = new String[list.size()];
		for (int i = 0; i < retval.length; i++) {
			retval[i] = list.get(i);
		}
		return retval;
	}

	private static Class[] toClassArray(List<Class> list) {
		Class[] retval = new Class[list.size()];
		for (int i = 0; i < retval.length; i++) {
			retval[i] = list.get(i);
		}
		return retval;
	}

	private void addPartialParamCall(String func_name, int num_formal_params, int num_actual_params) {

		// condition the argument parameters

		List<Class> arg_classes = new ArrayList<Class>();
		List<String> arg_names = new ArrayList<String>();
		Map<String, Integer> tmpLocalVars = new HashMap<String, Integer>();

		for (int i = num_actual_params - 1; i >= 0; --i) {
			arg_classes.add(Object.class);
			arg_names.add("locals_" + i);
			tmpLocalVars.put("locals_" + i, tmpLocalVars.size() + 1);
		}

		InstructionList tmpIl = new InstructionList();
		MethodGen method = new MethodGen(ACC_PUBLIC | ACC_FINAL, getObjectType(Object.class), buildArgs(toClassArray(arg_classes)), toStringArray(arg_names), "FUNC_" + func_name, classname, tmpIl, cp);

		tmpIl.append(InstructionConstants.ALOAD_0);

		arg_classes.clear();
		for (int i = num_formal_params - 1; i >= 0; --i) {
			arg_classes.add(Object.class);
			if (i >= num_actual_params) {
				tmpIl.append(InstructionConstants.ACONST_NULL);
			} else {
				tmpIl.append(InstructionFactory.createLoad(getObjectType(Object.class), tmpLocalVars.get("locals_" + i)));
			}
		}
		tmpIl.append(factory.createInvoke(classname, "FUNC_" + func_name, getObjectType(Object.class), buildArgs(toClassArray(arg_classes)), Constants.INVOKEVIRTUAL));

		tmpIl.append(InstructionFactory.createReturn(getObjectType(Object.class)));

		method.setMaxStack();
		method.setMaxLocals();
		cg.addMethod(method.getMethod());
		tmpIl.dispose();
	}


	private org.jawk.intermediate.Address exit_address;

	private Map<org.jawk.intermediate.Address, List<BranchHandle>> branch_handles = new HashMap<org.jawk.intermediate.Address, List<BranchHandle>>();
	private Map<Integer, InstructionHandle> instruction_handles = new HashMap<Integer, InstructionHandle>();

	private String nf_field = null;
	private String nr_field = null;
	private String fnr_field = null;
	private String fs_field = null;
	private String rs_field = null;
	private String ofs_field = null;
	private String rstart_field = null;
	private String rlength_field = null;
	private String filename_field = null;
	private String subsep_field = null;
	private String convfmt_field = null;
	private String ofmt_field = null;
	private String environ_field = null;
	private String argc_field = null;
	private String argv_field = null;

	private int convfmt_offset = -1;
	private int environ_offset = -1;
	private int subsep_offset = -1;
	private int ofmt_offset = -1;
	private int argv_offset = -1;
	private int argc_offset = -1;
	private int rs_offset = -1;

	private int ps_arg_idx = 0;
	private int fmt_arg_idx = 0;
	private int arr_idx_arg_idx = 0;

	// tuples provided only in the event of dumping global variables to stdout
	// via the _DUMP extension
	private void translateToJVM(PositionForCompilation position, int opcode, AwkTuples tuples) {
		switch (opcode) {
			case AwkTuples._SET_EXIT_ADDRESS_: {
				exit_address = position.addressArg();
				break;
			}
			case AwkTuples._GOTO_: {
				JVMTools_GOTO(position.addressArg());
				break;
			}
			case AwkTuples._SET_NUM_GLOBALS_: {
				assert mg_temp == null && il_temp == null || mg_temp != null && il_temp != null;

				if (mg_temp != null) {
					resolveBranchHandleTargets();

					mg.setMaxStack();
					mg.setMaxLocals();
					cg.addMethod(mg.getMethod());
					il.dispose();

					mg = mg_temp;
					il = il_temp;
					branch_handles = bhs_temp;
					instruction_handles = ihs_temp;
					local_vars = lvs_temp;
				} // else , do nothing

				int num_globals = position.intArg(0);
				for (int i = 0; i < num_globals; i++) {
					JVMTools_allocateField(Object.class, "global_" + i);
				}

				// call "settings.getVariables()"

				JVMTools_getField(JRT_Class, "input_runtime");
				il.append(InstructionConstants.ALOAD_1);	// 1st parameter is (AwkSettings) "settings"
				JVMTools_invokeVirtual(Map.class, AwkSettings.class, "getVariables");
				// ..., JRT, Map

				// cycle through the map, assigning variables that exist

				JVMTools_invokeVirtual(Void.TYPE, JRT_Class, "assignInitialVariables", Map.class);

				// (done)

				//JVMTools_NOP();
				break;
			}
			case AwkTuples._PUSH_: {
				Object arg = position.arg(0);
				if        (arg instanceof Integer) {
					JVMTools_pushInteger(((Integer) arg).intValue());
				} else if (arg instanceof Double) {
					JVMTools_pushDouble(((Double) arg).doubleValue());
				} else if (arg instanceof String) {
					JVMTools_pushString(arg.toString());
				} else {
					throw new Error("Invalid position arg: " + arg + " (" + arg.getClass().getName() + ")");
				}
				break;
			}
			case AwkTuples._IFFALSE_: {
				JVMTools_getField(JRT_Class, "input_runtime");
				JVMTools_SWAP();
				JVMTools_invokeVirtual(Boolean.TYPE, JRT_Class, "toBoolean", Object.class);
				JVMTools_IFEQ(position.addressArg());
				break;
			}
			case AwkTuples._IFTRUE_: {
				JVMTools_getField(JRT_Class, "input_runtime");
				JVMTools_SWAP();
				JVMTools_invokeVirtual(Boolean.TYPE, JRT_Class, "toBoolean", Object.class);
				JVMTools_IFNE(position.addressArg());
				break;
			}
			case AwkTuples._NOT_: {
				JVMTools_getField(JRT_Class, "input_runtime");
				JVMTools_SWAP();
				JVMTools_invokeVirtual(Boolean.TYPE, JRT_Class, "toBoolean", Object.class);
				BranchHandle bh = JVMTools_IFNE();
				il.append(factory.createFieldAccess(classname, "ONE", getObjectType(Integer.class), Constants.GETSTATIC));
				BranchHandle bh2 = JVMTools_GOTO();
				InstructionHandle ih =
						il.append(factory.createFieldAccess(classname, "ZERO", getObjectType(Integer.class), Constants.GETSTATIC));
				bh.setTarget(ih);
				InstructionHandle ih2 = JVMTools_NOP();
				bh2.setTarget(ih2);
				break;
			}
			case AwkTuples._NOP_: {
				JVMTools_NOP();
				break;
			}
			case AwkTuples._SET_WITHIN_END_BLOCKS_: {
				assert il == il_main;
				// put a RETURN at the end of ScriptMain
				addExitCode(il_main, mg_main);
				il = il_reb;
				mg = mg_reb;
				local_vars = lv_reb;
				// just to ensure something in runEndBlocks
				// because if not, setMaxLocals will fail
				JVMTools_NOP();
				break;
			}
			case AwkTuples._PRINT_: {
				MethodGen lmg = mg;
				// ..., {args}

				int num_args = position.intArg(0);
				if (num_args == 0) {
					// WITHOUT arguments
					// (use $0)

					il.append(factory.createFieldAccess(classname, "ZERO", getObjectType(Integer.class), Constants.GETSTATIC));
					getInputField();

					num_args = 1;
					// ..., $0
				}

				assert num_args >= 1;

				for (int i = 0; i < num_args; i++) {
					// stack contains objects
					//JVMTools_DEBUG_TOS();	// TOS = top-of-stack
					// ..., {args}, arg
					JVMTools_toAwkStringForOutput();
					JVMTools_printString();
					// ..., {args}

					if (i < num_args - 1) {
						JVMTools_getField(Object.class, ofs_field);
						JVMTools_invokeVirtual(String.class, Object.class, "toString");

						// if OFS is "", use " "

						JVMTools_DUP();
						BranchHandle bh = JVMTools_ifStringNotEquals("");

						JVMTools_POP();
						JVMTools_pushString(" ");

						InstructionHandle ih = JVMTools_NOP();
						bh.setTarget(ih);

						// ..., {args}, OFS
						JVMTools_printString();
						// ..., {args}
					}
				}

				// ...
				JVMTools_println();
				// ...
				break;
			}
			case AwkTuples._PRINT_TO_FILE_:
			case AwkTuples._PRINT_TO_PIPE_: {
				// ..., argN, ..., arg2, arg1, output-filename or cmd-string
				int num_args = position.intArg(0);
				if (num_args == 0) {
					// WITHOUT arguments
					// (use $0)

					il.append(factory.createFieldAccess(classname, "ZERO", getObjectType(Integer.class), Constants.GETSTATIC));
					getInputField();
					JVMTools_SWAP();
					num_args = 1;
					// ..., $0, output-filename or cmd-string
				}

				assert num_args >= 1;

				// ..., argN, ..., arg2, arg1, output-filename or cmd-string

				// convert output-filename or cmd-string to printstream
				JVMTools_toAwkString();
				JVMTools_getField(JRT_Class, "input_runtime");
				JVMTools_SWAP();
				switch (opcode) {
					case AwkTuples._PRINT_TO_FILE_: {
						boolean append = position.boolArg(1);	// true = append to file
						il.append(new PUSH(cp, append));
						// [...], JRT, output-filename, append
						JVMTools_invokeVirtual(PrintStream.class, JRT_Class, "jrtGetPrintStream", String.class, Boolean.TYPE);
						break;
					}
					case AwkTuples._PRINT_TO_PIPE_: {
						JVMTools_invokeVirtual(PrintStream.class, JRT_Class, "jrtSpawnForOutput", String.class);
						break;
					}
					default:
						throw new Error("Invalid opcode for print to file or pipe: " + AwkTuples.toOpcodeString(opcode));
				}

				// ..., argN, ..., arg2, arg1, ps

				for (int i = 0; i < num_args; i++) {
					// stack contains objects
					JVMTools_SWAP();
					JVMTools_toAwkStringForOutput();
					JVMTools_SWAP();
					JVMTools_printStringWithPS();
					if (i < num_args - 1) {
						JVMTools_getField(Object.class, ofs_field);
						JVMTools_invokeVirtual(String.class, Object.class, "toString");

						// if OFS is "", use " "

						JVMTools_DUP();
						BranchHandle bh = JVMTools_ifStringNotEquals("");

						JVMTools_POP();
						JVMTools_pushString(" ");

						InstructionHandle ih =
								JVMTools_SWAP();
						bh.setTarget(ih);
						JVMTools_printStringWithPS();
					}
				}
				JVMTools_printlnWithPS();
				JVMTools_POP();
				break;
			}
			case AwkTuples._PRINTF_:
			case AwkTuples._SPRINTF_: {
				MethodGen lmg = mg;
				int num_args = position.intArg(0);
				// ..., {args}
				JVMTools_toAwkString();
				LocalVariableGen fmt_arg = lmg.addLocalVariable("fmt_arg_" + (++fmt_arg_idx), getObjectType(String.class), null, null);
				InstructionHandle ih =
						il.append(InstructionFactory.createStore(getObjectType(String.class), fmt_arg.getIndex()));
				fmt_arg.setStart(ih);
				il.append(new PUSH(cp, num_args - 1));
				il.append(factory.createNewArray(getObjectType(Object.class), (short) 1));
				// ..., {args}, array
				for (int i = 0; i < num_args - 1; i++) {
					// ..., {args}, array
					JVMTools_DUP_X1();
					// ..., {args}, array, arg, array
					JVMTools_SWAP();
					// ..., {args}, array, array, arg
					il.append(new PUSH(cp, i));
					// ..., {args}, array, array, arg, i
					JVMTools_SWAP();
					// ..., {args}, array, array, i, arg
					il.append(InstructionFactory.createArrayStore(getObjectType(Object.class)));
					// ..., {args}, array
				}

				// ..., array

				il.append(InstructionFactory.createLoad(getObjectType(String.class), fmt_arg.getIndex()));
				switch (opcode) {
					case AwkTuples._PRINTF_:
						JVMTools_invokeStatic(Void.TYPE, JRT_Class,
								settings.isCatchIllegalFormatExceptions() ? "printfFunction" : "printfFunctionNoCatch",
								Object[].class, String.class);
						break;
					case AwkTuples._SPRINTF_:
						JVMTools_invokeStatic(String.class, JRT_Class,
								settings.isCatchIllegalFormatExceptions() ? "sprintfFunction" : "sprintfFunctionNoCatch",
								Object[].class, String.class);
						break;
					default:
						throw new Error("Invalid opcode for [s]printf: " + AwkTuples.toOpcodeString(opcode));
				}
				break;
			}
			case AwkTuples._PRINTF_TO_FILE_:
			case AwkTuples._PRINTF_TO_PIPE_: {
				MethodGen lmg = mg;
				int num_args = position.intArg(0);

				// ..., {args-with-fmt}, output-filename or cmd-string
				// convert output-filename or cmd-string to printstream
				JVMTools_toAwkString();
				JVMTools_getField(JRT_Class, "input_runtime");
				JVMTools_SWAP();
				switch (opcode) {
					case AwkTuples._PRINTF_TO_FILE_: {
						boolean append = position.boolArg(1);
						il.append(new PUSH(cp, append));
						JVMTools_invokeVirtual(PrintStream.class, JRT_Class, "jrtGetPrintStream", String.class, Boolean.TYPE);
						break;
					}
					case AwkTuples._PRINTF_TO_PIPE_: {
						JVMTools_invokeVirtual(PrintStream.class, JRT_Class, "jrtSpawnForOutput", String.class);
						break;
					}
					default:
						throw new Error("Invalid opcode for printf to file or pipe: " + AwkTuples.toOpcodeString(opcode));
				}
				// ..., {args-with-fmt}, ps

				// deal with the format string
				JVMTools_SWAP();
				JVMTools_toAwkString();
				// ..., {args}, ps, format_string
				LocalVariableGen fmt_arg = lmg.addLocalVariable("fmt_arg_" + (++fmt_arg_idx), getObjectType(String.class), null, null);
				InstructionHandle ih =
						il.append(InstructionFactory.createStore(getObjectType(String.class), fmt_arg.getIndex()));
				fmt_arg.setStart(ih);

				// ..., {args}, ps
				JVMTools_SWAP();
				// ..., {args-1}, ps, arg
				il.append(new PUSH(cp, num_args - 1));
				il.append(factory.createNewArray(getObjectType(Object.class), (short) 1));
				// ..., {args-1}, ps, arg, array
				for (int i = 0; i < num_args - 1; i++) {
					// ..., {args-rest}, ps, arg, array
					JVMTools_DUP_X1();
					// ..., {args}, ps, array, arg, array
					JVMTools_SWAP();
					// ..., {args}, ps, array, array, arg
					il.append(new PUSH(cp, i));
					// ..., {args}, ps, array, array, arg, i
					JVMTools_SWAP();
					// ..., {args}, ps, array, array, i, arg
					il.append(InstructionFactory.createArrayStore(getObjectType(Object.class)));
					// ..., {args}, ps, array
					if (i != num_args - 2) {
						// ..., {args}, ps, array
						JVMTools_DUP_X2();
						JVMTools_POP();
						// ..., {args}, array, arg, ps
						JVMTools_DUP_X2();
						JVMTools_POP();
						// ..., {args}, ps, array, arg
						JVMTools_SWAP();
						// ..., {args}, ps, arg, array
					}
				}

				// ..., ps, array

				il.append(InstructionFactory.createLoad(getObjectType(String.class), fmt_arg.getIndex()));
				// ..., ps, array, fmt_arg
				JVMTools_invokeStatic(Void.TYPE, JRT_Class,
						settings.isCatchIllegalFormatExceptions() ? "printfFunction" : "printfFunctionNoCatch",
						PrintStream.class, Object[].class, String.class);
				// ...
				break;
			}
			case AwkTuples._CONCAT_: {
				// ..., arg2, arg1
				JVMTools_getLocalVariable(StringBuffer.class, "sb");
				// ..., arg2, arg1, sb
				JVMTools_DUP();
				// ..., arg2, arg1, sb, sb
				il.append(InstructionConstants.ICONST_0);
				// ..., arg2, arg1, sb, sb, 0
				JVMTools_invokeVirtual(Void.TYPE, StringBuffer.class, "setLength", Integer.TYPE);
				// ..., arg2, arg1, sb
				JVMTools_SWAP();
				// ..., arg2, sb, arg1
				JVMTools_toAwkString();
				JVMTools_invokeVirtual(StringBuffer.class, StringBuffer.class, "append", String.class);
				// ..., arg2, sb
				JVMTools_SWAP();
				// ..., sb, arg2
				JVMTools_toAwkString();
				JVMTools_invokeVirtual(StringBuffer.class, StringBuffer.class, "append", String.class);
				JVMTools_invokeVirtual(String.class, StringBuffer.class, "toString");
				break;
			}
			case AwkTuples._ASSIGN_: {
				int offset = position.intArg(0);
				boolean is_global = position.boolArg(1);
				JVMTools_DUP();
				JVMTools_setVariable(offset, is_global);

				/*if (is_global) {
					JVMTools_storeField(Object.class, "global_" + offset);
				} else {
					throw new Error("local variable assignment not supported");
				}*/
				break;
			}
			case AwkTuples._ASSIGN_ARRAY_: {
				int offset = position.intArg(0);
				boolean is_global = position.boolArg(1);
				// ..., value, array_index
				JVMTools_SWAP();
				// ..., array_index, value
				JVMTools_DUP_X1();
				// ..., value, array_index, value
				JVMTools_getVariable(offset, is_global, true);	// true = it is an array
				JVMTools_cast(AssocArrayClass);
				// ..., value, array_index, value, assocarray
				JVMTools_DUP_X2();
				JVMTools_POP();
				// ..., value, assoc_array, array_index, value
				JVMTools_invokeVirtual(Object.class, AssocArrayClass, "put", Object.class, Object.class);
				JVMTools_POP();
				// value is left on the stack
				break;
			}
			case AwkTuples._APPLY_SUBSEP_: {
				int num_args = position.intArg(0);

				// ..., arg2, arg1
				JVMTools_getLocalVariable(StringBuffer.class, "sb");
				// ..., arg1, arg2, ..., argN, sb
				// (assume 2 args here)
				JVMTools_DUP();
				// ..., arg1, arg2, sb, sb
				il.append(InstructionConstants.ICONST_0);
				// ..., arg1, arg2, sb, sb, 0
				JVMTools_invokeVirtual(Void.TYPE, StringBuffer.class, "setLength", Integer.TYPE);
				// ..., arg1, arg2, sb
				for (int i = 0; i < num_args; i++) {
					if (i > 0) {
						il.append(InstructionConstants.ICONST_0);
						JVMTools_getVariable(subsep_offset, true, false);	// true = is_global, false = is NOT array
						JVMTools_invokeVirtual(String.class, Object.class, "toString");
						JVMTools_invokeVirtual(StringBuffer.class, StringBuffer.class, "insert", Integer.TYPE, String.class);
					}
					JVMTools_SWAP();
					// ..., arg1, sb, arg2
					JVMTools_toAwkString();
					il.append(InstructionConstants.ICONST_0);
					JVMTools_SWAP();
					JVMTools_invokeVirtual(StringBuffer.class, StringBuffer.class, "insert", Integer.TYPE, String.class);
				}
				/*
				// ..., arg1, sb
				JVMTools_SWAP();
				// ..., sb, arg1
				JVMTools_toAwkString();
				JVMTools_invokeVirtual(StringBuffer.class, StringBuffer.class, "append", String.class);
				 */
				JVMTools_invokeVirtual(String.class, StringBuffer.class, "toString");
				break;
			}
			case AwkTuples._KEYLIST_: {
				JVMTools_cast(AssocArrayClass);
				JVMTools_invokeVirtual(Set.class, AssocArrayClass, "keySet");
				JVMTools_new(KeyListImplClass.getName(), Set.class);
				break;
			}
			case AwkTuples._DUP_: {
				JVMTools_DUP();
				break;
			}
			case AwkTuples._CHECK_CLASS_: {
				JVMTools_cast(position.classArg());
				break;
			}
			case AwkTuples._IS_EMPTY_KEYLIST_: {
				JVMTools_invokeInterface(Integer.TYPE, KeyList.class, "size");
				JVMTools_IFEQ(position.addressArg());
				break;
			}
			case AwkTuples._GET_FIRST_AND_REMOVE_FROM_KEYLIST_: {
				JVMTools_invokeInterface(Object.class, KeyList.class, "getFirstAndRemove");
				break;
			}
			case AwkTuples._POP_: {
				JVMTools_POP();
				break;
			}
			case AwkTuples._SWAP_: {
				JVMTools_SWAP();
				break;
			}
			case AwkTuples._DEREFERENCE_: {
				int offset = position.intArg(0);
				boolean is_array = position.boolArg(1);
				boolean is_global = position.boolArg(2);
				JVMTools_getVariable(offset, is_global, is_array);
				break;
			}
			case AwkTuples._DEREF_ARRAY_: {
				JVMTools_DUP();
				JVMTools_instanceOf(AssocArrayClass);
				BranchHandle bh = JVMTools_IFNE();

				// not an AssocArray
				JVMTools_throwNewException(AwkRuntimeExceptionClass, "Attempting to index to a non-associative array.");

				// an AssocArray
				InstructionHandle ih =
						JVMTools_cast(AssocArrayClass);
				JVMTools_SWAP();
				JVMTools_invokeVirtual(Object.class, AssocArrayClass, "get", Object.class);

				bh.setTarget(ih);

				break;
			}
			case AwkTuples._CAST_INT_:
			case AwkTuples._INTFUNC_: {
				JVMTools_toDouble();
				JVMTools_D2I();
				JVMTools_invokeStatic(Integer.class, Integer.class, "valueOf", Integer.TYPE);
				break;
			}
			case AwkTuples._CAST_DOUBLE_: {
				JVMTools_toDouble();
				JVMTools_invokeStatic(Double.class, Double.class, "valueOf", Double.TYPE);
				break;
			}
			case AwkTuples._CAST_STRING_: {
				JVMTools_invokeVirtual(String.class, Object.class, "toString");
				break;
			}
			case AwkTuples._NEGATE_: {
				JVMTools_toDouble();
				JVMTools_DNEG();
				JVMTools_fromDoubleToNumber();
				break;
			}
			case AwkTuples._ADD_: {
				JVMTools_toDouble(2);	// 2 = num reference arguments
				JVMTools_DADD();
				JVMTools_fromDoubleToNumber();
				break;
			}
			case AwkTuples._SUBTRACT_: {
				JVMTools_toDouble(2);	// 2 = num reference arguments
				JVMTools_DSUB();
				JVMTools_fromDoubleToNumber();
				break;
			}
			case AwkTuples._MULTIPLY_: {
				JVMTools_toDouble(2);	// 2 = num reference arguments
				JVMTools_DMUL();
				JVMTools_fromDoubleToNumber();
				break;
			}
			case AwkTuples._DIVIDE_: {
				JVMTools_toDouble(2);	// 2 = num reference arguments
				JVMTools_DDIV();
				JVMTools_fromDoubleToNumber();
				break;
			}
			case AwkTuples._MOD_: {
				JVMTools_toDouble(2);	// 2 = num reference arguments
				JVMTools_DREM();	// double remainder
				JVMTools_fromDoubleToNumber();
				break;
			}
			case AwkTuples._POW_: {
				JVMTools_toDouble(2);	// 2 = num reference arguments
				// execute Math.pow
				JVMTools_invokeStatic(Double.TYPE, Math.class, "pow", Double.TYPE, Double.TYPE);
				JVMTools_fromDoubleToNumber();
				break;
			}
			case AwkTuples._PLUS_EQ_:
			case AwkTuples._MINUS_EQ_:
			case AwkTuples._MULT_EQ_:
			case AwkTuples._DIV_EQ_:
			case AwkTuples._MOD_EQ_:
			case AwkTuples._POW_EQ_: {
				int offset = position.intArg(0);
				boolean is_global = position.boolArg(1);
				/*
				if (is_global)
					JVMTools_getField(Object.class, "global_"+offset);
				else
					throw new Error("local variable access for *_EQ opcodes not supported");
				 */
				JVMTools_getVariable(offset, is_global, false);	// false = not an array
				/*
				JVMTools_toDouble();
				JVMTools_storeRegister();
				JVMTools_toDouble();
				JVMTools_loadRegister();
				// already swapped appropriately
				 */
				JVMTools_toDouble(2);

				switch(opcode) {
					case AwkTuples._PLUS_EQ_: JVMTools_DADD(); break;
					case AwkTuples._MINUS_EQ_: JVMTools_DSUB(); break;
					case AwkTuples._MULT_EQ_: JVMTools_DMUL(); break;
					case AwkTuples._DIV_EQ_: JVMTools_DDIV(); break;
					case AwkTuples._MOD_EQ_: JVMTools_DREM(); break;
					case AwkTuples._POW_EQ_: JVMTools_invokeStatic(Double.TYPE, Math.class, "pow", Double.TYPE, Double.TYPE); break;
					default: throw new Error("Unknown opcode: "+AwkTuples.toOpcodeString(opcode));
				}

				JVMTools_fromDoubleToNumber();
				// keep the result on the stack (a+=b evaluates to the result)
				JVMTools_DUP();
				/*
				if (is_global)
					JVMTools_storeField(Object.class, "global_"+offset);
				else
					throw new Error("local variable access for *_EQ opcodes not supported");
				 */
				JVMTools_setVariable(offset, is_global);
				break;
			}
			case AwkTuples._PLUS_EQ_ARRAY_:
			case AwkTuples._MINUS_EQ_ARRAY_:
			case AwkTuples._MULT_EQ_ARRAY_:
			case AwkTuples._DIV_EQ_ARRAY_:
			case AwkTuples._MOD_EQ_ARRAY_:
			case AwkTuples._POW_EQ_ARRAY_: {
				int offset = position.intArg(0);
				boolean is_global = position.boolArg(1);
				// ..., value, array-idx

				JVMTools_DUP_X1();
				// ..., array-idx, value, array-idx
				JVMTools_getVariable(offset, is_global, true);	// true = an array
				JVMTools_cast(AssocArrayClass);
				// ..., array-idx, value, array-idx, AssocArray
				JVMTools_DUP_X2();
				JVMTools_SWAP();
				// ..., array-idx, AssocArray, value, AssocArray, array-idx
				JVMTools_invokeVirtual(Object.class, AssocArrayClass, "get", Object.class);
				// ..., array-idx, AssocArray, value, orig-value
				JVMTools_toDouble(2);
				// ..., array-idx, AssocArray, orig-value, value
				switch (opcode) {
					case AwkTuples._PLUS_EQ_ARRAY_: JVMTools_DADD(); break;
					case AwkTuples._MINUS_EQ_ARRAY_: JVMTools_DSUB(); break;
					case AwkTuples._MULT_EQ_ARRAY_: JVMTools_DMUL(); break;
					case AwkTuples._DIV_EQ_ARRAY_: JVMTools_DDIV(); break;
					case AwkTuples._MOD_EQ_ARRAY_: JVMTools_DREM(); break;
					case AwkTuples._POW_EQ_ARRAY_: JVMTools_invokeStatic(Double.TYPE, Math.class, "pow", Double.TYPE, Double.TYPE); break;
					default: throw new Error("Unknown opcode: "+AwkTuples.toOpcodeString(opcode));
				}
				JVMTools_fromDoubleToNumber();
				// ..., array-idx, AssocArray, new-value
				JVMTools_SWAP();
				JVMTools_DUP_X2();
				JVMTools_POP();
				// ..., AssocArray, array-idx, new-value
				JVMTools_DUP_X2();
				// ..., new-value, AssocArray, array-idx, new-value
				JVMTools_invokeVirtual(Object.class, AssocArrayClass, "put", Object.class, Object.class);
				// ..., new-value, old-value
				JVMTools_POP();
				// ..., new-value

				break;
			}
			case AwkTuples._CMP_LT_: {
				il.append(new PUSH(cp, 1));	// reverse because items are swapped on the stack
				JVMTools_invokeStatic(Boolean.TYPE, JRT_Class, "compare2", Object.class, Object.class, Integer.TYPE);
				JVMTools_invokeStatic(Integer.class, Integer.class, "valueOf", Integer.TYPE);
				break;
			}
			case AwkTuples._CMP_GT_: {
				il.append(new PUSH(cp, -1));	// reverse because items are swapped on the stack
				JVMTools_invokeStatic(Boolean.TYPE, JRT_Class, "compare2", Object.class, Object.class, Integer.TYPE);
				JVMTools_invokeStatic(Integer.class, Integer.class, "valueOf", Integer.TYPE);
				break;
			}
			case AwkTuples._CMP_EQ_: {
				il.append(new PUSH(cp, 0));	// reverse because items are swapped on the stack
				JVMTools_invokeStatic(Boolean.TYPE, JRT_Class, "compare2", Object.class, Object.class, Integer.TYPE);
				JVMTools_invokeStatic(Integer.class, Integer.class, "valueOf", Integer.TYPE);
				break;
			}
			case AwkTuples._INC_:
			case AwkTuples._DEC_: {
				int offset = position.intArg(0);
				boolean is_global = position.boolArg(1);

				JVMTools_getVariable(offset, is_global, false);	// false = not an array
				switch (opcode) {
					case AwkTuples._INC_:
						JVMTools_invokeStatic(Object.class, JRT_Class, "inc", Object.class);
						break;
					case AwkTuples._DEC_:
						JVMTools_invokeStatic(Object.class, JRT_Class, "dec", Object.class);
						break;
					default:
						throw new Error("Invalid opcode for inc/dec: " + AwkTuples.toOpcodeString(opcode));
				}
				JVMTools_setVariable(offset, is_global);

				break;
			}
			case AwkTuples._INC_ARRAY_REF_:
			case AwkTuples._DEC_ARRAY_REF_: {
				int offset = position.intArg(0);
				boolean is_global = position.boolArg(1);
				// ..., idx
				JVMTools_DUP();
				// ..., idx, idx
				JVMTools_getVariable(offset, is_global, true);	// true = is array
				JVMTools_cast(AssocArrayClass);
				// ..., idx, idx, aa
				JVMTools_DUP_X2();
				// ..., aa, idx, idx, aa
				JVMTools_SWAP();
				// ..., aa, idx, aa, idx
				JVMTools_invokeVirtual(Object.class, AssocArrayClass, "get", Object.class);
				// ..., aa, idx, item
				switch (opcode) {
					case AwkTuples._INC_ARRAY_REF_:
						JVMTools_invokeStatic(Object.class, JRT_Class, "inc", Object.class);
						break;
					case AwkTuples._DEC_ARRAY_REF_:
						JVMTools_invokeStatic(Object.class, JRT_Class, "dec", Object.class);
						break;
					default:
						throw new Error("Invalid opcode for inc/dec array ref: " + AwkTuples.toOpcodeString(opcode));
				}
				// ..., item+1, aa, idx, item+/-1
				JVMTools_invokeVirtual(Object.class, AssocArrayClass, "put", Object.class, Object.class);
				JVMTools_POP();
				// ..., item+/-1
				// ITEM IS LEFT ON THE STACK
				break;
			}
			case AwkTuples._EXIT_WITH_CODE_: {
				JVMTools_toDouble();
				JVMTools_D2I();
				JVMTools_storeField(Integer.TYPE, "exit_code");

				// throw EndException here
				JVMTools_throwNewException(EndExceptionClass, "exit() called");

				break;
			}
			case AwkTuples._FUNCTION_: {
				String func_name = position.arg(0).toString();
				int num_params = position.intArg(1);

				assert mg_temp == null && il_temp == null || mg_temp != null && il_temp != null;

				if (mg_temp != null) {
					resolveBranchHandleTargets();

					mg.setMaxStack();
					mg.setMaxLocals();
					cg.addMethod(mg.getMethod());
					il.dispose();
				} else {
					mg_temp = mg;
					il_temp = il;
					bhs_temp = branch_handles;
					ihs_temp = instruction_handles;
					lvs_temp = local_vars;
				}

				// build arg array
				Type[] params = new Type[num_params];
				String[] names = new String[num_params];
				for (int i = 0; i < num_params; i++) {
					params[i] = getObjectType(Object.class);
					names[i] = "locals_" + (num_params - i - 1);
				}

				il = new MyInstructionList();
				mg = new MethodGen(ACC_PUBLIC,
						Type.OBJECT,
						params,
						names,
						"FUNC_" + func_name,
						classname,
						il, cp);
				branch_handles = new HashMap<org.jawk.intermediate.Address, List<BranchHandle>>();
				instruction_handles = new HashMap<Integer, InstructionHandle>();
				local_vars = new HashMap<String, Integer>();

				// so the JVMTools knows about the parameters like it does for local variables
				JVMTools_allocateFunctionParameters(names);

				JVMTools_allocateLocalVariable(Object.class, "_return_value_");
				il.append(InstructionConstants.ACONST_NULL);
				JVMTools_storeToLocalVariable(Object.class, "_return_value_");

				JVMTools_allocateLocalVariable(StringBuffer.class, "sb");
				JVMTools_new("java.lang.StringBuffer");
				JVMTools_storeToLocalVariable(StringBuffer.class, "sb");

				JVMTools_allocateLocalVariable(Double.TYPE, "dregister");
				// bug with BCEL:
				// an access to this variable is crucial... otherwise,
				// setMaxLocals() fails to account for it,
				// causing a corrupt class file error upon invocation
				// of the JVM
				il.append(new PUSH(cp, 0.0));
				JVMTools_storeToLocalVariable(Double.TYPE, "dregister");

				JVMTools_NOP();

				break;
			}
			case AwkTuples._SET_RETURN_RESULT_: {
				// return value already on the stack
				JVMTools_storeToLocalVariable(Object.class, "_return_value_");
				break;
			}
			case AwkTuples._RETURN_FROM_FUNCTION_: {
				JVMTools_getLocalVariable(Object.class, "_return_value_");
				il.append(InstructionFactory.createReturn(Type.OBJECT));
				break;
			}
			case AwkTuples._THIS_: {
				il.append(InstructionConstants.ALOAD_0);
				break;
			}
			case AwkTuples._CALL_FUNCTION_: {
				// do nothing, for now
				String func_name = position.arg(1).toString();
				int num_formal_params = position.intArg(2);
				int num_actual_params = position.intArg(3);
				assert num_formal_params >= num_actual_params;

				// what will occur is the following:
				// - multiple arg verions of the method
				// - each version simply *pre*-pads the arg list with NULLs
				// - every "local" access is a reverse-offset into the arg list

				// by this time, all callable/called variations of the
				// method have been defined
				// therefore, just call the method

				// build arg list

				Class[] arg_array = new Class[num_actual_params];
				for (int i = 0; i < num_actual_params; i++) {
					arg_array[i] = Object.class;
				}

				il.append(factory.createInvoke(classname, "FUNC_" + func_name, getObjectType(Object.class), buildArgs(arg_array), INVOKEVIRTUAL));

				// check for NULL return value
				JVMTools_DUP();
				BranchHandle bh = JVMTools_IFNONNULL();
				JVMTools_POP();
				JVMTools_pushString("");
				InstructionHandle ih = JVMTools_NOP();
				bh.setTarget(ih);

				break;
			}
			case AwkTuples._REGEXP_: {
				il.append(new PUSH(cp, (String) position.arg(0)));
				JVMTools_toAwkString();
				JVMTools_DUP();
				// ..., regexp_str, regexp_str
				JVMTools_getField(Map.class, "regexps");
				JVMTools_SWAP();
				JVMTools_invokeInterface(Object.class, Map.class, "get", Object.class);
				// ..., regexp_str, Pattern-or-NULL
				JVMTools_DUP();
				// ..., regexp_str, Pattern-or-NULL, Pattern-or-NULL

				BranchHandle bh = JVMTools_IFNONNULL();
				// regexp doesn't exist in the Map
				JVMTools_POP();
				JVMTools_DUP();
				// ..., regexp_str, regexp_str
				JVMTools_invokeStatic(Pattern.class, Pattern.class, "compile", String.class);
				// ..., regexp_str, Pattern
				JVMTools_DUP_X1();
				// ..., Pattern, regexp_str, Pattern
				JVMTools_getField(Map.class, "regexps");
				JVMTools_DUP_X2();
				JVMTools_POP();
				// ..., Pattern, regexps, regexp_str, Pattern
				JVMTools_invokeInterface(Object.class, Map.class, "put", Object.class, Object.class);
				JVMTools_POP();
				// ..., Pattern
				BranchHandle bh2 = JVMTools_GOTO();	// end

				// ..., regexp_str, Pattern-or-NULL
				InstructionHandle ih = JVMTools_SWAP();
				bh.setTarget(ih);
				JVMTools_POP();
				// ..., Pattern-or-NULL

				InstructionHandle ih2 = JVMTools_NOP();
				bh2.setTarget(ih2);
				break;
			}
			case AwkTuples._REGEXP_PAIR_: {
				// ..., regexp2 (string), regexp1 (string)
				JVMTools_getField(Map.class, "pattern_pairs");
				// ..., regexp2, regexp1, pattern_pairs
				il.append(new PUSH(cp, position.index()));
				JVMTools_invokeStatic(Integer.class, Integer.class, "valueOf", Integer.TYPE);
				// ..., regexp2, regexp1, pattern_pairs, pos-idx
				JVMTools_invokeInterface(Object.class, Map.class, "get", Object.class);
				// ..., regexp2, regexp1, pattern_pair
				JVMTools_DUP();
				// ..., regexp2, regexp1, pattern_pair, pattern_pair

				BranchHandle bh = JVMTools_IFNONNULL();

				// pattern_pair IS NULL!

				// ..., regexp2, regexp1, pattern_pair
				JVMTools_POP();
				// ..., regexp2, regexp1
				JVMTools_invokeVirtual(String.class, Object.class, "toString");
				JVMTools_SWAP();
				JVMTools_invokeVirtual(String.class, Object.class, "toString");
				// ..., regexp1, regexp2
				JVMTools_new(PatternPairClass.getName(), String.class, String.class);
				// ..., pattern_pair
				JVMTools_DUP();
				// ..., pattern_pair, pattern_pair
				il.append(new PUSH(cp, position.index()));
				JVMTools_invokeStatic(Integer.class, Integer.class, "valueOf", Integer.TYPE);
				// ..., pattern_pair, pattern_pair, pos-idx
				JVMTools_SWAP();
				// ..., pattern_pair, pos-idx, pattern_pair
				JVMTools_getField(Map.class, "pattern_pairs");
				// ..., pattern_pair, pos-idx, pattern_pair, pattern_pairs
				JVMTools_DUP_X2();
				JVMTools_POP();
				// ..., pattern_pair, pattern_pairs, pos-idx, pattern_pair
				JVMTools_invokeInterface(Object.class, Map.class, "put", Object.class, Object.class);
				JVMTools_POP();
				// ..., pattern_pair
				BranchHandle bh2 = JVMTools_GOTO();

				// pattern_pair is NOT null!

				// ..., regexp2, regexp1, pattern_pair
				InstructionHandle ih = JVMTools_DUP_X2();
				bh.setTarget(ih);
				JVMTools_POP();
				JVMTools_POP();
				JVMTools_POP();
				// ..., pattern_pair

				InstructionHandle ih2 = JVMTools_NOP();
				bh2.setTarget(ih2);

				break;
			}
			case AwkTuples._MATCHES_: {
				// ..., arg2, arg1
				JVMTools_invokeVirtual(String.class, Object.class, "toString");
				JVMTools_SWAP();
				// ..., (String) arg1, arg2
				JVMTools_DUP();
				JVMTools_instanceOf(Pattern.class);
				BranchHandle bh = JVMTools_IFEQ();

				// Pattern
				// ..., (String) arg1, arg2
				JVMTools_cast(Pattern.class);
				BranchHandle bh2 = JVMTools_GOTO();	// goto "push ONE or ZERO object" code


				// NOT Pattern
				// ..., (String) arg1, arg2
				InstructionHandle ih = JVMTools_toAwkString();
				bh.setTarget(ih);
				JVMTools_invokeStatic(Pattern.class, Pattern.class, "compile", String.class);

				InstructionHandle ih2 = JVMTools_SWAP();
				bh2.setTarget(ih2);
				// ..., (Pattern) arg2, (String) arg1
				JVMTools_invokeVirtual(Matcher.class, Pattern.class, "matcher", CharSequence.class);
				JVMTools_invokeVirtual(Boolean.TYPE, Matcher.class, "find");

				BranchHandle bh3 = JVMTools_IFEQ();
				il.append(factory.createFieldAccess(classname, "ONE", getObjectType(Integer.class), Constants.GETSTATIC));
				BranchHandle bh4 = JVMTools_GOTO();
				InstructionHandle ih3 = il.append(factory.createFieldAccess(classname, "ZERO", getObjectType(Integer.class), Constants.GETSTATIC));
				bh3.setTarget(ih3);
				InstructionHandle ih4 = JVMTools_NOP();
				bh4.setTarget(ih4);

				break;
			}
			case AwkTuples._TO_NUMBER_: {
				// call to toBoolean
				JVMTools_getField(JRT_Class, "input_runtime");
				JVMTools_SWAP();
				JVMTools_invokeVirtual(Boolean.TYPE, JRT_Class, "toBoolean", Object.class);
				BranchHandle bh3 = JVMTools_IFEQ();
				il.append(factory.createFieldAccess(classname, "ONE", getObjectType(Integer.class), Constants.GETSTATIC));
				BranchHandle bh4 = JVMTools_GOTO();
				InstructionHandle ih3 = il.append(factory.createFieldAccess(classname, "ZERO", getObjectType(Integer.class), Constants.GETSTATIC));
				bh3.setTarget(ih3);
				InstructionHandle ih4 = JVMTools_NOP();
				bh4.setTarget(ih4);
				break;
			}
			case AwkTuples._SPLIT_: {
				int numargs = position.intArg(0);
				JVMTools_getVariable(convfmt_offset, true, false);	// true = is_global, false = NOT an array
				JVMTools_invokeVirtual(String.class, Object.class, "toString");
				if (numargs == 2) {
					JVMTools_invokeStatic(Integer.TYPE, JRT_Class, "split", Object.class, Object.class, String.class);
				} else if (numargs == 3) {
					JVMTools_invokeStatic(Integer.TYPE, JRT_Class, "split", Object.class, Object.class, Object.class, String.class);
				} else {
					throw new Error(numargs + ": Too many arguments for split.");
				}
				JVMTools_invokeStatic(Integer.class, Integer.class, "valueOf", Integer.TYPE);
				break;
			}
			case AwkTuples._SQRT_: {
				JVMTools_toDouble(1);
				JVMTools_invokeStatic(Double.TYPE, Math.class, "sqrt", Double.TYPE);
				JVMTools_invokeStatic(Double.class, Double.class, "valueOf", Double.TYPE);
				break;
			}
			case AwkTuples._LOG_: {
				JVMTools_toDouble(1);
				JVMTools_invokeStatic(Double.TYPE, Math.class, "log", Double.TYPE);
				JVMTools_invokeStatic(Double.class, Double.class, "valueOf", Double.TYPE);
				break;
			}
			case AwkTuples._SIN_: {
				JVMTools_toDouble(1);
				JVMTools_invokeStatic(Double.TYPE, Math.class, "sin", Double.TYPE);
				JVMTools_invokeStatic(Double.class, Double.class, "valueOf", Double.TYPE);
				break;
			}
			case AwkTuples._COS_: {
				JVMTools_toDouble(1);
				JVMTools_invokeStatic(Double.TYPE, Math.class, "cos", Double.TYPE);
				JVMTools_invokeStatic(Double.class, Double.class, "valueOf", Double.TYPE);
				break;
			}
			case AwkTuples._EXP_: {
				JVMTools_toDouble(1);
				JVMTools_invokeStatic(Double.TYPE, Math.class, "exp", Double.TYPE);
				JVMTools_invokeStatic(Double.class, Double.class, "valueOf", Double.TYPE);
				break;
			}
			case AwkTuples._ATAN2_: {
				// ..., arg2, arg1
				JVMTools_toDouble(2);
				// ..., arg1, arg2	[as doubles]
				JVMTools_invokeStatic(Double.TYPE, Math.class, "atan2", Double.TYPE, Double.TYPE);
				JVMTools_invokeStatic(Double.class, Double.class, "valueOf", Double.TYPE);
				break;
			}
			case AwkTuples._INDEX_: {
				// ..., arg2, arg1
				JVMTools_toAwkString();
				JVMTools_SWAP();
				JVMTools_toAwkString();
				// ..., arg1, arg2
				JVMTools_invokeVirtual(Integer.TYPE, String.class, "indexOf", String.class);
				// ..., int
				il.append(new PUSH(cp, 1));
				JVMTools_IADD();
				// ..., int+1
				JVMTools_invokeStatic(Integer.class, Integer.class, "valueOf", Integer.TYPE);

				break;
			}
			case AwkTuples._SUBSTR_: {
				int numargs = position.intArg(0);
				// ..., [endpos], startpos, string_obj
				JVMTools_toAwkString();
				// ..., [endpos], startpos, string
				if (numargs == 2) {
					JVMTools_invokeStatic(String.class, JRT_Class, "substr", Object.class, String.class);
				} else {
					JVMTools_invokeStatic(String.class, JRT_Class, "substr", Object.class, Object.class, String.class);
				}
				break;
			}
			case AwkTuples._CONSUME_INPUT_: {
				JVMTools_getField(JRT_Class, "input_runtime");
				il.append(new PUSH(cp, false));
				JVMTools_invokeVirtual(Boolean.TYPE, JRT_Class, "jrtConsumeInput", Boolean.TYPE);
				JVMTools_IFEQ(position.addressArg());
				break;
			}
			case AwkTuples._GETLINE_INPUT_: {
				JVMTools_getField(JRT_Class, "input_runtime");
				il.append(new PUSH(cp, true));
				JVMTools_invokeVirtual(Boolean.TYPE, JRT_Class, "jrtConsumeInput", Boolean.TYPE);
				JVMTools_DUP();
				JVMTools_invokeStatic(Integer.class, Integer.class, "valueOf", Integer.TYPE);
				JVMTools_SWAP();
				// ..., Integer(retcode), retcode for getline

				BranchHandle bh = JVMTools_IFLE();
				// 1
				JVMTools_getField(JRT_Class, "input_runtime");
				il.append(factory.createFieldAccess(JRT_Class.getName(), "input_line", getObjectType(String.class), Constants.GETFIELD));
				BranchHandle bh2 = JVMTools_GOTO();

				// 0 or -1
				InstructionHandle ih =
						il.append(new PUSH(cp, ""));
				bh.setTarget(ih);

				InstructionHandle ih2 =
						JVMTools_NOP();
				bh2.setTarget(ih2);

				// ..., retcode for getline, input-string

				break;
			}
			case AwkTuples._GET_INPUT_FIELD_: {
				getInputField();
				break;
			}
			case AwkTuples._USE_AS_FILE_INPUT_:
			case AwkTuples._USE_AS_COMMAND_INPUT_: {
				// ..., filename-as-object-type
				JVMTools_toAwkString();
				// ..., filename
				JVMTools_getField(JRT_Class, "input_runtime");
				// ..., filename, JRT
				JVMTools_DUP_X1();
				// ..., JRT, filename, JRT
				JVMTools_SWAP();
				// ..., JRT, JRT, filename
				switch (opcode) {
					case AwkTuples._USE_AS_FILE_INPUT_:
						JVMTools_invokeVirtual(Integer.class, JRT_Class, "jrtConsumeFileInputForGetline", String.class);
						break;
					case AwkTuples._USE_AS_COMMAND_INPUT_:
						JVMTools_invokeVirtual(Integer.class, JRT_Class, "jrtConsumeCommandInputForGetline", String.class);
						break;
					default:
						throw new Error("Invalid opcode for _USE_AS_*_INPUT_: " + AwkTuples.toOpcodeString(opcode));
				}
				// ..., JRT, retcode
				JVMTools_SWAP();
				// ..., retcode, JRT
				JVMTools_invokeVirtual(String.class, JRT_Class, "jrtGetInputString");
				// ..., retcode, input-string
				// NOTE: 2 items on the stack !!!
				break;
			}
			case AwkTuples._CLOSE_: {
				JVMTools_toAwkString();
				JVMTools_getField(JRT_Class, "input_runtime");
				JVMTools_SWAP();
				JVMTools_invokeVirtual(Integer.class, JRT_Class, "jrtClose", String.class);
				break;
			}
			case AwkTuples._SYSTEM_: {
				JVMTools_toAwkString();
				JVMTools_invokeStatic(Integer.class, JRT_Class, "jrtSystem", String.class);
				break;
			}
			case AwkTuples._ASSIGN_AS_INPUT_: {
				il.append(factory.createInvoke("java.lang.Object", "toString", getObjectType(String.class), buildArgs(new Class[] {}), INVOKEVIRTUAL));
				JVMTools_DUP();
				// ..., text, text
				JVMTools_getField(JRT_Class, "input_runtime");
				// ..., text, text, JRT
				JVMTools_DUP_X1();
				// ..., text, JRT, text, JRT
				JVMTools_SWAP();
				// ..., text, JRT, JRT, text
				il.append(factory.createFieldAccess(JRT_Class.getName(), "input_line", getObjectType(String.class), Constants.PUTFIELD));
				// ..., text, JRT
				JVMTools_invokeVirtual(Void.TYPE, JRT_Class, "jrtParseFields");
				// ..., text (as string, see toString() prior to DUP)
				break;
			}
			case AwkTuples._ASSIGN_AS_INPUT_FIELD_: {
				assignAsInputField();
				break;
			}
			case AwkTuples._MATCH_: {
				// ..., ere, s
				JVMTools_toAwkString();
				JVMTools_SWAP();
				JVMTools_toAwkString();
				// ..., s, ere
				JVMTools_invokeStatic(Pattern.class, Pattern.class, "compile", String.class);
				// ..., s, pattern
				JVMTools_SWAP();
				// ..., pattern, s
				JVMTools_invokeVirtual(Matcher.class, Pattern.class, "matcher", CharSequence.class);
				// ..., matcher
				JVMTools_DUP();
				// ..., matcher, matcher
				JVMTools_invokeVirtual(Boolean.TYPE, Matcher.class, "find");
				// ..., matcher, boolean-result
				BranchHandle bh = JVMTools_IFEQ();

				// A MATCH!

				// ..., matcher

				JVMTools_DUP();
				// ..., matcher, matcher
				JVMTools_invokeVirtual(Integer.TYPE, Matcher.class, "end");
				JVMTools_SWAP();
				JVMTools_invokeVirtual(Integer.TYPE, Matcher.class, "start");
				JVMTools_DUP_X1();
				// ..., start, end, start
				JVMTools_ISUB();
				JVMTools_invokeStatic(Integer.class, Integer.class, "valueOf", Integer.TYPE);
				JVMTools_storeField(Object.class, rlength_field);
				// ..., start
				il.append(new PUSH(cp, 1));
				JVMTools_IADD();
				JVMTools_invokeStatic(Integer.class, Integer.class, "valueOf", Integer.TYPE);
				JVMTools_DUP();
				JVMTools_storeField(Object.class, rstart_field);

				// ..., start()+1

				BranchHandle bh2 = JVMTools_GOTO();

				// !!! NOT A MATCH !!!

				// ..., matcher

				InstructionHandle ih =
						JVMTools_POP();
				bh.setTarget(ih);
				il.append(factory.createFieldAccess(classname, "MINUS_ONE", getObjectType(Integer.class), Constants.GETSTATIC));
				JVMTools_storeField(Object.class, rlength_field);
				il.append(factory.createFieldAccess(classname, "ZERO", getObjectType(Integer.class), Constants.GETSTATIC));
				JVMTools_DUP();
				JVMTools_storeField(Object.class, rstart_field);

				InstructionHandle ih2 =
						JVMTools_NOP();
				bh2.setTarget(ih2);

				break;
			}
			case AwkTuples._TOUPPER_: {
				// ..., obj
				JVMTools_toAwkString();
				// ..., str
				il.append(factory.createInvoke(String.class.getName(), "toUpperCase", getObjectType(String.class), buildArgs(new Class[] {}), INVOKEVIRTUAL));
				break;
			}
			case AwkTuples._TOLOWER_: {
				// ..., obj
				JVMTools_toAwkString();
				// ..., str
				il.append(factory.createInvoke(String.class.getName(), "toLowerCase", getObjectType(String.class), buildArgs(new Class[] {}), INVOKEVIRTUAL));
				break;
			}
			case AwkTuples._SUB_FOR_VARIABLE_: {
				int offset = position.intArg(0);
				boolean is_global = position.boolArg(1);
				boolean is_gsub = position.boolArg(2);
				// ..., orig_value, repl, ere
				JVMTools_getLocalVariable(StringBuffer.class, "sb");
				JVMTools_getField(Object.class, convfmt_field);
				JVMTools_invokeVirtual(String.class, Object.class, "toString");
				// ..., orig_value, repl, ere, sb, convfmt
				if (is_gsub) {
					JVMTools_invokeStatic(Integer.class, JRT_Class, "replaceAll", Object.class, Object.class, Object.class, StringBuffer.class, String.class);
				} else {
					JVMTools_invokeStatic(Integer.class, JRT_Class, "replaceFirst", Object.class, Object.class, Object.class, StringBuffer.class, String.class);
				}
				JVMTools_getLocalVariable(StringBuffer.class, "sb");
				JVMTools_invokeVirtual(String.class, Object.class, "toString");
				// ..., repl_count, new_string
				JVMTools_setVariable(offset, is_global);
				// ..., repl_count

				break;
			}
			case AwkTuples._SUB_FOR_DOLLAR_0_: {
				// ..., repl, ere
				// (target: ..., $field, repl, ere, fieldnum)
				// get $0
				JVMTools_getField(JRT_Class, "input_runtime");
				JVMTools_invokeVirtual(String.class, JRT_Class, "jrtGetInputString");
				JVMTools_DUP_X2();
				JVMTools_POP();
				// ..., $0, repl, ere
				il.append(factory.createFieldAccess(classname, "ZERO", getObjectType(Integer.class), Constants.GETSTATIC));
				// ..., $0, repl, ere, 0

				// NO BREAK
			}
			case AwkTuples._SUB_FOR_DOLLAR_REFERENCE_: {
				boolean is_gsub = position.boolArg(0);
				// (start)
				// ..., $field, repl, ere, fieldnum
				JVMTools_DUP_X2();
				// ..., $field, fieldnum, repl, ere, fieldnum
				getInputField();
				// ..., $field, fieldnum, repl, ere, orig_value
				JVMTools_DUP_X2();
				// ..., $field, fieldnum, orig_value, repl, ere, orig_value
				JVMTools_POP();
				// ..., $field, fieldnum, orig_value, repl, ere
				JVMTools_getLocalVariable(StringBuffer.class, "sb");
				JVMTools_getField(Object.class, convfmt_field);
				JVMTools_invokeVirtual(String.class, Object.class, "toString");
				// ..., $field, fieldnum, orig_value, repl, ere, sb, convfmt
				if (is_gsub) {
					JVMTools_invokeStatic(Integer.class, JRT_Class, "replaceAll", Object.class, Object.class, Object.class, StringBuffer.class, String.class);
				} else {
					JVMTools_invokeStatic(Integer.class, JRT_Class, "replaceFirst", Object.class, Object.class, Object.class, StringBuffer.class, String.class);
				}
				// ..., $field, fieldnum, repl_count
				JVMTools_SWAP();
				// ..., $field, repl_count, fieldnum
				JVMTools_getLocalVariable(StringBuffer.class, "sb");
				JVMTools_invokeVirtual(String.class, Object.class, "toString");
				// ..., $field, repl_count, fieldnum, new_string
				JVMTools_SWAP();
				// ..., $field, repl_count, new_string, fieldnum

				// same as _ASSIGN_AS_INPUT_FIELD_
				assignAsInputField();
				JVMTools_POP();

				// ..., $field, repl_count
				JVMTools_SWAP();
				JVMTools_POP();

				// ..., repl_count

				break;
			}
			case AwkTuples._SUB_FOR_ARRAY_REFERENCE_: {
				MethodGen lmg = mg;
				int arr_offset = position.intArg(0);
				boolean is_global = position.boolArg(1);
				boolean is_gsub = position.boolArg(2);

				// ..., orig-value, repl-string, ere, array-index

				// (target: orig-value, repl-string, ere, sb, convfmt)

				LocalVariableGen array_idx_arg = lmg.addLocalVariable("arr_idx_arg_" + (++arr_idx_arg_idx), getObjectType(Object.class), null, null);
				InstructionHandle ih =
						il.append(InstructionFactory.createStore(getObjectType(Object.class), array_idx_arg.getIndex()));
				array_idx_arg.setStart(ih);
				// ..., orig-value, repl-string, ere
				JVMTools_getLocalVariable(StringBuffer.class, "sb");
				JVMTools_getField(Object.class, convfmt_field);
				JVMTools_invokeVirtual(String.class, Object.class, "toString");
				// ..., orig-value, repl-string, ere, sb, convfmt
				if (is_gsub) {
					JVMTools_invokeStatic(Integer.class, JRT_Class, "replaceAll", Object.class, Object.class, Object.class, StringBuffer.class, String.class);
				} else {
					JVMTools_invokeStatic(Integer.class, JRT_Class, "replaceFirst", Object.class, Object.class, Object.class, StringBuffer.class, String.class);
				}
				// ..., repl_count

				// (target: repl_count, array, array-idx, value)

				JVMTools_getVariable(arr_offset, is_global, true);	// true = is an array
				JVMTools_cast(AssocArrayClass);
				// ..., repl_count, array
				il.append(InstructionFactory.createLoad(getObjectType(Object.class), array_idx_arg.getIndex()));
				// ..., repl_count, array, array-index
				JVMTools_getLocalVariable(StringBuffer.class, "sb");
				JVMTools_invokeVirtual(String.class, Object.class, "toString");
				// ..., repl_count, array, array-index, value
				JVMTools_invokeVirtual(Object.class, AssocArrayClass, "put", Object.class, Object.class);
				JVMTools_POP();
				// ..., repl_count

				break;
			}
			case AwkTuples._DELETE_ARRAY_ELEMENT_: {
				int offset = position.intArg(0);
				boolean is_global = position.boolArg(1);

				JVMTools_getVariable(offset, is_global, true);	// true = is an array
				JVMTools_cast(AssocArrayClass);
				// array_idx, array
				JVMTools_SWAP();
				// array, array_idx
				JVMTools_invokeVirtual(Object.class, AssocArrayClass, "remove", Object.class);
				JVMTools_POP();
				break;
			}
			case AwkTuples._DELETE_ARRAY_: {
				int offset = position.intArg(0);
				boolean is_global = position.boolArg(1);

				il.append(InstructionConstants.ACONST_NULL);
				JVMTools_setVariable(offset, is_global);

				break;
			}
			case AwkTuples._IS_IN_: {
				// ..., AssocArray, key
				JVMTools_SWAP();
				JVMTools_cast(AssocArrayClass);
				JVMTools_SWAP();
				// ..., AssocArray, key
				JVMTools_invokeVirtual(Boolean.TYPE, AssocArrayClass, "isIn", Object.class);
				BranchHandle bh3 = JVMTools_IFEQ();
				il.append(factory.createFieldAccess(classname, "ONE", getObjectType(Integer.class), Constants.GETSTATIC));
				BranchHandle bh4 = JVMTools_GOTO();
				InstructionHandle ih3 = il.append(factory.createFieldAccess(classname, "ZERO", getObjectType(Integer.class), Constants.GETSTATIC));
				bh3.setTarget(ih3);
				InstructionHandle ih4 = JVMTools_NOP();
				bh4.setTarget(ih4);
				break;
			}
			case AwkTuples._INC_DOLLAR_REF_:
			case AwkTuples._DEC_DOLLAR_REF_: {
				// ..., fieldnum

				// JVMTools_toDouble();
				// JVMTools_D2I();

				JVMTools_DUP();
				// ..., fieldnum, fieldnum

				getInputField();

				// ..., fieldnum, value

				JVMTools_toDouble(1);
				// ADD ONE
				il.append(new PUSH(cp, 1.0));
				switch (opcode) {
					case AwkTuples._INC_DOLLAR_REF_:
						JVMTools_DADD();
						break;
					case AwkTuples._DEC_DOLLAR_REF_:
						JVMTools_DSUB();
						break;
					default:
						throw new Error("Invalid opcode for inc/dec dollar ref: " + AwkTuples.toOpcodeString(opcode));
				}

				// ..., fieldnum, value+1
				JVMTools_fromDoubleToNumber();
				JVMTools_SWAP();
				// ..., value+1, fieldnum

				// same as _ASSIGN_AS_INPUT_FIELD_

				assignAsInputField();
				JVMTools_POP();

				break;
			}
			case AwkTuples._PLUS_EQ_INPUT_FIELD_:
			case AwkTuples._MINUS_EQ_INPUT_FIELD_:
			case AwkTuples._MULT_EQ_INPUT_FIELD_:
			case AwkTuples._DIV_EQ_INPUT_FIELD_:
			case AwkTuples._MOD_EQ_INPUT_FIELD_:
			case AwkTuples._POW_EQ_INPUT_FIELD_: {
				// ..., incval, fieldnum
				JVMTools_DUP_X1();
				// ..., fieldnum, incval, fieldnum
				getInputField();
				// ..., fieldnum, incval, value
				JVMTools_toDouble(2);
				// ..., fieldnum, value, incval (as doubles)
				switch (opcode) {
					case AwkTuples._PLUS_EQ_INPUT_FIELD_:
						JVMTools_DADD();
						break;
					case AwkTuples._MINUS_EQ_INPUT_FIELD_:
						JVMTools_DSUB();
						break;
					case AwkTuples._MULT_EQ_INPUT_FIELD_:
						JVMTools_DMUL();
						break;
					case AwkTuples._DIV_EQ_INPUT_FIELD_:
						JVMTools_DDIV();
						break;
					case AwkTuples._MOD_EQ_INPUT_FIELD_:
						JVMTools_DREM();
						break;
					case AwkTuples._POW_EQ_INPUT_FIELD_:
						JVMTools_invokeStatic(Double.TYPE, Math.class, "pow", Double.TYPE, Double.TYPE);
						break;
					default:
						throw new Error("Invalid opcode for inc/dec_eq dollar ref: " + AwkTuples.toOpcodeString(opcode));
				}
				// ..., fieldnum, sum
				JVMTools_fromDoubleToNumber();

				// used to be:
				//
				// JVMTools_SWAP();
				// // ..., sum, fieldnum
				// assignAsInputField();
				// // ..., sum (as string result from assignInputField())
				//
				// But, problem is that the evaluated expression always returns
				// as a string with this method (assignInputField() leaves
				// a String on the stack).
				// To avoid this problem,
				// we'll leave the actual sum on the stack, removing the
				// return value from assignAsInputField().

				// ..., fieldnum, sum (as double)
				JVMTools_DUP_X1();
				// ..., sum, fieldnum, sum
				JVMTools_SWAP();
				// ..., sum, sum, fieldnum
				assignAsInputField();
				// ..., sum, sum (as string)
				JVMTools_POP();
				// ..., sum (as double)
				// leave result on the stack
				break;
			}
			case AwkTuples._SRAND_: {
				int numargs = position.intArg(0);
				if (numargs == 0) {
					JVMTools_getField(Integer.TYPE, "oldseed");
					il.append(InstructionConstants.ALOAD_0);
					JVMTools_invokeStatic(Integer.TYPE, JRT_Class, "timeSeed");
					JVMTools_DUP();
					il.append(InstructionConstants.ALOAD_0);
					JVMTools_SWAP();
					// ..., oldseed, this, seed, this, seed
				} else {
					JVMTools_toDouble();
					JVMTools_D2I();
					JVMTools_getField(Integer.TYPE, "oldseed");
					JVMTools_SWAP();
					// ..., oldseed, seed
					JVMTools_DUP();
					il.append(InstructionConstants.ALOAD_0);
					JVMTools_DUP_X2();
					JVMTools_SWAP();
					// ..., oldseed, this, seed, this, seed
				}
				// ..., oldseed, this, seed, this, seed
				il.append(factory.createFieldAccess(classname, "oldseed", getObjectType(Integer.TYPE), Constants.PUTFIELD));
				// ..., oldseed, this, seed
				JVMTools_invokeStatic(Random.class, JRT_Class, "newRandom", Integer.TYPE);
				// ..., oldseed, this, random_#_generator
				il.append(factory.createFieldAccess(classname, "random_number_generator", getObjectType(Random.class), Constants.PUTFIELD));
				// ..., oldseed
				break;
			}
			case AwkTuples._RAND_: {
				JVMTools_getField(Random.class, "random_number_generator");
				// ..., random_#_generator
				BranchHandle bh = JVMTools_IFNONNULL();

				// random_#_generator is NULL

				JVMTools_invokeStatic(Integer.TYPE, JRT_Class, "timeSeed");
				// ..., seed
				JVMTools_DUP();
				// ..., seed, seed
				JVMTools_invokeStatic(Random.class, JRT_Class, "newRandom", Integer.TYPE);
				// ..., seed, random_#_generator
				il.append(InstructionConstants.ALOAD_0);
				// ..., seed, random_#_generator, this
				JVMTools_DUP_X2();
				JVMTools_SWAP();
				// ..., this, seed, this, random_#_generator
				il.append(factory.createFieldAccess(classname, "random_number_generator", getObjectType(Random.class), Constants.PUTFIELD));
				// ..., this, seed
				il.append(factory.createFieldAccess(classname, "oldseed", getObjectType(Integer.TYPE), Constants.PUTFIELD));
				// ...

				InstructionHandle ih =
						JVMTools_getField(Random.class, "random_number_generator");
				bh.setTarget(ih);
				// ..., random_#_generator
				JVMTools_invokeVirtual(Double.TYPE, Random.class, "nextDouble");
				JVMTools_invokeStatic(Double.class, Double.class, "valueOf", Double.TYPE);
				// ..., random_# (as a Double class)

				break;
			}
			case AwkTuples._APPLY_RS_: {
				JVMTools_getField(JRT_Class, "input_runtime");
				JVMTools_getVariable(rs_offset, true, false);	// true = is global, false = is NOT an array
				JVMTools_invokeVirtual(Void.TYPE, JRT_Class, "applyRS", Object.class);
				break;
			}
			case AwkTuples._LENGTH_: {
				int numargs = position.intArg(0);
				if (numargs == 0) {
					il.append(factory.createFieldAccess(classname, "ZERO", getObjectType(Integer.class), Constants.GETSTATIC));
					getInputField();
				}
				// ..., obj
				JVMTools_invokeVirtual(String.class, Object.class, "toString");
				// ..., string
				JVMTools_invokeVirtual(Integer.TYPE, String.class, "length");
				// ..., length
				JVMTools_invokeStatic(Integer.class, Integer.class, "valueOf", Integer.TYPE);
				// ..., length (as Integer)

				break;
			}

			case AwkTuples._SLEEP_: {
				int numargs = position.intArg(0);
				if (numargs == 0) {
					il.append(new PUSH(cp, 1000L));
				} else {
					JVMTools_toDouble();
					JVMTools_D2L();
					il.append(new PUSH(cp, 1000L));
					JVMTools_LMUL();
				}
				JVMTools_invokeStatic(Void.TYPE, Thread.class, "sleep", Long.TYPE);
				break;
			}

			case AwkTuples._DUMP_: {
				int numargs = position.intArg(0);
				if (numargs == 0) {
					// no args
					// dump all variables

					Map<String, Integer> global_var_offset_map = tuples.getGlobalVariableOffsetMap();

					for (Map.Entry<String, Integer> var : global_var_offset_map.entrySet()) {
						// dump "name = value" onto stdout

						String name = var.getKey();
						int offset = var.getValue();

						// ...
						JVMTools_print(name + " = ");
						JVMTools_getStaticField(System.class.getName(), "out", PrintStream.class);
						JVMTools_getField(Object.class, "global_" + offset);
						// if it's a map, get its "map" field
						// for printing
						JVMTools_DUP();
						JVMTools_instanceOf(AssocArrayClass);
						BranchHandle bh = JVMTools_IFEQ();

						// it is an AssocArray!
						JVMTools_cast(AssocArrayClass);
						il.append(factory.createFieldAccess(AssocArrayClass.getName(), "map", getObjectType(Map.class), Constants.GETFIELD));

						InstructionHandle ih = JVMTools_invokeVirtual(Void.TYPE, PrintStream.class, "println", Object.class);
						bh.setTarget(ih);
					}
				} else {
					// ..., argN, argN-1, ..., arg2, arg1
					for (int i = 0; i < numargs; ++i) {
						// ..., arg
						JVMTools_cast(AssocArrayClass);
						il.append(factory.createFieldAccess(AssocArrayClass.getName(), "map", getObjectType(Map.class), Constants.GETFIELD));
						JVMTools_getStaticField(System.class.getName(), "out", PrintStream.class);
						JVMTools_SWAP();
						JVMTools_invokeVirtual(Void.TYPE, PrintStream.class, "println", Object.class);
					}
				}
				break;
			}

			case AwkTuples._NF_OFFSET_:
			case AwkTuples._NR_OFFSET_:
			case AwkTuples._FNR_OFFSET_:
			case AwkTuples._FS_OFFSET_:
			case AwkTuples._RS_OFFSET_:
			case AwkTuples._OFS_OFFSET_:
			case AwkTuples._RSTART_OFFSET_:
			case AwkTuples._RLENGTH_OFFSET_:
			case AwkTuples._FILENAME_OFFSET_:
			case AwkTuples._SUBSEP_OFFSET_:
			case AwkTuples._CONVFMT_OFFSET_:
			case AwkTuples._OFMT_OFFSET_:
			case AwkTuples._ENVIRON_OFFSET_:
			case AwkTuples._ARGC_OFFSET_:
			case AwkTuples._ARGV_OFFSET_:
				// DO NOTHING! Done already.
				break;
			default:
				throw new Error("Unknown opcode: " + AwkTuples.toOpcodeString(opcode));
		}
	}

	private void resolveBranchHandleTargets() {
		for (org.jawk.intermediate.Address addr : branch_handles.keySet()) {
			List<BranchHandle> list = branch_handles.get(addr);
			for (BranchHandle bh : list) {
				bh.setTarget(instruction_handles.get(addr.index()));
			}
		}
	}

	private void getInputField() {
		// same code as _GET_INPUT_FIELD_
		JVMTools_getField(JRT_Class, "input_runtime");
		JVMTools_SWAP();
		JVMTools_invokeVirtual(Object.class, JRT_Class, "jrtGetInputField", Object.class);
	}

	private void assignAsInputField() {
		// ..., text, fieldnum
		JVMTools_toDouble();
		JVMTools_D2I();
		// ..., text, fieldnum (as integer)

		JVMTools_DUP();
		// ..., text, fieldnum, fieldnum
		BranchHandle bh = JVMTools_IFEQ();

		// $1, $2, ...
		// ..., text, fieldnum
		JVMTools_getField(JRT_Class, "input_runtime");
		JVMTools_DUP_X2();
		JVMTools_POP();
		// ..., JRT, text, fieldnum
		JVMTools_invokeVirtual(String.class, JRT_Class, "jrtSetInputField", Object.class, Integer.TYPE);
		BranchHandle bh2 = JVMTools_GOTO();

		// $0
		// ..., text, fieldnum
		InstructionHandle ih =
				JVMTools_POP();
		bh.setTarget(ih);
		// ..., text

		JVMTools_invokeVirtual(String.class, Object.class, "toString");

		JVMTools_DUP();
		// ..., text, text
		JVMTools_getField(JRT_Class, "input_runtime");
		// ..., text, text, JRT
		JVMTools_DUP_X1();
		// ..., text, JRT, text, JRT
		JVMTools_SWAP();
		// ..., text, JRT, JRT, text
		il.append(factory.createFieldAccess(JRT_Class.getName(), "input_line", getObjectType(String.class), Constants.PUTFIELD));
		// ..., text, JRT
		JVMTools_invokeVirtual(Void.TYPE, JRT_Class, "jrtParseFields");
		// ..., text

		InstructionHandle ih2 =
				JVMTools_NOP();
		bh2.setTarget(ih2);
	}

	////////////////////////////////////////////////////////////////////////////////
	private void JVMTools_allocateStaticField(Class vartype, String varname) {
		JVMTools_allocateStaticField(vartype, varname, ACC_PRIVATE);
	}

	private void JVMTools_allocateStaticField(Class vartype, String varname, int public_or_private) {
		FieldGen fg = new FieldGen(public_or_private | ACC_STATIC | ACC_FINAL,
				getObjectType(vartype),
				varname,
				cp);
		cg.addField(fg.getField());
	}

	private void JVMTools_allocateField(Class vartype, String varname) {
		FieldGen fg = new FieldGen(ACC_PRIVATE,
				getObjectType(vartype),
				varname,
				cp);
		cg.addField(fg.getField());
	}

	private void JVMTools_GOTO(org.jawk.intermediate.Address addr) {
		BranchHandle bh = il.append(new GOTO(null));
		JVMTools_addBranchHandle(addr, bh);
	}

	private BranchHandle JVMTools_GOTO() {
		return il.append(new GOTO(null));
	}

	private void JVMTools_IFEQ(org.jawk.intermediate.Address addr) {
		BranchHandle bh = il.append(new IFEQ(null));
		JVMTools_addBranchHandle(addr, bh);
	}

	private void JVMTools_IFNE(org.jawk.intermediate.Address addr) {
		BranchHandle bh = il.append(new IFNE(null));
		JVMTools_addBranchHandle(addr, bh);
	}

	private void JVMTools_IFLE(org.jawk.intermediate.Address addr) {
		BranchHandle bh = il.append(new IFLE(null));
		JVMTools_addBranchHandle(addr, bh);
	}

	private BranchHandle JVMTools_IFNONNULL() {
		return il.append(new IFNONNULL(null));
	}

	private InstructionHandle JVMTools_NOP() {
		return il.append(InstructionConstants.NOP);
	}

	private InstructionHandle JVMTools_SWAP() {
		return il.append(InstructionConstants.SWAP);
	}

	private InstructionHandle JVMTools_POP() {
		return il.append(InstructionConstants.POP);
	}

	private InstructionHandle JVMTools_POP2() {
		return il.append(InstructionConstants.POP2);
	}

	private InstructionHandle JVMTools_DUP() {
		return il.append(InstructionConstants.DUP);
	}

	private InstructionHandle JVMTools_DUP_X1() {
		return il.append(InstructionConstants.DUP_X1);
	}

	private InstructionHandle JVMTools_DUP_X2() {
		return il.append(InstructionConstants.DUP_X2);
	}

	private void JVMTools_DUP2() {
		il.append(InstructionConstants.DUP2);
	}

	private void JVMTools_DUP2_X1() {
		il.append(InstructionConstants.DUP2_X1);
	}

	private void JVMTools_DUP2_X2() {
		il.append(InstructionConstants.DUP2_X2);
	}

	private InstructionHandle JVMTools_D2I() {
		return il.append(InstructionConstants.D2I);
	}

	private InstructionHandle JVMTools_I2D() {
		return il.append(InstructionConstants.I2D);
	}

	private void JVMTools_D2L() {
		il.append(InstructionConstants.D2L);
	}

	private void JVMTools_IADD() {
		il.append(InstructionConstants.IADD);
	}

	private void JVMTools_ISUB() {
		il.append(InstructionConstants.ISUB);
	}

	private void JVMTools_DADD() {
		il.append(InstructionConstants.DADD);
	}

	private void JVMTools_DSUB() {
		il.append(InstructionConstants.DSUB);
	}

	private void JVMTools_DMUL() {
		il.append(InstructionConstants.DMUL);
	}

	private void JVMTools_DDIV() {
		il.append(InstructionConstants.DDIV);
	}

	private void JVMTools_DREM() {
		il.append(InstructionConstants.DREM);
	}

	private void JVMTools_DNEG() {
		il.append(InstructionConstants.DNEG);
	}

	private void JVMTools_LMUL() {
		il.append(InstructionConstants.LMUL);
	}

	private void JVMTools_returnVoid() {
		il.append(InstructionFactory.createReturn(Type.VOID));
	}

	private void JVMTools_pushInteger(int i) {
		il.append(new PUSH(cp, i));
		JVMTools_invokeStatic(Integer.class, Integer.class, "valueOf", Integer.TYPE);
	}

	private void JVMTools_pushDouble(double d) {
		il.append(new PUSH(cp, d));
		JVMTools_invokeStatic(Double.class, Double.class, "valueOf", Double.TYPE);
	}

	private void JVMTools_pushString(String s) {
		il.append(new PUSH(cp, s));
	}

	private void JVMTools_pushBoolean(boolean b) {
		il.append(new PUSH(cp, b));
	}

	private void JVMTools_invokeInterface(Class return_type, Class orig_class, String method_name) {
		il.append(factory.createInvoke(orig_class.getName(), method_name, getObjectType(return_type), buildArgs(new Class[] {}), INVOKEINTERFACE));
	}

	private void JVMTools_invokeInterface(Class return_type, Class orig_class, String method_name, Class arg_type) {
		il.append(factory.createInvoke(orig_class.getName(), method_name, getObjectType(return_type), buildArgs(new Class[] {arg_type}), INVOKEINTERFACE));
	}

	private void JVMTools_invokeInterface(Class return_type, Class orig_class, String method_name, Class arg_type, Class arg2_type) {
		il.append(factory.createInvoke(orig_class.getName(), method_name, getObjectType(return_type), buildArgs(new Class[] {arg_type, arg2_type}), INVOKEINTERFACE));
	}

	private void JVMTools_invokeStatic(Class return_type, Class orig_class, String method_name) {
		il.append(factory.createInvoke(orig_class.getName(), method_name, getObjectType(return_type), buildArgs(new Class[] {}), INVOKESTATIC));
	}

	private void JVMTools_invokeStatic(Class return_type, Class orig_class, String method_name, Class arg_type) {
		il.append(factory.createInvoke(orig_class.getName(), method_name, getObjectType(return_type), buildArgs(new Class[] {arg_type}), INVOKESTATIC));
	}

	private void JVMTools_invokeStatic(Class return_type, Class orig_class, String method_name, Class arg_type, Class arg2_type) {
		il.append(factory.createInvoke(orig_class.getName(), method_name, getObjectType(return_type), buildArgs(new Class[] {arg_type, arg2_type}), INVOKESTATIC));
	}

	private void JVMTools_invokeStatic(Class return_type, Class orig_class, String method_name, Class arg_type, Class arg2_type, Class arg3_type) {
		il.append(factory.createInvoke(orig_class.getName(), method_name, getObjectType(return_type), buildArgs(new Class[] {arg_type, arg2_type, arg3_type}), INVOKESTATIC));
	}

	private void JVMTools_invokeStatic(Class return_type, Class orig_class, String method_name, Class arg_type, Class arg2_type, Class arg3_type, Class arg4_type) {
		il.append(factory.createInvoke(orig_class.getName(), method_name, getObjectType(return_type), buildArgs(new Class[] {arg_type, arg2_type, arg3_type, arg4_type}), INVOKESTATIC));
	}

	private void JVMTools_invokeStatic(Class return_type, Class orig_class, String method_name, Class arg_type, Class arg2_type, Class arg3_type, Class arg4_type, Class arg5_type) {
		il.append(factory.createInvoke(orig_class.getName(), method_name, getObjectType(return_type), buildArgs(new Class[] {arg_type, arg2_type, arg3_type, arg4_type, arg5_type}), INVOKESTATIC));
	}

	private InstructionHandle JVMTools_invokeVirtual(Class return_type, Class orig_class, String method_name) {
		return il.append(factory.createInvoke(orig_class.getName(), method_name, getObjectType(return_type), buildArgs(new Class[] {}), INVOKEVIRTUAL));
	}

	private InstructionHandle JVMTools_invokeVirtual(Class return_type, Class orig_class, String method_name, Class arg_type) {
		return il.append(factory.createInvoke(orig_class.getName(), method_name, getObjectType(return_type), buildArgs(new Class[] {arg_type}), INVOKEVIRTUAL));
	}

	private InstructionHandle JVMTools_invokeVirtual(Class return_type, Class orig_class, String method_name, Class arg_type, Class arg_type2) {
		return il.append(factory.createInvoke(orig_class.getName(), method_name, getObjectType(return_type), buildArgs(new Class[] {arg_type, arg_type2}), INVOKEVIRTUAL));
	}

	private InstructionHandle JVMTools_invokeVirtual(Class return_type, Class orig_class, String method_name, Class arg_type, Class arg_type2, Class arg_type3) {
		return il.append(factory.createInvoke(orig_class.getName(), method_name, getObjectType(return_type), buildArgs(new Class[] {arg_type, arg_type2, arg_type3}), INVOKEVIRTUAL));
	}

	private void JVMTools_allocateLocalVariable(Class vartype, String varname) {
		assert local_vars.get(varname) == null;
		LocalVariableGen lg = mg.addLocalVariable(varname, getObjectType(vartype), null, null);
		local_vars.put(varname, lg.getIndex());
		lg.setStart(JVMTools_NOP());
	}

	private void JVMTools_allocateFunctionParameters(String[] param_names) {
		for (int i = 0; i < param_names.length; i++) {
			assert local_vars.get(param_names[i]) == null;
			local_vars.put(param_names[i], i + 1);
		}
	}

	private InstructionHandle JVMTools_getLocalVariable(Class vartype, String varname) {
		Integer I = local_vars.get(varname);
		if (I == null) {
			throw new Error(varname + " not found as a local variable");
		}
		return il.append(InstructionFactory.createLoad(getObjectType(vartype), I));
	}

	private void JVMTools_storeToLocalVariable(Class vartype, String varname) {
		Integer I = local_vars.get(varname);
		if (I == null) {
			throw new Error(varname + " not found as a local variable");
		}
		il.append(InstructionFactory.createStore(getObjectType(vartype), I));
	}

	private void JVMTools_addBranchHandle(org.jawk.intermediate.Address addr, BranchHandle bh) {
		List<BranchHandle> list = branch_handles.get(addr);
		if (list == null) {
			branch_handles.put(addr, list = new ArrayList<BranchHandle>());
		}
		list.add(bh);
	}

	private void JVMTools_print(String const_str) {
		JVMTools_getStaticField("java.lang.System", "out", PrintStream.class);
		il.append(new PUSH(cp, const_str));
		JVMTools_invokeVirtual(Void.TYPE, PrintStream.class, "print", String.class);
	}

	private void JVMTools_println() {
		JVMTools_getStaticField("java.lang.System", "out", PrintStream.class);
		JVMTools_invokeVirtual(Void.TYPE, PrintStream.class, "println");
	}

	private void JVMTools_printlnWithPS() {
		// ..., ps
		JVMTools_DUP();
		JVMTools_invokeVirtual(Void.TYPE, PrintStream.class, "println");
		// ..., ps
	}

	private InstructionHandle JVMTools_getStaticField(String classname, String fieldname, Class fieldtype) {
		return il.append(factory.createFieldAccess(classname, fieldname, getObjectType(fieldtype), Constants.GETSTATIC));
	}

	private InstructionHandle JVMTools_getField(Class fieldtype, String fieldname) {
		InstructionHandle ih = il.append(InstructionConstants.ALOAD_0);
		il.append(factory.createFieldAccess(classname, fieldname, getObjectType(fieldtype), Constants.GETFIELD));
		return ih;
	}

	private void JVMTools_storeStaticField(Class fieldtype, String fieldname) {
		il.append(factory.createFieldAccess(classname, fieldname, getObjectType(fieldtype), Constants.PUTSTATIC));
	}

	private void JVMTools_storeField(Class fieldtype, String fieldname) {
		il.append(InstructionConstants.ALOAD_0);
		JVMTools_SWAP();
		il.append(factory.createFieldAccess(classname, fieldname, getObjectType(fieldtype), Constants.PUTFIELD));
	}

	private InstructionHandle JVMTools_printString() {
		InstructionHandle ih = JVMTools_getStaticField("java.lang.System", "out", PrintStream.class);
		JVMTools_SWAP();
		JVMTools_invokeVirtual(Void.TYPE, PrintStream.class, "print", String.class);
		return ih;
	}

	private InstructionHandle JVMTools_printStringWithPS() {
		// ..., string, ps
		InstructionHandle ih = JVMTools_DUP_X1();
		JVMTools_SWAP();
		JVMTools_invokeVirtual(Void.TYPE, PrintStream.class, "print", String.class);
		// ..., ps
		return ih;
	}

	private void JVMTools_new(String newtype) {
		il.append(factory.createNew(newtype));
		JVMTools_DUP();
		il.append(factory.createInvoke(newtype, "<init>", Type.VOID, buildArgs(new Class[] {}), INVOKESPECIAL));
	}

	private void JVMTools_new(String newtype, Class paramtype) {
		JVMTools_new(il, newtype, paramtype);
	}

	private void JVMTools_new(InstructionList il, String newtype, Class paramtype) {
		il.append(factory.createNew(newtype));
		il.append(InstructionConstants.DUP_X1);
		il.append(InstructionConstants.SWAP);
		il.append(factory.createInvoke(newtype, "<init>", Type.VOID, buildArgs(new Class[] {paramtype}), INVOKESPECIAL));
	}

	private void JVMTools_new(String newtype, Class paramtype1, Class paramtype2) {
		// ..., param1, param2
		il.append(factory.createNew(newtype));
		// ..., param1, param2, objref
		JVMTools_DUP_X2();
		JVMTools_DUP_X2();
		JVMTools_POP();
		// ..., objref, objref, param1, param2
		il.append(factory.createInvoke(newtype, "<init>", Type.VOID, buildArgs(new Class[] {paramtype1, paramtype2}), INVOKESPECIAL));
	}

	private void JVMTools_instanceOf(Class checkclass) {
		il.append(factory.createInstanceOf(new ObjectType(checkclass.getName())));
	}

	private InstructionHandle JVMTools_cast(Class to) {
		return il.append(factory.createCheckCast(new ObjectType(to.getName())));
	}

	private void JVMTools_swap2() {
		JVMTools_DUP2_X2();
		JVMTools_POP2();
	}

	private InstructionHandle JVMTools_toAwkString() {
		InstructionHandle ih = JVMTools_getVariable(convfmt_offset, true, false);	// true = is_global, false = NOT an array
		JVMTools_invokeVirtual(String.class, Object.class, "toString");
		JVMTools_invokeStatic(String.class, JRT_Class, "toAwkString", Object.class, String.class);
		return ih;
	}

	private InstructionHandle JVMTools_toAwkStringForOutput() {
		InstructionHandle ih = JVMTools_getVariable(ofmt_offset, true, false);	// true = is_global, false = NOT an array
		JVMTools_invokeVirtual(String.class, Object.class, "toString");
		JVMTools_invokeStatic(String.class, JRT_Class, "toAwkStringForOutput", Object.class, String.class);
		return ih;
	}

	private void JVMTools_toDouble() {
		JVMTools_invokeStatic(Double.TYPE, JRT_Class, "toDouble", Object.class);
	}

	private void JVMTools_toDouble(int num_refs) {
		if (num_refs == 1) {
			JVMTools_toDouble();
		} else if (num_refs == 2) {
			JVMTools_toDouble();
			JVMTools_storeRegister();
			JVMTools_toDouble();
			JVMTools_loadRegister();
			JVMTools_swap2();
		} else {
			throw new Error("num_refs of "+num_refs+" unsupported.");
		}
	}

	private void JVMTools_storeRegister() {
		JVMTools_storeToLocalVariable(Double.TYPE, "dregister");
	}

	private InstructionHandle JVMTools_loadRegister() {
		return JVMTools_getLocalVariable(Double.TYPE, "dregister");
	}

	private BranchHandle JVMTools_ifInt() {
		// stack: ..., double
		JVMTools_DUP2();
		// ..., double, double
		JVMTools_D2I();
		// ..., double, int
		JVMTools_I2D();
		// ..., double, double
		JVMTools_DCMPL();
		// ..., result
		return JVMTools_IFEQ();
	}

	private void JVMTools_DCMPL() {
		il.append(InstructionConstants.DCMPL);
	}

	private BranchHandle JVMTools_IFEQ() {
		return il.append(new IFEQ(null));
	}

	private BranchHandle JVMTools_IFNE() {
		return il.append(new IFNE(null));
	}

	private BranchHandle JVMTools_IFLE() {
		return il.append(new IFLE(null));
	}

	private void JVMTools_fromDoubleToNumber() {
		JVMTools_DUP2();
		BranchHandle bh = JVMTools_ifInt();
		// double here
		JVMTools_invokeStatic(Double.class, Double.class, "valueOf", Double.TYPE);
		BranchHandle bh_end = JVMTools_GOTO();

		InstructionHandle ih =
				// int here
				JVMTools_D2I();
		bh.setTarget(ih);
		JVMTools_invokeStatic(Integer.class, Integer.class, "valueOf", Integer.TYPE);

		InstructionHandle ih_end = JVMTools_NOP();
		bh_end.setTarget(ih_end);
	}

	private BranchHandle JVMTools_ifStringNotEquals(String str) {
		// stack: ..., stringref
		JVMTools_pushString(str);
		JVMTools_invokeVirtual(Boolean.TYPE, String.class, "equals", Object.class);
		return JVMTools_IFEQ();
	}

	private void JVMTools_newAssocArray() {
		il.append(factory.createNew(AssocArrayClass.getName()));
		il.append(InstructionConstants.DUP);
		il.append(new PUSH(cp, settings.isUseSortedArrayKeys()));	// false = not in sorted order
		il.append(factory.createInvoke(AssocArrayClass.getName(), "<init>",
				Type.VOID,
				buildArgs(new Class[] {Boolean.TYPE}),
				INVOKESPECIAL));
	}

	private InstructionHandle JVMTools_getVariable(int offset, boolean is_global, boolean is_array) {
		if (offset < 0) {
			throw new IllegalArgumentException("offset = " + offset + " ?! is_global=" + is_global + ", is_array=" + is_array);
		}
		InstructionHandle retval;
		if (is_global) {
			retval = JVMTools_getField(Object.class, "global_" + offset);
			JVMTools_DUP();
			BranchHandle bh = JVMTools_IFNONNULL();
			JVMTools_POP();
			if (is_array) {
				JVMTools_newAssocArray();
			} else {
				JVMTools_pushString("");
			}
			JVMTools_DUP();
			JVMTools_storeField(Object.class, "global_" + offset);
			InstructionHandle ih = JVMTools_NOP();
			bh.setTarget(ih);
		} else {
			retval = JVMTools_getLocalVariable(Object.class, "locals_" + offset);
			JVMTools_DUP();
			BranchHandle bh = JVMTools_IFNONNULL();
			JVMTools_POP();
			if (is_array) {
				JVMTools_newAssocArray();
			} else {
				JVMTools_pushString("");
			}
			JVMTools_DUP();
			JVMTools_storeToLocalVariable(Object.class, "locals_" + offset);
			InstructionHandle ih = JVMTools_NOP();
			bh.setTarget(ih);
		}
		return retval;
	}

	private void JVMTools_setVariable(int offset, boolean is_global) {
		if (is_global) {
			JVMTools_storeField(Object.class, "global_" + offset);
		} else {
			JVMTools_storeToLocalVariable(Object.class, "locals_" + offset);
		}
	}

	private void JVMTools_throwNewException(Class cls, String msg) {
		JVMTools_throwNewException(il, cls, msg);
	}

	private void JVMTools_throwNewException(InstructionList il, Class cls, String msg) {
		il.append(new PUSH(cp, msg));
		JVMTools_new(il, cls.getName(), String.class);
		//JVMTools_DEBUG("Throwing end exception: "+msg);
		il.append(InstructionConstants.ATHROW);
	}

	private void JVMTools_DEBUG(String msg) {
		JVMTools_getStaticField(System.class.getName(), "out", PrintStream.class);
		il.append(new PUSH(cp, "DEBUG: " + msg));
		JVMTools_invokeVirtual(Void.TYPE, PrintStream.class, "println", String.class);
	}

	private void JVMTools_DEBUG(int offset, boolean is_global) {
		JVMTools_DEBUG("Variable offset: " + offset + ", " + is_global + " ...");
		JVMTools_getStaticField(System.class.getName(), "out", PrintStream.class);
		JVMTools_getVariable(offset, is_global, false);	// false = is NOT an array
		JVMTools_invokeVirtual(Void.TYPE, PrintStream.class, "println", Object.class);
	}

	/**
	 * Print the top-of-stack as an object reference via PrintStream.println().
	 * <p>
	 * <strong>Warning:</strong> If the top-of-stack is not an
	 * object reference (i.e., an integer or a double), a VerifyError
	 * will occur.
	 * </p>
	 */
	private void JVMTools_DEBUG_TOS() {
		// ... objref
		JVMTools_DUP();
		JVMTools_invokeVirtual(Class.class, Object.class, "getClass");
		JVMTools_getStaticField(System.class.getName(), "out", PrintStream.class);
		JVMTools_SWAP();
		// ... objref, System.out, objref
		JVMTools_invokeVirtual(Void.TYPE, PrintStream.class, "println", Object.class);
		// ... objref
	}

	////////////////////////////////////////////////////////////////////////////////

	private static Type getObjectType(Class cls) {
		if (cls.isArray()) {
			return new ArrayType(getObjectType(cls.getComponentType()), 1);
		} else if (cls == Boolean.TYPE) {
			return Type.BOOLEAN;
		} else if (cls == Byte.TYPE) {
			return Type.BYTE;
		} else if (cls == Character.TYPE) {
			return Type.CHAR;
		} else if (cls == Double.TYPE) {
			return Type.DOUBLE;
		} else if (cls == Float.TYPE) {
			return Type.FLOAT;
		} else if (cls == Integer.TYPE) {
			return Type.INT;
		} else if (cls == Long.TYPE) {
			return Type.LONG;
		} else if (cls == Short.TYPE) {
			return Type.SHORT;
		} else if (cls == String.class) {
			return Type.STRING;
		} else if (cls == StringBuffer.class) {
			return Type.STRINGBUFFER;
		} else if (cls == Object.class) {
			return Type.OBJECT;
		} else if (cls == Void.TYPE) {
			return Type.VOID;
		} else {
			return new ObjectType(cls.getName());
		}
	}

	private static Type[] buildArgs(Class[] arguments) {
		java.util.List<Type> arg_list = new ArrayList<Type>();
		for (Class cls : arguments) {
			arg_list.add(getObjectType(cls));
		}
		return arg_list.toArray(new Type[0]);
	}
}
