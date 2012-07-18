package org.jawk.util;

import java.util.ArrayList;

/**
 * A stack implemented with an ArrayList.  Unlike the java.util.Stack
 * which uses a java.util.Vector as a storage mechanism,
 * this implementation is non-synchronized to improve performance.
 * <p>
 * It performs quicker than the LinkedListStackImpl version.
 * <p>
 * There is no maximum capacity which is enforced, nor is there any
 * checks if pop() is executed on an empty stack.
 */
public class ArrayStackImpl<E> extends ArrayList<E> implements MyStack<E> {
  /**
   * Allocates an ArrayList with a capacity of 100.
   */
  public ArrayStackImpl() { super(100); }
  /**
   * Push an item to the stack.
   *
   * @param o The item to push onto the stack.
   */
  public void push(E o) { add(o); }
  /**
   * Pops an item off the stack.
   * <p>
   * Warning: no checks are done in terms of size, etc.
   * If a pop() occurrs on an empty stack,
   * an ArrayIndexOutOfBoundException is thrown.
   *
   * @return The top of the stack.  The element is subsequently
   *	removed from the stack.
   */
  public E pop() { return remove(size()-1); }

  public E peek() { return get(size()-1); }
}
