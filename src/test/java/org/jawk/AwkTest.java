package org.jawk;

import static org.junit.Assert.assertArrayEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.ExpectedException;

@SuppressWarnings("static-method")
public class AwkTest {

	private static final boolean IS_WINDOWS = (System.getProperty("os.name").contains("Windows"));

	private static final String LF = System.getProperty("line.separator");

	@SafeVarargs
	static <T> T[] array(T ... vals) {
		return vals;
	}

	static String[] monotoneArray(final String val, final int num) {
		return Collections.nCopies(num, val).toArray(new String[num]);
	}

	static void awk(String ... args) throws ClassNotFoundException, IOException {
		Main.main(args);
	}

	static File classpathFile(final Class<?> c, String path) {
		final URL resource = c.getResource(path);
		try {
			final File relative = resource == null ?  new File(path) :
				Paths.get(resource.toURI()).toFile();
			return relative.getAbsoluteFile();
		} catch (final URISyntaxException e) {
			throw new IllegalStateException("Illegal URL " + resource, e);
		}
	}

	static String pathTo(String name) throws IOException {
		final File file = classpathFile(AwkTest.class, name);
		if (!file.exists()) throw new FileNotFoundException(file.toString());
		return file.getPath();
	}

	@Rule
	public ExpectedException willThrow = ExpectedException.none();

	@Rule
	public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();


	String[] linesOutput() {
		return systemOutRule.getLog().split(LF);
	}

	@Test @Ignore // FIXME this doesn't work because usage calls exit!
	public void testEmpty() throws Exception {
		willThrow.expect(IllegalArgumentException.class);
		awk("");
	}

	/**
	 * Tests the program <pre>$ awk 1 /dev/null</pre>
	 * @see <a href="http://www.gnu.org/software/gawk/manual/gawk.html#Names>A Rose by Any Other Name</a>
	 */
	@Test
	public void test1() throws Exception {
		final String devnull = IS_WINDOWS ? pathTo("empty.txt") : "/dev/null";
		awk("1", devnull);
	}

	/**
	 * Tests the program <pre>$ awk 'BEGIN { print "Don\47t Panic!" }'</pre>
	 * @see <a href="http://www.gnu.org/software/gawk/manual/gawk.html#Read-Terminal>Running awk Without Input Files</a>
	 */
	@Test
	public void testDontPanic() throws Exception {
		awk("BEGIN { print \"Don\47t Panic!\" }");
		assertArrayEquals(array("Don't Panic!"), linesOutput());
	}

	/**
	 * Tests the program <pre>$ awk -f advice.awk</pre>
	 * It should output <pre>Don't Panic!</pre>
	 * @see <a href="http://www.gnu.org/software/gawk/manual/gawk.html#Read-Terminal>Running awk Without Input Files</a>
	 */
	@Test
	public void testDontPanicAdvice() throws Exception {
		awk("-f", pathTo("advice.awk"));
		assertArrayEquals(array("Don't Panic!"), linesOutput());
	}

	/**
	 * Tests the program <pre>awk '/li/ { print $0 }' mail-list</pre>
	 * It should output 4 records containing the string "li".
	 * @see <a href="http://www.gnu.org/software/gawk/manual/gawk.html#Very-Simple>Some Simple Examples</a>
	 */
	@Test
	public void testMailListLiList() throws Exception {
		awk("/li/ {print $0}", pathTo("mail-list"));
		assertArrayEquals(array(
				"Amelia       555-5553     amelia.zodiacusque@gmail.com    F",
				"Broderick    555-0542     broderick.aliquotiens@yahoo.com R",
				"Julie        555-6699     julie.perscrutabor@skeeve.com   F",
				"Samuel       555-3430     samuel.lanceolis@shu.edu        A"),
			linesOutput());
	}

	/**
	 * @see <a hef="http://www.gnu.org/software/gawk/manual/gawk.html#Two-Rules">Two Rules</a>
	 */
	@Test
	public void testTwoRules() throws Exception {
		awk("/12/ {print $0} /21/ {print $0}", pathTo("mail-list"), pathTo("inventory-shipped"));
		assertArrayEquals(array(
				"Anthony      555-3412     anthony.asserturo@hotmail.com   A",
				"Camilla      555-2912     camilla.infusarum@skynet.be     R",
				"Fabius       555-1234     fabius.undevicesimus@ucb.edu    F",
				"Jean-Paul    555-2127     jeanpaul.campanorum@nyu.edu     R",
				"Jean-Paul    555-2127     jeanpaul.campanorum@nyu.edu     R",
				"Jan  21  36  64 620",
				"Apr  21  70  74 514"),
			linesOutput());
	}

	@Test
	public void testEmptyPattern() throws Exception {
		awk("//", pathTo("inventory-shipped"));
		assertArrayEquals(
				array(
					"Jan  13  25  15 115",
					"Feb  15  32  24 226",
					"Mar  15  24  34 228",
					"Apr  31  52  63 420",
					"May  16  34  29 208",
					"Jun  31  42  75 492",
					"Jul  24  34  67 436",
					"Aug  15  34  47 316",
					"Sep  13  55  37 277",
					"Oct  29  54  68 525",
					"Nov  20  87  82 577",
					"Dec  17  35  61 401",
					"",
					"Jan  21  36  64 620",
					"Feb  26  58  80 652",
					"Mar  24  75  70 495",
					"Apr  21  70  74 514"),
				linesOutput());
	}

	@Test
	public void testUninitializedVarible() throws Exception {
		awk("//{ if (v == 0) {print \"uninitialize variable\"} else {print}}",
				pathTo("inventory-shipped"));
		assertArrayEquals(monotoneArray("uninitialize variable", 17),
				linesOutput());
	}

	@Test
	public void testUninitializedVarible2() throws Exception {
		awk("//{ v = 1; if (v == 0) {print \"uninitialize variable\"} else {print}}",
				pathTo("inventory-shipped"));
		assertArrayEquals(
				array(
					"Jan  13  25  15 115",
					"Feb  15  32  24 226",
					"Mar  15  24  34 228",
					"Apr  31  52  63 420",
					"May  16  34  29 208",
					"Jun  31  42  75 492",
					"Jul  24  34  67 436",
					"Aug  15  34  47 316",
					"Sep  13  55  37 277",
					"Oct  29  54  68 525",
					"Nov  20  87  82 577",
					"Dec  17  35  61 401",
					"",
					"Jan  21  36  64 620",
					"Feb  26  58  80 652",
					"Mar  24  75  70 495",
					"Apr  21  70  74 514"),
				linesOutput());
	}

	@Test
	public void testArrayStringKey() throws Exception {
		awk("//{i=1; j=\"1\"; v[i] = 100; print v[i] v[j];}",
				pathTo("inventory-shipped"));
		assertArrayEquals(monotoneArray("100100", 17), linesOutput());
	}

	@Test
	public void testArrayStringKey2() throws Exception {
		awk("//{i=1; j=\"1\"; v[j] = 100; print v[i] v[j];}",
				pathTo("inventory-shipped"));
		assertArrayEquals(monotoneArray("100100", 17), linesOutput());
	}
}
