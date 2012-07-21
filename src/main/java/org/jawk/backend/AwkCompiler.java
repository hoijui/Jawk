package org.jawk.backend;

import org.jawk.intermediate.AwkTuples;

/**
 * Compile a Jawk script. The target machine can be any actual or virtual
 * machine. If a compiler implementation is provided upon the release of
 * Jawk, the compiler targets the JVM.
 *
 * @param tuples The tuples containing the intermediate code.
 */
public interface AwkCompiler {

	/**
	 * Traverse the tuples, translating tuple opcodes and arguments
	 * to target machine code.
	 *
	 * @param tuples The tuples to compile.
	 */
	void compile(AwkTuples tuples);
}
