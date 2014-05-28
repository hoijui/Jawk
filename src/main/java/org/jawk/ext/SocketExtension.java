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
import org.jawk.util.AwkSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enable Socket processing in Jawk.
 * <p>
 * To use:
 * <blockquote><pre>
 * ## example echo server using createCServerSocket (character-based)
 * BEGIN {
 * 	css = createCServerSocket(7777);
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
 * </p>
 * <p>
 * The extension functions are as follows:
 * <ul>
 * <hr>
 * <li><strong><em><font size=+1>createServerSocket</font></em></strong> -<br>
 * Sets up a server createSocket to listen for incoming
 * connections. SocketRead on Sockets accepted
 * by createServerSocket return arbitrary-length Strings
 * (bytes buffered by the input stream, converted
 * to a string).<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>port number - required
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string handle to a createServerSocket.
 * </ul><p>
 * <li><strong><em><font size=+1>createCServerSocket</font></em></strong> -<br>
 * Sets up a server createSocket to listen for incoming
 * connections. SocketRead on Sockets accepted
 * by createCServerSocket return strings which terminate
 * by a newline, or text in the input buffer just
 * prior to the closing of the createSocket.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>port number - required
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string handle to a createCServerSocket.
 * </ul><p>
 * <hr>
 * <li><strong><em><font size=+1>Socket</font></em></strong> -<br>
 * Create a Socket and connect it to a TCP createSocket
 * endpoint. SocketRead on Sockets returned
 * by Socket return arbitrary-length Strings
 * (bytes buffered by the input stream, converted
 * to a string).<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>hostName/IP/"localhost" - required
 * <li>port number - required
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string handle to a Socket.
 * </ul><p>
 * <li><strong><em><font size=+1>createCSocket</font></em></strong> -<br>
 * Create a Socket and connect it to a TCP createSocket
 * endpoint. SocketRead on Sockets returned
 * by Socket return strings which terminate
 * by a newline, or text in the input buffer just
 * prior to the closing of the createSocket.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>hostName/IP/"localhost" - required
 * <li>port number - required
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string handle to a createCSocket.
 * </ul><p>
 * <hr>
 * <li><strong><em><font size=+1>SocketAcceptBlock</font></em></strong> -<br>
 * Blocks until a createServerSocket or createCServerSocket
 * is ready to accept a connecting Socket.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>Any mix of
 * createServerSocket or createCServerSocket handles
 * and/or associative arrays whose keys
 * are createServerSocket or createCServerSocket handles.
 * The last argument can optionally be
 * another block call for block chaining.
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string of the form:
 * <code><font size=+1>SocketAccept<em>OFS</em>handle</font></code>
 * where handle is a createServerSocket or createCServerSocket
 * handle.
 * </ul><p>
 * <li><strong><em><font size=+1>SocketInputBlock</font></em></strong> -<br>
 * Blocks until a Socket or createCSocket is ready
 * to accept input (via SocketRead).<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>Any mix of
 * Socket or createCSocket handles and/or associative
 * arrays whose keys are Socket or createCSocket handles.
 * The last argument can optionally be
 * another block call for block chaining.
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string of the form: <code><font size=+1>SocketInput<em>OFS</em>handle</font></code>
 * where handle is a Socket or createCSocket
 * handle.
 * </ul><p>
 * <li><strong><em><font size=+1>SocketCloseBlock</font></em></strong> -<br>
 * Blocks until a createServerSocket, createCServerSocket,
 * Socket, or createCSocket has been closed on the
 * remote end.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>Any mix of
 * createServerSocket, createCServerSocket, Socket, or createCSocket
 * handles and/or associative
 * arrays whose keys are createServerSocket, createCServerSocket,
 * Socket, or createCSocket handles.
 * The last argument can optionally be
 * another block call for block chaining.
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string of the form: <code><font size=+1>SocketClose<em>OFS</em>handle</font></code>
 * where handle is a createServerSocket, createCServerSocket, Socket,
 * or createCSocket handle.
 * </ul><p>
 * <hr>
 * <li><strong><em><font size=+1>SocketAccept</font></em></strong> -<br>
 * Accepts a Socket from a createServerSocket or
 * a createCServerSocket. The operation will
 * block if there is no Socket to accept.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>createServerSocket-or-createCServerSocket handle - required
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string handle to a Socket or createCSocket.
 * </ul><p>
 * <hr>
 * <li><strong><em><font size=+1>SocketRead</font></em></strong> -<br>
 * Reads input from the input stream of a Socket
 * or a createCSocket. For a Socket, the input length
 * is arbitrary. For a createCSocket, the input
 * length is bounded by a newline or upon
 * termination of the createSocket.
 * The operation will block if there is no input
 * on the createSocket.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>Socket-or-createCSocket handle - required
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>A string containing the input on the createSocket.
 * </ul><p>
 * <li><strong><em><font size=+1>SocketWrite</font></em></strong> -<br>
 * Writes data to the Socket or createCSocket.
 * For a Socket, the string is converted
 * to bytes (via java.lang.String.getBytes()),
 * and the bytes are sent to the createSocket's
 * output stream.
 * For a createCSocket, println() is called on the
 * underlying createSocket's PrintStream.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>Socket-or-createCSocket handle - required
 * <li>msg - required - The string to write to
 * 	the Socket. For a createCSocket, a newline
 * 	is added to it (via the
 * 	java.io.PrintStream.println() method).
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>1 upon a successful write, 0 otherwise
 * </ul><p>
 * <li><strong><em><font size=+1>SocketFlush</font></em></strong> -<br>
 * Flushes the output stream of a Socket or createCSocket.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>Socket-or-createCSocket handle - required
 * </ul>
 * <strong>Returns:</strong>
 * <ul>
 * <li>1 upon a successful flush, 0 otherwise
 * </ul><p>
 * <hr>
 * <li><strong><em><font size=+1>SocketClose</font></em></strong> -<br>
 * Closes the Socket/CSocket on the local end,
 * or a createServerSocket/CServerSocket.
 * Can be called in response to a SocketCloseBlock
 * event, or to force a Socket/CSocket connection to
 * terminate.<br>
 * <strong>Parameters:</strong>
 * <ul>
 * <li>Socket/CSocket/ServerSocket/CServerSocket handle - required
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
	 * Either threaded or non-threaded (nio-style) createSocket
	 * handling. The threaded implementation is provided
	 * upon initial release. Non-threaded
	 * functionality will be available in a subsequent
	 * release.
	 */
	private IOStyle implDelegate;

	@Override
	public final void init(VariableManager vm, JRT jrt, final AwkSettings settings) {
		super.init(vm, jrt, settings);
		implDelegate = new ThreadedIOStyle(vm);
	}

	@Override
	public final String getExtensionName() {
		return "Socket Support";
	}

	@Override
	public final String[] extensionKeywords() {
		return new String[] {
					"ServerSocket", // i.e., ss = createServerSocket(8080) or ss = createServerSocket("ip", 8080)
					"CServerSocket", // i.e., css = createCServerSocket(8080) or css = createCServerSocket("ip", 8080)
					"Socket", // i.e., s = Socket("localhost", 8080)
					"CSocket", // i.e., cs = createCSocket("localhost", 8080)
					"SocketAcceptBlock", // i.e., $0 = SocketAcceptBlock(ss, css,
					"SocketInputBlock", // i.e., SocketInputBlock(s, cs,
					"SocketCloseBlock", // i.e., SocketCloseBlock(ss,css, s,cs)));
					"SocketAccept", // i.e., cs = SocketAccept(css)
					"SocketRead", // i.e., buf = SocketRead(s) or line = SocketRead(cs)
					"SocketWrite", // i.e., SocketWrite(s, "hi there\n") or SocketWrite(cs, "hi there")
					"SocketFlush", // i.e., SocketFlush(s) or SocketFlush(cs)
					"SocketClose", // i.e., SocketClose(ss) or SocketClose(cs)
				};
	}

	@Override
	public final Object invoke(String methodName, Object[] args) {
		// large if-then-else block to decide which extension to invoke
		if        (methodName.equals("ServerSocket")) {
			if (args.length == 1) {
				return implDelegate.createServerSocket(
					null,
					(int) JRT.toDouble(args[0])
				);
			} else if (args.length == 2) {
				return implDelegate.createServerSocket(
					toAwkString(args[0]),
					(int) JRT.toDouble(args[1])
				);
			} else {
				throw new IllegalAwkArgumentException("Expecting 1 or 2 arguments, not " + args.length);
			}
		} else if (methodName.equals("CServerSocket")) {
			if (args.length == 1) {
				return implDelegate.createCServerSocket(
					null,
					(int) JRT.toDouble(args[0])
				);
			} else if (args.length == 2) {
				return implDelegate.createCServerSocket(
					toAwkString(args[0]),
					(int) JRT.toDouble(args[1])
				);
			} else {
				throw new IllegalAwkArgumentException("Expecting 1 or 2 arguments, not " + args.length);
			}
		} else if (methodName.equals("Socket")) {
			checkNumArgs(args, 2);
			return implDelegate.createSocket(
				toAwkString(args[0]),
				(int) JRT.toDouble(args[1])
			);
		} else if (methodName.equals("CSocket")) {
			checkNumArgs(args, 2);
			return implDelegate.createCSocket(
				toAwkString(args[0]),
				(int) JRT.toDouble(args[1])
			);
		} else if (methodName.equals("SocketAcceptBlock")) {
			return implDelegate.socketAcceptBlock(args);
		} else if (methodName.equals("SocketInputBlock")) {
			return implDelegate.socketInputBlock(args);
		} else if (methodName.equals("SocketCloseBlock")) {
			return implDelegate.socketCloseBlock(args);
		} else if (methodName.equals("SocketAccept")) {
			checkNumArgs(args, 1);
			return implDelegate.socketAccept(toAwkString(args[0]));
		} else if (methodName.equals("SocketRead")) {
			checkNumArgs(args, 1);
			return implDelegate.socketRead(toAwkString(args[0]));
		} else if (methodName.equals("SocketWrite")) {
			checkNumArgs(args, 2);
			return implDelegate.socketWrite(
				toAwkString(args[0]),
				toAwkString(args[1])
			);
		} else if (methodName.equals("SocketFlush")) {
			checkNumArgs(args, 1);
			return implDelegate.socketFlush(toAwkString(args[0]));
		} else if (methodName.equals("SocketClose")) {
			checkNumArgs(args, 1);
			return implDelegate.socketClose(toAwkString(args[0]));
		} else {
			throw new NotImplementedError(methodName);
		}
	}
}

//
// INTERFACE TO DELEGATE
//


/**
 * Interface to the createSocket handling delegate which
 * does all the work. The SocketExtension manager
 * class delegates all concrete createSocket IO
 * processing to an instance of this interface.
 */
interface IOStyle {

	/**
	 * Sets up a server createSocket to listen for incoming
	 * connections. SocketRead on sockets accepted
	 * by createServerSocket return arbitrary-length Strings
	 * (bytes buffered by the input stream, converted
	 * to a string).
	 *
	 * @param hostname The host-name or IP address as a string.
	 *   host-name can be null.
	 * @param port The port number.
	 *
	 * @return A handle to a newly created createServerSocket.
	 */
	String createServerSocket(String hostname, int port);

	/**
	 * Sets up a server createSocket to listen for incoming
	 * connections. SocketRead on sockets accepted
	 * by createCServerSocket return strings which terminate
	 * by a newline, or text in the input buffer just
	 * prior to the closing of the createSocket.
	 *
	 * @param hostname The host-name or IP address as a string.
	 *   host-name can be null.
	 * @param port The port number.
	 *
	 * @return A handle to a newly created createCServerSocket.
	 */
	String createCServerSocket(String hostname, int port);

	/**
	 * Create a Socket and connect it to a TCP createSocket
	 * endpoint. SocketRead on sockets returned
	 * by Socket return arbitrary-length Strings
	 * (bytes buffered by the input stream, converted
	 * to a string).
	 *
	 * @param hostname The host-name or IP address as a string.
	 *   host-name can be null.
	 * @param port The port number.
	 *
	 * @return A handle to a newly created Socket.
	 */
	String createSocket(String hostname, int port);

	/**
	 * Create a Socket and connect it to a TCP createSocket
	 * endpoint. SocketRead on sockets returned
	 * by Socket return strings which terminate
	 * by a newline, or text in the input buffer just
	 * prior to the closing of the createSocket.
	 *
	 * @param hostname The host-name or IP address as a string.
	 *   host-name can be null.
	 * @param port The port number.
	 *
	 * @return A handle to a newly created createCSocket.
	 */
	String createCSocket(String hostname, int port);

	/**
	 * Blocks until a createServerSocket or createCServerSocket
	 * is ready to accept a connecting createSocket.
	 *
	 * @param args An array of
	 *   createServerSocket or createCServerSocket handles
	 *   and/or associative arrays whose keys
	 *   are createServerSocket or createCServerSocket handles.
	 *   The last argument can optionally be
	 *   another block call for block chaining.
	 *
	 * @return A block object conditioned
	 *   to block on the acceptance of
	 *   createSocket connections from any of
	 *   the ServerSockets / CServerSockets
	 *   referred to by the handles passed
	 *   in to the object array.
	 */
	BlockObject socketAcceptBlock(Object[] args);

	/**
	 * Blocks until a createSocket or createCSocket is ready
	 * to accept input (via SocketRead).
	 *
	 * @param args An array of
	 * createSocket or createCSocket handles and/or associative
	 * arrays whose keys are createSocket or createCSocket handles.
	 * The last argument can optionally be
	 * another block call for block chaining.
	 *
	 * @return A block object conditioned
	 *   to block on the availability of
	 *   input from any of the sockets / CSockets
	 *   referred to by the handles passed
	 *   in to the object array.
	 */
	BlockObject socketInputBlock(Object[] args);

	/**
	 * Blocks until a createServerSocket, createCServerSocket,
	 * createSocket, or createCSocket has been closed on the
	 * remote end.
	 *
	 * @param args An array of
	 *   createServerSocket, createCServerSocket, createSocket, or createCSocket
	 *   handles and/or associative
	 *   arrays whose keys are createServerSocket, createCServerSocket,
	 *   createSocket, or createCSocket handles.
	 *   The last argument can optionally be
	 *   another block call for block chaining.
	 *
	 * @return A block object conditioned
	 *   to block until any of the sockets /
	 *   CSockets / ServerSockets / CServerSockets
	 *   in to the object array have closed.
	 */
	BlockObject socketCloseBlock(Object[] args);

	/**
	 * Accepts a createSocket from a createServerSocket or
	 * a createCServerSocket. The operation will
	 * block if there is no createSocket to accept.
	 *
	 * @param handle A string handle to a createServerSocket
	 *   or createCServerSocket.
	 *
	 * @return A handle to a createSocket or createCSocket that
	 *   has connected to the createServerSocket / createCServerSocket
	 *   referred to by the handle argument.
	 */
	String socketAccept(String handle);

	/**
	 * Reads input from the input stream of a createSocket
	 * or a createCSocket. For a createSocket, the input length
	 * is arbitrary. For a createCSocket, the input
	 * length is bounded by a newline or upon
	 * termination of the createSocket.
	 * The operation will block if there is no input
	 * on the createSocket.
	 *
	 * @param handle A string handle to a createSocket
	 *   or createCSocket.
	 *
	 * @return A block of byte input from a createSocket
	 *   (converted to a string), or a line of
	 *   string input from a createCSocket bounded by
	 *   a newline in the stream or upon the closing
	 *   of the createCSocket.
	 */
	String socketRead(String handle);

	/**
	 * Writes data to the createSocket or createCSocket.
	 * For a createSocket, the string is converted
	 * to bytes (via java.lang.String.getBytes()),
	 * and the bytes are sent to the underlying
	 * createSocket's output stream.
	 * For a createCSocket, println() is called on the
	 * underlying createSocket's PrintStream.
	 *
	 * @param handle A string handle to a createSocket
	 *   or createCSocket.
	 * @param buf The string containing the
	 *   bytes to write. SocketWrite writes
	 *   the contents of the resulting buf.getBytes()
	 *   call to the createSocket.
	 *
	 * @param handle A String handle to a createSocket
	 *   or createCSocket.
	 * @param buf A string containing a block of
	 *   bytes to write to a createSocket (via
	 *   {@see java.lang.String#getBytes()}) if handle
	 *   refers to a createSocket. If handle refers
	 *   to a createCSocket, the line of text to write
	 *   via {@see PrintStream#println(String)}.
	 *
	 * @return 1 upon a successful write,
	 *   0 upon an IO exception/error.
	 */
	int socketWrite(String handle, String buf);

	/**
	 * Flushes the output stream of a createSocket or createCSocket.
	 *
	 * @param handle A string handle to a createSocket
	 * or createCSocket.
	 *
	 * @return 1 upon a successful flush operation,
	 * 0 upon an IO exception/error.
	 */
	int socketFlush(String handle);

	/**
	 * Closes the createSocket/CSocket on the local end,
	 * or a createServerSocket/CServerSocket.
	 * Can be called in response to a SocketCloseBlock
	 * event, or to force a createSocket/CSocket connection to
	 * terminate.
	 *
	 * @param handle A string handle to a createSocket,
	 * 	createCSocket, createServerSocket, or createCServerSocket.
	 *
	 * @return 1 upon a successful close operation,
	 * 0 upon an IO exception/error.
	 */
	int socketClose(String handle);
}

/**
 * A view of two maps as one map.
 */
class MapUnion<K, V> extends AbstractMap<K, V> {

	private Map<K, V> m1;
	private Map<K, V> m2;

	MapUnion(Map<K, V> m1, Map<K, V> m2) {
		this.m1 = m1;
		this.m2 = m2;
	}

	@Override
	public final Set<Map.Entry<K, V>> entrySet() {
		// build the entry set
		Set<Map.Entry<K, V>> entries = new HashSet<Map.Entry<K, V>>();

		Set<Map.Entry<K, V>> s1 = m1.entrySet();
		Set<Map.Entry<K, V>> s2 = m2.entrySet();
		for (Map.Entry<K, V> me : s1) {
			entries.add(me);
		}
		for (Map.Entry<K, V> me : s2) {
			entries.add(me);
		}

		return entries;
	}
}

class ThreadedIOStyle implements IOStyle {

	private static final Logger LOG = LoggerFactory.getLogger(ThreadedIOStyle.class);

	private String lastError = null;

	/**
	 * Map of "Socket"/"createCSocket" handles to
	 * the objects which perform the actual
	 * read and block operations.
	 * <p>
	 * <strong>Note:</strong>
	 * "consumers" originally was of type
	 * <code>Map<String, Consumer></code>, but changed to <code>...,Closeable</code>.
	 * (Likewise, "accepters" was originally ...,Accepter,
	 * but then changed to ...,Closeable.)
	 * Why? Because MapUnion could not infer that "Consumer"
	 * nor "Accepter" were extensions of "Blockable".
	 * MapUnion originally accepted 3 generic parameters:
	 * K, V1 extends Blockable, and V2 extends <code>Blockable</code>.
	 * And, closeBlocker's BulkBlockObject Map parameter was:
	 * <code>new MapUnion<String, Accepter, Consumer>(accepters, consumers)</code>.
	 * But, it wouldn't compile.
	 * The resulting warning/error messages stated that
	 * "capture #XXX of ? extends Blockable" does not
	 * match "? extends Blockable". I believe the error
	 * results from Blockable being compiled against
	 * Java 1.5.x and extensions being developed against
	 * Java 1.6.x, and that they don't grok one another
	 * in this scenario.
	 * We, then, decided to assign its lowest common
	 * subclass, "Closeable", and typecast when we need
	 * specific "Accepter" and "Consumer" functionality.
	 * This resolved the issue while losing some
	 * compile-time type safety.
	 * </p>
	 */
	private final Map<String, Closeable> consumers = new HashMap<String, Closeable>();

	/**
	 * Map of "createServerSocket"/"createCServerSocket" handles
	 * to the objects which perform the actual
	 * createSocket accept operation.
	 * <p>
	 * <strong>Note:</strong>
	 * See lengthy diatribe above for "consumers".
	 * The same applies for "accepters"'s generic
	 * type choice for values of the Map.
	 */
	private final Map<String, Closeable> accepters = new HashMap<String, Closeable>();

	private final VariableManager vm;

	private final BulkBlockObject acceptBlocker;
	private final BulkBlockObject inputBlocker;
	private final BulkBlockObject closeBlocker;

	private int socketIdx = 0;
	private int ssocketIdx = 0;

	ThreadedIOStyle(VariableManager vm) {
		assert vm != null;
		this.vm = vm;
		acceptBlocker = new BulkBlockObject("SocketAccept", accepters, vm);
		inputBlocker = new BulkBlockObject("SocketInput", consumers, vm);
		closeBlocker = new BulkBlockObject("SocketClose", new MapUnion<String, Closeable>(accepters, consumers), vm);
	}

	@Override
	public final String createServerSocket(String hostname, int port) {
		try {
			ServerSocket ss;
			if (hostname == null) {
				ss = new ServerSocket(port);
			} else { // 0 = default backlog
				ss = new ServerSocket(port, 0, InetAddress.getByName(hostname));
			}
			String handle = createHandle(ss);
			//ssockets.put(handle, ss);
			Accepter accepterThread = new Accepter(handle, ss);
			accepters.put(handle, accepterThread);
			accepterThread.start();
			return handle;
		} catch (IOException ioe) {
			LOG.error("Failed to create ServerSocket for " + hostname + ":" + port, ioe);
			lastError = ioe.toString();
			return "";
		}
	}

	@Override
	public final String createCServerSocket(String hostName, int port) {
		try {
			ServerSocket ss;
			if (hostName == null) {
				ss = new ServerSocket(port);
			} else { // 0 = default backlog
				ss = new ServerSocket(port, 0, InetAddress.getByName(hostName));
			}
			String handle = createHandle(ss);
			//ssockets.put(handle, ss);
			Accepter accepterThread = new CAccepter(handle, ss);
			accepters.put(handle, accepterThread);
			accepterThread.start();
			return handle;
		} catch (IOException ioe) {
			LOG.error("Failed to create CServerSocket for " + hostName + ":" + port, ioe);
			lastError = ioe.toString();
			return "";
		}
	}

	@Override
	public final String createSocket(String hostname, int port) {
		// create the createSocket
		try {
			Socket socket = new Socket(hostname, port);
			String handle = createHandle(socket);
			//sockets.put(handle, createSocket);
			// start the reader
			Consumer readerThread = new ByteConsumer(handle, socket);
			consumers.put(handle, readerThread);
			readerThread.start();
			return handle;
		} catch (IOException ioe) {
			LOG.error("Failed to create Socket for " + hostname + ":" + port, ioe);
			lastError = ioe.toString();
			return "";
		}
	}

	@Override
	public final String createCSocket(String hostname, int port) {
		try {
			// create the createSocket
			Socket socket = new Socket(hostname, port);
			String handle = createHandle(socket);
			//sockets.put(handle, createSocket);
			// start the reader
			Consumer readerThread = new CharacterConsumer(handle, socket);
			consumers.put(handle, readerThread);
			readerThread.start();
			return handle;
		} catch (IOException ioe) {
			LOG.error("Failed to create CSocket for " + hostname + ":" + port, ioe);
			lastError = ioe.toString();
			return "";
		}
	}

	private String createHandle(Socket socket) {
		return "Socket:" + socket.getInetAddress().toString() + ":" + socket.getPort() + "/" + (++socketIdx);
	}

	private String createHandle(ServerSocket ssocket) {
		return "ServerSocket:" + ssocket.getInetAddress().toString() + ":" + ssocket.getLocalPort() + "/" + (++ssocketIdx);
	}

	/*private final String getFS() {
		return toAwkString(vm.getFS());
	}*/

	//////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////

	private final BlockHandleValidator acceptHandleValidator = new BlockHandleValidator() {

		@Override
		public String isBlockHandleValid(String handle) {
			Closeable closeable = accepters.get(handle);
			if (closeable == null) {
				return "Invalid ServerSocket handle.";
			}
			if (closeable.isClosed()) {
				return "ServerSocket is closed.";
			} else {
				return null;	// valid
			}
		}
	};

	private final BlockHandleValidator inputHandleValidator = new BlockHandleValidator() {

		@Override
		public String isBlockHandleValid(String handle) {
			Closeable closeable = consumers.get(handle);
			if (closeable == null) {
				return "Invalid socket handle. (Could have already been closed?)";
			}
			if (closeable.isClosed()) {
				return "Socket is closed.";
			} else {
				return null;	// valid
			}
		}
	};

	private final BlockHandleValidator closeHandleValidator = new BlockHandleValidator() {

		@Override
		public String isBlockHandleValid(String handle) {
			Closeable closeable = accepters.get(handle);
			if (closeable == null) {
				closeable = consumers.get(handle);
			}
			if (closeable == null) {
				return "Invalid socket handle. (Could have already been closed?)";
			}
			if (closeable.isClosed()) {
				return "Socket is already closed.";
			} else {
				return null;	// valid
			}
		}
	};

	@Override
	public final BlockObject socketAcceptBlock(Object[] args) {
		return acceptBlocker.setHandles(args, vm, acceptHandleValidator);
	}

	@Override
	public final BlockObject socketInputBlock(Object[] args) {
		return inputBlocker.setHandles(args, vm, inputHandleValidator);
	}

	@Override
	public final BlockObject socketCloseBlock(Object[] args) {
		return closeBlocker.setHandles(args, vm, closeHandleValidator);
	}

	@Override
	public final String socketAccept(String handle) {
		try {
			Accepter accepter = (Accepter) accepters.get(handle);
			if (accepter == null) {
				throw new IllegalAwkArgumentException("Invalid server socket handle : " + handle);
			}
			// it's "as if" acceptBlocker is querying whether to block or not
			if (accepter.willBlock(acceptBlocker) && accepter.isClosed()) {
				lastError = "Server closed.";
				return "";
			}
			return accepter.getSocket();
		} catch (InterruptedException ie) {
			throw new Error("A queue operation cannot be interrupted.", ie);
		} catch (IOException ioe) {
			throw new Error("Error occurred during creation of accepted socket.", ioe);
		}
	}

	@Override
	public final String socketRead(String handle) {
		try {
			Consumer consumer = (Consumer) consumers.get(handle);
			if (consumer == null) {
				throw new IllegalAwkArgumentException("Invalid socket handle : " + handle);
			}
			// it's "as if" inputBlocker is querying whether to block or not
			if (consumer.willBlock(inputBlocker) && consumer.isClosed()) {
				lastError = "No more input.";
				return "";
			}
			return consumer.getInput();
		} catch (InterruptedException ie) {
			throw new Error("A queue operation cannot be interrupted.", ie);
		}
	}

	@Override
	public final int socketWrite(String handle, String buf) {
		Consumer consumer = (Consumer) consumers.get(handle);
		if (consumer == null) {
			throw new IllegalAwkArgumentException("Invalid socket handle : " + handle);
		}
		return consumer.write(buf);
	}

	@Override
	public final int socketFlush(String handle) {
		Consumer consumer = (Consumer) consumers.get(handle);
		if (consumer == null) {
			throw new IllegalAwkArgumentException("Invalid socket handle : " + handle);
		}
		return consumer.flush();
	}

	@Override
	public final int socketClose(String handle) {
		Closeable t = consumers.remove(handle);
		if (t == null) {
			t = accepters.remove(handle);
		}
		if (t == null) {
			throw new IllegalAwkArgumentException("Invalid [server]socket handle : " + handle);
		}

		int retval;
		try {
			t.close();
			retval = 1;
		} catch (IOException ioe) {
			LOG.warn("Failed to close socket " + handle, ioe);
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

//		private String handle;
		private ServerSocket serverSocket;

		// only 1 slot
		private BlockingQueue<Socket> queue = new ArrayBlockingQueue<Socket>(1);

		private Accepter(String handle, ServerSocket serverSocket)
				throws IOException
		{
//			this.handle = handle;
			//this.handle = serverSocket.get(handle);
			this.serverSocket = serverSocket;
			assert serverSocket != null;
		}

		@Override
		public boolean willBlock(BlockObject bo) {
			return queue.size() == 0;
		}

		@Override
		public final void run() {
			if (Thread.currentThread() != Accepter.this) {
				throw new Error("Invalid thread access : " + Thread.currentThread());
			}
			try {
				Socket socket;
				while ((socket = serverSocket.accept()) != null) {
					queue.put(socket);
					synchronized (acceptBlocker) {
						acceptBlocker.notify();
					}
				}
			} catch (InterruptedException ie) {
				throw new Error("A queue operation cannot be interrupted.", ie);
			} catch (SocketException se) {
				// no big deal
				// TODO? assume we should just shutdown now
			} catch (IOException ioe) {
				LOG.warn("Failed to accept on the server-socket", ioe);
				// no big deal
			}
			synchronized (closeBlocker) {
				closeBlocker.notify();
			}
		}

		protected BlockingQueue<Socket> getQueue() {
			return queue;
		}

		public String getSocket()
				throws IOException, InterruptedException
		{
			Socket socket = queue.take();
			// ... same as createSocket() method ...
			String handle = createHandle(socket);
			// start the reader
			Consumer readerThread = new ByteConsumer(handle, socket);
			readerThread.start();
			consumers.put(handle, readerThread);
			return handle;
		}

		@Override
		public boolean isClosed() {
			return !isAlive();
		}

		@Override
		public final void close()
				throws IOException
		{
			serverSocket.close();
		}
	}

	private final class CAccepter extends Accepter {

		private CAccepter(String handle, ServerSocket ssocket)
				throws IOException
		{
			super(handle, ssocket);
		}

		@Override
		public String getSocket()
				throws IOException, InterruptedException
		{
			Socket socket = getQueue().take();
			// ... same as createSocket() method ...
			String handle = createHandle(socket);
			// start the reader
			Consumer readerThread = new CharacterConsumer(handle, socket);
			readerThread.start();
			consumers.put(handle, readerThread);
			return handle;
		}
	}

	//////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////

	private interface Joinable extends Blockable {

		void join() throws InterruptedException;

		void interrupt();
	}

	private interface Closeable extends Joinable {

		boolean isClosed();

		void close() throws IOException;
	}

	private interface Consumer extends Closeable {

		void start();

		String getInput() throws InterruptedException;

		int write(String buf);

		int flush();
	}

	private abstract class AbstractConsumer<T> extends Thread implements Consumer {

//		private final String handle;
		private final Socket socket;
		private final PrintStream printStream;
		private int state = ACTIVE_STATE;

		// only 1 slot
		private BlockingQueue<T> queue = new ArrayBlockingQueue<T>(1);

		protected AbstractConsumer(String handle, Socket socket)
				throws IOException
		{
//			this.handle = handle;
			//socket = sockets.get(handle);
			this.socket = socket;
			assert socket != null;
			printStream = new PrintStream(socket.getOutputStream(), true);
		}

		@Override
		public final boolean willBlock(BlockObject bo) {
			if (bo == inputBlocker) {
				return queue.size() == 0;
			} else if (bo == closeBlocker) {
				return state == ACTIVE_STATE;
			} else {
				throw new Error("Unknown block object : " + bo.getNotifierTag());
			}
		}

		protected abstract T readFromSocket() throws IOException;

		@Override
		public final void run() {
			if (Thread.currentThread() != AbstractConsumer.this) {
				throw new Error("Invalid thread access : " + Thread.currentThread());
			}
			try {
				T input;
				while ((input = readFromSocket()) != null) {
					queue.put(input);
					synchronized (inputBlocker) {
						inputBlocker.notify();
					}
				}
			} catch (InterruptedException ie) {
				throw new Error("A queue operation cannot be interrupted.", ie);
			} catch (SocketException se) {
				// no big deal
				// TODO? assume we should just shutdown now
			} catch (IOException ioe) {
				LOG.warn("Failed to read from socket", ioe);
				// no big deal
			}
			synchronized (closeBlocker) {
				if (state == ACTIVE_STATE) {
					state = CLOSE_PENDING_STATE;
					closeBlocker.notify();
				}
			}
		}

		protected abstract String readFromQueue() throws InterruptedException;

		protected PrintStream getPrintStream() {
			return printStream;
		}

		protected BlockingQueue<T> getQueue() {
			return queue;
		}

		@Override
		public final String getInput()
				throws InterruptedException
		{
			assert state != CLOSED_STATE;	// active or closePending
			String str = readFromQueue();
			if (queue.size() == 0 && state == CLOSE_PENDING_STATE) {
				synchronized (closeBlocker) {
					// could be either ACTIVE or CLOSE_PENDING states
					assert state != CLOSED_STATE;
					closeBlocker.notify();
				}
			}
			return str;
		}

		@Override
		public final int flush() {
			printStream.flush();
			return 1;
		}

		@Override
		public final boolean isClosed() {
			return state == CLOSED_STATE;
		}

		@Override
		public final void close()
				throws IOException
		{
			socket.close();
		}
	}

	private static final int ACTIVE_STATE = 1;
	private static final int CLOSE_PENDING_STATE = 2;
	private static final int CLOSED_STATE = 3;

	private final class CharacterConsumer extends AbstractConsumer<String> {

		private final BufferedReader br;

		private CharacterConsumer(String handle, Socket socket)
				throws IOException
		{
			super(handle, socket);	// constructs createSocket (protected field in AbstractConsumer)
			br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		}

		@Override
		protected String readFromSocket()
				throws IOException
		{
			return br.readLine();
		}

		@Override
		protected String readFromQueue()
				throws InterruptedException
		{
			return getQueue().take();
		}

		@Override
		public int write(String buf) {
			getPrintStream().println(buf);
			return 1;
		}
	}

	private final class ByteConsumer extends AbstractConsumer<Integer> {

		private final BufferedInputStream bis;
		private final byte[] readBuffer = new byte[4096];

		private ByteConsumer(String handle, Socket socket)
				throws IOException
		{
			super(handle, socket);	// constructs createSocket (protected field in AbstractConsumer)
			bis = new BufferedInputStream(socket.getInputStream());
		}

		@Override
		protected Integer readFromSocket()
				throws IOException
		{
			int len = bis.read(readBuffer, 0, readBuffer.length);
			if (len < 0) {
				return null;
			} else {
				return len;
			}
		}

		@Override
		protected String readFromQueue()
				throws InterruptedException
		{
			int len = getQueue().take();
			String str = new String(readBuffer, 0, len);
			return str;
		}

		@Override
		public int write(String buffer) {
			try {
				byte[] b = buffer.getBytes();
				getPrintStream().write(b);
				return 1;
			} catch (IOException ioe) {
				LOG.warn("Failed to write buffer", ioe);
				lastError = ioe.toString();
				return 0;
			}
		}
	}
}
