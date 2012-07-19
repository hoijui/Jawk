package org.jawk.util;

import java.io.ByteArrayOutputStream;
import java.io.File; // for File.separator
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Load classes from a particular directory, disregarding the
 * environmental class-path setting.
 * This is useful when a directory is specified for class files,
 * and it would not make sense to deviate from that directory.
 * <p>
 * For Jawk, this is used when the -d argument is present.
 */
public final class DestDirClassLoader extends ClassLoader {

	private String dirname;

	public DestDirClassLoader(String dirname) {
		this.dirname = dirname;
	}

	@Override
	protected Class<?> findClass(String name)
			throws ClassNotFoundException {
		byte[] b = loadClassData(name);
		return defineClass(name, b, 0, b.length);
	}

	private byte[] loadClassData(String name)
			throws ClassNotFoundException {
		String filename = dirname + File.separator + name + ".class";
		try {
			FileInputStream f = new FileInputStream(filename);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] b = new byte[4096];
			int len;
			while ((len = f.read(b, 0, b.length)) >= 0) {
				baos.write(b, 0, len);
			}
			f.close();
			baos.close();
			return baos.toByteArray();
		} catch (IOException ioe) {
			ioe.printStackTrace();
			throw new ClassNotFoundException("Could not load " + filename + ": " + ioe);
		}
	}
} // private static final class DestDirClassLoader {ClassLoader}
