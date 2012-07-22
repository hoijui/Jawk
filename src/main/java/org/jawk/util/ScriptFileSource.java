package org.jawk.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.Reader;

/**
 * Represents one AWK-script file content source.
 */
public class ScriptFileSource extends ScriptSource {

	private String filePath;
	private Reader fileReader;
	private InputStream fileInputStream;

	public ScriptFileSource(String filePath) {
		super(filePath, null, filePath.endsWith(".ai"));

		this.filePath = filePath;
		this.fileReader = null;
		this.fileInputStream = null;
	}

	public String getFilePath() {
		return filePath;
	}

	@Override
	public Reader getReader() {

		if ((fileReader == null) && !isIntermediate()) {
			try {
				fileReader = new FileReader(filePath);
			} catch (FileNotFoundException ex) {
				ex.printStackTrace();
			}
		}

		return fileReader;
	}

	@Override
	public InputStream getInputStream() {

		if ((fileInputStream == null) && isIntermediate()) {
			try {
				fileInputStream = new FileInputStream(filePath);
			} catch (FileNotFoundException ex) {
				ex.printStackTrace();
			}
		}

		return fileInputStream;
	}
}
