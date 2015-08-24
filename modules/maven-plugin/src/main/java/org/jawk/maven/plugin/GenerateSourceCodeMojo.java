package org.jawk.maven.plugin;

import java.io.File;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jawk.Awk;
import org.jawk.ExitException;
import org.jawk.util.AwkSettings;
import org.jawk.util.ScriptFileSource;
import org.jawk.util.ScriptSource;

/**
 * Maven goal that allows to generate source code by running AWK scripts.
 * @description
 */
@Mojo(
		name="generate",
		defaultPhase=LifecyclePhase.GENERATE_SOURCES,
		requiresDependencyResolution=ResolutionScope.COMPILE)
public class GenerateSourceCodeMojo extends AbstractMojo {

	/**
	 * Variable key-value pairs, that will be set while running AWK.
	 * On a command line run of AWK, you would set these like:
	 * "-DMY_VAR=myValue".
	 */
	@Parameter(required=true)
	private Properties variables;

	/**
	 * Instead of reading from an input file,
	 * the contents of this is basically mapped to StdIn for the AWK run.
	 * @see #inputFile
	 */
	@Parameter
	private String inputContent;

	/**
	 * File the AWK code is going to process as input.
	 * @see #inputContent
	 */
	@Parameter
	private File inputFile;

	/**
	 * Location of the script file(s).
	 * @see #scriptContent
	 */
	@Parameter
	private List<File> scriptFiles;

	/**
	 * AWK code to run, as you would give it as argument to AWK
	 * on the command line, for example.
	 * Example: "{ print \"Hello AWK-World!\" }"
	 * @see #scriptFiles
	 */
	@Parameter
	private String scriptContent;

	/**
	 * Where relative output file-paths are rooted in.
	 */
	@Parameter(defaultValue="${project.build.directory}/generated-sources")
	private File outputDirectory;

	@Override
	public void execute() throws MojoExecutionException {

		if ((inputContent == null) && (inputFile == null)) {
			throw new MojoExecutionException(
					"You need to define either inputContent or inputFile");
		}
		if ((inputContent != null) && (inputFile != null)) {
			throw new MojoExecutionException(
					"You may not provide inputContent and inputFile");
		}

		if ((scriptContent == null) && scriptFiles.isEmpty()) {
			throw new MojoExecutionException(
					"You need to define either scriptContent or provide at least one scriptFiles entry");
		}
		if ((scriptContent != null) && !scriptFiles.isEmpty()) {
			throw new MojoExecutionException(
					"You may not provide scriptContent and scriptFile entries");
		}

		getLog().info("Hello, world.");

		try {
			if (!outputDirectory.exists()) {
				outputDirectory.mkdirs();
			}

			Awk awk = new Awk();
			AwkSettings awkSettings = new AwkSettings();

			if (inputContent != null) {
				// TODO do something with inputContent
				throw new RuntimeException("inputContent handling not implemented yet (needs changes in Jawk core)");
			} else {
				// inputFile is not null; this was alreayd checked earlier
				awkSettings.getNameValueOrFileNames().add(inputFile.getAbsolutePath());
			}

			for (Map.Entry<Object, Object> variable : variables.entrySet()) {
				awkSettings.getVariables().put((String) variable.getKey(), variable.getValue());
			}
			if (scriptContent != null) {
				awkSettings.addScriptSource(new ScriptSource(ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT, new StringReader(scriptContent), false));
			} else {
				// scriptFiles is not empty; this was alreayd checked earlier
				for (File scriptFile : scriptFiles) {
					awkSettings.addScriptSource(new ScriptFileSource(scriptFile.getAbsolutePath()));
				}
			}

			awk.invoke(awkSettings);
		} catch (ExitException ex) {
			if (ex.getCode() != ExitException.EXIT_CODE_OK) {
				throw new MojoExecutionException("Error while executing the AWK script(s)", ex);
			}
		} catch (Exception ex) {
			throw new MojoExecutionException("Error executing Jawk", ex);
		}
	}
}
