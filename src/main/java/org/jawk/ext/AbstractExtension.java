package org.jawk.ext;

import org.jawk.jrt.IllegalAwkArgumentException;
import org.jawk.jrt.JRT;
import org.jawk.jrt.VariableManager;
import org.jawk.util.AwkSettings;

/**
 * Base class of various extensions.
 * <p>
 * Provides functionality common to most extensions,
 * such as VM and JRT variable management, and convenience
 * methods such as checkNumArgs() and toAwkString().
 * </p>
 */
public abstract class AbstractExtension implements JawkExtension {

	private JRT jrt;
	private VariableManager vm;
	private AwkSettings settings;

	@Override
	public void init(VariableManager vm, JRT jrt, final AwkSettings settings) {
		this.vm = vm;
		this.jrt = jrt;
		this.settings = settings;
	}

	/**
	 * Convert a Jawk variable to a Jawk string
	 * based on the value of the CONVFMT variable.
	 *
	 * @param obj The Jawk variable to convert to a Jawk string.
	 *
	 * @return A string representation of obj after CONVFMT
	 *   has been applied.
	 */
	protected final String toAwkString(Object obj) {
		return JRT.toAwkString(obj, getVm().getCONVFMT().toString());
	}

	/**
	 * Assume no guarantee of any extension parameter being an
	 * associative array.
	 *
	 * @param extensionKeyword The extension keyword to check.
	 * @param argCount The number of actual parameters used in this
	 *   extension invocation.
	 */
	@Override
	public int[] getAssocArrayParameterPositions(String extensionKeyword, int argCount) {
		return new int[0];
	}

	/**
	 * Verifies that an exact number of arguments
	 * has been passed in by checking the length
	 * of the argument array.
	 *
	 * @param arr The arguments to check.
	 * @param expectedNum The expected number of arguments.
	 *
	 * @throws IllegalAwkArgumentException if the number of arguments
	 *   do not match the expected number of arguments.
	 */
	protected static void checkNumArgs(Object[] arr, int expectedNum) {
		// some sanity checks on the arguments
		// (made into assertions so that
		// production code does not perform
		// these checks)
		assert arr != null;
		assert expectedNum >= 0;

		if (arr.length != expectedNum) {
			throw new IllegalAwkArgumentException("Expecting " + expectedNum + " arg(s), got " + arr.length);
		}
	}

	protected JRT getJrt() {
		return jrt;
	}

	protected VariableManager getVm() {
		return vm;
	}

	protected AwkSettings getSettings() {
		return settings;
	}
}
