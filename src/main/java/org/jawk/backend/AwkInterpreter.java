package org.jawk.backend;

import org.jawk.ExitException;
import org.jawk.intermediate.AwkTuples;

/**
 * Interpret a Jawk script within this JVM.
 *
 * @param tuples The tuples containing the intermediate code.
 */
public interface AwkInterpreter {

	/**
	 * Traverse the tuples, interpreting tuple opcodes and arguments
	 * and acting on them accordingly.
	 *
	 * @param tuples The tuples to compile.
	 *
	 * @throws ExitException indicates that the interpreter would like
	 *   the application to exit.
	 */
	void interpret(AwkTuples tuples) throws ExitException;
}
