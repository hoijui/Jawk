package org.jawk;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jawk.util.AwkSettings;
import org.jawk.util.ScriptFileSource;
import org.jawk.util.ScriptSource;

public class AwkTestHelper {

	/** Temporary directory where to store temporary stuff */
	private static String tempDirectory;
	static {
    	Path tempDirectoryPath;
		try {
			tempDirectoryPath = Files.createTempDirectory("jawk-gawk-test");
	    	tempDirectoryPath.toFile().deleteOnExit();
	    	tempDirectory = tempDirectoryPath.toFile().getAbsolutePath();
	    	} catch (IOException e) {
	    		e.printStackTrace();
	    	}
	}
	
	/**
	 * Executes the specified AWK script
	 * <p>
	 * @param scriptFile File containing the AWK script to execute
	 * @param inputFileList List of files that contain the input to be parsed by the AWK script
	 * @return the printed output of the script as a String
	 * @throws ExitException when the AWK script forces its exit with a specified code
	 * @throws IOException on I/O problems
	 * @throws ClassNotFoundException 
	 */
	static String runAwk(File scriptFile, List<String> inputFileList) throws IOException, ExitException, ClassNotFoundException {
		
		AwkSettings settings = new AwkSettings();
		
		// Set the input files
		settings.getNameValueOrFileNames().addAll(inputFileList);
		
		// Set TEMPDIR so the AWK scripts can "play" with it
		settings.getNameValueOrFileNames().add("TEMPDIR=" + tempDirectory);

		// Create the OutputStream, to collect the result as a String
		ByteArrayOutputStream resultBytesStream = new ByteArrayOutputStream();
    	settings.setOutputStream(new UniformPrintStream(resultBytesStream));
    	
    	// Sets the AWK script to execute
    	settings.addScriptSource(new ScriptFileSource(scriptFile.getAbsolutePath()));
    	
    	// Execute the awk script against the specified input
		Awk awk = new Awk();
		awk.invoke(settings);
		
		// Return the result as a string
		return resultBytesStream.toString(StandardCharsets.UTF_8);

	}
	

	/**
	 * Executes the specified script against the specified input
	 * <p>
	 * @param script AWK script to execute (as a String)
	 * @param input Text to process (as a String)
	 * @return result as a String
	 * @throws ExitException when the AWK script forces its exit with a specified code
	 * @throws IOException on I/O problems
	 * @throws ClassNotFoundException 
	 */
	static String runAwk(String script, String input) throws IOException, ExitException, ClassNotFoundException {
		
		AwkSettings settings = new AwkSettings();
		
		// Set the input
		if (input != null) {
			settings.setInput(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
		}
		
       	// We force \n as the Record Separator (RS) because even if running on Windows
       	// we're passing Java strings, where end of lines are simple \n
       	settings.setDefaultRS("\n");
       	
       	// Create the OutputStream, to collect the result as a String
		ByteArrayOutputStream resultBytesStream = new ByteArrayOutputStream();
    	settings.setOutputStream(new UniformPrintStream(resultBytesStream));
    	
    	// Sets the AWK script to execute
    	settings.addScriptSource(new ScriptSource("Body", new StringReader(script), false));
    	
    	// Execute the awk script against the specified input
		Awk awk = new Awk();
		awk.invoke(settings);
		
		// Return the result as a string
		return resultBytesStream.toString(StandardCharsets.UTF_8);

	}


	/**
	 * Reads the specified resource file and returns its content as a String
	 *
	 * @param path Path to the resource file
	 * @return The content of the resource file as a String
	 * @throws IOException 
	 */
	static String readTextFile(File textFile) throws IOException {
	
		StringBuilder builder = new StringBuilder();
	
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(textFile)))) {
			String l;
			while ((l = reader.readLine()) != null) {
				builder.append(l).append('\n');
			}
			
		}
	
		return builder.toString();
	}
	

}
