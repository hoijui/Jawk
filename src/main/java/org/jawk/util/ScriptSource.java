package org.jawk.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * Represents one AWK-script content source.
 * This is usually either a string,
 * given on the command line with the first non-"-" parameter,
 * or an "*.awk" (normal) or "*.ai" (intermediate) script,
 * given as a path with a "-f" command line switch.
 */
public class ScriptSource {

	public static final String DESCRIPTION_COMMAND_LINE_SCRIPT
			= "<command-line-supplied-script>";

	private String description;
	private Reader reader;
	private boolean intermediate;

	public ScriptSource(String description, Reader reader, boolean intermediate) {

		this.description = description;
		this.reader = reader;
		this.intermediate = intermediate;
	}

	public final String getDescription() {
		return description;
	}

	/**
	 * Obtain the InputStream containing the intermediate file.
	 * This returns non-null only if {@see #isIntermediate()}
	 * returns <code>false</code>.
	 *
	 * @return The reader which contains the intermediate file, null if
	 *   either the -f argument is not used, or the argument does not
	 *   refer to an intermediate file.
	 */
	public Reader getReader()
			throws IOException
	{
		return reader;
	}

	/**
	 * Returns the <code>InputStream</code> serving the contents of this source.
	 * This returns non-<code>null</code> only if {@see #isIntermediate()}
	 * returns <code>true</code>.
	 */
	public InputStream getInputStream()
			throws IOException
	{
		return null;
	}

	/**
	 * Indicates whether the underlying source is an intermediate file.
	 * Intermediate files end with the ".ai" extension.
	 * No other determination is made whether the file is an intermediate
	 * one or not.
	 * That is, the content of the file is not checked.
	 *
	 * @return <code>true</code> if the "-f optarg" is an intermediate file
	 *   (a file ending in ".ai"), <code>false</code> otherwise.
	 */
	public final boolean isIntermediate() {
		return intermediate;
	}

	@Override
	public String toString() {
		return getDescription();
	}
}
