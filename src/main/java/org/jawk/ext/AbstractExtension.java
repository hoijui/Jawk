package org.jawk.ext;

import org.jawk.jrt.IllegalAwkArgumentException;
import org.jawk.jrt.JRT;
import org.jawk.jrt.VariableManager;

/**
 * Base class of various extensions.
 * <p>
 * Provides functionality common to most extensions,
 * such as vm and jrt variable management, and convenience
 * methods such as checkNumArgs() and toAwkString().
 */
public abstract class AbstractExtension implements JawkExtension {

	protected JRT jrt;
	protected VariableManager vm;

	public void init(VariableManager vm, JRT jrt) {
		this.vm = vm;
		this.jrt = jrt;
	}

	/**
	 * Convert a Jawk variable to a Jawk string
	 * based on the value of the CONVFMT variable.
	 *
	 * @param obj The Jawk variable to convert to a Jawk string.
	 *
	 * @return A string representation of obj after CONVFMT
	 * 	has been applied.
	 */
	protected final String toAwkString(Object obj) {
		return JRT.toAwkString(obj, vm.getCONVFMT().toString());
	}

	/**
	 * Assume no guarantee of any extension parameter being an
	 * associative array.
	 *
	 * @param extension_keyword The extension keyword to check.
	 * @param arg_count The number of actual parameters used in this
	 * 	extension invocation.
	 */
	public int[] getAssocArrayParameterPositions(String extension_keyword, int arg_count) {
		return new int[0];
	}

	/**
	 * Verifies that an exact number of arguments
	 * has been passed in by checking the length
	 * of the argument array.
	 *
	 * @param arr The arguments to check.
	 * @param expected_num The expected number of arguments.
	 *
	 * @throws IllegalAwkArgumentException if the number of arguments
	 * 	do not match the expected number of arguments.
	 */
	protected static final void checkNumArgs(Object[] arr, int expected_num) {
		// some sanity checks on the arguments
		// (made into assertions so that
		// production code does not perform
		// these checks)
		assert arr != null;
		assert expected_num >= 0;

		if (arr.length != expected_num) {
			throw new IllegalAwkArgumentException("Expecting " + expected_num + " arg(s), got " + arr.length);
		}
	}
} // public abstract class AbstractExtension [JawkExtension]
