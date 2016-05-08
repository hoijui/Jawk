package org.jawk.jrt;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * An AWK associative array.
 * <p>
 * The implementation requires the ability to choose,
 * at runtime, whether the keys are to be maintained in
 * sorted order or not. Therefore, the implementation
 * contains a reference to a Map (either TreeMap or
 * HashMap, depending on whether to maintain keys in
 * sorted order or not) and delegates calls to it
 * accordingly.
 * </p>
 */
public class AssocArray implements Comparator<Object> {

	private Map<Object, Object> map;

	public AssocArray(boolean sortedArrayKeys) {
		if (sortedArrayKeys) {
			map = new TreeMap<Object, Object>(this);
		} else {
			map = new HashMap<Object, Object>();
		}
	}

	/**
	 * The parameter to useMapType to convert
	 * this associative array to a HashMap.
	 */
	public static final int MT_HASH = 2;
	/**
	 * The parameter to useMapType to convert
	 * this associative array to a LinkedHashMap.
	 */
	public static final int MT_LINKED = 2 << 1;
	/**
	 * The parameter to useMapType to convert
	 * this associative array to a TreeMap.
	 */
	public static final int MT_TREE = 2 << 2;

	/**
	 * Convert the map which backs this associative array
	 * into one of HashMap, LinkedHashMap, or TreeMap.
	 *
	 * @param mapType Can be one of MT_HASH, MT_LINKED,
	 *   or MT_TREE.
	 */
	public void useMapType(int mapType) {
		assert map.isEmpty();
		switch (mapType) {
			case MT_HASH:
				map = new HashMap<Object, Object>();
				break;
			case MT_LINKED:
				map = new LinkedHashMap<Object, Object>();
				break;
			case MT_TREE:
				map = new TreeMap<Object, Object>(this);
				break;
			default:
				throw new Error("Invalid map type : " + mapType);
		}
	}

	/**
	 * Provide a string representation of the delegated
	 * map object.
	 * It exists to support the _DUMP keyword.
	 */
	public String mapString() {
		// was:
		//return map.toString();
		// but since the extensions, assoc arrays can become keys as well
		StringBuilder sb = new StringBuilder().append('{');
		int cnt = 0;
		for (Object o : map.keySet()) {
			if (cnt > 0) {
				sb.append(", ");
			}
			if (o instanceof AssocArray) {
				sb.append(((AssocArray) o).mapString());
			} else {
				sb.append(o.toString());
			}
			//sb.append('=').append(map.get(o));
			sb.append('=');
			Object o2 = map.get(o);
			if (o2 instanceof AssocArray) {
				sb.append(((AssocArray) o2).mapString());
			} else {
				sb.append(o2.toString());
			}
			++cnt;
		}
		return sb.append('}').toString();
	}

	/** a "null" value in Awk */
	private static final String BLANK = "";

	/**
	 * Test whether a particular key is
	 * contained within the associative array.
	 * Unlike get(), which adds a blank (null)
	 * reference to the associative array if the
	 * element is not found, isIn will not.
	 * It exists to support the IN keyword.
	 */
	public boolean isIn(Object key) {
		return map.get(key) != null;
	}

	/**
	 * Get the value of an associative array
	 * element given a particular key.
	 * If the key does not exist, a null value
	 * (blank string) is inserted into the array
	 * with this key, and the null value is returned.
	 */
	public Object get(Object key) {
		Object result = map.get(key);
		if (result == null) {
			if (key != null) {
				try {
					// try a primitive version key
					int iKey = Integer.parseInt(key.toString());
					result = map.get(iKey);

					if (result != null)
						return result;
				} catch (Exception e) {
				}
			}
			// based on the AWK specification:
			// Any reference (except for IN expressions) to a non-existent
			// array element will automatically create it.
			result = BLANK;
			map.put(key, result);
		}
		return result;
	}

	public Object put(Object key, Object value) {
		if (key != null) {
			try {
				// Save a primitive version
				int iKey = Integer.parseInt(key.toString());
				put(iKey, value);
			} catch (Exception e) {
			}
		}

		return map.put(key, value);
	}

	/**
	 * Added to support insertion of primitive key types.
	 */
	public Object put(int key, Object value) {
		return map.put(key, value);
	}

	public Set<Object> keySet() {
		return map.keySet();
	}

	public void clear() {
		map.clear();
	}

	public Object remove(Object key) {
		return map.remove(key);
	}

	@Override
	public String toString() {
		throw new AwkRuntimeException("Cannot evaluate an unindexed array.");
	}

	/**
	 * Comparator implementation used by the TreeMap
	 * when keys are to be maintained in sorted order.
	 */
	@Override
	public int compare(Object o1, Object o2) {

		if (o1 instanceof String || o2 instanceof String) {
			// use string comparison
			String s1 = o1.toString();
			String s2 = o2.toString();
			return s1.compareTo(s2);
		} else {
			if (o1 instanceof Double || o2 instanceof Double) {
				Double d1 = ((Double) o1);
				Double d2 = ((Double) o2);
				return d1.compareTo(d2);
			} else {
				Integer i1 = ((Integer) o1);
				Integer i2 = ((Integer) o2);
				return i1.compareTo(i2);
			}
		}
	}

	public String getMapVersion() {
		return map.getClass().getPackage().getSpecificationVersion();
	}
}
