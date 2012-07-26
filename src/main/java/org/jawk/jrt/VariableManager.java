package org.jawk.jrt;

/**
 * The AWK Variable Manager.
 * It provides getter/setter methods for global AWK variables.
 * Its purpose is to expose a variable management interface to
 * the JRT, even though the implementation is provided by
 * the AWK script at script compile-time.
 * <p>
 * The getters/setters here do not access <strong>all</strong>
 * special AWK variables, such as <code>RSTART</code>
 * and <code>ENVIRON</code>. That's because these variables
 * are not referred to within the JRT.
 * </p>
 *
 * @see JRT
 * @see org.jawk.backend.AwkCompiler
 * @see org.jawk.backend.AwkCompilerImpl
 *
 * @author Danny Daglas
 */
public interface VariableManager {

	/** Retrieve the contents of the ARGC variable. */
	Object getARGC();

	/** Retrieve the contents of the ARGV variable. */
	Object getARGV();

	/** Retrieve the contents of the CONVFMT variable. */
	Object getCONVFMT();

	/** Retrieve the contents of the FS variable. */
	Object getFS();

	/** Retrieve the contents of the RS variable. */
	Object getRS();

	/** Retrieve the contents of the OFS variable. */
	Object getOFS();

	/** Retrieve the contents of the SUBSEP variable. */
	Object getSUBSEP();

	/** Set the contents of the FILENAME variable. */
	void setFILENAME(String fileName);

	/** Set the contents of the NF variable. */
	void setNF(Integer newNf);

	/** Increases the NR variable by 1. */
	void incNR();

	/** Increases the FNR variable by 1. */
	void incFNR();

	/** Resets the FNR variable to 0. */
	void resetFNR();

	/**
	 * Set the contents of a user-defined AWK
	 * variable. Used when processing
	 * <em>name=value</em> command-line arguments
	 * (either via -v or via ARGV).
	 *
	 * @param name The AWK variable name.
	 * @param value The new contents of the variable.
	 */
	void assignVariable(String name, Object value);
}
