package org.jawk;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
			result = AwkTestHelper.runAwk(awkFile, inputFileList);
			String expectedResult = AwkTestHelper.readTextFile(okFile);
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
}
