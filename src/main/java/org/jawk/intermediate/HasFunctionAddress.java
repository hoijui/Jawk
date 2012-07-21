package org.jawk.intermediate;

/**
 * A placeholder for an object which has a reference to
 * a function address, but which may not be realized yet.
 * This is particularly important for forward-referenced
 * functions. For example:
 * <blockquote>
 * <pre>
 * BEGIN { f(3) }
 * function f(x) { print x*x }
 * </pre>
 * </blockquote>
 * f() is referred to prior to its definition. Therefore,
 * the getFunctionAddress() call within the BEGIN block
 * will not return a meaningful address. However, anytime
 * after f(x) is defined, getFunctionAddress() will return
 * the correct function address.
 */
public interface HasFunctionAddress {

	/**
	 * Get an address to the tuple where this function is
	 * defined.
	 * <p>
	 * If getFunctionAddress() is called prior to defining
	 * the function address (prior to parsing the function
	 * block), the result is undefined. (As of this writing,
	 * a NullPointerException is thrown.)
	 * </p>
	 */
	Address getFunctionAddress();
}
