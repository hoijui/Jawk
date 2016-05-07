package org.jawk.util;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A simple container for the parameters of a single AWK invocation.
 * These values have defaults.
 * These defaults may be changed through command line arguments,
 * or when invoking Jawk programmatically, from within Java code.
 */
public class AwkSettings {

	/**
	 * Where input is read from.
	 * By default, this is {@link System#in}.
	 */
	private InputStream input = System.in;

	/**
	 * Contains variable assignments which are applied prior to
	 * executing the script (-v assignments).
	 * The values may be of type <code>Integer</code>,
	 * <code>Double</code> or <code>String</code>.
	 */
	private Map<String, Object> variables = new HashMap<String, Object>();

	/**
	 * Contains name=value or filename entries.
	 * Order is important, which is why name=value and filenames
	 * are listed in the same List container.
	 */
	private List<String> nameValueOrFileNames = new ArrayList<String>();

	/**
	 * Script sources meta info.
	 * This will usually be either one String container,
	 * made up of the script given on the command line directly,
	 * with the first non-"-" parameter,
	 * or one or multiple script file names (if provided with -f switches).
	 */
	private List<ScriptSource> scriptSources = new ArrayList<ScriptSource>();

	/**
	 * Whether to interpret or compile the script.
	 * Initial value is set to <code>false</code> (interpret).
	 */
	private boolean compile = false;

	/**
	 * Whether to compile and execute the script.
	 * Initial value is set to <code>false</code> (interpret).
	 */
	private boolean compileRun = false;

	/**
	 * Initial Field Separator (FS) value.
	 * <code>null</code> means the default FS value.
	 */
	private String fieldSeparator = null;

	/**
	 * Whether to dump the syntax tree;
	 * <code>false</code> by default.
	 */
	private boolean dumpSyntaxTree = false;

	/**
	 * Whether to dump the intermediate code;
	 * <code>false</code> by default.
	 */
	private boolean dumpIntermediateCode = false;

	/**
	 * Whether to enable additional functions (_sleep/_dump);
	 * <code>false</code> by default.
	 */
	private boolean additionalFunctions = false;

	/**
	 * Whether to enable additional type functions (_INTEGER/_DOUBLE/_STRING);
	 * <code>false</code> by default.
	 */

	private boolean additionalTypeFunctions = false;

	/**
	 * Whether to maintain array keys in sorted order;
	 * <code>false</code> by default.
	 */
	private boolean useSortedArrayKeys = false;

	/**
	 * Whether to trap <code>IllegalFormatExceptions</code>
	 * for <code>[s]printf</code>;
	 * <code>true</code> by default.
	 */
	private boolean catchIllegalFormatExceptions = true;

	/**
	 * Whether user extensions are enabled;
	 * <code>false</code> by default.
	 */
	private boolean userExtensions = false;

	/**
	 * Whether Jawk consumes stdin or ARGV file input;
	 * <code>true</code> by default.
	 */
	private boolean useStdIn = false;

	/**
	 * Write to intermediate file;
	 * <code>false</code> by default.
	 */
	private boolean writeIntermediateFile = false;

	/**
	 * Output filename;
	 * <code>null</code> by default,
	 * which means the appropriate default file-name will get used.
	 */
	private String outputFilename = null;

	/**
	 * Compiled destination directory (if provided);
	 * <code>"."</code> by default.
	 */
	private String destinationDirectory = ".";

	/**
	 * Provide a human readable representation of the parameters values.
	 */
	public String toDescriptionString() {

		StringBuilder desc = new StringBuilder();

		final char newLine = '\n';

		desc.append("variables = ")
				.append(getVariables()).append(newLine);
		desc.append("nameValueOrFileNames = ")
				.append(getNameValueOrFileNames()).append(newLine);
		desc.append("scriptSources = ")
				.append(scriptSources).append(newLine);
		desc.append("(should) compile = ")
				.append(isCompile()).append(newLine);
		desc.append("(should) compile & run = ")
				.append(isCompileRun()).append(newLine);
		desc.append("fieldSeparator = ")
				.append(getFieldSeparator()).append(newLine);
		desc.append("dumpSyntaxTree = ")
				.append(isDumpSyntaxTree()).append(newLine);
		desc.append("dumpIntermediateCode = ")
				.append(isDumpIntermediateCode()).append(newLine);
		desc.append("additionalFunctions = ")
				.append(isAdditionalFunctions()).append(newLine);
		desc.append("additionalTypeFunctions = ")
				.append(isAdditionalTypeFunctions()).append(newLine);
		desc.append("useSortedArrayKeys = ")
				.append(isUseSortedArrayKeys()).append(newLine);
		desc.append("catchIllegalFormatExceptions = ")
				.append(isCatchIllegalFormatExceptions()).append(newLine);
		desc.append("writeIntermediateFile = ")
				.append(isWriteIntermediateFile()).append(newLine);
		desc.append("outputFilename = ")
				.append(getOutputFilename()).append(newLine);
		desc.append("destinationDirectory = ")
				.append(getDestinationDirectory()).append(newLine);

		return desc.toString();
	}

	/**
	 * Provides a description of extensions that are enabled/disabled.
	 * The default compiler implementation uses this method
	 * to describe extensions which are compiled into the script.
	 * The description is then provided to the user within the usage.
	 *
	 * @return A description of the extensions which are enabled/disabled.
	 */
	public String toExtensionDescription() {

		StringBuilder extensions = new StringBuilder();

		if (isAdditionalFunctions()) {
			extensions.append(", _sleep & _dump enabled");
		}
		if (isAdditionalTypeFunctions()) {
			extensions.append(", _INTEGER, _DOUBLE, _STRING enabled");
		}
		if (isUseSortedArrayKeys()) {
			extensions.append(", associative array keys are sorted");
		}
		if (isCatchIllegalFormatExceptions()) {
			extensions.append(", IllegalFormatExceptions NOT trapped");
		}

		if (extensions.length() > 0) {
			return "{extensions: " + extensions.substring(2) + "}";
		} else {
			return "{no compiled extensions utilized}";
		}
	}


	private void addInitialVariable(String keyValue) {
		int equalsIdx = keyValue.indexOf('=');
		assert equalsIdx >= 0;
		String name = keyValue.substring(0, equalsIdx);
		String valueString = keyValue.substring(equalsIdx + 1);
		Object value;
		// deduce type
		try {
			value = Integer.parseInt(valueString);
		} catch (NumberFormatException nfe) {
			try {
				value = Double.parseDouble(valueString);
			} catch (NumberFormatException nfe2) {
				value = valueString;
			}
		}
		// note: can overwrite previously defined variables
		getVariables().put(name, value);
	}

	/**
	 * @param defaultFileName The filename to return if -o argument
	 *   is not used.
	 *
	 * @return the optarg for the -o parameter, or the defaultFileName
	 *   parameter if -o is not utilized.
	 */
	public String getOutputFilename(String defaultFileName) {
		if (getOutputFilename() == null) {
			return defaultFileName;
		} else {
			return getOutputFilename();
		}
	}

	/**
	 * Returns the script sources meta info.
	 * This will usually be either one String container,
	 * made up of the script given on the command line directly,
	 * with the first non-"-" parameter,
	 * or one or multiple script file names (if provided with -f switches).
	 */
	public List<ScriptSource> getScriptSources() {
		return scriptSources;
	}

	public void addScriptSource(ScriptSource scriptSource) {
		scriptSources.add(scriptSource);
	}

	/**
	 * Where input is read from.
	 * By default, this is {@link System#in}.
	 * @return the input
	 */
	public InputStream getInput() {
		return input;
	}

	/**
	 * Where input is read from.
	 * By default, this is {@link System#in}.
	 * @param input the input to set
	 */
	public void setInput(InputStream input) {
		this.input = input;
	}

	/**
	 * Contains variable assignments which are applied prior to
	 * executing the script (-v assignments).
	 * The values may be of type <code>Integer</code>,
	 * <code>Double</code> or <code>String</code>.
	 * @return the variables
	 */
	public Map<String, Object> getVariables() {
		return variables;
	}

	/**
	 * Contains variable assignments which are applied prior to
	 * executing the script (-v assignments).
	 * The values may be of type <code>Integer</code>,
	 * <code>Double</code> or <code>String</code>.
	 * @param variables the variables to set
	 */
	public void setVariables(Map<String, Object> variables) {
		this.variables = variables;
	}

	/**
	 * Contains name=value or filename entries.
	 * Order is important, which is why name=value and filenames
	 * are listed in the same List container.
	 * @return the nameValueOrFileNames
	 */
	public List<String> getNameValueOrFileNames() {
		return nameValueOrFileNames;
	}

	/**
	 * Contains name=value or filename entries.
	 * Order is important, which is why name=value and filenames
	 * are listed in the same List container.
	 * @param nameValueOrFileNames the nameValueOrFileNames to set
	 */
	public void setNameValueOrFileNames(List<String> nameValueOrFileNames) {
		this.nameValueOrFileNames = nameValueOrFileNames;
	}

	/**
	 * Script sources meta info.
	 * This will usually be either one String container,
	 * made up of the script given on the command line directly,
	 * with the first non-"-" parameter,
	 * or one or multiple script file names (if provided with -f switches).
	 * @param scriptSources the scriptSources to set
	 */
	public void setScriptSources(List<ScriptSource> scriptSources) {
		this.scriptSources = scriptSources;
	}

	/**
	 * Whether to interpret or compile the script.
	 * Initial value is set to <code>false</code> (interpret).
	 * @return the compile
	 */
	public boolean isCompile() {
		return compile;
	}

	/**
	 * Whether to interpret or compile the script.
	 * Initial value is set to <code>false</code> (interpret).
	 * @param compile the compile to set
	 */
	public void setCompile(boolean compile) {
		this.compile = compile;
	}

	/**
	 * Whether to compile and execute the script.
	 * Initial value is set to <code>false</code> (interpret).
	 * @return the compileRun
	 */
	public boolean isCompileRun() {
		return compileRun;
	}

	/**
	 * Whether to compile and execute the script.
	 * Initial value is set to <code>false</code> (interpret).
	 * @param compileRun the compileRun to set
	 */
	public void setCompileRun(boolean compileRun) {
		this.compileRun = compileRun;
	}

	/**
	 * Initial Field Separator (FS) value.
	 * <code>null</code> means the default FS value.
	 * @return the fieldSeparator
	 */
	public String getFieldSeparator() {
		return fieldSeparator;
	}

	/**
	 * Initial Field Separator (FS) value.
	 * <code>null</code> means the default FS value.
	 * @param fieldSeparator the fieldSeparator to set
	 */
	public void setFieldSeparator(String fieldSeparator) {
		this.fieldSeparator = fieldSeparator;
	}

	/**
	 * Whether to dump the syntax tree;
	 * <code>false</code> by default.
	 * @return the dumpSyntaxTree
	 */
	public boolean isDumpSyntaxTree() {
		return dumpSyntaxTree;
	}

	/**
	 * Whether to dump the syntax tree;
	 * <code>false</code> by default.
	 * @param dumpSyntaxTree the dumpSyntaxTree to set
	 */
	public void setDumpSyntaxTree(boolean dumpSyntaxTree) {
		this.dumpSyntaxTree = dumpSyntaxTree;
	}

	/**
	 * Whether to dump the intermediate code;
	 * <code>false</code> by default.
	 * @return the dumpIntermediateCode
	 */
	public boolean isDumpIntermediateCode() {
		return dumpIntermediateCode;
	}

	/**
	 * Whether to dump the intermediate code;
	 * <code>false</code> by default.
	 * @param dumpIntermediateCode the dumpIntermediateCode to set
	 */
	public void setDumpIntermediateCode(boolean dumpIntermediateCode) {
		this.dumpIntermediateCode = dumpIntermediateCode;
	}

	/**
	 * Whether to enable additional functions (_sleep/_dump);
	 * <code>false</code> by default.
	 * @return the additionalFunctions
	 */
	public boolean isAdditionalFunctions() {
		return additionalFunctions;
	}

	/**
	 * Whether to enable additional functions (_sleep/_dump);
	 * <code>false</code> by default.
	 * @param additionalFunctions the additionalFunctions to set
	 */
	public void setAdditionalFunctions(boolean additionalFunctions) {
		this.additionalFunctions = additionalFunctions;
	}

	/**
	 * Whether to enable additional type functions (_INTEGER/_DOUBLE/_STRING);
	 * <code>false</code> by default.
	 * @return the additionalTypeFunctions
	 */
	public boolean isAdditionalTypeFunctions() {
		return additionalTypeFunctions;
	}

	/**
	 * Whether to enable additional type functions (_INTEGER/_DOUBLE/_STRING);
	 * <code>false</code> by default.
	 * @param additionalTypeFunctions the additionalTypeFunctions to set
	 */
	public void setAdditionalTypeFunctions(boolean additionalTypeFunctions) {
		this.additionalTypeFunctions = additionalTypeFunctions;
	}

	/**
	 * Whether to maintain array keys in sorted order;
	 * <code>false</code> by default.
	 * @return the useSortedArrayKeys
	 */
	public boolean isUseSortedArrayKeys() {
		return useSortedArrayKeys;
	}

	/**
	 * Whether to maintain array keys in sorted order;
	 * <code>false</code> by default.
	 * @param useSortedArrayKeys the useSortedArrayKeys to set
	 */
	public void setUseSortedArrayKeys(boolean useSortedArrayKeys) {
		this.useSortedArrayKeys = useSortedArrayKeys;
	}

	/**
	 * Whether user extensions are enabled;
	 * <code>false</code> by default.
	 * @return the userExtensions
	 */
	public boolean isUserExtensions() {
		return userExtensions;
	}

	/**
	 * Whether user extensions are enabled;
	 * <code>false</code> by default.
	 * @param userExtensions the userExtensions to set
	 */
	public void setUserExtensions(boolean userExtensions) {
		this.userExtensions = userExtensions;
	}

	/**
	 * Write to intermediate file;
	 * <code>false</code> by default.
	 * @return the writeIntermediateFile
	 */
	public boolean isWriteIntermediateFile() {
		return writeIntermediateFile;
	}

	/**
	 * Write to intermediate file;
	 * <code>false</code> by default.
	 * @param writeIntermediateFile the writeIntermediateFile to set
	 */
	public void setWriteIntermediateFile(boolean writeIntermediateFile) {
		this.writeIntermediateFile = writeIntermediateFile;
	}

	/**
	 * Output filename;
	 * <code>null</code> by default,
	 * which means the appropriate default file-name will get used.
	 * @return the outputFilename
	 */
	public String getOutputFilename() {
		return outputFilename;
	}

	/**
	 * Output filename;
	 * <code>null</code> by default,
	 * which means the appropriate default file-name will get used.
	 * @param outputFilename the outputFilename to set
	 */
	public void setOutputFilename(String outputFilename) {
		this.outputFilename = outputFilename;
	}

	/**
	 * Compiled destination directory (if provided);
	 * <code>"."</code> by default.
	 * @return the destinationDirectory
	 */
	public String getDestinationDirectory() {
		return destinationDirectory;
	}

	/**
	 * Compiled destination directory (if provided).
	 * @param destinationDirectory the destinationDirectory to set,
	 *   <code>"."</code> by default.
	 */
	public void setDestinationDirectory(String destinationDirectory) {

		if (destinationDirectory == null) {
			throw new IllegalArgumentException("The destination directory might never be null (you might want to use \".\")");
		}

		this.destinationDirectory = destinationDirectory;
	}

	/**
	 * Whether to trap <code>IllegalFormatExceptions</code>
	 * for <code>[s]printf</code>;
	 * <code>true</code> by default.
	 * @return the catchIllegalFormatExceptions
	 */
	public boolean isCatchIllegalFormatExceptions() {
		return catchIllegalFormatExceptions;
	}

	/**
	 * Whether to trap <code>IllegalFormatExceptions</code>
	 * for <code>[s]printf</code>;
	 * <code>true</code> by default.
	 * @param catchIllegalFormatExceptions the catchIllegalFormatExceptions to set
	 */
	public void setCatchIllegalFormatExceptions(boolean catchIllegalFormatExceptions) {
		this.catchIllegalFormatExceptions = catchIllegalFormatExceptions;
	}

	/**
	 * Whether Jawk consumes stdin or ARGV file input;
	 * <code>true</code> by default.
	 * @return the useStdIn
	 */
	public boolean isUseStdIn() {
		return useStdIn;
	}

	/**
	 * Whether Jawk consumes stdin or ARGV file input;
	 * <code>true</code> by default.
	 * @param useStdIn the useStdIn to set
	 */
	public void setUseStdIn(boolean useStdIn) {
		this.useStdIn = useStdIn;
	}
}
