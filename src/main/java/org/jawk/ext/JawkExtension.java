package org.jawk.ext;

import org.jawk.jrt.JRT;
import org.jawk.jrt.VariableManager;
import org.jawk.util.AwkSettings;

/**
 * A Jawk Extension.
 * <p>
 * Instances of this interface are eligible for insertion
 * into Jawk as an extension to the language. Extensions
 * appear within a Jawk script as function calls.
 * </p>
 * <p>
 * Extensions introduce native Java modules into the Jawk language.
 * This enables special services into Jawk, such as Sockets,
 * GUIs, databases, etc. natively into Jawk.
 * </p>
 * <p>
 * Extension functions can be used anywhere an AWK function,
 * builtin or user-defined, can be used. One immediate consideration
 * is the default Jawk input mechanism, where if action rules exist
 * (other than BEGIN/END), Jawk requires input from stdin before
 * processing these rules. It may be desirable to trigger action
 * rules on an extension rather than stdin user input. To prohibit
 * Jawk default behavior, a new command-line argument, "-ni" for
 * "no input", disables Jawk default behavior of consuming input
 * from stdin for action rules.
 * <blockquote>
 * <strong>Note:</strong> By disabling Jawk's default behavior of
 * consuming input from stdin, it can cause your script to loop
 * through all of the action rule conditions repeatedly, consuming
 * CPU without bounds. To guard against this, the extension should
 * provide some sort of poll or block call to avoid
 * out-of-control CPU resource consumption.
 * </blockquote>
 * </p>
 * <p>
 * Extensions introduce keywords into the Jawk parser.
 * Keywords are of type _EXTENSION_ tokens. As a result,
 * extension keywords cannot collide with other Jawk keywords,
 * variables, or function names. The extension mechanism
 * also guards against keyword collision with other extensions.
 * The Jawk lexer expects extension keywords to match as _ID_'s.
 * </p>
 */
public interface JawkExtension {

	/**
	 * Called after the creation and before normal processing of the
	 * extension, pass in the Jawk Runtime Manager
	 * and the Variable Manager once.
	 * <p>
	 * It is guaranteed init() is called before invoke() is called.
	 * </p>
	 */
	void init(VariableManager vm, JRT jrt, final AwkSettings settings);

	/**
	 * The name of the extension package.
	 */
	String getExtensionName();

	/**
	 * All the extended keywords supported
	 * by this extension.
	 * <p>
	 * <strong>Note:</strong> Jawk will
	 * throw a runtime exception if the
	 * keyword collides with any other keyword
	 * in the system, extension or otherwise.
	 * </p>
	 */
	String[] extensionKeywords();

	/**
	 * Define the parameters which are <strong>expected</strong> to be
	 * associative arrays. This is used by the semantic analyzer
	 * to enforce type checking and correct Jawk variable allocation
	 * (which is done at the beginning of script execution).
	 *
	 * @param extensionKeyword The extension keyword to check.
	 * @param numArgs How many actual parameters are used in the call.
	 *
	 * @return An array of parameter indexes containing associative arrays.
	 *   <strong>Note:</strong> non-inclusion of a parameter index
	 *   into this array makes no implication as to whether the
	 *   parameter is a scalar or an associative array. It means
	 *   that its type is not guaranteed to be an associative array.
	 */
	int[] getAssocArrayParameterPositions(String extensionKeyword, int numArgs);

	/**
	 * Invoke extension as a method.
	 *
	 * @param keyword The extension keyword.
	 * @param args Arguments to the extension.
	 *
	 * @return The return value (result) of the extension.
	 */
	Object invoke(String keyword, Object[] args);
}
