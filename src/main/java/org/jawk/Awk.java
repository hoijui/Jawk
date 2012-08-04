package org.jawk;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import org.jawk.backend.AVM;
import org.jawk.backend.AwkCompiler;
import org.jawk.ext.JawkExtension;
import org.jawk.frontend.AwkParser;
import org.jawk.frontend.AwkSyntaxTree;
import org.jawk.intermediate.AwkTuples;
import org.jawk.util.AwkSettings;
import org.jawk.util.DestDirClassLoader;
import org.jawk.util.ScriptSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point into the parsing, analysis, and execution/compilation
 * of a Jawk script.
 * This entry point is used when Jawk is executed as a library.
 * If you want to use Jawk as a stand-alone application, please use {@see Main}.
 * <p>
 * The overall process to execute a Jawk script is as follows:
 * <ul>
 * <li>Parse the Jawk script, producing an abstract syntax tree.</li>
 * <li>Traverse the abstract syntax tree, producing a list of
 *	 instruction tuples for the interpreter.</li>
 * <li>Either:
 *   <ul>
 *   <li>Traverse the list of tuples, providing a runtime which
 *	   ultimately executes the Jawk script, <strong>or</strong></li>
 *   <li>Translate the list of tuples into JVM code, providing
 *     a compiled representation of the script to JVM.</li>
 *   </ul>
 *   Command-line parameters dictate which action is to take place.</li>
 * </ul>
 * Two additional semantic checks on the syntax tree are employed
 * (both to resolve function calls for defined functions).
 * As a result, the syntax tree is traversed three times.
 * And the number of times tuples are traversed is depends
 * on whether interpretation or compilation takes place.
 * As of this writing, Jawk traverses the tuples once for
 * interpretation, and two times for compilation (once for
 * global variable arrangement, and the second time for
 * translation to byte-code).
 * </p>
 * <p>
 * By default a minimal set of extensions are automatically
 * included. Please refer to the EXTENSION_PREFIX static field
 * contents for an up-to-date list. As of the initial release
 * of the extension system, the prefix defines the following
 * extensions:
 * <ul>
 * <li>CoreExtension</li>
 * <li>SocketExtension</li>
 * <li>StdinExtension</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Note:</strong> Compilation requires the installation of
 * <a href="http://jakarta.apache.org/bcel/" target=_TOP>
 * The Apache Byte Code Engineering Library (BCEL)</a>.
 * Please see the AwkCompilerImpl JavaDocs or the
 * project web page for more details.
 * </p>
 *
 * @see org.jawk.backend.AVM
 * @see org.jawk.backend.AwkCompilerImpl
 *
 * @author Danny Daglas
 */
public class Awk {

	private static final String DEFAULT_EXTENSIONS
			= org.jawk.ext.CoreExtension.class.getName()
			+ "#" + org.jawk.ext.SocketExtension.class.getName()
			+ "#" + org.jawk.ext.StdinExtension.class.getName();
	private static final Logger LOG = LoggerFactory.getLogger(Awk.class);

	public Awk() {}

	/**
	 * An entry point to Jawk that provides the exit code of the script
	 * if interpreted or an compiler error status if compiled.
	 * If compiled, a non-zero exit status indicates that there
	 * was a compilation problem.
	 *
	 * @param settings This tells AWK what to do
	 *   (where to get input from, where to write it to, in what mode to run,
	 *   ...)
	 *
	 * @return The exit code to the script if interpreted, or exit code
	 * 	 of the compiler.
	 *
	 * @throws IOException upon an IO error.
	 * @throws ClassNotFoundException if compilation is requested,
	 *	 but no compilation implementation class is found.
	 * @throws ExitException if interpretation is requested,
	 *	 and a specific exit code is requested.
	 */
	public void invoke(AwkSettings settings)
			throws IOException, ClassNotFoundException, ExitException
	{
		AVM avm = null;
		try {
			// key = Keyword, value = JawkExtension
			Map<String, JawkExtension> extensions;
			if (settings.isUserExtensions()) {
				extensions = getJawkExtensions();
				LOG.trace("user extensions = {}", extensions.keySet());
			} else {
				extensions = Collections.emptyMap();
				LOG.trace("user extensions not enabled");
			}

			AwkTuples tuples = new AwkTuples();
			// to be defined below

			List<ScriptSource> notIntermediateScriptSources = new ArrayList<ScriptSource>(settings.getScriptSources().size());
			for (ScriptSource scriptSource : settings.getScriptSources()) {
				if (scriptSource.isIntermediate()) {
					// read the intermediate file, bypassing frontend processing
					tuples = (AwkTuples) readObjectFromInputStream(scriptSource.getInputStream()); // FIXME only the last intermediate file is used!
				} else {
					notIntermediateScriptSources.add(scriptSource);
				}
			}
			if (!notIntermediateScriptSources.isEmpty()) {
				AwkParser parser = new AwkParser(
						settings.isAdditionalFunctions(),
						settings.isAdditionalTypeFunctions(),
						settings.isUseStdIn(),
						extensions);
				// parse the script
				AwkSyntaxTree ast = parser.parse(notIntermediateScriptSources);

				if (settings.isDumpSyntaxTree()) {
					// dump the syntax tree of the script to a file
					String filename = settings.getOutputFilename("syntax_tree.lst");
					LOG.info("writing to '{}'", filename);
					PrintStream ps = new PrintStream(new FileOutputStream(filename));
					if (ast != null) {
						ast.dump(ps);
					}
					ps.close();
					return;
				}
				// otherwise, attempt to traverse the syntax tree and build
				// the intermediate code
				if (ast != null) {
					// 1st pass to tie actual parameters to back-referenced formal parameters
					ast.semanticAnalysis();
					// 2nd pass to tie actual parameters to forward-referenced formal parameters
					ast.semanticAnalysis();
					// build tuples
					int result = ast.populateTuples(tuples);
					// ASSERTION: NOTHING should be left on the operand stack ...
					assert result == 0;
					// Assign queue.next to the next element in the queue.
					// Calls touch(...) per Tuple so that addresses can be normalized/assigned/allocated
					tuples.postProcess();
					// record global_var -> offset mapping into the tuples
					// so that the interpreter/compiler can assign variables
					// on the "file list input" command line
					parser.populateGlobalVariableNameToOffsetMappings(tuples);
				}
				if (settings.isWriteIntermediateFile()) {
					// dump the intermediate code to an intermediate code file
					String filename = settings.getOutputFilename("a.ai");
					LOG.info("writing to '{}'", filename);
					writeObjectToFile(tuples, filename);
					return;
				}
			}
			if (settings.isDumpIntermediateCode()) {
				// dump the intermediate code to a human-readable text file
				String filename = settings.getOutputFilename("avm.lst");
				LOG.info("writing to '{}'", filename);
				PrintStream ps = new PrintStream(new FileOutputStream(filename));
				tuples.dump(ps);
				ps.close();
				return;
			}

			if (settings.isCompileRun() || settings.isCompile()) {
				// compile!
				attemptToCompile(settings, tuples);
				if (settings.isCompileRun()) {
					attemptToExecuteCompiledResult(settings);
				}
			} else {
				// interpret!
				avm = new AVM(settings, extensions);
				avm.interpret(tuples);
			}
		} finally {
			if (avm != null) {
				avm.waitForIO();
			}
		}
	}

	/**
	 * Use reflection in attempt to access the compiler.
	 */
	private static void attemptToCompile(AwkSettings settings, AwkTuples tuples) {
		try {
			LOG.trace("locating AwkCompilerImpl...");
			Class<?> compilerClass = Class.forName("org.jawk.backend.AwkCompilerImpl");
			LOG.trace("found: {}", compilerClass);
			try {
				Constructor constructor = compilerClass.getConstructor(AwkSettings.class);
				try {
					LOG.trace("allocating new instance of the AwkCompiler class...");
					AwkCompiler compiler = (AwkCompiler) constructor.newInstance(settings);
					LOG.trace("allocated: {}", compiler);
					LOG.trace("compiling...");
					compiler.compile(tuples);
					LOG.trace("done");
				} catch (InstantiationException ie) {
					throw new Error("Cannot instantiate the compiler", ie);
				} catch (IllegalAccessException iae) {
					throw new Error("Cannot instantiate the compiler", iae);
				} catch (java.lang.reflect.InvocationTargetException ite) {
					throw new Error("Cannot instantiate the compiler", ite);
				}
			} catch (NoSuchMethodException nsme) {
				throw new Error("Cannot find the constructor", nsme);
			}
		} catch (ClassNotFoundException cnfe) {
			throw new IllegalArgumentException("Cannot find the AwkCompiler", cnfe);
		}
	}

	private static void attemptToExecuteCompiledResult(AwkSettings settings) {
		String classname = settings.getOutputFilename("AwkScript");
		try {
			LOG.trace("locating {}...", classname);
			Class<?> scriptClass;
			String destinationDirectory = settings.getDestinationDirectory();
			ClassLoader cl = new DestDirClassLoader(destinationDirectory);
			scriptClass = cl.loadClass(classname);
			LOG.trace("found: {} in {}", new Object[] {scriptClass, destinationDirectory});
			try {
				Constructor constructor = scriptClass.getConstructor();
				try {
					LOG.trace("allocating and executing new instance of {} class...", classname);
					Object obj = constructor.newInstance();
					Method method = scriptClass.getDeclaredMethod("ScriptMain", new Class<?>[] {AwkSettings.class});
					Object result = method.invoke(obj, new Object[] {settings});
				} catch (InstantiationException ie) {
					throw new Error("Cannot instantiate the script", ie);
				} catch (IllegalAccessException iae) {
					throw new Error("Cannot instantiate the script", iae);
				} catch (java.lang.reflect.InvocationTargetException ite) {
					Throwable exception = ite.getCause();
					if (exception == null) {
						throw new Error("Cannot instantiate the script", ite);
					} else {
						throw new Error("Cannot instantiate the script", exception);
					}
				}
			} catch (NoSuchMethodException nsme) {
				throw new Error("Cannot find the constructor", nsme);
			}
		} catch (ClassNotFoundException cnfe) {
			throw new IllegalArgumentException("Cannot find the " + classname + " class.", cnfe);
		}
	}

	private static Object readObjectFromInputStream(InputStream is)
			throws IOException, ClassNotFoundException
	{
		ObjectInputStream ois = new ObjectInputStream(is);
		Object retval = ois.readObject();
		ois.close();
		return retval;
	}

	private static void writeObjectToFile(Object object, String filename)
			throws IOException
	{
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename));
		oos.writeObject(object);
		oos.close();
	}

	private static Map<String, JawkExtension> getJawkExtensions() {
		String extensionsStr = System.getProperty("jawk.extensions", null);
		if (extensionsStr == null) {
			//return Collections.emptyMap();
			extensionsStr = DEFAULT_EXTENSIONS;
		} else {
			extensionsStr = DEFAULT_EXTENSIONS + "#" + extensionsStr;
		}

		// use reflection to obtain extensions

		Set<Class> extensionClasses = new HashSet<Class>();
		Map<String, JawkExtension> retval = new HashMap<String, JawkExtension>();

		StringTokenizer st = new StringTokenizer(extensionsStr, "#");
		while (st.hasMoreTokens()) {
			String cls = st.nextToken();
			LOG.trace("cls = {}", cls);
			try {
				Class<?> c = Class.forName(cls);
				// check if it's a JawkException
				if (!JawkExtension.class.isAssignableFrom(c)) {
					throw new ClassNotFoundException(cls + " does not implement JawkExtension");
				}
				if (extensionClasses.contains(c)) {
					LOG.warn("class {} is multiple times referred in extension class list. Skipping.", cls);
					continue;
				} else {
					extensionClasses.add(c);
				}

				// it is...
				// create a new instance and put it here
				try {
					JawkExtension ji = (JawkExtension) c.newInstance();
					String[] keywords = ji.extensionKeywords();
					for (String keyword : keywords) {
						if (retval.get(keyword) != null) {
							throw new IllegalArgumentException("keyword collision : " + keyword
									+ " for both " + retval.get(keyword).getExtensionName()
									+ " and " + ji.getExtensionName());
						}
						retval.put(keyword, ji);
					}
				} catch (InstantiationException ie) {
					LOG.warn("Cannot instantiate {} : {}", new Object[] {c, ie});
				} catch (IllegalAccessException iae) {
					LOG.warn("Cannot instantiate {} : {}", new Object[] {c, iae});
				}
			} catch (ClassNotFoundException cnfe) {
				LOG.warn("Cannot classload {} : {}", new Object[] {cls, cnfe});
			}
		}

		return retval;
	}
}
