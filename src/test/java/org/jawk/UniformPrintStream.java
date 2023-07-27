package org.jawk;

import java.io.OutputStream;
import java.io.PrintStream;

public class UniformPrintStream extends PrintStream {

	public UniformPrintStream(OutputStream out) {
		super(out);
	}
	
	@Override
	public void println() {
		this.write('\n');
	}

}
