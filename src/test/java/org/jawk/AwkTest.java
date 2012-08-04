package org.jawk;

import static org.junit.Assert.assertArrayEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

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
}
