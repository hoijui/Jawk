package org.jawk.util;

import java.util.LinkedList;

/**
 * A simple delegate to a LinkedList.  Unlike the java.util.Stack,
 * this implementation is non-synchronized to improve performance.
 * <p>
 * It performs slower than the ArrayStackImpl version.
 * <p>
 * There is no maximum capacity which is enforced, nor is there any
 * checks if pop() is executed on an empty stack.
 */
public class LinkedListStackImpl<E> extends LinkedList<E> implements MyStack<E> {
  public void push(E o) { addFirst(o); }
  public E pop() { return removeFirst(); }
}

