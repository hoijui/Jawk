
package org.jawk.ext;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.jawk.NotImplementedError;
import org.jawk.jrt.AssocArray;
import org.jawk.jrt.AwkRuntimeException;
import org.jawk.jrt.BlockObject;
import org.jawk.jrt.IllegalAwkArgumentException;
import org.jawk.jrt.JRT;
import org.jawk.jrt.VariableManager;

/**
 * Extensions which make developing in Jawk and
 * interfacing other extensions with Jawk
 * much easier.
 * <p>
 * The extension functions which are available are as follows:
 * <ul>
 * <li><strong>Array</strong> - <code><font size=+1>Array(array,1,3,5,7,9)</font></code><br>
 * Inserts elements into an associative array whose keys
 * are ordered non-negative integers, and the values
 * are the arguments themselves. The first argument is
 * the associative array itself.
 * <li><strong>Map/HashMap/TreeMap/LinkedMap</strong> - <code><font size=+1>Map(map,k1,v1,k2,v2,...,kN,vN)</font></code>,
 * or <code><font size=+1>Map(k1,v1,k2,v2,...,kN,vN)</font></code>.<br>
 * Build an associative array with its keys/values as
 * parameters. The odd parameter count version takes
 * the map name as the first parameter, while the even
 * parameter count version returns an anonymous associative
 * array for the purposes of providing a map by function
 * call parameter.<br>
 * Map/HashMap configures the associative array as a
 * hash map, TreeMap as an ordered map, and LinkedMap
 * as a map which traverses the key set in order of
 * insertion.
 * <li><strong>MapUnion</strong> - <code><font size=+1>MapUnion(map,k1,v1,k2,v2,...,kN,vN)</font></code><br>
 * Similar to Map, except that map is not cleared prior
 * to populating it with key/value pairs from the
 * parameter list.
 * <li><strong>MapCopy</strong> - <code><font size=+1>cnt = MapCopy(aa_target, aa_source)</font></code><br>
 * Clears the target associative array and copies the
 * contents of the source associative array to the
 * target associative array.
 * <li><strong>TypeOf</strong> - <code><font size=+1>typestring = TypeOf(item)</font></code><br>
 * Returns one of the following depending on the argument:
 * 	<ul>
 * 	<li>"String"
 * 	<li>"Integer"
 * 	<li>"AssocArray"
 * 	<li>"Reference" (see below)
 * 	</ul>
 * <li><strong>String</strong> - <code><font size=+1>str = String(3)</font></code><br>
 * Converts its argument to a String.
 * Similar to the _STRING extension, but provided
 * for completeness/normalization.
 * <li><strong>Double</strong> - <code><font size=+1>dbl = Double(3)</font></code><br>
 * Converts its argument ot a Double.
 * Similar to the _DOUBLE extension, but provided
 * for completeness/normalization.
 * <li><strong>Halt</strong> - <code><font size=+1>Halt()</font></code><br>
 * Similar to exit(), except that END blocks are
 * not executed if Halt() called before END
 * block processing.
 * <li><strong>Timeout</strong> - <code><font size=+1>r = Timeout(300)</font></code><br>
 * A blocking function which waits N milliseconds
 * before unblocking (continuing). This is useful in scripts which
 * employ blocking, but occasionally needs to break out
 * of the block to perform some calculation, polling, etc.
 * <li><strong>Throw</strong> - <code><font size=+1>Throw("this is an awkruntimeexception")</font></code><br>
 * Throws an AwkRuntimeException from within the script.
 * <li><strong>Version</strong> - <code><font size=+1>print Version(aa)</font></code><br>
 * Prints the version of the Java class which represents the parameter.
 * <li><strong>Date</strong> - <code><font size=+1>str = Date()</font></code><br>
 * Similar to the Java equivalent : str = new Date().toString();
 * <li><strong>FileExists</strong> - <code><font size=+1>b = FileExists("/a/b/c")</font></code><br>
 * Returns 0 if the file doesn't exist, 1 otherwise.
 * <li><strong>NewRef[erence]/Dereference/DeRef/Unreference/UnRef/etc.</strong> -
 * Reference Management Functions.</font></code><br>
 * These are described in detail below.
 * </ul>
 * </p>
 * <p>
 * <h1>Reference Management</h1>
 * AWK's memory model provides only 4 types of variables
 * for use within AWK scripts:
 * <ul>
 * <li>Integer
 * <li>Double
 * <li>String
 * <li>Associative Array
 * </ul>
 * Variables can hold any of these types. However, unlike
 * for scalar types (integer/double/string), AWK applies
 * the following restrictions with regard to associative
 * arrays:
 * <ul>
 * <li>Associative array assignments (i.e., assocarray1 = associarray2)
 *	are prohibited.
 * <li>Functions cannot return associative arrays.
 * </ul>
 * These restrictions, while sufficient for AWK, are detrimental
 * to extensions because associative arrays are excellent vehicles
 * for configuration and return values for user extensions.
 * Plus, associative arrays can be overriden, which can be used
 * to enforce type safety within user extensions. Unfortunately, the
 * memory model restrictions make using associative arrays in this
 * capacity very difficult.
 * </p>
 * <p>
 * We attempt to alleviate these difficulties by adding references
 * to Jawk via the CoreExtension module.
 * References convert associative arrays into
 * unique strings called <strong>reference handles</strong>.
 * Since reference handles are strings, they can be
 * assigned and returned via AWK functions without restriction.
 * And, reference handles are then used by other reference extension
 * functions to perform common associative array operations, such as
 * associative array cell lookup and assignment, key existence
 * check, and key iteration.
 * </p>
 * <p>
 * The reference model functions are explained below:
 * <ul>
 * <li><strong>NewRef / NewReference</strong> - <code><font size=+1>handle = NewRef(assocarray)</font></code><br>
 * Store map into reference cache. Return the unique string handle
 * for this associative array.
 * <li><strong>DeRef / Dereference</strong> - <code><font size=+1>val = DeRef(handle, key)</font></code><br>
 * Return the cell value of the associative array referenced by the key.
 * In other words:
 * <blockquote><pre>
 * return assocarray[key]</pre></blockquote>
 * <li><strong>UnRef / Unreference</strong> - <code><font size=+1>UnRef(handle)</font></code><br>
 * Eliminate the reference occupied by the reference cache.
 * <li><strong>InRef</strong> - <code><font size=+1>while(key = InRef(handle)) ...</font></code><br>
 * Iterate through the key-set of the associative array
 * referred to by handle in the reference cache.
 * This is similar to:
 * <blockquote><pre>
 * for (key in assocarray)
 * 	...</pre></blockquote>
 * where <code>assocarray</code> is the associative array referred to by
 * handle in the reference cache.
 * <br>
 * <strong>Warning:</strong> unlike the IN keyword, InRef
 * will maintain state regardless of scope. That is,
 * if one were to break; out of the while loop above,
 * the next call to InRef() will be the next anticipated
 * element of the <code>assoc</code> array.
 * <li><strong>IsInRef</strong> - <code><font size=+1>b = IsInRef(handle, key)</font></code><br>
 * Checks whether the associative array in the reference cache
 * contains the key. This is similar to:
 * <blockquote><pre>
 * if (key in assocarray)
 *	...</pre></blockquote>
 * where <code>assocarray</code> is the associative array referred to by
 * handle in the reference cache.
 * <li><strong>DumpRefs</strong> - <code><font size=+1>DumpRefs()</font></code><br>
 * Dumps the reference cache to stdout.
 * </ul>
 * </p>
 */
public class CoreExtension extends AbstractExtension implements JawkExtension {

	private static CoreExtension instance = null;
	private static final Object INSTANCE_LOCK = new Object();

	public CoreExtension() {
		synchronized (INSTANCE_LOCK) {
			if (instance == null) {
				instance = this;
			} else {
				System.err.println("Warning : Multiple CoreExtension instances in this VM. Using original instance.");
			}
		}
	}

	@Override
	public String getExtensionName() {
		return "Core Extension";
	}

	@Override
	public String[] extensionKeywords() {
		return new String[] {
				"Array",	// i.e. Array(array,1,3,5,7,9,11)
				"Map",		// i.e. Map(assocarray, "hi", "there", "testing", 3, 5, Map("item1", "item2", "i3", 4))
				"HashMap",	// i.e. HashMap(assocarray, "hi", "there", "testing", 3, 5, Map("item1", "item2", "i3", 4))
				"TreeMap",	// i.e. TreeMap(assocarray, "hi", "there", "testing", 3, 5, Map("item1", "item2", "i3", 4))
				"LinkedMap",	// i.e. LinkedMap(assocarray, "hi", "there", "testing", 3, 5, Map("item1", "item2", "i3", 4))
				"MapUnion",	// i.e. MapUnion(assocarray, "hi", "there", "testing", 3, 5, Map("item1", "item2", "i3", 4))
				"MapCopy",	// i.e. cnt = MapCopy(aa_target, aa_source)
				"TypeOf",	// i.e. typestring = TypeOf(item)
				"String",	// i.e. str = String(3)
				"Double",	// i.e. dbl = Double(3)
				"Halt",		// i.e. Halt()
				"Dereference",	// i.e. f(Dereference(r1))
				"DeRef",	// i.e. 	(see above, but replace Dereference with DeRef)
				"NewReference",	// i.e. ref = NewReference(Map("hi","there"))
				"NewRef",	// i.e. 	(see above, but replace Reference with Ref)
				"Unreference",	// i.e. b = Unreference(ref)
				"UnRef",	// i.e. 	(see above, but replace Unreference with UnRef)
				"InRef",	// i.e. while(k = InRef(r2)) [ same as for(k in assocarr) ]
				"IsInRef",	// i.e. if (IsInRef(r1, "key")) [ same as if("key" in assocarr) ]
				"DumpRefs",	// i.e. DumpRefs()
				"Timeout",	// i.e. r = Timeout(300)
				"Throw",	// i.e. Throw("this is an awkruntimeexception")
				"Version",	// i.e. print Version(aa)

				"Date",		// i.e. str = Date()
				"FileExists",	// i.e. b = FileExists("/a/b/c")
				};
	}

	@Override
	public int[] getAssocArrayParameterPositions(String extension_keyword, int num_args) {
		if ((      extension_keyword.equals("Map")
				|| extension_keyword.equals("HashMap")
				|| extension_keyword.equals("LinkedMap")
				|| extension_keyword.equals("TreeMap")) && ((num_args % 2) == 1))
		{
			// first argument of a *Map() function
			// must be an associative array
			return new int[] {0};
		} else if (extension_keyword.equals("Array")) {
			// first argument of Array must be
			// an associative array
			return new int[] {0};
		} else if (extension_keyword.equals("NewReference")
				|| extension_keyword.equals("NewRef"))
		{
			if (num_args == 1) {
				return new int[] {0};
			} else {
				return super.getAssocArrayParameterPositions(extension_keyword, num_args);
			}
		} else {
			return super.getAssocArrayParameterPositions(extension_keyword, num_args);
		}
	}

	@Override
	public Object invoke(String keyword, Object[] args) {
		if (false) {
		} else if (keyword.equals("Map") || keyword.equals("HashMap")) {
			return map(args, vm, AssocArray.MT_HASH);
		} else if (keyword.equals("LinkedMap")) {
			return map(args, vm, AssocArray.MT_LINKED);
		} else if (keyword.equals("TreeMap")) {
			return map(args, vm, AssocArray.MT_TREE);
		} else if (keyword.equals("MapUnion")) {
			return mapunion(args, vm, AssocArray.MT_LINKED);
		} else if (keyword.equals("MapCopy")) {
			checkNumArgs(args, 2);
			return mapcopy(args);
		} else if (keyword.equals("Array")) {
			return array(args, vm);
		} else if (keyword.equals("TypeOf")) {
			checkNumArgs(args, 1);
			return typeof(args[0], vm);
		} else if (keyword.equals("String")) {
			checkNumArgs(args, 1);
			return tostring(args[0], vm);
		} else if (keyword.equals("Double")) {
			checkNumArgs(args, 1);
			return todouble(args[0], vm);
		} else if (keyword.equals("Halt")) {
			if (args.length == 0) {
				Runtime.getRuntime().halt(0);
			} else if (args.length == 1) {
				Runtime.getRuntime().halt((int) JRT.toDouble(args[0]));
			} else {
				throw new IllegalAwkArgumentException(keyword + " requires 0 or 1 argument, not " + args.length);
			}
		} else if (keyword.equals("NewReference") || keyword.equals("NewRef")) {
			if (args.length == 1) {
				return newreference(args[0]);
			} else if (args.length == 3) {
				return newreference(toAwkString(args[0]), args[1], args[2]);
			} else {
				throw new IllegalAwkArgumentException(keyword + " requires 1 or 3 arguments, not " + args.length);
			}
		} else if (keyword.equals("Dereference") || keyword.equals("DeRef")) {
			if (args.length == 1) {
				return resolve(dereference(args[0], vm), vm);
			} else if (args.length == 2) {
				return resolve(dereference(toAwkString(args[0]), args[1], vm), vm);
			} else {
				throw new IllegalAwkArgumentException(keyword + " requires 1 or 2 arguments, not " + args.length);
			}
		} else if (keyword.equals("Unreference") || keyword.equals("UnRef")) {
			checkNumArgs(args, 1);
			return unreference(args[0], vm);
		} else if (keyword.equals("InRef")) {
			checkNumArgs(args, 1);
			return inref(args[0], vm);
		} else if (keyword.equals("IsInRef")) {
			checkNumArgs(args, 2);
			return isinref(args[0], args[1], vm);
		} else if (keyword.equals("DumpRefs")) {
			checkNumArgs(args, 0);
			dumprefs();
		} else if (keyword.equals("Timeout")) {
			checkNumArgs(args, 1);
			return timeout((int) JRT.toDouble(args[0]));
		} else if (keyword.equals("Throw")) {
			throw new AwkRuntimeException(Arrays.toString(args));
		} else if (keyword.equals("Version")) {
			checkNumArgs(args, 1);
			return version(args[0]);
		} else if (keyword.equals("Date")) {
			if (args.length == 0) {
				return date();
			} else if (args.length == 1) {
				return date(toAwkString(args[0]));
			} else {
				throw new IllegalAwkArgumentException(keyword + " expects 0 or 1 argument, not " + args.length);
			}
		} else if (keyword.equals("FileExists")) {
			checkNumArgs(args, 1);
			return fileexists(toAwkString(args[0]));
		} else {
			throw new NotImplementedError(keyword);
		}
		// never reached
		return null;
	}

	private Object resolve(Object arg, VariableManager vm) {
		while (true) {
			if (arg instanceof AssocArray) {
				return arg;
			}
			String arg_check = toAwkString(arg);
			if (reference_map.get(arg_check) != null) {
				arg = reference_map.get(arg_check);
			} else {
				return arg;
			}
		}
	}

	private int refmap_idx = 0;
	private Map<String, Object> reference_map = new HashMap<String, Object>();

	static String newreference(Object arg) {
		if (!(arg instanceof AssocArray)) { // FIXME see other FIXME below
			throw new IllegalAwkArgumentException("NewRef[erence] requires an assoc array, not " + arg.getClass().getName());
		}

		// otherwise, set the reference and return the new key

		// get next refmap_idx
		int r_idx = instance.refmap_idx++;
		// inspect the argument
		String arg_string;
		if (arg instanceof AssocArray) { // FIXME This does not make sense with the FIXME marked line above
			arg_string = arg.getClass().getName();
		} else {
			arg_string = arg.toString();
		}
		if (arg_string.length() > 63) {
			arg_string = arg_string.substring(0, 60) + "...";
		}
		// build Reference (scalar) string to this argument
		String retval = "@REFERENCE@ " + r_idx + " <" + arg_string + ">";
		instance.reference_map.put(retval, arg);
		return retval;
	}

	// this version assigns an assoc array a key/value pair
	static Object newreference(String refstring, Object key, Object value) {
		AssocArray aa = (AssocArray) instance.reference_map.get(refstring);
		if (aa == null) {
			throw new IllegalAwkArgumentException("AssocArray reference doesn't exist.");
		}
		return aa.put(key, value);
	}

	// this version assigns an object to a reference
	private Object dereference(Object arg, VariableManager vm) {
		// return the reference if the arg is a reference key
		if (arg instanceof AssocArray) {
			throw new IllegalAwkArgumentException("an assoc array cannot be a reference handle");
		} else {
			String arg_check = toAwkString(arg);
			return dereference(arg_check);
		}
	}

	// split this out for static access by other extensions
	static Object dereference(String arg_check) {
		if (instance.reference_map.get(arg_check) != null) {
			return instance.reference_map.get(arg_check);
		} else {
			throw new IllegalAwkArgumentException(arg_check + " not a valid reference");
		}
	}

	// this version assumes an assoc array is stored as a reference,
	// and to retrieve the stored value
	static Object dereference(String refstring, Object key, VariableManager vm) {
		AssocArray aa = (AssocArray) instance.reference_map.get(refstring);
		if (aa == null) {
			throw new IllegalAwkArgumentException("AssocArray reference doesn't exist.");
		}
		if (!(key instanceof AssocArray)) {
			// check if key is a reference string!
			String key_check = instance.toAwkString(key);
			if (instance.reference_map.get(key_check) != null) // assume it is a reference rather than an assoc array key itself
			{
				key = instance.reference_map.get(key_check);
			}
		}
		return aa.get(key);
	}

	static int unreference(Object arg, VariableManager vm) {
		String arg_check = instance.toAwkString(arg);
		if (instance.reference_map.get(arg_check) == null) {
			throw new IllegalAwkArgumentException("Not a reference : " + arg_check);
		}

		instance.reference_map.remove(arg_check);
		assert instance.reference_map.get(arg_check) == null;
		return 1;
	}
	private Map<AssocArray, Iterator> iterators = new HashMap<AssocArray, Iterator>();

	private Object inref(Object arg, VariableManager vm) {
		if (arg instanceof AssocArray) {
			throw new IllegalAwkArgumentException("InRef requires a Reference (string) argument, not an assoc array");
		}
		String arg_check = toAwkString(arg);
		if (reference_map.get(arg_check) == null) {
			throw new IllegalAwkArgumentException("Not a reference : " + arg_check);
		}
		Object o = reference_map.get(arg_check);
		if (!(o instanceof AssocArray)) {
			throw new IllegalAwkArgumentException("Reference not an assoc array. ref.class = " + o.getClass().getName());
		}

		AssocArray aa = (AssocArray) o;

		// use an in_map to keep track of existing iterators

		//Iterator<Object> iter = iterators.get(aa);
		Iterator iter = iterators.get(aa);
		if (iter == null) //iterators.put(aa, iter = aa.keySet().iterator());
		// without a new Collection, modification to the
		// assoc array during iteration causes a ConcurrentModificationException
		{
			iterators.put(aa, iter = new ArrayList<Object>(aa.keySet()).iterator());
		}

		Object retval = null;

		while (iter.hasNext()) {
			retval = iter.next();
			if (retval instanceof String && retval.toString().equals("")) {
				throw new AwkRuntimeException("Assoc array key contains a blank string ?!");
			}
			break;
		}

		if (retval == null) {
			iterators.remove(aa);
			retval = "";
		}

		if (retval instanceof AssocArray) {
			// search if item is referred to already
			for (String ref : reference_map.keySet()) {
				if (reference_map.get(ref) == retval) {
					return ref;
				}
			}
			// otherwise, return new reference to this item
			//return newreference(arg_check, retval);
			return newreference(retval);
		} else {
			return retval;
		}
	}

	private static final Integer ZERO = Integer.valueOf(0);
	private static final Integer ONE = Integer.valueOf(1);

	private int isinref(Object ref, Object key, VariableManager vm) {
		if (ref instanceof AssocArray) {
			throw new IllegalAwkArgumentException("Expecting a reference string for the 1st argument, not an assoc array.");
		}
		String refstring = toAwkString(ref);
		return isinref(refstring, key);
	}

	static int isinref(String refstring, Object key) {
		Object o = instance.reference_map.get(refstring);
		if (o == null) {
			throw new IllegalAwkArgumentException("Invalid refstring : " + refstring);
		}
		AssocArray aa = (AssocArray) o;
		return aa.isIn(key) ? ONE : ZERO;
	}

	private void dumprefs() {
		for (Object o1 : reference_map.keySet()) {
			Object o2 = reference_map.get(o1);
			if (o1 instanceof AssocArray) {
				o1 = ((AssocArray) o1).mapString();
			}
			if (o2 instanceof AssocArray) {
				o2 = ((AssocArray) o2).mapString();
			}
			System.out.println("REF : " + o1 + " = " + o2);
		}
	}

	static String typeof(Object arg, VariableManager vm) {
		if (false) {
			throw new Error("Should never reach here.");
		} else if (arg instanceof AssocArray) {
			return "AssocArray";
		} else if (arg instanceof Integer) {
			return "Integer";
		} else if (arg instanceof Double) {
			return "Double";
		} else {
			String string_rep = instance.toAwkString(arg);
			if (instance.reference_map.get(string_rep) != null) {
				return "Reference";
			} else {
				return "String";
			}
		}
	}

	private int get(AssocArray retval, AssocArray map, Object key) {
		retval.clear();
		retval.put(0, map.get(key));
		return 1;
	}

	private Object toscalar(AssocArray aa) {
		return aa.get(0);
	}

	private Object map(Object[] args, VariableManager vm, int map_type) {
		if (args.length % 2 == 0) {
			return submap(args, vm, map_type);
		} else {
			return toplevelmap(args, vm, map_type, false);	// false = map assignment
		}
	}

	private Object mapunion(Object[] args, VariableManager vm, int map_type) {
		return toplevelmap(args, vm, map_type, true);	// true = map union
	}

	private int toplevelmap(Object[] args, VariableManager vm, int map_type, boolean map_union) {
		AssocArray aa = (AssocArray) args[0];
		if (!map_union) {
			aa.clear();
			aa.useMapType(map_type);
		}
		int cnt = 0;
		for (int i = 1; i < args.length; i += 2) {
			if (args[i] instanceof AssocArray) {
				args[i] = newreference(args[i]);
			}
			if (args[i + 1] instanceof AssocArray) {
				args[i + 1] = newreference(args[i + 1]);
			}

			aa.put(args[i], args[i + 1]);

			++cnt;
		}
		return cnt;
	}

	private AssocArray submap(Object[] args, VariableManager vm, int map_type) {
		AssocArray aa = new AssocArray(false);
		aa.useMapType(map_type);
		for (int i = 0; i < args.length; i += 2) {
			if (args[i] instanceof AssocArray) {
				args[i] = newreference(args[i]);
			}
			if (args[i + 1] instanceof AssocArray) {
				args[i + 1] = newreference(args[i + 1]);
			}

			aa.put(args[i], args[i + 1]);
		}
		return aa;
	}

	private int array(Object[] args, VariableManager vm) {
		AssocArray aa = (AssocArray) args[0];
		aa.clear();
		aa.useMapType(AssocArray.MT_TREE);
		String subsep = toAwkString(vm.getSUBSEP());
		int cnt = 0;
		for (int i = 1; i < args.length; ++i) {
			Object o = args[i];
			if (o instanceof AssocArray) {
				AssocArray arr = (AssocArray) o;
				for (Object key : arr.keySet()) {
					aa.put("" + i + subsep + key, arr.get(key));
				}
			} else {
				aa.put("" + i, o);
			}
			//aa.put(args[i], args[i+1]);
			++cnt;
		}
		return cnt;
	}

	/*private AssocArray subarray(Object[] args, VariableManager vm) {
		AssocArray aa = new AssocArray(false);
		aa.clear();
		//aa.useLinkedHashMap();
		aa.useMapType(AssocArray.MT_TREE);
		String subsep = toAwkString(vm.getSUBSEP());
		int cnt = 0;
		for (int i = 1; i <= args.length; ++i) {
			Object o = args[i - 1];
			if (o instanceof AssocArray) {
				AssocArray arr = (AssocArray) o;
				for (Object key : arr.keySet()) {
					aa.put("" + i + subsep + key, arr.get(key));
				}
			} else {
				aa.put("" + i, o);
			}
			//aa.put(args[i], args[i+1]);
			++cnt;
		}
		return aa;
	}*/
	private int mapcopy(Object[] args) {
		AssocArray aa_target = (AssocArray) args[0];
		AssocArray aa_source = (AssocArray) args[1];
		aa_target.clear();
		int cnt = 0;
		for (Object o : aa_source.keySet()) {
			aa_target.put(o, aa_source.get(o));
			++cnt;
		}
		return cnt;
	}

	private Object todouble(Object arg, VariableManager vm) {
		if (arg instanceof AssocArray) {
			throw new IllegalArgumentException("Cannot deduce double value from an associative array.");
		}
		if (arg instanceof Number) {
			return ((Number) arg).doubleValue();
		}

		// otherwise, a string

		try {
			String str = toAwkString(arg);
			double d = Double.parseDouble(str);
			return d;
		} catch (NumberFormatException nfe) {
			return "";
		}
	}

	private static String tostring(Object arg, VariableManager vm) {
		if (arg instanceof AssocArray) {
			return ((AssocArray) arg).mapString();
		} else {
			return instance.toAwkString(arg);
		}
	}
	private int wait_int = 0;
	private BlockObject timeout_blocker = new BlockObject() {

		@Override
		public String getNotifierTag() {
			return "Timeout";
		}

		@Override
		public final void block()
				throws InterruptedException
		{
			synchronized (timeout_blocker) {
				timeout_blocker.wait(wait_int);
			}
		}
	};

	private Object timeout(int ms) {
		if (ms <= 0) {
			throw new IllegalAwkArgumentException("Timeout requires a positive # argument, not " + ms + ".");
		}
		wait_int = ms;
		return timeout_blocker;
	}

	private String version(Object obj) {
		if (obj instanceof AssocArray) {
			return ((AssocArray) obj).getMapVersion();
		} else {
			Class<?> cls = (Class<?>) obj.getClass();
			return cls.getPackage().getSpecificationVersion();
		}
	}

	// single threaded, so one Date object (unsynchronized) will do
	private final Date date_obj = new Date();

	private String date() {
		date_obj.setTime(System.currentTimeMillis());
		return date_obj.toString();
	}
	private final SimpleDateFormat df = new SimpleDateFormat();

	private String date(String format_string) {
		date_obj.setTime(System.currentTimeMillis());
		df.applyPattern(format_string);
		return df.format(date_obj);
	}

	private int fileexists(String path) {
		if (new File(path).exists()) {
			return ONE;
		} else {
			return ZERO;
		}
	}
}
