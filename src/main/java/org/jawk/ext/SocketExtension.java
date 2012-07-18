package org.jawk.ext;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.jawk.NotImplementedError;
import org.jawk.jrt.BlockHandleValidator;
import org.jawk.jrt.BlockObject;
import org.jawk.jrt.Blockable;
import org.jawk.jrt.BulkBlockObject;
import org.jawk.jrt.IllegalAwkArgumentException;
import org.jawk.jrt.JRT;
import org.jawk.jrt.VariableManager;

/**
 * Enable Socket processing in Jawk.
 * <p>
 * To use:
 * <blockquote><pre>
 * ## example echo server using CServerSocket (character-based)
 * BEGIN {
 * 	css = CServerSocket(7777);
 * }
 * $0 = SocketAcceptBlock(css,
 * 	SocketInputBlock(handles,
 * 	SocketCloseBlock(css, handles \
 * 	)));
 * $1 == "SocketAccept" {
 * 	handles[SocketAccept($2)] = 1
 * }
 * $1 == "SocketClose" {
 * 	SocketClose($2)
 * 	delete handles[$2]
 * }
 * $1 == "SocketInput" {
 * 	input = SocketRead($2)
 * 	SocketWrite($2, input);	## do the echo
 * }
 * </pre></blockquote>
 * <p>
 * The extension functions are as follows:
 * <ul>
 * <hr>
 * <li><strong><em><font size=+1>ServerSocket</font></em></strong> -<br>
 * Sets up a server socket to listen for incomming
 * connections.  SocketRead on sockets accepted
 * by ServerSocket return arbitrary-length Strings
 * (bytes buffered by the input stream, converted
 * to a string).<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>port number - required
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string handle to a serversocket.
 * </ul><p>
 * <li><strong><em><font size=+1>CServerSocket</font></em></strong> -<br>
 * Sets up a server socket to listen for incomming
 * connections.  SocketRead on sockets accepted
 * by CServerSocket return strings which terminate
 * by a newline, or text in the input buffer just
 * prior to the closing of the socket.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>port number - required
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string handle to a cserversocket.
 * </ul><p>
 * <hr>
 * <li><strong><em><font size=+1>Socket</font></em></strong> -<br>
 * Create a Socket and connect it to a TCP socket
 * endpoint.  SocketRead on sockets returned
 * by Socket return arbitrary-length Strings
 * (bytes buffered by the input stream, converted
 * to a string).<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>hostname/ip/"localhost" - required
 * <li>port number - required
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string handle to a socket.
 * </ul><p>
 * <li><strong><em><font size=+1>CSocket</font></em></strong> -<br>
 * Create a Socket and connect it to a TCP socket
 * endpoint.  SocketRead on sockets returned
 * by Socket return strings which terminate
 * by a newline, or text in the input buffer just
 * prior to the closing of the socket.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>hostname/ip/"localhost" - required
 * <li>port number - required
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string handle to a csocket.
 * </ul><p>
 * <hr>
 * <li><strong><em><font size=+1>SocketAcceptBlock</font></em></strong> -<br>
 * Blocks until a serversocket or cserversocket
 * is ready to accept a connecting socket.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>Any mix of
 * serversocket or cserversocket handles
 * and/or associative arrays whose keys
 * are serversocket or cserversocket handles.
 * The last argument can optionally be
 * another block call for block chaining.
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string of the form:
 * <code><font size=+1>SocketAccept<em>OFS</em>handle</font></code>
 * where handle is a serversocket or cserversocket
 * handle.
 * </ul><p>
 * <li><strong><em><font size=+1>SocketInputBlock</font></em></strong> -<br>
 * Blocks until a socket or csocket is ready
 * to accept input (via SocketRead).<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>Any mix of
 * socket or csocket handles and/or associative
 * arrays whose keys are socket or csocket handles.
 * The last argument can optionally be
 * another block call for block chaining.
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string of the form: <code><font size=+1>SocketInput<em>OFS</em>handle</font></code>
 * where handle is a socket or csocket
 * handle.
 * </ul><p>
 * <li><strong><em><font size=+1>SocketCloseBlock</font></em></strong> -<br>
 * Blocks until a serversocket, cserversocket,
 * socket, or csocket has been closed on the
 * remote end.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>Any mix of
 * serversocket, cserversocket, socket, or csocket
 * handles and/or associative
 * arrays whose keys are serversocket, cserversocket,
 * socket, or csocket handles.
 * The last argument can optionally be
 * another block call for block chaining.
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string of the form: <code><font size=+1>SocketClose<em>OFS</em>handle</font></code>
 * where handle is a serversocket, cserversocket, socket,
 * or csocket handle.
 * </ul><p>
 * <hr>
 * <li><strong><em><font size=+1>SocketAccept</font></em></strong> -<br>
 * Accepts a socket from a serversocket or
 * a cserversocket.  The operation will
 * block if there is no socket to accept.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>serversocket-or-cserversocket handle - required
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string handle to a socket or csocket.
 * </ul><p>
 * <hr>
 * <li><strong><em><font size=+1>SocketRead</font></em></strong> -<br>
 * Reads input from the input stream of a socket
 * or a csocket.  For a socket, the input length
 * is arbitrary.  For a csocket, the input
 * length is bounded by a newline or upon
 * termination of the socket.
 * The operation will block if there is no input
 * on the socket.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>socket-or-csocket handle - required
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string containing the input on the socket.
 * </ul><p>
 * <li><strong><em><font size=+1>SocketWrite</font></em></strong> -<br>
 * Writes data to the socket or csocket.
 * For a socket, the string is converted
 * to bytes (via java.lang.String.getBytes()),
 * and the bytes are sent to the socket's
 * output stream.
 * For a csocket, println() is called on the
 * underlying socket's PrintStream.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>socket-or-csocket handle - required
 * <li>msg - required - The string to write to
 * 	the socket.  For a csocket, a newline
 * 	is added to it (via the
 * 	java.io.PrintStream.println() method).
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>1 upon a successful write, 0 otherwise
 * </ul><p>
 * <li><strong><em><font size=+1>SocketFlush</font></em></strong> -<br>
 * Flushes the output stream of a socket or csocket.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>socket-or-csocket handle - required
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>1 upon a successful flush, 0 otherwise
 * </ul><p>
 * <hr>
 * <li><strong><em><font size=+1>SocketClose</font></em></strong> -<br>
 * Closes the socket/csocket on the local end,
 * or a serversocket/cserversocket.
 * Can be called in response to a SocketCloseBlock
 * event, or to force a socket/csocket connection to
 * terminate.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>socket/csocket/serversocket/cserversocket handle - required
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>1 upon successful close, 0 otherwise
 * </ul><p>
 * <hr>
 * </ul>
 */
public class SocketExtension extends AbstractExtension {
  /**
   * Either threaded or non-threaded (nio-style) socket
   * handling.  The threaded implementation is provided
   * upon initial release. Non-threaded
   * functionality will be available in a subsequent
   * release.
   */
  private IO_Style impl_delegate;

  public final void init(VariableManager vm, JRT jrt) {
	  super.init(vm, jrt);
	  impl_delegate = new Threaded_IO_Style(vm);
  }

  public final String getExtensionName() {
	return "Socket Support";
  }

  public final String[] extensionKeywords() {
	return new String[] {
		"ServerSocket",		// i.e., ss = ServerSocket(8080) or ss = ServerSocket("ip", 8080)
		"CServerSocket",	// i.e., css = CServerSocket(8080) or css = CServerSocket("ip", 8080)
		"Socket",		// i.e., s = Socket("localhost", 8080)
		"CSocket",		// i.e., cs = CSocket("localhost", 8080)
		"SocketAcceptBlock",	// i.e., $0 = SocketAcceptBlock(ss, css,
		"SocketInputBlock",	// i.e.,	SocketInputBlock(s, cs,
		"SocketCloseBlock",	// i.e.,	  SocketCloseBlock(ss,css, s,cs)));
		"SocketAccept",		// i.e., cs = SocketAccept(css)
		"SocketRead",		// i.e., buf = SocketRead(s) or line = SocketRead(cs)
		"SocketWrite",		// i.e., SocketWrite(s, "hi there\n") or SocketWrite(cs, "hi there")
		"SocketFlush",		// i.e., SocketFlush(s) or SocketFlush(cs)
		"SocketClose",		// i.e., SocketClose(ss) or SocketClose(cs)
	};
  }

  public final Object invoke(String method_name, Object[] args) {
	// large if-then-else block to decide which extension to invoke
	if (false)
		throw new Error("Should never reach.");
	else if (method_name.equals("ServerSocket")) {
		if (args.length == 1)
			return impl_delegate.serversocket(
				null,
				(int) JRT.toDouble(args[0])
			);
		else if (args.length == 2)
			return impl_delegate.serversocket(
				toAwkString(args[0]),
				(int) JRT.toDouble(args[1])
			);
		else
			throw new IllegalAwkArgumentException("Expecting 1 or 2 arguments, not "+args.length);
	}
	else if (method_name.equals("CServerSocket")) {
		if (args.length == 1)
			return impl_delegate.cserversocket(
				null,
				(int) JRT.toDouble(args[0])
			);
		else if (args.length == 2)
			return impl_delegate.cserversocket(
				toAwkString(args[0]),
				(int) JRT.toDouble(args[1])
			);
		else
			throw new IllegalAwkArgumentException("Expecting 1 or 2 arguments, not "+args.length);
	}
	else if (method_name.equals("Socket")) {
		checkNumArgs(args, 2);
		return impl_delegate.socket(
			toAwkString(args[0]),
			(int) JRT.toDouble(args[1])
		);
	}
	else if (method_name.equals("CSocket")) {
		checkNumArgs(args, 2);
		return impl_delegate.csocket(
			toAwkString(args[0]),
			(int) JRT.toDouble(args[1])
		);
	}
	else if (method_name.equals("SocketAcceptBlock"))
		return impl_delegate.socketacceptblock(args);
	else if (method_name.equals("SocketInputBlock"))
		return impl_delegate.socketinputblock(args);
	else if (method_name.equals("SocketCloseBlock")) {
		return impl_delegate.socketcloseblock(args);
	}
	else if (method_name.equals("SocketAccept")) {
		checkNumArgs(args, 1);
		return impl_delegate.socketaccept(toAwkString(args[0]));
	}
	else if (method_name.equals("SocketRead")) {
		checkNumArgs(args, 1);
		return impl_delegate.socketread(toAwkString(args[0]));
	}
	else if (method_name.equals("SocketWrite")) {
		checkNumArgs(args, 2);
		return impl_delegate.socketwrite(
			toAwkString(args[0]),
			toAwkString(args[1])
		);
	}
	else if (method_name.equals("SocketFlush")) {
		checkNumArgs(args, 1);
		return impl_delegate.socketflush(toAwkString(args[0]));
	}
	else if (method_name.equals("SocketClose")) {
		checkNumArgs(args, 1);
		return impl_delegate.socketclose(toAwkString(args[0]));
	}
	else
		throw new NotImplementedError(method_name);
  }
} // public class SocketExtension {AbstractExtension}

//
// INTERFACE TO DELEGATE
//

/**
 * Interface to the socket handling delegate which
 * does all the work.  The SocketExtension manager
 * class delegates all concrete socket IO
 * processing to an instance of this interface.
 */
interface IO_Style {
  /**
   * Sets up a server socket to listen for incomming
   * connections.  SocketRead on sockets accepted
   * by ServerSocket return arbitrary-length Strings
   * (bytes buffered by the input stream, converted
   * to a string).
   *
   * @param hostname The hostname or IP address as a string.
   * 	hostname can be null.
   * @param port The port number.
   *
   * @return A handle to a newly created serversocket.
   */
  String serversocket(String hostname, int port);
  /**
   * Sets up a server socket to listen for incomming
   * connections.  SocketRead on sockets accepted
   * by CServerSocket return strings which terminate
   * by a newline, or text in the input buffer just
   * prior to the closing of the socket.
   *
   * @param hostname The hostname or IP address as a string.
   * 	hostname can be null.
   * @param port The port number.
   *
   * @return A handle to a newly created cserversocket.
   */
  String cserversocket(String hostname, int port);
  /**
   * Create a Socket and connect it to a TCP socket
   * endpoint.  SocketRead on sockets returned
   * by Socket return arbitrary-length Strings
   * (bytes buffered by the input stream, converted
   * to a string).
   *
   * @param hostname The hostname or IP address as a string.
   * 	hostname can be null.
   * @param port The port number.
   *
   * @return A handle to a newly created socket.
   */
  String socket(String hostname, int port);
  /**
   * Create a Socket and connect it to a TCP socket
   * endpoint.  SocketRead on sockets returned
   * by Socket return strings which terminate
   * by a newline, or text in the input buffer just
   * prior to the closing of the socket.
   *
   * @param hostname The hostname or IP address as a string.
   * 	hostname can be null.
   * @param port The port number.
   *
   * @return A handle to a newly created csocket.
   */
  String csocket(String hostname, int port);
  /**
   * Blocks until a serversocket or cserversocket
   * is ready to accept a connecting socket.
   *
   * @param args An array of
   * serversocket or cserversocket handles
   * and/or associative arrays whose keys
   * are serversocket or cserversocket handles.
   * The last argument can optionally be
   * another block call for block chaining.
   *
   * @return A block object conditioned
   * to block on the acceptance of
   * socket connections from any of
   * the serversockets / cserversockets
   * referred to by the handles passed
   * in to the object array.
   */
  BlockObject socketacceptblock(Object[] args);
  /**
   * Blocks until a socket or csocket is ready
   * to accept input (via SocketRead).
   *
   * @param args An array of
   * socket or csocket handles and/or associative
   * arrays whose keys are socket or csocket handles.
   * The last argument can optionally be
   * another block call for block chaining.
   *
   * @return A block object conditioned
   * to block on the availability of
   * input from any of the sockets / csockets
   * referred to by the handles passed
   * in to the object array.
   */
  BlockObject socketinputblock(Object[] args);
  /**
   * Blocks until a serversocket, cserversocket,
   * socket, or csocket has been closed on the
   * remote end.
   *
   * @param args An array of
   * serversocket, cserversocket, socket, or csocket
   * handles and/or associative
   * arrays whose keys are serversocket, cserversocket,
   * socket, or csocket handles.
   * The last argument can optionally be
   * another block call for block chaining.
   *
   * @return A block object conditioned
   * to block until any of the sockets /
   * csockets / serversockets / cserversockets
   * in to the object array have closed.
   */
  BlockObject socketcloseblock(Object[] args);
  /**
   * Accepts a socket from a serversocket or
   * a cserversocket.  The operation will
   * block if there is no socket to accept.
   *
   * @param handle A string handle to a serversocket
   * or cserversocket.
   *
   * @return A handle to a socket or csocket that
   * has connected to the serversocket / cserversocket
   * referred to by the handle argument.
   */
  String socketaccept(String handle);
  /**
   * Reads input from the input stream of a socket
   * or a csocket.  For a socket, the input length
   * is arbitrary.  For a csocket, the input
   * length is bounded by a newline or upon
   * termination of the socket.
   * The operation will block if there is no input
   * on the socket.
   *
   * @param handle A string handle to a socket
   * or csocket.
   *
   * @return A block of byte input from a socket
   * (converted to a string), or a line of
   * string input from a csocket bounded by
   * a newline in the stream or upon the closing
   * of the csocket.
   */
  String socketread(String handle);
  /**
   * Writes data to the socket or csocket.
   * For a socket, the string is converted
   * to bytes (via java.lang.String.getBytes()),
   * and the bytes are sent to the underlying
   * socket's output stream.
   * For a csocket, println() is called on the
   * underlying socket's PrintStream.
   *
   * @param handle A string handle to a socket
   * or csocket.
   * @param buf The string containing the
   * bytes to write.  SocketWrite writes
   * the contents of the resulting buf.getBytes()
   * call to the socket.
   *
   * @param handle A String handle to a socket
   * or csocket.
   * @param buf A string containing a block of
   * bytes to write to a socket (via
   * java.lang.String.getBytes()) if handle
   * refers to a socket.  If handle refers
   * to a csocket, the line of text to write
   * via PrintStream.println(String).
   *
   * @return 1 upon a successful write,
   * 0 upon an IO exception/error.
   */
  int socketwrite(String handle, String buf);
  /**
   * Flushes the output stream of a socket or csocket.
   *
   * @param handle A string handle to a socket
   * or csocket.
   *
   * @return 1 upon a successful flush operation,
   * 0 upon an IO exception/error.
   */
  int socketflush(String handle);
   /**
   * Closes the socket/csocket on the local end,
   * or a serversocket/cserversocket.
   * Can be called in response to a SocketCloseBlock
   * event, or to force a socket/csocket connection to
   * terminate.
   *
   * @param handle A string handle to a socket,
   * 	csocket, serversocket, or cserversocket.
   *
   * @return 1 upon a successful close operation,
   * 0 upon an IO exception/error.
   */
  int socketclose(String handle);
}

/**
 * A view of two maps as one map.
 */
class MapUnion<K,V> extends AbstractMap<K,V> {
  private Map<K,V> m1;
  private Map<K,V> m2;
  MapUnion(Map<K,V> m1, Map<K,V> m2) {
	this.m1 = m1;
	this.m2 = m2;
  }
  public final Set<Map.Entry<K,V>> entrySet() {
	// build the entry set
	Set<Map.Entry<K,V>> entry_set = new HashSet<Map.Entry<K,V>>();

	Set<Map.Entry<K,V>> s1 = m1.entrySet();
	Set<Map.Entry<K,V>> s2 = m2.entrySet();
	for(Map.Entry<K,V> me : s1)
		entry_set.add(me);
	for(Map.Entry<K,V> me : s2)
		entry_set.add(me);

	return entry_set;
  }
}

class Threaded_IO_Style implements IO_Style {

  private String last_err = null;
  /**
   * Map of "Socket"/"CSocket" handles to
   * the objects which perform the actual
   * read and block operations.
   * <p>
   * <strong>Note:</strong>
   * "consumers" originally was of type
   * Map<String,Consumer>, but changed to ...,Closeable.
   * (Likewise, "accepters" was originally ...,Accepter,
   * but then changed to ...,Closeable.)
   * Why?  Because MapUnion could not infer that "Consumer"
   * nor "Accepter" were extensions of "Blockable".
   * MapUnion originally accepted 3 generic parameters:
   * K, V1 extends Blockable, and V2 extends Blockable.
   * And, close_blocker's BulkBlockObject Map parameter was:
   * new MapUnion<String,Accepter,Consumer>(accepters,consumers).
   * But, it wouldn't compile.
   * The resulting warning/error messages stated that
   * "capture #XXX of ? extends Blockable" does not
   * match "? extends Blockable".  I believe the error
   * results from Blockable being compiled against
   * Java 1.5.x and extensions being developed against
   * Java 1.6.x, and that they don't grok one another
   * in this scenario.
   * We, then, decided to assign its lowest common
   * subclass, "Closeable", and typecast when we need
   * specific "Accepter" and "Consumer" functionality.
   * This resolved the issue while losing some
   * compile-time type safety.
   */
  private final Map<String,Closeable> consumers = new HashMap<String,Closeable>();

  /**
   * Map of "ServerSocket"/"CServerSocket" handles
   * to the objects which perform the actual
   * socket accept operation.
   * <p>
   * <strong>Note:</strong>
   * See lengthy diatribe above for "consumers".
   * The same applies for "accepters"'s generic
   * type choice for values of the Map.
   */
  private final Map<String,Closeable> accepters = new HashMap<String,Closeable>();

  private final VariableManager vm;

  private final BulkBlockObject accept_blocker;
  private final BulkBlockObject input_blocker;
  private final BulkBlockObject close_blocker;

  Threaded_IO_Style(VariableManager vm) {
	assert vm != null;
	this.vm = vm;
	accept_blocker = new BulkBlockObject("SocketAccept", accepters, vm);
	input_blocker = new BulkBlockObject("SocketInput", consumers, vm);
	close_blocker = new BulkBlockObject("SocketClose", new MapUnion<String,Closeable>(accepters, consumers), vm);
  }

  public final String serversocket(String hostname, int port) {
	try {
		ServerSocket ss;
		if (hostname == null)
			ss = new ServerSocket(port);
		else
			// 0 = default backlog
			ss = new ServerSocket(port, 0, InetAddress.getByName(hostname));
		String handle = createHandle(ss);
		//ssockets.put(handle, ss);
		Accepter accepter_thread = new Accepter(handle, ss);
		accepters.put(handle, accepter_thread);
		accepter_thread.start();
		return handle;
	} catch (IOException ioe) {
		ioe.printStackTrace();
		last_err = ioe.toString();
		return "";
	}
  }

  public final String cserversocket(String hostname, int port) {
	try {
		ServerSocket ss;
		if (hostname == null)
			ss = new ServerSocket(port);
		else
			// 0 = default backlog
			ss = new ServerSocket(port, 0, InetAddress.getByName(hostname));
		String handle = createHandle(ss);
		//ssockets.put(handle, ss);
		Accepter accepter_thread = new CAccepter(handle, ss);
		accepters.put(handle, accepter_thread);
		accepter_thread.start();
		return handle;
	} catch (IOException ioe) {
		ioe.printStackTrace();
		last_err = ioe.toString();
		return "";
	}
  }

  public final String socket(String hostname, int port) {
	// create the socket
	try {
		Socket socket = new Socket(hostname, port);
		String handle = createHandle(socket);
		//sockets.put(handle, socket);
		// start the reader
		Consumer reader_thread = new ByteConsumer(handle, socket);
		consumers.put(handle, reader_thread);
		reader_thread.start();
		return handle;
	} catch (IOException ioe) {
		ioe.printStackTrace();
		last_err = ioe.toString();
		return "";
	}
  }

  public final String csocket(String hostname, int port) {
	try {
		// create the socket
		Socket socket = new Socket(hostname, port);
		String handle = createHandle(socket);
		//sockets.put(handle, socket);
		// start the reader
		Consumer reader_thread = new CharacterConsumer(handle, socket);
		consumers.put(handle, reader_thread);
		reader_thread.start();
		return handle;
	} catch (IOException ioe) {
		ioe.printStackTrace();
		last_err = ioe.toString();
		return "";
	}
  }

  private int socket_idx = 0;
  private int ssocket_idx = 0;

  private final String createHandle(Socket socket) {
	return "Socket:"+socket.getInetAddress().toString()+":"+socket.getPort()+"/"+(++socket_idx);
  }

  private final String createHandle(ServerSocket ssocket) {
	return "ServerSocket:"+ssocket.getInetAddress().toString()+":"+ssocket.getLocalPort()+"/"+(++ssocket_idx);
  }

  /*
  private final String getFS() {
	return toAwkString(vm.getFS());
  }
  */

  //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////

  private final BlockHandleValidator accept_handle_validator = new BlockHandleValidator() {
	// to satisfy the BlockHandleValidator interface
	public String isBlockHandleValid(String handle) {
		Closeable closeable = accepters.get(handle);
		if (closeable == null)
			return "Invalid ServerSocket handle.";
		if (closeable.isClosed())
			return "ServerSocket is closed.";
		else
			return null;	// valid
	}
  };

  private final BlockHandleValidator input_handle_validator = new BlockHandleValidator() {
	// to satisfy the BlockHandleValidator interface
	public String isBlockHandleValid(String handle) {
		Closeable closeable = consumers.get(handle);
		if (closeable == null)
			return "Invalid socket handle.  (Could have already been closed?)";
		if (closeable.isClosed())
			return "Socket is closed.";
		else
			return null;	// valid
	}
  };

  private final BlockHandleValidator close_handle_validator = new BlockHandleValidator() {
	// to satisfy the BlockHandleValidator interface
	public String isBlockHandleValid(String handle) {
		Closeable closeable = accepters.get(handle);
		if (closeable == null)
			closeable = consumers.get(handle);
		if (closeable == null)
			return "Invalid socket handle.  (Could have already been closed?)";
		if (closeable.isClosed())
			return "Socket is already closed.";
		else
			return null;	// valid
	}
  };

  public final BlockObject socketacceptblock(Object[] args) {
	return accept_blocker.populateHandleSet(args, vm, accept_handle_validator);
  }

  public final BlockObject socketinputblock(Object[] args) {
	return input_blocker.populateHandleSet(args, vm, input_handle_validator);
  }

  public final BlockObject socketcloseblock(Object[] args) {
	return close_blocker.populateHandleSet(args, vm, close_handle_validator);
  }

  public final String socketaccept(String handle) {
	try {
		Accepter accepter = (Accepter) accepters.get(handle);
		if (accepter == null)
			throw new IllegalAwkArgumentException("Invalid server socket handle : "+handle);
		// it's "as if" accept_blocker is querying whether to block or not
		if (accepter.willBlock(accept_blocker) && accepter.isClosed()) {
			last_err = "Server closed.";
			return "";
		}
		return accepter.getSocket();
	} catch (InterruptedException ie) {
		ie.printStackTrace();
		throw new Error("A queue operation cannot be interrupted.");
	} catch (IOException ioe) {
		ioe.printStackTrace();
		throw new Error("Error occurred during creation of accepted socket.");
	}
  }

  public final String socketread(String handle) {
	try {
		Consumer consumer = (Consumer) consumers.get(handle);
		if (consumer == null)
			throw new IllegalAwkArgumentException("Invalid socket handle : "+handle);
		// it's "as if" input_blocker is querying whether to block or not
		if (consumer.willBlock(input_blocker) && consumer.isClosed()) {
			last_err = "No more input.";
			return "";
		}
		return consumer.getInput();
	} catch (InterruptedException ie) {
		ie.printStackTrace();
		throw new Error("A queue operation cannot be interrupted.");
	}
  }

  public final int socketwrite(String handle, String buf) {
	Consumer consumer = (Consumer) consumers.get(handle);
	if (consumer == null)
		throw new IllegalAwkArgumentException("Invalid socket handle : "+handle);
	return consumer.write(buf);
  }

  public final int socketflush(String handle) {
	Consumer consumer = (Consumer) consumers.get(handle);
	if (consumer == null)
		throw new IllegalAwkArgumentException("Invalid socket handle : "+handle);
	return consumer.flush();
  }

  public final int socketclose(String handle) {
	Closeable t = consumers.remove(handle);
	if (t == null)
		t = accepters.remove(handle);
	if (t == null)
		throw new IllegalAwkArgumentException("Invalid [server]socket handle : "+handle);

	int retval;
	try {
		t.close();
		retval = 1;
	} catch (IOException ioe) {
		ioe.printStackTrace();
		retval = 0;
	}
	// interrupt the thread
	t.interrupt();
	// join on the thread
	try {
		t.join();
	} catch (InterruptedException ie) {
		throw new Error("A socket close() cannot be interrupted.");
	}
	return retval;
  }

  //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////

  private class Accepter extends Thread implements Closeable, Blockable {
	private String handle;
	private ServerSocket ssocket;

	// only 1 slot
	protected BlockingQueue<Socket> queue = new ArrayBlockingQueue<Socket>(1);
	//private BlockingQueue<String> queue = new LinkedBlockingQueue<String>();
	public boolean willBlock(BlockObject bo) { return queue.size() == 0; }

	private Accepter(String handle, ServerSocket ssocket)
	throws IOException {
		this.handle = handle;
		//ssocket = ssockets.get(handle);
		this.ssocket = ssocket;
		assert ssocket != null;
	}
	public final void run() {
		if(Thread.currentThread() != Accepter.this)
			throw new Error("Invalid thread access : "+Thread.currentThread());
		try {
			Socket socket;
			while((socket = ssocket.accept()) != null) {
				queue.put(socket);
				synchronized(accept_blocker) {
					accept_blocker.notify();
				}
			}
		} catch (InterruptedException ie) {
			ie.printStackTrace();
			throw new Error("A queue operation cannot be interrupted.");
		} catch (SocketException se) {
			// no big deal
			// assume we should just shutdown now
		} catch (IOException ioe) {
			ioe.printStackTrace();
			// no big deal
		}
		synchronized(close_blocker) {
			close_blocker.notify();
		}
	}
	// can be overridden
	public String getSocket()
	throws IOException, InterruptedException {
		Socket socket = queue.take();
		// ... same as socket() method ...
		String handle = createHandle(socket);
		// start the reader
		Consumer reader_thread = new ByteConsumer(handle, socket);
		reader_thread.start();
		consumers.put(handle, reader_thread);
		return handle;
	}

	public boolean isClosed() {
		return ! isAlive();
	}

	public final void close()
	throws IOException {
		ssocket.close();
	}
  }

  private final class CAccepter extends Accepter {
	private CAccepter(String handle, ServerSocket ssocket)
	throws IOException {
		super(handle, ssocket);
	}

	public final String getSocket()
	throws IOException, InterruptedException {
		Socket socket = queue.take();
		// ... same as socket() method ...
		String handle = createHandle(socket);
		// start the reader
		Consumer reader_thread = new CharacterConsumer(handle, socket);
		reader_thread.start();
		consumers.put(handle, reader_thread);
		return handle;
	}
  }

  //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////

  private interface Joinable extends Blockable {
	void join() throws InterruptedException ;
	void interrupt();
  }

  private interface Closeable extends Joinable {
	boolean isClosed();
	void close() throws IOException ;
  }

  private interface Consumer extends Closeable {
	void start();
	String getInput() throws InterruptedException ;
	int write(String buf);
	int flush();
  }

  private abstract class AbstractConsumer<T> extends Thread implements Consumer {
	private final String handle;
	protected final Socket socket;
	protected final PrintStream ps;
	private int state = ACTIVE_STATE;

	// only 1 slot
	protected BlockingQueue<T> queue = new ArrayBlockingQueue<T>(1);
	public final boolean willBlock(BlockObject bo) {
		if (bo == input_blocker)
			return queue.size() == 0;
		else if (bo == close_blocker)
			return state == ACTIVE_STATE;
		else
			throw new Error("Unknown block object : "+bo.getNotifierTag());
	}

	protected AbstractConsumer(String handle, Socket socket)
	throws IOException {
		this.handle = handle;
		//socket = sockets.get(handle);
		this.socket = socket;
		assert socket != null;
		ps = new PrintStream(socket.getOutputStream(), true);
	}

	protected abstract T readFromSocket() throws IOException ;

	public final void run() {
		if(Thread.currentThread() != AbstractConsumer.this)
			throw new Error("Invalid thread access : "+Thread.currentThread());
		try {
			T input;
			while((input = readFromSocket()) != null) {
				queue.put(input);
				synchronized(input_blocker) {
					input_blocker.notify();
				}
			}
		} catch (InterruptedException ie) {
			ie.printStackTrace();
			throw new Error("A queue operation cannot be interrupted.");
		} catch (SocketException se) {
			// no big deal
			// assume we should just shutdown now
		} catch (IOException ioe) {
			ioe.printStackTrace();
			// no big deal
		}
		synchronized(close_blocker) {
			if (state == ACTIVE_STATE) {
				state = CLOSE_PENDING_STATE;
				close_blocker.notify();
			}
		}
	}

	protected abstract String readFromQueue() throws InterruptedException ;

	public final String getInput()
	throws InterruptedException {
		assert state != CLOSED_STATE;	// active or close_pending
		String str = readFromQueue();
		if (queue.size() == 0 && state == CLOSE_PENDING_STATE)
			synchronized(close_blocker) {
				// could be either ACTIVE or CLOSE_PENDING states
				assert state != CLOSED_STATE;
				close_blocker.notify();
			}
		return str;
	}

	/*
	public final int write(String buf) {
		ps.println(buf);
		return 1;
	}
	*/

	// write is defined in subclasses

	public final int flush() {
		ps.flush();
		return 1;
	}

	public final boolean isClosed() {
		return state == CLOSED_STATE;
	}

	public final void close()
	throws IOException {
		socket.close();
	}
  } // private abstract class AbstractConsumer<T> {Thread} [Consumer]

  private static final int ACTIVE_STATE = 1;
  private static final int CLOSE_PENDING_STATE = 2;
  private static final int CLOSED_STATE = 3;

  private final class CharacterConsumer extends AbstractConsumer<String> {
	private final BufferedReader br;

	private CharacterConsumer(String handle, Socket socket)
	throws IOException {
		super(handle, socket);	// constructs socket (protected field in AbstractConsumer)
		br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	}


	protected final String readFromSocket()
	throws IOException {
		return br.readLine();
	}

	protected final String readFromQueue()
	throws InterruptedException {
		return queue.take();
	}

	public final int write(String buf) {
		ps.println(buf);
		return 1;
	}
  }

  private final class ByteConsumer extends AbstractConsumer<Integer> {
	private final BufferedInputStream bis;
	private final byte[] buf = new byte[4096];

	private ByteConsumer(String handle, Socket socket)
	throws IOException {
		super(handle, socket);	// constructs socket (protected field in AbstractConsumer)
		bis = new BufferedInputStream(socket.getInputStream());
	}

	protected final Integer readFromSocket()
	throws IOException {
		int len = bis.read(buf, 0, buf.length);
		if (len < 0)
			return null;
		else
			return len;
	}

	protected final String readFromQueue()
	throws InterruptedException {
		int len = queue.take();
		String str = new String(buf, 0, len);
		return str;
	}

	public final int write(String buf) {
		try {
			byte[] b = buf.getBytes();
			ps.write(b);
			return 1;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			last_err = ioe.toString();
			return 0;
		}
	}
  }
} // class Threaded_IO_Style [IO_Style]
