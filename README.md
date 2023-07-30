# Jawk

Jawk is a pure Java implementation of [AWK](https://en.wikipedia.org/wiki/AWK). It executes the specified AWK scripts to parse and process text input, and generate a text output. Jawk can be used as a CLI, but more importantly it can be invoked from within your Java project.

This project is forked from the excellent [Jawk project](https://jawk.sourceforge.net/) that was maintained by [hoijui on GitHub](https://github.com/hoijui/Jawk).

[Project Documentation](https://sentrysoftware.github.io/Jawk)

## Run Jawk CLI

It's very simple:

* Download the [latest release](releases)
* Make sure to have Java installed on your system ([download](https://adoptium.net/))
* Execute **Jawk.jar** just like the "traditional" AWK

Processing of *stdin*:

```bash
$ echo "hello world" | java -jar Jawk.jar '{print $2 ", " $1 "!"}'

world, hello!
```

Execute on Windows (beware of double-double quotes!):

```bash
C:\> echo "hello world" | java -jar Jawk.jar "{print $2 "", "" $1 ""!""}"

world, hello!
```

Classic processing of an input file:

```bash
$ java -jar Jawk.jar -F : '{print $1 "," $6}' /etc/passwd

root,/root
daemon,/usr/sbin
bin,/bin
sys,/dev
sync,/bin
games,/usr/games
man,/var/cache/man
lp,/var/spool/lpd
mail,/var/mail
```

Execute a script file:

**example.awk**:

```awk
BEGIN {
	totalUsed = 0
	totalAvailable = 0
}
$6 !~ "wsl" && $3 ~ "[0-9]+" {
	totalUsed += $3
	totalAvailable += $4
}
END {
	printf "Total Used: %.1f GB\n", totalUsed / 1048576
	printf "Total Available: %.1f GB\n", totalAvailable / 1048576
}
```

```bash
$ df -kP | java -jar Jawk.jar -f example.awk

Total Used: 559.8 GB
Total Available: 2048.0 GB
```

## Run AWK inside Java

Add the below repository to the list of repositories in your [Maven settings](https://maven.apache.org/settings.html):

```xml
<repository>
	<id>github</id>
	<url>https://maven.pkg.github.com/sentrysoftware/Jawk</url>
	<releases><enabled>true</enabled></releases>
	<snapshots><enabled>true</enabled></snapshots>
</repository>
```

Add Jawk in the list of dependencies in your [Maven **pom.xml**](https://maven.apache.org/pom.html):

```xml
<dependencies>
	<!-- [...] -->
	<dependency>
		<groupId>com.sentrysoftware</groupId>
		<artifactId>jawk</artifactId>
		<version>2.1.00-SNAPSHOT</version> <!-- Use the latest version released -->
	</dependency>
</dependencies>
```

Invoke AWK scripts files on input files:

```java
	/**
	 * Executes the specified AWK script
	 * <p>
	 * @param scriptFile File containing the AWK script to execute
	 * @param inputFileList List of files that contain the input to be parsed by the AWK script
	 * @return the printed output of the script as a String
	 * @throws ExitException when the AWK script forces its exit with a specified code
	 * @throws IOException on I/O problems
	 */
	private String runAwk(File scriptFile, List<String> inputFileList) throws IOException, ExitException {

		AwkSettings settings = new AwkSettings();

		// Set the input files
		settings.getNameValueOrFileNames().addAll(inputFileList);

		// Create the OutputStream, to collect the result as a String
		ByteArrayOutputStream resultBytesStream = new ByteArrayOutputStream();
    	settings.setOutputStream(new PrintStream(resultBytesStream));

    	// Sets the AWK script to execute
    	settings.addScriptSource(new ScriptFileSource(scriptFile.getAbsolutePath()));

    	// Execute the awk script against the specified input
		Awk awk = new Awk();
		awk.invoke(settings);

		// Return the result as a string
		return resultBytesStream.toString(StandardCharsets.UTF_8);

	}
```

Execute AWK script (as String) on String input:

```java
	/**
	 * Executes the specified script against the specified input
	 * <p>
	 * @param script AWK script to execute (as a String)
	 * @param input Text to process (as a String)
	 * @return result as a String
	 * @throws ExitException when the AWK script forces its exit with a specified code
	 * @throws IOException on I/O problems
	 */
	private String runAwk(String script, String input) throws IOException, ExitException {

		AwkSettings settings = new AwkSettings();

		// Set the input files
		settings.setInput(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

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
```

## Differences with `gawk`

List...

## Differences with hoijui's original Jawk

List...

