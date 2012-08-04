package org.jawk.intermediate;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jawk.util.LinkedListStackImpl;
import org.jawk.util.MyStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AwkTuples implements Serializable {

	private static final Logger LOG = LoggerFactory.getLogger(AwkTuples.class);

	private VersionManager version_manager = new VersionManager();

	private static final class AddressImpl implements Address, Serializable {

		private String lbl;
		private int idx = -1;

		private AddressImpl(String lbl) {
			this.lbl = lbl;
		}

		@Override
		public String label() {
			return lbl;
		}

		@Override
		public String toString() {
			return label();
		}

		@Override
		public void assignIndex(int idx) {
			this.idx = idx;
		}

		@Override
		public int index() {
			assert idx >= 0 : toString();
			return idx;
		}
	}

	private class PositionImpl implements PositionForInterpretation, PositionForCompilation {
		// index within the queue

		private int idx = 0;
		private Tuple tuple = queue.isEmpty() ? null : queue.get(idx); // current tuple

		@Override
		public int index() {
			return idx;
		}

		@Override
		public boolean isEOF() {
			return idx >= queue.size();
		}

		@Override
		public void next() {
			assert tuple != null;
			++idx;
			tuple = tuple.getNext();
			assert queue.size() == idx || queue.get(idx) == tuple;
		}

		@Override
		public void jump(Address address) {
			tuple = queue.get(idx = address.index());
		}

		@Override
		public String toString() {
			return "[" + idx + "]-->" + tuple.toString();
		}

		@Override
		public int opcode() {
			return tuple.getOpcode();
		}

		@Override
		public int intArg(int arg_idx) {
			Class c = tuple.getTypes()[arg_idx];
			if (c == Integer.class) {
				return tuple.getInts()[arg_idx];
			}
			throw new Error("Invalid arg type: " + c + ", arg_idx = " + arg_idx + ", tuple = " + tuple);
		}

		@Override
		public boolean boolArg(int arg_idx) {
			Class c = tuple.getTypes()[arg_idx];
			if (c == Boolean.class) {
				return tuple.getBools()[arg_idx];
			}
			throw new Error("Invalid arg type: " + c + ", arg_idx = " + arg_idx + ", tuple = " + tuple);
		}

		@Override
		public Object arg(int arg_idx) {
			Class c = tuple.getTypes()[arg_idx];
			if (c == Integer.class) {
				return tuple.getInts()[arg_idx];
			}
			if (c == Double.class) {
				return tuple.getDoubles()[arg_idx];
			}
			if (c == String.class) {
				return tuple.getStrings()[arg_idx];
			}
			if (c == Address.class) {
				assert arg_idx == 0;
				return tuple.getAddress();
			}
			throw new Error("Invalid arg type: " + c + ", arg_idx = " + arg_idx + ", tuple = " + tuple);
		}

		@Override
		public Address addressArg() {
			assert tuple.getAddress() != null || tuple.getHasFuncAddr() != null : "tuple.address = " + tuple.getAddress() + ", tuple.has_func_addr = " + tuple.getHasFuncAddr();
			if (tuple.getAddress() == null) {
				tuple.setAddress(tuple.getHasFuncAddr().getFunctionAddress());
			}
			return tuple.getAddress();
		}

		@Override
		public Class classArg() {
			//Tuple tuple = queue.get(idx);
			assert tuple.getCls() != null;
			return tuple.getCls();
		}

		@Override
		public int lineNumber() {
			assert tuple.getLineno() != -1 : "The line number should have been set by queue.add(), but was not.";
			return tuple.getLineno();
		}

		@Override
		public int current() {
			return idx;
		}

		@Override
		public void jump(int idx) {
			tuple = queue.get(this.idx = idx);
		}
	}

	// made public to access static members of AwkTuples via Java Reflection
	private static final class Tuple implements Serializable {

		private int opcode;
		private int[] ints = new int[4];
		private boolean[] bools = new boolean[4];
		private double[] doubles = new double[4];
		private String[] strings = new String[4];
		private Class[] types = new Class[4];
		private Address address = null;
		private Class cls = null;
		private transient HasFunctionAddress hasFuncAddr = null;
		// to avoid polluting the constructors,
		// setLineNumber(int) populates this field
		// (called by an anonymous inner subclass of ArrayList,
		// assigned to queue - see above)
		private int lineno = -1;
		private Tuple next = null;

		private Tuple(int opcode) {
			this.opcode = opcode;
		}

		private Tuple(int opcode, int i1) {
			this(opcode);
			ints[0] = i1;
			types[0] = Integer.class;
		}

		private Tuple(int opcode, int i1, int i2) {
			this(opcode, i1);
			ints[1] = i2;
			types[1] = Integer.class;
		}

		private Tuple(int opcode, int i1, boolean b2) {
			this(opcode, i1);
			bools[1] = b2;
			types[1] = Boolean.class;
		}

		private Tuple(int opcode, int i1, boolean b2, boolean b3) {
			this(opcode, i1, b2);
			bools[2] = b3;
			types[2] = Boolean.class;
		}

		private Tuple(int opcode, double d1) {
			this(opcode);
			doubles[0] = d1;
			types[0] = Double.class;
		}

		private Tuple(int opcode, String s1) {
			this(opcode);
			strings[0] = s1;
			types[0] = String.class;
		}

		private Tuple(int opcode, boolean b1) {
			this(opcode);
			bools[0] = b1;
			types[0] = Boolean.class;
		}

		private Tuple(int opcode, String s1, int i2) {
			this(opcode, s1);
			ints[1] = i2;
			types[1] = Integer.class;
		}

		private Tuple(int opcode, Address address) {
			this(opcode);
			this.address = address;
			types[0] = Address.class;
		}

		private Tuple(int opcode, String strarg, int intarg, boolean boolarg) {
			this(opcode, strarg, intarg);
			bools[2] = boolarg;
			types[2] = Boolean.class;
		}

		private Tuple(int opcode, HasFunctionAddress has_func_addr, String s2, int i3, int i4) {
			this(opcode);
			this.hasFuncAddr = has_func_addr;
			strings[1] = s2;
			types[1] = String.class;
			ints[2] = i3;
			types[2] = Integer.class;
			ints[3] = i4;
			types[3] = Integer.class;
		}

		private Tuple(int opcode, Class cls) {
			this(opcode);
			this.cls = cls;
			types[0] = Class.class;
		}

		private Tuple(int opcode, String s1, String s2) {
			this(opcode, s1);
			strings[1] = s2;
			types[1] = String.class;
		}

		private boolean hasNext() {
			return (next != null);
		}

		private Tuple getNext() {
			return next;
		}

		private void setNext(Tuple next) {
			this.next = next;
		}

		private void setLineNumber(int lineno) {
			assert this.lineno == -1 : "The line number was already set to " + this.lineno + ". Later lineno = " + lineno + ".";
			this.lineno = lineno;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(toOpcodeString(opcode));
			int idx = 0;
			while ((idx < types.length) && (types[idx] != null)) {
				sb.append(", ");
				Class type = types[idx];
				if (type == Integer.class) {
					sb.append(ints[idx]);
				} else if (type == Boolean.class) {
					sb.append(bools[idx]);
				} else if (type == Double.class) {
					sb.append(doubles[idx]);
				} else if (type == String.class) {
					sb.append('"').append(strings[idx]).append('"');
				} else if (type == Address.class) {
					assert (idx == 0);
					sb.append(address);
				} else if (type == Class.class) {
					assert (idx == 0);
					sb.append(cls);
				} else if (type == HasFunctionAddress.class) {
					assert (idx == 0);
					sb.append(hasFuncAddr);
				} else {
					throw new Error("Unknown param type (" + idx + "): " + type);
				}
				++idx;
			}
			return sb.toString();
		}

		/**
		 * Update this tuple to populate the address argument value if necessary;
		 * and, check if address points to a proper element in the tuple queue.
		 * <p>
		 * The address will be updated only if there exists a HasFunctionAddress
		 * argument for this tuple.
		 * </p>
		 * <p>
		 * This is executed after the tuples are constructed so that function address
		 * references can be resolved. Otherwise, forward declared functions will
		 * not be resolved in the Tuple list.
		 * </p>
		 */
		public void touch(java.util.List<Tuple> queue) {
			assert lineno != -1 : "The line number should have been set by queue.add(), but was not.";
			if (hasFuncAddr != null) {
				address = hasFuncAddr.getFunctionAddress();
				types[0] = Address.class;
			}
			if (address != null) {
				if (address.index() == -1) {
					throw new Error("address " + address + " is unresolved");
				}
				if (address.index() >= queue.size()) {
					throw new Error("address " + address + " doesn't resolve to an actual list element");
				}
			}
		}

		private int getOpcode() {
			return opcode;
		}

		private int[] getInts() {
			return ints;
		}

		private boolean[] getBools() {
			return bools;
		}

		private double[] getDoubles() {
			return doubles;
		}

		private String[] getStrings() {
			return strings;
		}

		private Class[] getTypes() {
			return types;
		}

		private void setAddress(Address address) {
			this.address = address;
		}

		private Address getAddress() {
			return address;
		}

		private int getLineno() {
			return lineno;
		}

		private void setOpcode(int opcode) {
			this.opcode = opcode;
		}

		private Class getCls() {
			return cls;
		}

		private HasFunctionAddress getHasFuncAddr() {
			return hasFuncAddr;
		}
	}

	// made public to be accessable via Java Reflection
	// (see toOpcodeString() method below)

	/**
	 * Pops an item off the operand stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: ...
	 * </p>
	 */
	public static final int _POP_ = 257;	// x -> 0
	/**
	 * Pushes an item onto the operand stack.
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x ...
	 * </p>
	 */
	public static final int _PUSH_ = 258;	// 0 -> x
	/**
	 * Pops and evaluates the top-of-stack; if
	 * false, it jumps to a specified address.
	 * <p>
	 * Argument: address
	 * </p>
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: ...
	 * </p>
	 */
	public static final int _IFFALSE_ = 259;	// x -> 0
	/**
	 * Converts the top-of-stack to a number.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: x (as a number)
	 * </p>
	 */
	public static final int _TO_NUMBER_ = 260;	// x1 -> x2
	/**
	 * Pops and evaluates the top-of-stack; if
	 * true, it jumps to a specified address.
	 * <p>
	 * Argument: address
	 * </p>
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: ...
	 * </p>
	 */
	public static final int _IFTRUE_ = 261;	// x -> 0
	/**
	 * Jumps to a specified address. The operand stack contents
	 * are unaffected.
	 */
	public static final int _GOTO_ = 262;	// 0 -> 0
	/**
	 * A no-operation. The operand stack contents are
	 * unaffected.
	 */
	public static final int _NOP_ = 263;	// 0 -> 0
	/**
	 * Prints N number of items that are on the operand stack.
	 * The number of items are passed in as a tuple argument.
	 * <p>
	 * Argument: # of items (N)
	 * </p>
	 * <p>
	 * Stack before: x1 x2 x3 .. xN ...<br/>
	 * Stack after: ...
	 * </p>
	 */
	public static final int _PRINT_ = 264;	// x1, x2, ... xn -> 0
	/**
	 * Prints N number of items that are on the operand stack to
	 * a specified file. The file is passed in on the stack.
	 * The number of items are passed in as a tuple argument,
	 * as well as whether to overwrite the file or not (append mode).
	 * <p>
	 * Argument 1: # of items (N)<br/>
	 * Argument 2: true = append, false = overwrite
	 * </p>
	 * <p>
	 * Stack before: x1 x2 x3 .. xN filename ...<br/>
	 * Stack after: ...
	 * </p>
	 */
	public static final int _PRINT_TO_FILE_ = 265;	// x1, x2, ... xn -> 0
	/**
	 * Prints N number of items that are on the operand stack to
	 * a process executing a specified command (via a pipe).
	 * The command string is passed in on the stack.
	 * The number of items are passed in as a tuple argument.
	 * <p>
	 * Argument: # of items (N)
	 * </p>
	 * <p>
	 * Stack before: x1 x2 x3 .. xN command-string ...<br/>
	 * Stack after: ...
	 * </p>
	 */
	public static final int _PRINT_TO_PIPE_ = 266;	// x1, x2, ... xn -> 0
	/**
	 * Performs a formatted print of N items that are on the operand stack.
	 * The number of items are passed in as a tuple argument.
	 * <p>
	 * Argument: # of items (N)
	 * </p>
	 * <p>
	 * Stack before: x1 x2 x3 .. xN ...<br/>
	 * Stack after: ...
	 * </p>
	 */
	public static final int _PRINTF_ = 267;	// x1, x2, ... xn -> 0
	/**
	 * Performs a formatted print of N items that are on the operand stack to
	 * a specified file. The file is passed in on the stack.
	 * The number of items are passed in as a tuple argument,
	 * as well as whether to overwrite the file or not (append mode).
	 * <p>
	 * Argument 1: # of items (N)<br/>
	 * Argument 2: true = append, false = overwrite
	 * </p>
	 * <p>
	 * Stack before: x1 x2 x3 .. xN filename ...<br/>
	 * Stack after: ...
	 * </p>
	 */
	public static final int _PRINTF_TO_FILE_ = 268;	// x1, x2, ... xn -> 0
	/**
	 * Performs a formatted print of N items that are on the operand stack to
	 * a process executing a specified command (via a pipe).
	 * The command string is passed in on the stack.
	 * The number of items are passed in as a tuple argument.
	 * <p>
	 * Argument: # of items (N)
	 * </p>
	 * <p>
	 * Stack before: x1 x2 x3 .. xN command-string ...<br/>
	 * Stack after: ...
	 * </p>
	 */
	public static final int _PRINTF_TO_PIPE_ = 269;	// x1, x2, ... xn -> 0
	public static final int _SPRINTF_ = 270;	// x1, x2, ... xn -> 0
	/**
	 * Depending on the argument, pop and evaluate the string length of the top-of-stack
	 * or evaluate the string length of $0; in either case, push the result onto
	 * the stack.
	 * <p>
	 * The input field length evaluation mode is provided to support backward
	 * compatibility with the deprecated usage of length (i.e., no arguments).
	 * </p>
	 * <p>
	 * Argument: 0 to use $0, use top-of-stack otherwise
	 * </p>
	 * <p>
	 * If argument is 0:
	 * <blockquote>
	 * Stack before: ...<br/>
	 * Stack after: length-of-$0 ...
	 * </blockquote>
	 * else
	 * <blockquote>
	 * Stack before: x ...<br/>
	 * Stack after: length-of-x ...
	 * </blockquote>
	 * </p>
	 */
	public static final int _LENGTH_ = 271;	// 0 -> x or x1 -> x2
	/**
	 * Pop and concatenate two strings from the top-of-stack; push the result onto
	 * the stack.
	 * <p>
	 * Stack before: x y ...<br/>
	 * Stack after: x-concatenated-with-y ...
	 * </p>
	 */
	public static final int _CONCAT_ = 272;	// x2, x1 -> x1x2
	/**
	 * Assigns the top-of-stack to a variable. The contents of the stack
	 * are unaffected.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * </p>
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: x ...
	 * </p>
	 */
	public static final int _ASSIGN_ = 273;	// x -> 0
	/**
	 * Assigns an item to an array element. The item remains on the stack.
	 * <p>
	 * Argument 1: offset of the particular associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * </p>
	 * <p>
	 * Stack before: index-into-array item ...<br/>
	 * Stack after: item ...
	 * </p>
	 */
	public static final int _ASSIGN_ARRAY_ = 274;	// x2, x1 -> 0
	/**
	 * Assigns the top-of-stack to $0. The contents of the stack are unaffected.
	 * Upon assignment, individual field variables are recalculated.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: x ...
	 * </p>
	 */
	public static final int _ASSIGN_AS_INPUT_ = 275;	// x -> 0
	/**
	 * Assigns an item as a particular input field; the field number can be 0.
	 * Upon assignment, associating input fields are affected. For example, if
	 * the following assignment were made:
	 * <blockquote>
	 * <pre>
	 * $3 = "hi"
	 * </pre>
	 * </blockquote>
	 * $0 would be recalculated. Likewise, if the following assignment were made:
	 * <blockquote>
	 * <pre>
	 * $0 = "hello there"
	 * </pre>
	 * </blockquote>
	 * $1, $2, ... would be recalculated.
	 * <p>
	 * Stack before: field-num x ...<br/>
	 * Stack after: x ...
	 * </p>
	 */
	public static final int _ASSIGN_AS_INPUT_FIELD_ = 276;	// x, y -> x
	/**
	 * Obtains an item from the variable manager and push it onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * </p>
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x ...
	 * </p>
	 */
	public static final int _DEREFERENCE_ = 277;	// 0 -> x
	/**
	 * Increase the contents of the variable by an adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * </p>
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x+n ...
	 * </p>
	 */
	public static final int _PLUS_EQ_ = 278;	// x -> x
	/**
	 * Decreases the contents of the variable by an adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * </p>
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x-n ...
	 * </p>
	 */
	public static final int _MINUS_EQ_ = 279;	// x -> x
	/**
	 * Multiplies the contents of the variable by an adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * </p>
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x*n ...
	 * </p>
	 */
	public static final int _MULT_EQ_ = 280;	// x -> x
	/**
	 * Divides the contents of the variable by an adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * </p>
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x/n ...
	 * </p>
	 */
	public static final int _DIV_EQ_ = 281;	// x -> x
	/**
	 * Takes the modules of the contents of the variable by an adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * </p>
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x%n ...
	 * </p>
	 */
	public static final int _MOD_EQ_ = 282;	// x -> x
	/**
	 * Raises the contents of the variable to the power of the adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * </p>
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x^n ...
	 * </p>
	 */
	public static final int _POW_EQ_ = 283;	// x -> x
	/**
	 * Increase the contents of an indexed array by an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * </p>
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x+n ...
	 * </p>
	 */
	public static final int _PLUS_EQ_ARRAY_ = 284;	// x -> x
	/**
	 * Decreases the contents of an indexed array by an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * </p>
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x-n ...
	 * </p>
	 */
	public static final int _MINUS_EQ_ARRAY_ = 285;	// x -> x
	/**
	 * Multiplies the contents of an indexed array by an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * </p>
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x*n ...
	 * </p>
	 */
	public static final int _MULT_EQ_ARRAY_ = 286;	// x -> x
	/**
	 * Divides the contents of an indexed array by an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * </p>
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x/n ...
	 * </p>
	 */
	public static final int _DIV_EQ_ARRAY_ = 287;	// x -> x
	/**
	 * Takes the modulus of the contents of an indexed array by an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * </p>
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x%n ...
	 * </p>
	 */
	public static final int _MOD_EQ_ARRAY_ = 288;	// x -> x
	/**
	 * Raises the contents of an indexed array to the power of an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * </p>
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x^n ...
	 * </p>
	 */
	public static final int _POW_EQ_ARRAY_ = 289;	// x -> x
	/**
	 * Increases the contents of an input field by an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x+n ...
	 * </p>
	 */
	public static final int _PLUS_EQ_INPUT_FIELD_ = 290;	// x1,x2 -> x
	/**
	 * Decreases the contents of an input field by an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x-n ...
	 * </p>
	 */
	public static final int _MINUS_EQ_INPUT_FIELD_ = 291;	// x1,x2 -> x
	/**
	 * Multiplies the contents of an input field by an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x*n ...
	 * </p>
	 */
	public static final int _MULT_EQ_INPUT_FIELD_ = 292;	// x1,x2 -> x
	/**
	 * Divides the contents of an input field by an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x/n ...
	 * </p>
	 */
	public static final int _DIV_EQ_INPUT_FIELD_ = 293;	// x1,x2 -> x
	/**
	 * Takes the modulus of the contents of an input field by an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x%n ...
	 * </p>
	 */
	public static final int _MOD_EQ_INPUT_FIELD_ = 294;	// x1,x2 -> x
	/**
	 * Raises the contents of an input field to the power of an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x^n ...
	 * </p>
	 */
	public static final int _POW_EQ_INPUT_FIELD_ = 295;	// x1,x2 -> x

	/**
	 * Seeds the random number generator. If there are no arguments, the current
	 * time (as a long value) is used as the seed. Otherwise, the top-of-stack is
	 * popped and used as the seed value.
	 * <p>
	 * Argument: # of arguments
	 * </p>
	 * <p>
	 * If # of arguments is 0:
	 * <blockquote>
	 * Stack before: ...<br/>
	 * Stack after: old-seed ...
	 * </blockquote>
	 * else
	 * <blockquote>
	 * Stack before: x ...<br/>
	 * Stack after: old-seed ...
	 * </blockquote>
	 * </p>
	 */
	public static final int _SRAND_ = 296;	// x2, x1 -> x1, x2
	/**
	 * Obtains the next random number from the random number generator
	 * and push it onto the stack.
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: random-number ...
	 * </p>
	 */
	public static final int _RAND_ = 297;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that pops the top-of-stack, removes its fractional part,
	 * if any, and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: (int)x ...
	 * </p>
	 */
	public static final int _INTFUNC_ = 298;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that pops the top-of-stack, takes its square root,
	 * and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: sqrt(x) ...
	 * </p>
	 */
	public static final int _SQRT_ = 299;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that pops the top-of-stack, calls the java.lang.Math.log method
	 * with the top-of-stack as the argument, and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: log(x) ...
	 * </p>
	 */
	public static final int _LOG_ = 300;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that pops the top-of-stack, calls the java.lang.Math.exp method
	 * with the top-of-stack as the argument, and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: exp(x) ...
	 * </p>
	 */
	public static final int _EXP_ = 301;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that pops the top-of-stack, calls the java.lang.Math.sin method
	 * with the top-of-stack as the argument, and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: sin(x) ...
	 * </p>
	 */
	public static final int _SIN_ = 302;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that pops the top-of-stack, calls the java.lang.Math.cos method
	 * with the top-of-stack as the argument, and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: cos(x) ...
	 * </p>
	 */
	public static final int _COS_ = 303;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that pops the first two items off the stack,
	 * calls the java.lang.Math.atan2 method
	 * with these as arguments, and places the result onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: atan2(x1,x2) ...
	 * </p>
	 */
	public static final int _ATAN2_ = 304;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that searches a string as input to a regular expression,
	 * the location of the match is pushed onto the stack.
	 * The RSTART and RLENGTH variables are set as a side effect.
	 * If a match is found, RSTART and function return value are set
	 * to the location of the match and RLENGTH is set to the length
	 * of the substring matched against the regular expression.
	 * If no match is found, RSTART (and return value) is set to
	 * 0 and RLENGTH is set to -1.
	 * <p>
	 * Stack before: string regexp ...<br/>
	 * Stack after: RSTART ...
	 * </p>
	 */
	public static final int _MATCH_ = 305;	// x1, x2 -> x
	/**
	 * Built-in function that locates a substring within a source string
	 * and pushes the location onto the stack. If the substring is
	 * not found, 0 is pushed onto the stack.
	 * <p>
	 * Stack before: string substring ...<br/>
	 * Stack after: location-index ...
	 * </p>
	 */
	public static final int _INDEX_ = 306;	// x1, x2 -> x
	/**
	 * Built-in function that substitutes an occurrence (or all occurrences)
	 * of a string in $0 and replaces it with another.
	 * <p>
	 * Argument: true if global sub, false otherwise.
	 * </p>
	 * <p>
	 * Stack before: regexp replacement-string ...<br/>
	 * Stack after: ...
	 * </p>
	 */
	public static final int _SUB_FOR_DOLLAR_0_ = 307;	// x -> 0
	/**
	 * Built-in function that substitutes an occurrence (or all occurrences)
	 * of a string in a field reference and replaces it with another.
	 * <p>
	 * Argument: true if global sub, false otherwise.
	 * </p>
	 * <p>
	 * Stack before: field-num regexp replacement-string ...<br/>
	 * Stack after: ...
	 * </p>
	 */
	public static final int _SUB_FOR_DOLLAR_REFERENCE_ = 308;	// x -> 0
	/**
	 * Built-in function that substitutes an occurrence (or all occurrences)
	 * of a string in a particular variable and replaces it with another.
	 * <p>
	 * Argument 1: variable offset in variable manager<br/>
	 * Argument 2: is global variable<br/>
	 * Argument 3: is global sub
	 * </p>
	 * <p>
	 * Stack before: regexp replacement-string orig-string ...<br/>
	 * Stack after: ...
	 * </p>
	 */
	public static final int _SUB_FOR_VARIABLE_ = 309;	// x -> 0
	/**
	 * Built-in function that substitutes an occurrence (or all occurrences)
	 * of a string in a particular array cell and replaces it with another.
	 * <p>
	 * Argument 1: array map offset in variable manager<br/>
	 * Argument 2: is global array map<br/>
	 * Argument 3: is global sub
	 * </p>
	 * <p>
	 * Stack before: array-index regexp replacement-string orig-string ...<br/>
	 * Stack after: ...
	 * </p>
	 */
	public static final int _SUB_FOR_ARRAY_REFERENCE_ = 310;	// x -> 0
	/**
	 * Built-in function to split a string by a regexp and put the
	 * components into an array.
	 * <p>
	 * Argument: # of arguments (parameters on stack)
	 * </p>
	 * <p>
	 * If # of arguments is 2:
	 * <blockquote>
	 * Stack before: string array ...<br/>
	 * Stack after: n ...
	 * </blockquote>
	 * else
	 * <blockquote>
	 * Stack before: string array regexp ...<br/>
	 * Stack after: n ...
	 * </blockquote>
	 * </p>
	 */
	public static final int _SPLIT_ = 311;	// x1 -> x2
	/**
	 * Built-in function that pushes a substring of the top-of-stack
	 * onto the stack.
	 * The tuple argument indicates whether to limit the substring
	 * to a particular end position, or to take the substring
	 * up to the end-of-string.
	 * <p>
	 * Argument: # of arguments
	 * </p>
	 * <p>
	 * If # of arguments is 2:
	 * <blockquote>
	 * Stack before: string start-pos ...<br/>
	 * Stack after: substring ...
	 * </blockquote>
	 * else
	 * <blockquote>
	 * Stack before: string start-pos end-pos ...<br/>
	 * Stack after: substring ...
	 * </blockquote>
	 * </p>
	 */
	public static final int _SUBSTR_ = 312;	// x1 -> x2
	/**
	 * Built-in function that converts all the letters in the top-of-stack
	 * to lower case and pushes the result onto the stack.
	 * <p>
	 * Stack before: STRING-ARGUMENT ...<br/>
	 * Stack after: string-argument ...
	 * </p>
	 */
	public static final int _TOLOWER_ = 313;	// x1 -> x2
	/**
	 * Built-in function that converts all the letters in the top-of-stack
	 * to upper case and pushes the result onto the stack.
	 * <p>
	 * Stack before: string-argument ...<br/>
	 * Stack after: STRING-ARGUMENT ...
	 * </p>
	 */
	public static final int _TOUPPER_ = 314;	// x1 -> x2
	/**
	 * Built-in function that executes the top-of-stack as a system command
	 * and pushes the return code onto the stack.
	 * <p>
	 * Stack before: cmd ...<br/>
	 * Stack after: return-code ...
	 * </p>
	 */
	public static final int _SYSTEM_ = 315;	// x1 -> x2

	/**
	 * Swaps the top two elements of the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x2 x1 ...
	 * </p>
	 */
	public static final int _SWAP_ = 316;	// x2, x1 -> x1, x2

	/**
	 * Numerically adds the top two elements of the stack with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1+x2 ...
	 * </p>
	 */
	public static final int _ADD_ = 317;	// x2, x1 -> x1+x2
	/**
	 * Numerically subtracts the top two elements of the stack with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1-x2 ...
	 * </p>
	 */
	public static final int _SUBTRACT_ = 318;	// x2, x1 -> x1-x2
	/**
	 * Numerically multiplies the top two elements of the stack with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1*x2 ...
	 * </p>
	 */
	public static final int _MULTIPLY_ = 319;	// x2, x1 -> x1*x2
	/**
	 * Numerically divides the top two elements of the stack with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1/x2 ...
	 * </p>
	 */
	public static final int _DIVIDE_ = 320;	// x2, x1 -> x1/x2
	/**
	 * Numerically takes the modulus of the top two elements of the stack with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1%x2 ...
	 * </p>
	 */
	public static final int _MOD_ = 321;	// x2, x1 -> x1/x2
	/**
	 * Numerically raises the top element to the power of the next element with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1^x2 ...
	 * </p>
	 */
	public static final int _POW_ = 322;	// x2, x1 -> x1/x2

	/**
	 * Increases the variable reference by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * </p>
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x+1 ...
	 * </p>
	 */
	public static final int _INC_ = 323;	// 0 -> x
	/**
	 * Decreases the variable reference by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * </p>
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x-1 ...
	 * </p>
	 */
	public static final int _DEC_ = 324;	// 0 -> x
	/**
	 * Increases the array element reference by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * </p>
	 * <p>
	 * Stack before: array-idx ...<br/>
	 * Stack after: x+1 ...
	 * </p>
	 */
	public static final int _INC_ARRAY_REF_ = 325;	// x -> x
	/**
	 * Decreases the array element reference by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * </p>
	 * <p>
	 * Stack before: array-idx ...<br/>
	 * Stack after: x-1 ...
	 * </p>
	 */
	public static final int _DEC_ARRAY_REF_ = 326;	// x -> x
	/**
	 * Increases the input field variable by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Stack before: field-idx ...<br/>
	 * Stack after: x+1
	 * </p>
	 */
	public static final int _INC_DOLLAR_REF_ = 327;	// x -> x
	/**
	 * Decreases the input field variable by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Stack before: field-idx ...<br/>
	 * Stack after: x-1
	 * </p>
	 */
	public static final int _DEC_DOLLAR_REF_ = 328;	// x -> x

	/**
	 * Duplicates the top-of-stack on the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: x x ...
	 * </p>
	 */
	public static final int _DUP_ = 329;	// x -> x, x
	/**
	 * Evaluates the logical NOT of the top stack element;
	 * pushes the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: !x ...
	 * </p>
	 */
	public static final int _NOT_ = 330;	// x -> !x
	/**
	 * Evaluates the numerical NEGATION of the top stack element;
	 * pushes the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: -x ...
	 * </p>
	 */
	public static final int _NEGATE_ = 331;	// x -> -x

	/**
	 * Compares the top two stack elements; pushes 1 onto the stack if equal, 0 if not equal.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1==x2
	 * </p>
	 */
	public static final int _CMP_EQ_ = 332;	// x2, x1 -> x1 == x2
	/**
	 * Compares the top two stack elements; pushes 1 onto the stack if x1 &lt; x2, 0 if not equal.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1&lt;x2
	 * </p>
	 */
	public static final int _CMP_LT_ = 333;	// x2, x1 -> x1 < x2
	/**
	 * Compares the top two stack elements; pushes 1 onto the stack if x1 &gt; x2, 0 if not equal.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1&gt;x2
	 * </p>
	 */
	public static final int _CMP_GT_ = 334;	// x2, x1 -> x1 < x2
	/**
	 * Applies a regular expression to the top stack element; pushes 1 if it matches,
	 * 0 if it does not match.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: (x1 ~ /x2/) ...
	 * </p>
	 */
	public static final int _MATCHES_ = 335;	// x2, x1 -> x1 ~ x2

	/**
	 * <strong>Extension:</strong> Pauses the execution thread by N number of seconds.
	 * <p>
	 * Stack before: N ...<br/>
	 * Stack after: ...
	 * </p>
	 */
	public static final int _SLEEP_ = 336;	// x -> 0
	public static final int _DUMP_ = 337;	// x -> 0

	public static final int _DEREF_ARRAY_ = 338;	// x -> x

	// for (x in y) {keyset} support
	/**
	 * Retrieves and pushes a Set of keys from an associative array onto the stack.
	 * The Set is tagged with a KeyList interface.
	 * <p>
	 * Stack before: associative-array ...<br/>
	 * Stack after: key-list-set ...
	 * </p>
	 */
	public static final int _KEYLIST_ = 339;	// 0 -> {keylist}
	/**
	 * Tests whether the KeyList (set) is empty; jumps to the argument
	 * address if empty, steps to the next instruction if not.
	 * <p>
	 * Argument: jump-address-if-empty
	 * </p>
	 * <p>
	 * Stack before: key-list ...<br/>
	 * Stack after: ...
	 * </p>
	 */
	public static final int _IS_EMPTY_KEYLIST_ = 340;	// {keylist} -> 0
	/**
	 * Removes an item from the KeyList (set) and pushes it onto the operand stack.
	 * <p>
	 * Stack before: key-list ...<br/>
	 * Stack after: 1st-item ...
	 * </p>
	 */
	public static final int _GET_FIRST_AND_REMOVE_FROM_KEYLIST_ = 341;	// {keylist} -> x

	// assertions
	/**
	 * Checks whether the top-of-stack is of a particular class type;
	 * if not, an AwkRuntimeException is thrown.
	 * The stack remains unchanged upon a successful check.
	 * <p>
	 * Argument: class-type (i.e., KeyList.class)
	 * </p>
	 * <p>
	 * Stack before: obj ...<br/>
	 * Stack after: obj ...
	 * </p>
	 */
	public static final int _CHECK_CLASS_ = 342;	// {class} -> 0

	// input
	//* Obtain an input string from stdin; push the result onto the stack.
	/**
	 * Push an input field onto the stack.
	 * <p>
	 * Stack before: field-id ...<br/>
	 * Stack after: x ...
	 * </p>
	 */
	public static final int _GET_INPUT_FIELD_ = 343;	// 0 -> x
	/**
	 * Consume next line of input; assigning $0 and recalculating $1, $2, etc.
	 * The input can come from the following sources:
	 * <ul>
	 * <li>stdin</li>
	 * <li>filename arguments</li>
	 * </ul>
	 * The operand stack is unaffected.
	 */
	public static final int _CONSUME_INPUT_ = 344;	// 0 -> 0
	/**
	 * Obtains input from stdin/filename-args and pushes
	 * input line and status code onto the stack.
	 * The input is partitioned into records based on the RS variable
	 * assignment as a regular expression.
	 * <p>
	 * If there is input available, the input string and a return code
	 * of 1 is pushed. If EOF is reached, a blank (null) string ("")
	 * is pushed along with a 0 return code. Upon an IO error,
	 * a blank string and a -1 is pushed onto the operand stack.
	 * </p>
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: input-string return-code ...
	 * </p>
	 */
	public static final int _GETLINE_INPUT_ = 345;	// 0 -> x
	/**
	 * Obtains input from a file and pushes
	 * input line and status code onto the stack.
	 * The input is partitioned into records based on the RS variable
	 * assignment as a regular expression.
	 * <p>
	 * Upon initial execution, the file is opened and the handle
	 * is maintained until it is explicitly closed, or until
	 * the VM exits. Subsequent calls will obtain subsequent
	 * lines (records) of input until no more records are available.
	 * </p>
	 * <p>
	 * If there is input available, the input string and a return code
	 * of 1 is pushed. If EOF is reached, a blank (null) string ("")
	 * is pushed along with a 0 return code. Upon an IO error,
	 * a blank string and a -1 is pushed onto the operand stack.
	 * </p>
	 * <p>
	 * Stack before: filename ...<br/>
	 * Stack after: input-string return-code ...
	 * </p>
	 */
	public static final int _USE_AS_FILE_INPUT_ = 346;	// x1 -> x2
	/**
	 * Obtains input from a command (process) and pushes
	 * input line and status code onto the stack.
	 * The input is partitioned into records based on the RS variable
	 * assignment as a regular expression.
	 * <p>
	 * Upon initial execution, the a process is spawned to execute
	 * the specified command and the process reference
	 * is maintained until it is explicitly closed, or until
	 * the VM exits. Subsequent calls will obtain subsequent
	 * lines (records) of input until no more records are available.
	 * </p>
	 * <p>
	 * If there is input available, the input string and a return code
	 * of 1 is pushed. If EOF is reached, a blank (null) string ("")
	 * is pushed along with a 0 return code. Upon an IO error,
	 * a blank string and a -1 is pushed onto the operand stack.
	 * </p>
	 * <p>
	 * Stack before: command-line ...<br/>
	 * Stack after: input-string return-code ...
	 * </p>
	 */
	public static final int _USE_AS_COMMAND_INPUT_ = 347;	// x1 -> x2

	// variable housekeeping
	/**
	 * Assign the NF variable offset. This is important for the
	 * AVM to set the variables as new input lines are processed.
	 * <p>
	 * The operand stack is unaffected.
	 * </p>
	 */
	public static final int _NF_OFFSET_ = 348;	// 0 -> 0
	/**
	 * Assign the NR variable offset. This is important for the
	 * AVM to increase the record number as new input lines received.
	 * <p>
	 * The operand stack is unaffected.
	 * </p>
	 */
	public static final int _NR_OFFSET_ = 349;	// 0 -> 0
	/**
	 * Assign the FNR variable offset. This is important for the
	 * AVM to increase the "file" record number as new input lines are received.
	 * <p>
	 * The operand stack is unaffected.
	 * </p>
	 */
	public static final int _FNR_OFFSET_ = 350;	// 0 -> 0
	/**
	 * Assign the FS variable offset. This is important for the
	 * AVM to know how to split fields upon incoming records of input.
	 * <p>
	 * The operand stack is unaffected.
	 * </p>
	 */
	public static final int _FS_OFFSET_ = 351;	// 0 -> 0
	/**
	 * Assign the RS variable offset. This is important for the
	 * AVM to know how to create records from the stream(s) of input.
	 * <p>
	 * The operand stack is unaffected.
	 * </p>
	 */
	public static final int _RS_OFFSET_ = 352;	// 0 -> 0
	/**
	 * Assign the OFS variable offset. This is important for the
	 * AVM to use when outputting expressions via PRINT.
	 * <p>
	 * The operand stack is unaffected.
	 * </p>
	 */
	public static final int _OFS_OFFSET_ = 353;	// 0 -> 0
	/**
	 * Assign the RSTART variable offset. The AVM sets this variable while
	 * executing the match() builtin function.
	 * <p>
	 * The operand stack is unaffected.
	 * </p>
	 */
	public static final int _RSTART_OFFSET_ = 354;	// 0 -> 0
	/**
	 * Assign the RLENGTH variable offset. The AVM sets this variable while
	 * executing the match() builtin function.
	 * <p>
	 * The operand stack is unaffected.
	 * </p>
	 */
	public static final int _RLENGTH_OFFSET_ = 355;	// 0 -> 0
	/**
	 * Assign the FILENAME variable offset. The AVM sets this variable while
	 * processing files from the command-line for input.
	 * <p>
	 * The operand stack is unaffected.
	 * </p>
	 */
	public static final int _FILENAME_OFFSET_ = 356;	// 0 -> 0
	/**
	 * Assign the SUBSEP variable offset. The AVM uses this variable while
	 * building an index of a multi-dimensional array.
	 * <p>
	 * The operand stack is unaffected.
	 * </p>
	 */
	public static final int _SUBSEP_OFFSET_ = 357;	// 0 -> 0
	/**
	 * Assign the CONVFMT variable offset. The AVM uses this variable while
	 * converting numbers to strings.
	 * <p>
	 * The operand stack is unaffected.
	 * </p>
	 */
	public static final int _CONVFMT_OFFSET_ = 358;	// 0 -> 0
	/**
	 * Assign the OFMT variable offset. The AVM uses this variable while
	 * converting numbers to strings for printing.
	 * <p>
	 * The operand stack is unaffected.
	 * </p>
	 */
	public static final int _OFMT_OFFSET_ = 359;	// 0 -> 0
	/**
	 * Assign the ENVIRON variable offset. The AVM provides environment
	 * variables through this array.
	 * <p>
	 * The operand stack is unaffected.
	 * </p>
	 */
	public static final int _ENVIRON_OFFSET_ = 360;	// 0 -> 0
	/**
	 * Assign the ARGC variable offset. The AVM provides the number of
	 * arguments via this variable.
	 * <p>
	 * The operand stack is unaffected.
	 * </p>
	 */
	public static final int _ARGC_OFFSET_ = 361;	// 0 -> 0
	/**
	 * Assign the ARGV variable offset. The AVM provides command-line
	 * arguments via this variable.
	 * <p>
	 * The operand stack is unaffected.
	 * </p>
	 */
	public static final int _ARGV_OFFSET_ = 362;	// 0 -> 0

	/**
	 * Apply the RS variable by notifying the partitioning reader that
	 * there is a new regular expression to use when partitioning input
	 * records.
	 * <p>
	 * The stack remains unaffected.
	 * </p>
	 */
	public static final int _APPLY_RS_ = 363;	// 0 -> 0

	public static final int _CALL_FUNCTION_ = 364;	// x1,x2,...,xn -> x
	public static final int _FUNCTION_ = 365;	// x1,x2,...,xn -> x
	public static final int _SET_RETURN_RESULT_ = 366;	// x -> 0
	public static final int _RETURN_FROM_FUNCTION_ = 367;	// 0 -> 0

	public static final int _SET_NUM_GLOBALS_ = 368;	// 0 -> 0

	public static final int _CLOSE_ = 369;	// x -> 0
	public static final int _APPLY_SUBSEP_ = 370;	// x -> 0

	public static final int _DELETE_ARRAY_ELEMENT_ = 371;	// 0 -> 0

	public static final int _SET_EXIT_ADDRESS_ = 372;	// 0 -> 0
	public static final int _SET_WITHIN_END_BLOCKS_ = 373;	// 0 -> 0
	public static final int _EXIT_WITH_CODE_ = 374;	// 0 -> 0

	public static final int _REGEXP_ = 375;		// 0 -> x
	public static final int _REGEXP_PAIR_ = 376;		// 0 -> x

	public static final int _IS_IN_ = 377;		// x,y -> x

	public static final int _CAST_INT_ = 378;		// x -> (int)x
	public static final int _CAST_DOUBLE_ = 379;		// x -> (double)x
	public static final int _CAST_STRING_ = 380;		// x -> (string)x

	public static final int _THIS_ = 381;		// 0 -> (this)

	public static final int _EXTENSION_ = 382;		// x1,x2,...,xn -> x
	public static final int _EXEC_ = 383;		// x1,x2,...,xn -> x

	public static final int _DELETE_ARRAY_ = 384;	// 0 -> 0

	/**
	 * Override add() to populate the line number for each tuple,
	 * rather than polluting all the constructors with this assignment.
	 */
	private java.util.List<Tuple> queue = new ArrayList<Tuple>(100) {

		@Override
		public boolean add(Tuple t) {
			t.setLineNumber(lineno_stack.peek());
			return super.add(t);
		}
	};
	private Set<Address> unresolved_addresses = new HashSet<Address>();
	/**
	 * Needed only for dumping intermediate code to text such that address labels are provided.
	 */
	private Map<Integer, Address> address_indexes = new HashMap<Integer, Address>();
	private Map<String, Integer> address_label_counts = new HashMap<String, Integer>();

	public static String toOpcodeString(int opcode) {
		Class c = AwkTuples.class;
		Field[] fields = c.getDeclaredFields();
		try {
			for (Field field : fields) {
				if ((field.getModifiers() & Modifier.STATIC) > 0 && field.getType() == Integer.TYPE && field.getInt(null) == opcode) {
					return field.getName();
				}
			}
		} catch (IllegalAccessException iac) {
			LOG.error("Failed to create OP-Code string", iac);
			return "[" + opcode + ": " + iac + "]";
		}
		return "{" + opcode + "}";
	}

	public void pop() {
		queue.add(new Tuple(_POP_));
	}

	public void push(Object o) {
		assert (o instanceof String) || (o instanceof Integer) || (o instanceof Double); //  || (o instanceof Pattern); //  || (o instanceof PatternPair);
		if (o instanceof String) {
			queue.add(new Tuple(_PUSH_, o.toString()));
		} else if (o instanceof Integer) {
			queue.add(new Tuple(_PUSH_, (Integer) o));
		} else if (o instanceof Double) {
			queue.add(new Tuple(_PUSH_, (Double) o));
		} else {
			assert false : "Invalid type for " + o + ", "+o.getClass();
		}
	}

	public void ifFalse(Address address) {
		queue.add(new Tuple(_IFFALSE_, address));
	}

	public void toNumber() {
		queue.add(new Tuple(_TO_NUMBER_));
	}

	public void ifTrue(Address address) {
		queue.add(new Tuple(_IFTRUE_, address));
	}

	public void gotoAddress(Address address) {
		queue.add(new Tuple(_GOTO_, address));
	}

	public Address createAddress(String label) {
		Integer I = address_label_counts.get(label);
		if (I == null) {
			I = 0;
		} else {
			//I = new Integer(I.intValue()+1);
			I = I + 1;
		}
		address_label_counts.put(label, I);
		Address address = new AddressImpl(label + "_" + I);
		unresolved_addresses.add(address);
		return address;
	}

	public AwkTuples address(Address address) {
		if (unresolved_addresses.contains(address)) {
			unresolved_addresses.remove(address);
			address.assignIndex(queue.size());
			address_indexes.put(queue.size(), address);
			return this;
		}
		throw new Error(address.toString() + " is already resolved, or unresolved from another scope.");
	}

	public void nop() {
		queue.add(new Tuple(_NOP_));
	}

	public void print(int num_exprs) {
		queue.add(new Tuple(_PRINT_, num_exprs));
	}

	public void printToFile(int num_exprs, boolean append) {
		queue.add(new Tuple(_PRINT_TO_FILE_, num_exprs, append));
	}

	public void printToPipe(int num_exprs) {
		queue.add(new Tuple(_PRINT_TO_PIPE_, num_exprs));
	}

	public void printf(int num_exprs) {
		queue.add(new Tuple(_PRINTF_, num_exprs));
	}

	public void printfToFile(int num_exprs, boolean append) {
		queue.add(new Tuple(_PRINTF_TO_FILE_, num_exprs, append));
	}

	public void printfToPipe(int num_exprs) {
		queue.add(new Tuple(_PRINTF_TO_PIPE_, num_exprs));
	}

	public void sprintf(int num_exprs) {
		queue.add(new Tuple(_SPRINTF_, num_exprs));
	}

	public void length(int num_exprs) {
		queue.add(new Tuple(_LENGTH_, num_exprs));
	}

	public void concat() {
		queue.add(new Tuple(_CONCAT_));
	}

	public void assign(int offset, boolean is_global) {
		queue.add(new Tuple(_ASSIGN_, offset, is_global));
	}

	public void assignArray(int offset, boolean is_global) {
		queue.add(new Tuple(_ASSIGN_ARRAY_, offset, is_global));
	}

	public void assignAsInput() {
		queue.add(new Tuple(_ASSIGN_AS_INPUT_));
	}

	public void assignAsInputField() {
		queue.add(new Tuple(_ASSIGN_AS_INPUT_FIELD_));
	}

	public void dereference(int offset, boolean is_array, boolean is_global) {
		queue.add(new Tuple(_DEREFERENCE_, offset, is_array, is_global));
	}

	public void plusEq(int offset, boolean is_global) {
		queue.add(new Tuple(_PLUS_EQ_, offset, is_global));
	}

	public void minusEq(int offset, boolean is_global) {
		queue.add(new Tuple(_MINUS_EQ_, offset, is_global));
	}

	public void multEq(int offset, boolean is_global) {
		queue.add(new Tuple(_MULT_EQ_, offset, is_global));
	}

	public void divEq(int offset, boolean is_global) {
		queue.add(new Tuple(_DIV_EQ_, offset, is_global));
	}

	public void modEq(int offset, boolean is_global) {
		queue.add(new Tuple(_MOD_EQ_, offset, is_global));
	}

	public void powEq(int offset, boolean is_global) {
		queue.add(new Tuple(_POW_EQ_, offset, is_global));
	}

	public void plusEqArray(int offset, boolean is_global) {
		queue.add(new Tuple(_PLUS_EQ_ARRAY_, offset, is_global));
	}

	public void minusEqArray(int offset, boolean is_global) {
		queue.add(new Tuple(_MINUS_EQ_ARRAY_, offset, is_global));
	}

	public void multEqArray(int offset, boolean is_global) {
		queue.add(new Tuple(_MULT_EQ_ARRAY_, offset, is_global));
	}

	public void divEqArray(int offset, boolean is_global) {
		queue.add(new Tuple(_DIV_EQ_ARRAY_, offset, is_global));
	}

	public void modEqArray(int offset, boolean is_global) {
		queue.add(new Tuple(_MOD_EQ_ARRAY_, offset, is_global));
	}

	public void powEqArray(int offset, boolean is_global) {
		queue.add(new Tuple(_POW_EQ_ARRAY_, offset, is_global));
	}

	public void plusEqInputField() {
		queue.add(new Tuple(_PLUS_EQ_INPUT_FIELD_));
	}

	public void minusEqInputField() {
		queue.add(new Tuple(_MINUS_EQ_INPUT_FIELD_));
	}

	public void multEqInputField() {
		queue.add(new Tuple(_MULT_EQ_INPUT_FIELD_));
	}

	public void divEqInputField() {
		queue.add(new Tuple(_DIV_EQ_INPUT_FIELD_));
	}

	public void modEqInputField() {
		queue.add(new Tuple(_MOD_EQ_INPUT_FIELD_));
	}

	public void powEqInputField() {
		queue.add(new Tuple(_POW_EQ_INPUT_FIELD_));
	}

	public void srand(int num) {
		queue.add(new Tuple(_SRAND_, num));
	}

	public void rand() {
		queue.add(new Tuple(_RAND_));
	}

	public void intFunc() {
		queue.add(new Tuple(_INTFUNC_));
	}

	public void sqrt() {
		queue.add(new Tuple(_SQRT_));
	}

	public void log() {
		queue.add(new Tuple(_LOG_));
	}

	public void exp() {
		queue.add(new Tuple(_EXP_));
	}

	public void sin() {
		queue.add(new Tuple(_SIN_));
	}

	public void cos() {
		queue.add(new Tuple(_COS_));
	}

	public void atan2() {
		queue.add(new Tuple(_ATAN2_));
	}

	public void match() {
		queue.add(new Tuple(_MATCH_));
	}

	public void index() {
		queue.add(new Tuple(_INDEX_));
	}

	public void subForDollar0(boolean is_gsub) {
		queue.add(new Tuple(_SUB_FOR_DOLLAR_0_, is_gsub));
	}

	public void subForDollarReference(boolean is_gsub) {
		queue.add(new Tuple(_SUB_FOR_DOLLAR_REFERENCE_, is_gsub));
	}

	public void subForVariable(int offset, boolean is_global, boolean is_gsub) {
		queue.add(new Tuple(_SUB_FOR_VARIABLE_, offset, is_global, is_gsub));
	}

	public void subForArrayReference(int offset, boolean is_global, boolean is_gsub) {
		queue.add(new Tuple(_SUB_FOR_ARRAY_REFERENCE_, offset, is_global, is_gsub));
	}

	public void split(int numargs) {
		queue.add(new Tuple(_SPLIT_, numargs));
	}

	public void substr(int numargs) {
		queue.add(new Tuple(_SUBSTR_, numargs));
	}

	public void tolower() {
		queue.add(new Tuple(_TOLOWER_));
	}

	public void toupper() {
		queue.add(new Tuple(_TOUPPER_));
	}

	public void system() {
		queue.add(new Tuple(_SYSTEM_));
	}

	public void exec() {
		queue.add(new Tuple(_EXEC_));
	}

	public void swap() {
		queue.add(new Tuple(_SWAP_));
	}

	public void add() {
		queue.add(new Tuple(_ADD_));
	}

	public void subtract() {
		queue.add(new Tuple(_SUBTRACT_));
	}

	public void multiply() {
		queue.add(new Tuple(_MULTIPLY_));
	}

	public void divide() {
		queue.add(new Tuple(_DIVIDE_));
	}

	public void mod() {
		queue.add(new Tuple(_MOD_));
	}

	public void pow() {
		queue.add(new Tuple(_POW_));
	}

	public void inc(int offset, boolean is_global) {
		queue.add(new Tuple(_INC_, offset, is_global));
	}

	public void dec(int offset, boolean is_global) {
		queue.add(new Tuple(_DEC_, offset, is_global));
	}

	public void incArrayRef(int offset, boolean is_global) {
		queue.add(new Tuple(_INC_ARRAY_REF_, offset, is_global));
	}

	public void decArrayRef(int offset, boolean is_global) {
		queue.add(new Tuple(_DEC_ARRAY_REF_, offset, is_global));
	}

	public void incDollarRef() {
		queue.add(new Tuple(_INC_DOLLAR_REF_));
	}

	public void decDollarRef() {
		queue.add(new Tuple(_DEC_DOLLAR_REF_));
	}

	public void dup() {
		queue.add(new Tuple(_DUP_));
	}

	public void not() {
		queue.add(new Tuple(_NOT_));
	}

	public void negate() {
		queue.add(new Tuple(_NEGATE_));
	}

	public void cmpEq() {
		queue.add(new Tuple(_CMP_EQ_));
	}

	public void cmpLt() {
		queue.add(new Tuple(_CMP_LT_));
	}

	public void cmpGt() {
		queue.add(new Tuple(_CMP_GT_));
	}

	public void matches() {
		queue.add(new Tuple(_MATCHES_));
	}

	public void sleep(int num_args) {
		queue.add(new Tuple(_SLEEP_, num_args));
	}

	public void dump(int num_args) {
		queue.add(new Tuple(_DUMP_, num_args));
	}

	public void dereferenceArray() {
		queue.add(new Tuple(_DEREF_ARRAY_));
	}

	public void keylist() {
		queue.add(new Tuple(_KEYLIST_));
	}

	public void isEmptyList(Address address) {
		queue.add(new Tuple(_IS_EMPTY_KEYLIST_, address));
	}

	public void getFirstAndRemoveFromList() {
		queue.add(new Tuple(_GET_FIRST_AND_REMOVE_FROM_KEYLIST_));
	}

	public boolean checkClass(Class cls) {
		queue.add(new Tuple(_CHECK_CLASS_, cls));
		return true;
	}

	public void getInputField() {
		queue.add(new Tuple(_GET_INPUT_FIELD_));
	}

	public void consumeInput(Address address) {
		queue.add(new Tuple(_CONSUME_INPUT_, address));
	}

	public void getlineInput() {
		queue.add(new Tuple(_GETLINE_INPUT_));
	}

	public void useAsFileInput() {
		queue.add(new Tuple(_USE_AS_FILE_INPUT_));
	}

	public void useAsCommandInput() {
		queue.add(new Tuple(_USE_AS_COMMAND_INPUT_));
	}

	public void nfOffset(int offset) {
		queue.add(new Tuple(_NF_OFFSET_, offset));
	}

	public void nrOffset(int offset) {
		queue.add(new Tuple(_NR_OFFSET_, offset));
	}

	public void fnrOffset(int offset) {
		queue.add(new Tuple(_FNR_OFFSET_, offset));
	}

	public void fsOffset(int offset) {
		queue.add(new Tuple(_FS_OFFSET_, offset));
	}

	public void rsOffset(int offset) {
		queue.add(new Tuple(_RS_OFFSET_, offset));
	}

	public void ofsOffset(int offset) {
		queue.add(new Tuple(_OFS_OFFSET_, offset));
	}

	public void rstartOffset(int offset) {
		queue.add(new Tuple(_RSTART_OFFSET_, offset));
	}

	public void rlengthOffset(int offset) {
		queue.add(new Tuple(_RLENGTH_OFFSET_, offset));
	}

	public void filenameOffset(int offset) {
		queue.add(new Tuple(_FILENAME_OFFSET_, offset));
	}

	public void subsepOffset(int offset) {
		queue.add(new Tuple(_SUBSEP_OFFSET_, offset));
	}

	public void convfmtOffset(int offset) {
		queue.add(new Tuple(_CONVFMT_OFFSET_, offset));
	}

	public void ofmtOffset(int offset) {
		queue.add(new Tuple(_OFMT_OFFSET_, offset));
	}

	public void environOffset(int offset) {
		queue.add(new Tuple(_ENVIRON_OFFSET_, offset));
	}

	public void argcOffset(int offset) {
		queue.add(new Tuple(_ARGC_OFFSET_, offset));
	}

	public void argvOffset(int offset) {
		queue.add(new Tuple(_ARGV_OFFSET_, offset));
	}

	public void applyRS() {
		queue.add(new Tuple(_APPLY_RS_));
	}

	public void function(String func_name, int num_formal_params) {
		queue.add(new Tuple(_FUNCTION_, func_name, num_formal_params));
	}
	//public void callFunction(Address addr, String func_name, int num_formal_params, int num_actual_params) { queue.add(new Tuple(_CALL_FUNCTION_, addr, func_name, num_formal_params, num_actual_params)); }

	public void callFunction(HasFunctionAddress has_func_addr, String func_name, int num_formal_params, int num_actual_params) {
		queue.add(new Tuple(_CALL_FUNCTION_, has_func_addr, func_name, num_formal_params, num_actual_params));
	}

	public void setReturnResult() {
		queue.add(new Tuple(_SET_RETURN_RESULT_));
	}

	public void returnFromFunction() {
		queue.add(new Tuple(_RETURN_FROM_FUNCTION_));
	}

	public void setNumGlobals(int num_globals) {
		queue.add(new Tuple(_SET_NUM_GLOBALS_, num_globals));
	}

	public void close() {
		queue.add(new Tuple(_CLOSE_));
	}

	public void applySubsep(int count) {
		queue.add(new Tuple(_APPLY_SUBSEP_, count));
	}

	public void deleteArrayElement(int offset, boolean is_global) {
		queue.add(new Tuple(_DELETE_ARRAY_ELEMENT_, offset, is_global));
	}

	public void deleteArray(int offset, boolean is_global) {
		queue.add(new Tuple(_DELETE_ARRAY_, offset, is_global));
	}

	public void setExitAddress(Address addr) {
		queue.add(new Tuple(_SET_EXIT_ADDRESS_, addr));
	}

	public void setWithinEndBlocks(boolean b) {
		queue.add(new Tuple(_SET_WITHIN_END_BLOCKS_, b));
	}

	public void exitWithCode() {
		queue.add(new Tuple(_EXIT_WITH_CODE_));
	}

	public void regexp(String regexp_str) {
		queue.add(new Tuple(_REGEXP_, regexp_str));
	}

	public void regexpPair() {
		queue.add(new Tuple(_REGEXP_PAIR_));
	}

	public void isIn() {
		queue.add(new Tuple(_IS_IN_));
	}

	public void castInt() {
		queue.add(new Tuple(_CAST_INT_));
	}

	public void castDouble() {
		queue.add(new Tuple(_CAST_DOUBLE_));
	}

	public void castString() {
		queue.add(new Tuple(_CAST_STRING_));
	}

	public void scriptThis() {
		queue.add(new Tuple(_THIS_));
	}

	public void extension(String extension_keyword, int param_count, boolean is_initial) {
		queue.add(new Tuple(_EXTENSION_, extension_keyword, param_count, is_initial));
	}

	public void dump(PrintStream ps) {
		ps.println("(" + version_manager + ")");
		ps.println();
		for (int i = 0; i < queue.size(); i++) {
			Address address = address_indexes.get(i);
			if (address == null) {
				ps.println(i + " : " + queue.get(i));
			} else {
				ps.println(i + " : [" + address + "] : " + queue.get(i));
			}
		}
	}

	public Position top() {
		return new PositionImpl();
	}

	/**
	 * Executed after all tuples are entered in the queue.
	 * Its main functions are:
	 * <ul>
	 * <li>Assign queue.next to the next element in the queue.</li>
	 * <li>Calls touch(...) per Tuple so that addresses can be normalized/assigned/allocated</li>
	 * properly.
	 * </ul>
	 */
	public void postProcess() {
		assert queue.isEmpty() || !queue.get(0).hasNext() : "postProcess() already executed";
		// allocate nexts
		for (int i = 0; i < queue.size() - 1; i++) {
			queue.get(i).setNext(queue.get(i + 1));
		}
		// touch per element
		for (Tuple tuple : queue) {
			tuple.touch(queue);
		}
	}
	private Map<String, Integer> global_var_offset_map = new HashMap<String, Integer>();
	private Map<String, Boolean> global_var_aarray_map = new HashMap<String, Boolean>();
	private Set<String> function_names = null;

	/**
	 * Accept a {variable_name -&gt; offset} mapping such that global variables can be
	 * assigned while processing name=value and filename command-line arguments.
	 */
	public void addGlobalVariableNameToOffsetMapping(String varname, int offset, boolean is_aarray) {
		if (global_var_offset_map.get(varname) != null) {
			assert global_var_aarray_map.get(varname) != null;
			return;
		}
		global_var_offset_map.put(varname, offset);
		global_var_aarray_map.put(varname, is_aarray);
	}

	/**
	 * Accept a set of function names from the parser. This is
	 * useful for invalidating name=value assignments from the
	 * command line parameters, either via -v arguments or
	 * passed into ARGV.
	 *
	 * @param function_names A set of function name strings.
	 */
	public void setFunctionNameSet(Set<String> function_names) {
		// setFunctionNameSet is called with a keySet from
		// a HashMap as a parameter, which is NOT
		// Serializable. Creating a new HashSet around
		// the parameter resolves the issue.
		// Otherwise, attempting to serialize this
		// object results in a NotSerializableEexception
		// being thrown because of function_names field
		// being a keyset from a HashMap.
		this.function_names = new HashSet<String>(function_names);
	}

	public Map<String, Integer> getGlobalVariableOffsetMap() {
		return global_var_offset_map;
	}

	public Map<String, Boolean> getGlobalVariableAarrayMap() {
		return global_var_aarray_map;
	}

	public Set<String> getFunctionNameSet() {
		assert function_names != null;
		return function_names;
	}

	// linenumber stack ...

	private MyStack<Integer> lineno_stack = new LinkedListStackImpl<Integer>();

	/**
	 * Push the current line number onto the line number stack.
	 * This is called by the parser to keep track of the
	 * current source line number. Keeping track of line
	 * numbers this way allows the runtime to report
	 * more meaningful errors by providing source line numbers
	 * within error reports.
	 *
	 * @param lineno The current source line number.
	 */
	public void pushSourceLineNumber(int lineno) {
		lineno_stack.push(lineno);
	}

	public void popSourceLineNumber(int lineno) {
		int tos = lineno_stack.pop();
		assert (lineno == tos);
	}


	/**
	 * Intermediate file version manager. Ensures the AwkTuples
	 * class version matches the version supported by the interpreter/compiler.
	 */
	private static class VersionManager implements Serializable {

		/**
		 * Class version number.
		 * This number is modified by the developer.
		 * It should be modified only if modifications
		 * to pre-existing tuple arguments are modified,
		 * or if instruction codes are removed.
		 * <p>
		 * <ul>
		 * <li> Version 1 - Initial release.</li>
		 * <li> Version 2 - Changes to support compilation to JVM.</li>
		 * </ul>
		 * </p>
		 */
		private static final int CLASS_VERSION = 2;

		/**
		 * Instance version number.
		 * The only way it could be different from the
		 * class version is only during deserialization.
		 */
		private int INSTANCE_VERSION = CLASS_VERSION;

		/**
		 * Upon deserialization, ensures the instance
		 * version matches the class version.
		 * @throws IOException upon an IO error
		 * @throws ClassNotFoundException if the class
		 *	that is deserialized cannot be found
		 * @throws InvalidClassException if the
		 *	instance version does not match
		 *	the class version
		 */
		private void readObject(ObjectInputStream ois)
				throws IOException, ClassNotFoundException
		{
			INSTANCE_VERSION = ois.readInt();
			if (INSTANCE_VERSION != CLASS_VERSION) {
				throw new InvalidClassException("Invalid intermeidate file format (instance version " + INSTANCE_VERSION + " != class version " + CLASS_VERSION + ")");
			}
		}

		private void writeObject(ObjectOutputStream oos)
				throws IOException
		{
			oos.writeInt(INSTANCE_VERSION);
		}

		@Override
		public String toString() {
			return "intermediate file format version = " + INSTANCE_VERSION;
		}
	}
}
