package org.jawk.frontend;

import java.io.PrintStream;
import org.jawk.intermediate.AwkTuples;

/**
 * A Jawk abstract syntax tree node. This provides
 * an appropriate public interface to the abstract
 * syntax tree.
 */
public interface AwkSyntaxTree {

	/**
	 * Dump a meaningful text representation of this
	 * abstract syntax tree node to the output (print)
	 * stream. Either it is called directly by the
	 * application program, or it is called by the
	 * parent node of this tree node.
	 *
	 * @param ps The print stream to dump the text
	 *   representation.
	 */
	void dump(PrintStream ps);

	/**
	 * Apply semantic checks to this node. The default
	 * implementation is to simply call semanticAnalysis()
	 * on all the children of this abstract syntax tree node.
	 * Therefore, this method must be overridden to provide
	 * meaningful semantic analysis / checks.
	 */
	void semanticAnalysis();

	/**
	 * Appends tuples to the AwkTuples list
	 * for this abstract syntax tree node. Subclasses
	 * must implement this method.
	 * <p>
	 * This is called either by the main program to generate a full
	 * list of tuples for the abstract syntax tree, or it is called
	 * by other abstract syntax tree nodes in response to their
	 * attempt at populating tuples.
	 * </p>
	 *
	 * @param tuples The tuples to populate.
	 *
	 * @return The number of items left on the operand stack after
	 *   these tuples have executed.
	 */
	int populateTuples(AwkTuples tuples);
}
