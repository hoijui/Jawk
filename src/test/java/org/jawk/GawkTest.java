package org.jawk;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jawk.util.AwkSettings;
import org.jawk.util.ScriptFileSource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test Suite based on unit and non-regression tests from gawk.
 * 
 * Each AWK script in the src/test/resources/gawk directory will be
 * executed against the corresponding *.in input, and its output will
 * be compared to the corresponding *.ok file.
 *
 */
@RunWith(Parameterized.class)
public class GawkTest {

	/** Temporary directory where to store temporary stuff */
	private static String tempDirectory;
	
	/** Counter of executed tests */
	private static AtomicInteger testCount = new AtomicInteger(0);
	
	/** Counter of the tests that succeeded */
	private static AtomicInteger successCount = new AtomicInteger(0);
	
    /**
     * Initialization of the tests (create a temporary directory for some of the scripts)
     * @throws Exception
     */
    @BeforeClass
    public static void beforeAll() throws Exception {
    	Path tempDirectoryPath = Files.createTempDirectory("jawk-gawk-test");
    	tempDirectoryPath.toFile().deleteOnExit();
    	tempDirectory = tempDirectoryPath.toFile().getAbsolutePath();
    }
    
    /**
     * @return the list of awk scripts in /src/test/resources/gawk
     * @throws Exception
     */
    @Parameters(name = "GAWK {0}")
    public static Iterable<String> awkList() throws Exception {
    	
		// Get the /gawk resource directory
		URL scriptsUrl = GawkTest.class.getResource("/gawk");
		if (scriptsUrl == null) {
			throw new IOException("Couldn't find resource /gawk");
		}

		File scriptsDir = new File(scriptsUrl.toURI());
		if (!scriptsDir.isDirectory()) {
			throw new IOException("/gawk is not a directory");
		}

		return Arrays.stream(scriptsDir.listFiles())
			.filter(sf -> sf.getName().toLowerCase().endsWith(".awk"))
			.map(File::getAbsolutePath)
			.collect(Collectors.toList());

    }

    /** Path to the AWK test script to execute */
    @Parameter
    public String awkPath;

	/**
	 * Execute the AWK script stored in {@link #awkPath}
	 * @throws Exception
	 */
	@Test
	public void test() throws Exception {

		// Get the AWK script file and parent directory
		File awkFile = new File(awkPath);
		String shortName = awkFile.getName().substring(0, awkFile.getName().length() - 4);
		File parent = awkFile.getParentFile();
		System.out.printf("Testing Jawk against gawk with %-15s:", shortName);
		testCount.incrementAndGet();
		
		// Get the file with the expected result
		File okFile = new File(parent, shortName + ".ok");
		
		// Get the list of input files (usually *.in, but could be *.in1, *.in2, etc.)
		List<String> inputFileList = IntStream.range(0, 10)
			.mapToObj(i -> (i == 0 ? "" : String.valueOf(i)))
			.map(i -> new File(parent, shortName + ".in" + i))
			.filter(File::isFile)
			.map(File::getAbsolutePath)
			.collect(Collectors.toList());
		
		String result;
		try {
			result = runAwk(awkFile, inputFileList);
			String expectedResult = readTextFile(okFile);
			if (expectedResult != null && expectedResult.equals(result)) {
				System.out.println("Success");
				successCount.incrementAndGet();
			} else {
				System.out.println("FAILED");
				System.err.printf("Test %s expected:\n<%s>\nbut was:\n<%s>\n", shortName, expectedResult, result);
			}
		} catch (Exception e) {
			System.out.println("FAILED");
			System.err.printf("Test %s threw %s: %s\n", shortName, e.getClass().getSimpleName(), e.getMessage());
		}
		
	}

	/**
	 * Executes the specified AWK script
	 * <p>
	 * @param scriptFile File containing the AWK script to execute
	 * @param inputFileList List of files that contain the input to be parsed by the AWK script
	 * @return the printed output of the script as a String
	 * @throws Exception in case of problems when parsing or executing the AWK script
	 */
	private String runAwk(File scriptFile, List<String> inputFileList) throws Exception {
		
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
     * Initialization of the tests (create a temporary directory for some of the scripts)
     * @throws Exception
     */
    @AfterClass
    public static void afterAll() throws Exception {
    	
    	double successPercentage = (double)successCount.get() / (double)testCount.get() * 100d;
    	
    	System.out.printf(
    			"\n========\nTEST RESULTS: Jawk compliant with gawk on %d/%d tests (%.1f%%)\n========\n", 
    			successCount.get(),
    			testCount.get(),
    			successPercentage
    	);
    	assertTrue("Jawk vs Gawk success rate must be > 19.8%", successPercentage > 19.8);
    }
    

	/**
	 * Reads the specified resource file and returns its content as a String
	 *
	 * @param path Path to the resource file
	 * @return The content of the resource file as a String
	 * @throws IOException 
	 */
	private static String readTextFile(File textFile) throws IOException {

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
