package org.jawk.jrt;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Implement the KeyList interface with an ArrayList.
 */
//public class KeyListImpl extends ArrayList<Object> implements KeyList
public class KeyListImpl implements KeyList {

	private List<Object> list;

	/**
	 * Convert the set to a KeyList.
	 * We could have used an ArrayList directly. However, tagging
	 * the implementation with a KeyList interface improves type
	 * checking within the parsing / semantic analysis phase.
	 */
	public KeyListImpl(Set<Object> set) {
		//super(set);
		list = new ArrayList<Object>(set);
	}

	@Override
	public final Object getFirstAndRemove() {
		return list.remove(0);
	}

	@Override
	public int size() {
		return list.size();
	}
}
