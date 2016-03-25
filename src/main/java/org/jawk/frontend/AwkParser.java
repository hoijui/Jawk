package org.jawk.frontend;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jawk.NotImplementedError;
import org.jawk.backend.AVM; // for NULL_OFFSET
import org.jawk.ext.JawkExtension;
import org.jawk.intermediate.Address;
import org.jawk.intermediate.AwkTuples;
import org.jawk.intermediate.HasFunctionAddress;
import org.jawk.jrt.KeyList;
import org.jawk.util.ScriptSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts the AWK script into a syntax tree,
 * which is useful the backend that either compiles or interprets the script.
 * <p>
 * It contains the internal state of the parser and the lexer.
 * </p>
 */
public class AwkParser {

	private static final Logger LOG = LoggerFactory.getLogger(AwkParser.class);

	/**
	 * Interface for statement AST nodes that can be interrupted
	 * with a break statement.
	 */
	private interface Breakable {
		Address breakAddress();
	}

	/**
	 * Interface for statement AST nodes that can be entered
	 * via a next statement.
	 */
	private interface Nextable {
		Address nextAddress();
	}

	/**
	 * Interface for statement AST nodes that can be re-entered
	 * with a continue statement.
	 */
	private interface Continueable {
		Address continueAddress();
	}

	/** Lexer token values, similar to yytok values in lex/yacc. */
	private static int s_idx = 257;

	// Lexable tokens...

	private static final int _EOF_ = s_idx++;
	private static final int _NEWLINE_ = s_idx++;
	private static final int _SEMICOLON_ = s_idx++;
	private static final int _ID_ = s_idx++;
	private static final int _FUNC_ID_ = s_idx++;
	private static final int _INTEGER_ = s_idx++;
	private static final int _DOUBLE_ = s_idx++;
	private static final int _STRING_ = s_idx++;

	private static final int _EQUALS_ = s_idx++;

	private static final int _AND_ = s_idx++;
	private static final int _OR_ = s_idx++;

	private static final int _EQ_ = s_idx++;
	private static final int _GT_ = s_idx++;
	private static final int _GE_ = s_idx++;
	private static final int _LT_ = s_idx++;
	private static final int _LE_ = s_idx++;
	private static final int _NE_ = s_idx++;
	private static final int _NOT_ = s_idx++;
	private static final int _PIPE_ = s_idx++;
	private static final int _QUESTION_MARK_ = s_idx++;
	private static final int _COLON_ = s_idx++;
	private static final int _APPEND_ = s_idx++;

	private static final int _PLUS_ = s_idx++;
	private static final int _MINUS_ = s_idx++;
	private static final int _MULT_ = s_idx++;
	private static final int _DIVIDE_ = s_idx++;
	private static final int _MOD_ = s_idx++;
	private static final int _POW_ = s_idx++;
	private static final int _COMMA_ = s_idx++;
	private static final int _MATCHES_ = s_idx++;
	private static final int _NOT_MATCHES_ = s_idx++;
	private static final int _DOLLAR_ = s_idx++;

	private static final int _INC_ = s_idx++;
	private static final int _DEC_ = s_idx++;

	private static final int _PLUS_EQ_ = s_idx++;
	private static final int _MINUS_EQ_ = s_idx++;
	private static final int _MULT_EQ_ = s_idx++;
	private static final int _DIV_EQ_ = s_idx++;
	private static final int _MOD_EQ_ = s_idx++;
	private static final int _POW_EQ_ = s_idx++;

	private static final int _OPEN_PAREN_ = s_idx++;
	private static final int _CLOSE_PAREN_ = s_idx++;
	private static final int _OPEN_BRACE_ = s_idx++;
	private static final int _CLOSE_BRACE_ = s_idx++;
	private static final int _OPEN_BRACKET_ = s_idx++;
	private static final int _CLOSE_BRACKET_ = s_idx++;

	private static final int _BUILTIN_FUNC_NAME_ = s_idx++;

	private static final int _EXTENSION_ = s_idx++;

	/**
	 * Contains a mapping of Jawk keywords to their
	 * token values.
	 * They closely correspond to AWK keywords, but with
	 * a few added extensions.
	 * <p>
	 * Keys are the keywords themselves, and values are the
	 * token values (equivalent to yytok values in lex/yacc).
	 * </p>
	 * <p>
	 * <strong>Note:</strong> whether built-in AWK function names
	 * and special AWK variable names are formally keywords or not,
	 * they are not stored in this map. They are separated
	 * into other maps.
	 * </p>
	 */
	private static final Map<String, Integer> KEYWORDS = new HashMap<String, Integer>();
	static {
		// special keywords
		KEYWORDS.put("function", s_idx++);
		KEYWORDS.put("BEGIN", s_idx++);
		KEYWORDS.put("END", s_idx++);
		KEYWORDS.put("in", s_idx++);

		// statements
		KEYWORDS.put("if", s_idx++);
		KEYWORDS.put("else", s_idx++);
		KEYWORDS.put("while", s_idx++);
		KEYWORDS.put("for", s_idx++);
		KEYWORDS.put("do", s_idx++);
		KEYWORDS.put("return", s_idx++);
		KEYWORDS.put("exit", s_idx++);
		KEYWORDS.put("next", s_idx++);
		KEYWORDS.put("continue", s_idx++);
		KEYWORDS.put("delete", s_idx++);
		KEYWORDS.put("break", s_idx++);

		// special-form functions
		KEYWORDS.put("print", s_idx++);
		KEYWORDS.put("printf", s_idx++);
		KEYWORDS.put("getline", s_idx++);
	}

	/**
	 * Built-in function token values.
	 * Built-in function token values are distinguished
	 * from lexer token values.
	 */
	private static int f_idx = 257;
	/**
	 * A mapping of built-in function names to their
	 * function token values.
	 * <p>
	 * <strong>Note:</strong> these are not lexer token
	 * values. Lexer token values are for keywords and
	 * operators.
	 * </p>
	 */
	private static final Map<String, Integer> BUILTIN_FUNC_NAMES = new HashMap<String, Integer>();
	static {
		BUILTIN_FUNC_NAMES.put("atan2", f_idx++);
		BUILTIN_FUNC_NAMES.put("close", f_idx++);
		BUILTIN_FUNC_NAMES.put("cos", f_idx++);
		BUILTIN_FUNC_NAMES.put("exp", f_idx++);
		BUILTIN_FUNC_NAMES.put("index", f_idx++);
		BUILTIN_FUNC_NAMES.put("int", f_idx++);
		BUILTIN_FUNC_NAMES.put("length", f_idx++);
		BUILTIN_FUNC_NAMES.put("log", f_idx++);
		BUILTIN_FUNC_NAMES.put("match", f_idx++);
		BUILTIN_FUNC_NAMES.put("rand", f_idx++);
		BUILTIN_FUNC_NAMES.put("sin", f_idx++);
		BUILTIN_FUNC_NAMES.put("split", f_idx++);
		BUILTIN_FUNC_NAMES.put("sprintf", f_idx++);
		BUILTIN_FUNC_NAMES.put("sqrt", f_idx++);
		BUILTIN_FUNC_NAMES.put("srand", f_idx++);
		BUILTIN_FUNC_NAMES.put("sub", f_idx++);
		BUILTIN_FUNC_NAMES.put("gsub", f_idx++);
		BUILTIN_FUNC_NAMES.put("substr", f_idx++);
		BUILTIN_FUNC_NAMES.put("system", f_idx++);
		BUILTIN_FUNC_NAMES.put("tolower", f_idx++);
		BUILTIN_FUNC_NAMES.put("toupper", f_idx++);
	}

	private static final int sp_idx = 257;
	/**
	 * Contains a mapping of Jawk special variables to their
	 * variable token values.
	 * As of this writing, they correspond exactly to
	 * standard AWK variables, no more, no less.
	 * <p>
	 * Keys are the variable names themselves, and values are the
	 * variable token values.
	 * </p>
	 */
	private static final Map<String, Integer> SPECIAL_VAR_NAMES = new HashMap<String, Integer>();
	static {
		SPECIAL_VAR_NAMES.put("NR", sp_idx);
		SPECIAL_VAR_NAMES.put("FNR", sp_idx);
		SPECIAL_VAR_NAMES.put("NF", sp_idx);
		SPECIAL_VAR_NAMES.put("FS", sp_idx);
		SPECIAL_VAR_NAMES.put("RS", sp_idx);
		SPECIAL_VAR_NAMES.put("OFS", sp_idx);
		SPECIAL_VAR_NAMES.put("RSTART", sp_idx);
		SPECIAL_VAR_NAMES.put("RLENGTH", sp_idx);
		SPECIAL_VAR_NAMES.put("FILENAME", sp_idx);
		SPECIAL_VAR_NAMES.put("SUBSEP", sp_idx);
		SPECIAL_VAR_NAMES.put("CONVFMT", sp_idx);
		SPECIAL_VAR_NAMES.put("OFMT", sp_idx);
		SPECIAL_VAR_NAMES.put("ENVIRON", sp_idx);
		SPECIAL_VAR_NAMES.put("ARGC", sp_idx);
		SPECIAL_VAR_NAMES.put("ARGV", sp_idx);
	}

	/**
	 * Defined as concrete implementation class (not an
	 * interface reference) as to not clutter the interface
	 * with methods appropriate for private access, only.
	 */
	private final AwkSymbolTableImpl symbol_table = new AwkSymbolTableImpl();

	private final boolean additional_functions;
	private final boolean additional_type_functions;
	private final boolean no_input;
	private final Map<String, JawkExtension> extensions;

	public AwkParser(boolean additional_functions, boolean additional_type_functions, boolean no_input, Map<String, JawkExtension> extensions) {
		this.additional_functions = additional_functions;
		this.additional_type_functions = additional_type_functions;
		this.no_input = no_input;
		// HACK : When recompiling via exec(),
		// this code is executed more than once.
		// As a result, guard against polluting the
		// KEYWORDS with different symbol ids.
		// etc.
		if (additional_functions && (KEYWORDS.get("_sleep") == null)) {
			// Must not be reentrant!
			// (See hack notice above.)
			assert KEYWORDS.get("_sleep") == null;
			assert KEYWORDS.get("_dump") == null;
			KEYWORDS.put("_sleep", s_idx++);
			KEYWORDS.put("_dump", s_idx++);
			BUILTIN_FUNC_NAMES.put("exec", f_idx++);
		}
		if (additional_type_functions && (KEYWORDS.get("_INTEGER") == null)) {
			// Must not be reentrant!
			// (See hack notice above.)
			assert KEYWORDS.get("_INTEGER") == null;
			assert KEYWORDS.get("_DOUBLE") == null;
			assert KEYWORDS.get("_STRING") == null;
			KEYWORDS.put("_INTEGER", s_idx++);
			KEYWORDS.put("_DOUBLE", s_idx++);
			KEYWORDS.put("_STRING", s_idx++);
		}
		// Special handling for exec().
		// Need to keep "extensions" around only
		// for exec(). But, must assign it regardless
		// even if "additional_functions" is not true
		// because it is a final field variable.
		this.extensions = extensions;
	}

	private List<ScriptSource> scriptSources;
	private int scriptSourcesCurrentIndex;
	private LineNumberReader reader;
	private int c;
	private int token;

	private StringBuffer text = new StringBuffer();
	private StringBuffer string = new StringBuffer();
	private StringBuffer regexp = new StringBuffer();
	private int chr;

	private void read()
			throws IOException
	{
		text.append((char) c);
		c = reader.read();
		// completely bypass \r's
		while (c == '\r') {
			c = reader.read();
		}
		if (c < 0) {
			chr = 0; // EoF
			// check if there are additional sources
			if ((scriptSourcesCurrentIndex + 1) < scriptSources.size()) {
				scriptSourcesCurrentIndex++;
				reader = new LineNumberReader(scriptSources.get(scriptSourcesCurrentIndex).getReader());
				read();
			}
		} else {
			chr = c;
		}
	}

	/**
	 * Parse the script streamed by script_reader. Build and return the
	 * root of the abstract syntax tree which represents the Jawk script.
	 *
	 * @param script_reader The Reader streaming the script to parse.
	 *
	 * @return The abstract syntax tree of this script.
	 *
	 * @throws IOException upon an IO error.
	 */
	public AwkSyntaxTree parse(List<ScriptSource> scriptSources)
			throws IOException
	{
		if ((scriptSources == null) || scriptSources.isEmpty()) {
			throw new IOException("No script sources supplied");
		}
		this.scriptSources = scriptSources;
		scriptSourcesCurrentIndex = 0;
		reader = new LineNumberReader(scriptSources.get(scriptSourcesCurrentIndex).getReader());
		read();
		lexer();
		return SCRIPT();
	}

	private class LexerException extends IOException {

		LexerException(String msg) {
			super(msg + " ("
					+ scriptSources.get(scriptSourcesCurrentIndex).getDescription()
					+ ":" + reader.getLineNumber() + ")");
		}
	}

	private void readRegexp()
			throws IOException
	{
		// should only contain nothing or =, depending on whether
		// starting with /... or /=...
		assert regexp.length() == 0 || regexp.length() == 1;
		while (c >= 0 && c != '\n' && (c != '/' || (regexp.length() > 0 && regexp.charAt(regexp.length() - 1) == '\\'))) {
			regexp.append((char) c);
			c = reader.read();
			// completely bypass \r's
			while (c == '\r') {
				c = reader.read();
			}
		}
		if (c < 0 || c == '\n') {
			throw new LexerException("Unterminated regular expression: " + regexp);
		}
		c = reader.read();
		// completely bypass \r's
		while (c == '\r') {
			c = reader.read();
		}
	}
	// LEXER
	private Thread lexer_thread;

	private synchronized boolean assertOneLexerThread() {
		if (lexer_thread == null) {
			lexer_thread = Thread.currentThread();
		}
		return lexer_thread == Thread.currentThread();
	}

	private static String toTokenString(int token) {
		Class c = AwkParser.class;
		Field[] fields = c.getDeclaredFields();
		try {
			for (Field field : fields) {
				if ((field.getModifiers() & Modifier.STATIC) > 0 && field.getType() == Integer.TYPE && field.getInt(null) == token) {
					return field.getName();
				}
			}
		} catch (IllegalAccessException iac) {
			LOG.error("Failed to create token string", iac);
			return "[" + token + ": " + iac + "]";
		}
		return "{" + token + "}";
	}

	private int lexer(int expected_token)
			throws IOException
	{

		assert assertOneLexerThread();

		if (token != expected_token) {
			throw new ParserException("Expecting " + expected_token + " " + toTokenString(expected_token) + ". Found: " + token + " " + toTokenString(token) + " (" + text + ")");
		}
		return lexer();
	}

	private int lexer()
			throws IOException
	{

		assert assertOneLexerThread();

		// clear whitespace
		while (c >= 0 && (c == ' ' || c == '\t' || c == '#' || c == '\\')) {
			if (c == '\\') {
				read();
				if (c == '\n') {
					read();
				}
				continue;
			}
			if (c == '#') {
				// kill comment
				while (c >= 0 && c != '\n') {
					read();
				}
//// Causes failure when comments are embedded within lines of code...
////			if (c == '\n')
////				read();
			} else {
				read();
			}
		}
		text.setLength(0);
		if (c < 0) {
			return token = _EOF_;
		}
		if (c == ',') {
			read();
			return token = _COMMA_;
		}
		if (c == '(') {
			read();
			return token = _OPEN_PAREN_;
		}
		if (c == ')') {
			read();
			return token = _CLOSE_PAREN_;
		}
		if (c == '{') {
			read();
			return token = _OPEN_BRACE_;
		}
		if (c == '}') {
			read();
			return token = _CLOSE_BRACE_;
		}
		if (c == '[') {
			read();
			return token = _OPEN_BRACKET_;
		}
		if (c == ']') {
			read();
			return token = _CLOSE_BRACKET_;
		}
		if (c == '$') {
			read();
			return token = _DOLLAR_;
		}
		if (c == '~') {
			read();
			return token = _MATCHES_;
		}
		if (c == '?') {
			read();
			return token = _QUESTION_MARK_;
		}
		if (c == ':') {
			read();
			return token = _COLON_;
		}
		if (c == '&') {
			read();
			if (c == '&') {
				read();
				return token = _AND_;
			}
			throw new LexerException("use && for logical and");
		}
		if (c == '|') {
			read();
			if (c == '|') {
				read();
				return token = _OR_;
			}
			return token = _PIPE_;
		}
		if (c == '=') {
			read();
			if (c == '=') {
				read();
				return token = _EQ_;
			}
			return token = _EQUALS_;
		}
		if (c == '+') {
			read();
			if (c == '=') {
				read();
				return token = _PLUS_EQ_;
			} else if (c == '+') {
				read();
				return token = _INC_;
			}
			return token = _PLUS_;
		}
		if (c == '-') {
			read();
			if (c == '=') {
				read();
				return token = _MINUS_EQ_;
			} else if (c == '-') {
				read();
				return token = _DEC_;
			}
			return token = _MINUS_;
		}
		if (c == '*') {
			read();
			if (c == '=') {
				read();
				return token = _MULT_EQ_;
			}
			return token = _MULT_;
		}
		if (c == '/') {
			read();
			if (c == '=') {
				read();
				return token = _DIV_EQ_;
			}
			return token = _DIVIDE_;
		}
		if (c == '%') {
			read();
			if (c == '=') {
				read();
				return token = _MOD_EQ_;
			}
			return token = _MOD_;
		}
		if (c == '^') {
			read();
			if (c == '=') {
				read();
				return token = _POW_EQ_;
			}
			return token = _POW_;
		}
		if (c == '>') {
			read();
			if (c == '=') {
				read();
				return token = _GE_;
			} else if (c == '>') {
				read();
				return token = _APPEND_;
			}
			return token = _GT_;
		}
		if (c == '<') {
			read();
			if (c == '=') {
				read();
				return token = _LE_;
			}
			return token = _LT_;
		}
		if (c == '!') {
			read();
			if (c == '=') {
				read();
				return token = _NE_;
			} else if (c == '~') {
				read();
				return token = _NOT_MATCHES_;
			}
			return token = _NOT_;
		}

		if (c == '.') {
			// double!
			read();
			boolean hit = false;
			while (c > 0 && Character.isDigit(c)) {
				hit = true;
				read();
			}
			if (!hit) {
				throw new LexerException("Decimal point encountered with no values on either side.");
			}
			return token = _DOUBLE_;
		}

		if (Character.isDigit(c)) {
			// integer or double.
			read();
			while (c > 0) {
				if (c == '.') {
					// double!
					read();
					while (c > 0 && Character.isDigit(c)) {
						read();
					}
					return token = _DOUBLE_;
				} else if (Character.isDigit(c)) {
					// integer or double.
					read();
				} else {
					break;
				}
			}
			// integer, only
			return token = _INTEGER_;
		}

		if (Character.isJavaIdentifierStart(c)) {
			read();
			while (Character.isJavaIdentifierPart(c)) {
				read();
			}
			// check for certain keywords
			// extensions override built-in stuff
			if (extensions.get(text.toString()) != null) {
				return token = _EXTENSION_;
			}
			Integer kw_token = KEYWORDS.get(text.toString());
			if (kw_token != null) {
				return token = kw_token.intValue();
			}
			Integer bf_idx = BUILTIN_FUNC_NAMES.get(text.toString());
			if (bf_idx != null) {
				return token = _BUILTIN_FUNC_NAME_;
			}
			if (c == '(') {
				return token = _FUNC_ID_;
			} else {
				return token = _ID_;
			}
		}

		if (c == ';') {
			read();
			while (c == ' ' || c == '\t' || c == '\n' || c == '#') {
				if (c == '\n') {
					break;
				}
				if (c == '#') {
					while (c >= 0 && c != '\n') {
						read();
					}
					if (c == '\n') {
						read();
					}
				} else {
					read();
				}
			}
			return token = _SEMICOLON_;
		}

		if (c == '\n') {
			read();
			while (c == ' ' || c == '\t' || c == '#' || c == '\n') {
				if (c == '#') {
					while (c >= 0 && c != '\n') {
						read();
					}
				}
				if (c == '\n') {
					read();
				} else {
					read();
				}
			}
			return token = _NEWLINE_;
		}

		if (c == '"') {
			// string
			read();
			string.setLength(0);
			while (token != _EOF_ && c != '"' && c != '\n') {
				if (c == '\\') {
					read();
					if (c == 't') {
						string.append('\t');
					} else if (c == 'n') {
						string.append('\n');
					} else if (c == 'r') {
						string.append('\r');
					} else {
						string.append((char)c);
					}
				} else {
					string.append((char) c);
				}
				read();
			}
			if (token == _EOF_ || c == '\n') {
				throw new LexerException("Unterminated string: " + text);
			}
			read();
			return token = _STRING_;
		}

		/*
		if (c == '\\') {
			c = reader.read();
			// completely bypass \r's
			while(c == '\r') c = reader.read();
			if (c<0)
				chr=0;	// eof
			else
				chr=c;
		}
		 */

		throw new LexerException("Invalid character (" + c + "): " + ((char) c));
	}

	// SUPPORTING FUNCTIONS/METHODS
	private void terminator()
			throws IOException
	{
		// like opt_terminator, except error if no terminator was found
		if (!opt_terminator()) {
			throw new ParserException("Expecting statement terminator. Got (" + token + "): " + text);
		}
	}

	private boolean opt_terminator()
			throws IOException
	{
		if (opt_newline()) {
			return true;
		} else if (token == _EOF_ || token == _CLOSE_BRACE_) {
			return true; // do nothing
		} else if (token == _SEMICOLON_) {
			lexer();
			return true;
		} else {
			// no terminator consumed
			return false;
		}
	}

	private boolean opt_newline()
			throws IOException
	{
		if (token == _NEWLINE_) {
			lexer();
			return true;
		} else {
			return false;
		}
	}

	// RECURSIVE DECENT PARSER:
	// SCRIPT : \n [RULE_LIST] EOF
	AST SCRIPT()
			throws IOException
	{
		AST rl;
		if (token != _EOF_) {
			rl = RULE_LIST();
		} else {
			rl = null;
		}
		lexer(_EOF_);
		return rl;
	}

	// RULE_LIST : \n [ ( RULE | FUNCTION terminator ) opt_terminator RULE_LIST ]
	AST RULE_LIST()
			throws IOException
	{
		opt_newline();
		AST rule_or_function = null;
		if (token == KEYWORDS.get("function")) {
			rule_or_function = FUNCTION();
		} else if (token != _EOF_) {
			rule_or_function = RULE();
		} else {
			return null;
		}
		opt_terminator();	// newline or ; (maybe)
		if (rule_or_function == null) {
			return RULE_LIST();
		} else {
			return new RuleList_AST(rule_or_function, RULE_LIST());
		}
	}

	AST FUNCTION()
			throws IOException
	{
		expectKeyword("function");
		String function_name;
		if (token == _FUNC_ID_ || token == _ID_) {
			function_name = text.toString();
			lexer();
		} else {
			throw new ParserException("Expecting function name. Got (" + token + "): " + text);
		}
		symbol_table.setFunctionName(function_name);
		lexer(_OPEN_PAREN_);
		AST formal_param_list;
		if (token == _CLOSE_PAREN_) {
			formal_param_list = null;
		} else {
			formal_param_list = FORMAL_PARAM_LIST(function_name);
		}
		lexer(_CLOSE_PAREN_);
		opt_newline();

		lexer(_OPEN_BRACE_);
		AST function_block = STATEMENT_LIST();
		lexer(_CLOSE_BRACE_);
		symbol_table.clearFunctionName(function_name);
		return symbol_table.addFunctionDef(function_name, formal_param_list, function_block);
	}

	AST FORMAL_PARAM_LIST(String func_name)
			throws IOException
	{
		if (token == _ID_) {
			String id = text.toString();
			int offset = symbol_table.addFunctionParameter(func_name, id);
			lexer();
			if (token == _COMMA_) {
				lexer();
				opt_newline();
				AST rest = FORMAL_PARAM_LIST(func_name);
				if (rest == null) {
					throw new ParserException("Cannot terminate a formal parameter list with a comma.");
				} else {
					return new FunctionDefParamList_AST(id, offset, rest);
				}
			} else {
				return new FunctionDefParamList_AST(id, offset, null);
			}
		} else {
			return null;
		}
	}

	// RULE : [ASSIGNMENT_EXPRESSION] [ { STATEMENT_LIST } ]
	AST RULE()
			throws IOException
	{
		AST opt_expr;
		AST opt_stmts;
		if (token == KEYWORDS.get("BEGIN")) {
			lexer();
			opt_expr = symbol_table.addBEGIN();
		} else if (token == KEYWORDS.get("END")) {
			lexer();
			opt_expr = symbol_table.addEND();
		} else if (token != _OPEN_BRACE_ && token != _SEMICOLON_ && token != _NEWLINE_ && token != _EOF_) {
			// true = allow comparators, allow IN keyword, do NOT allow multidim indices expressions
			opt_expr = ASSIGNMENT_EXPRESSION(true, true, false);
			// for /regex/, /regex/
			if (token == _COMMA_) {
				lexer();
				opt_newline();
				// true = allow comparators, allow IN keyword, do NOT allow multidim indices expressions
				opt_expr = new RegexpPair_AST(opt_expr, ASSIGNMENT_EXPRESSION(true, true, false));
			}
		} else {
			opt_expr = null;
		}
		if (token == _OPEN_BRACE_) {
			lexer();
			opt_stmts = STATEMENT_LIST();
			lexer(_CLOSE_BRACE_);
		} else {
			opt_stmts = null;
		}
		return new Rule_AST(opt_expr, opt_stmts);
	}

	// STATEMENT_LIST : [ STATEMENT_BLOCK|STATEMENT STATEMENT_LIST ]
	private AST STATEMENT_LIST()
			throws IOException
	{
		// statement lists can only live within curly brackets (braces)
		opt_newline();
		if (token == _CLOSE_BRACE_ || token == _EOF_) {
			return null;
		}
		AST stmt;
		if (token == _OPEN_BRACE_) {
			lexer();
			stmt = STATEMENT_LIST();
			lexer(_CLOSE_BRACE_);
		} else {
			if (token == _SEMICOLON_) {
				// an empty statement (;)
				// do not polute the syntax tree with nulls in this case
				// just return the next statement (recursively)
				lexer();
				return STATEMENT_LIST();
			} else {
				stmt = STATEMENT();
			}
		}

		AST rest = STATEMENT_LIST();
		if (rest == null) {
			return stmt;
		} else if (stmt == null) {
			return rest;
		} else {
			return new STATEMENTLIST_AST(stmt, rest);
		}
	}

	// EXPRESSION_LIST : ASSIGNMENT_EXPRESSION [, EXPRESSION_LIST]
	AST EXPRESSION_LIST(boolean allow_comparators, boolean allow_in_keyword)
			throws IOException
	{
		AST expr = ASSIGNMENT_EXPRESSION(allow_comparators, allow_in_keyword, false);	// do NOT allow multidim indices expressions
		if (token == _COMMA_) {
			lexer();
			opt_newline();
			return new FunctionCallParamList_AST(expr, EXPRESSION_LIST(allow_comparators, allow_in_keyword));
		} else {
			return new FunctionCallParamList_AST(expr, null);
		}
	}

	// ASSIGNMENT_EXPRESSION = COMMA_EXPRESSION [ (=,+=,-=,*=) ASSIGNMENT_EXPRESSION ]
	AST ASSIGNMENT_EXPRESSION(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		AST comma_expression = COMMA_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
		int op = 0;
		String txt = null;
		AST assignment_expression = null;
		if (token == _EQUALS_ || token == _PLUS_EQ_ || token == _MINUS_EQ_ || token == _MULT_EQ_ || token == _DIV_EQ_ || token == _MOD_EQ_ || token == _POW_EQ_) {
			op = token;
			txt = text.toString();
			lexer();
			assignment_expression = ASSIGNMENT_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
			return new AssignmentExpression_AST(comma_expression, op, txt, assignment_expression);
		}
		return comma_expression;
	}

	// COMMA_EXPRESSION = CONCAT_EXPRESSION [, COMMA_EXPRESSION] !!!ONLY IF!!! allow_multidim_indices is true
	// allow_multidim_indices is set to true when we need (1,2,3,4) expressions to collapse into an array index expression
	// (converts 1,2,3,4 to 1 SUBSEP 2 SUBSEP 3 SUBSEP 4) after an open parenthesis (grouping) expression starter
	AST COMMA_EXPRESSION(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		AST concat_expression = CONCAT_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
		if (allow_multidim_indices && token == _COMMA_) {
			// consume the comma
			lexer();
			opt_newline();
			AST rest = COMMA_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
			if (rest instanceof ArrayIndex_AST) {
				return new ArrayIndex_AST(concat_expression, rest);
			} else {
				return new ArrayIndex_AST(concat_expression, new ArrayIndex_AST(rest, null));
			}
		} else {
			return concat_expression;
		}
	}

	// CONCAT_EXPRESSION = LE_1 [ CONCAT_EXPRESSION ]
	AST CONCAT_EXPRESSION(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		AST te = TERTIARY_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
		if (token == _INTEGER_ || token == _DOUBLE_ || token == _OPEN_PAREN_ || token == _FUNC_ID_ || token == _INC_ || token == _DEC_ || token == _ID_ || token == _STRING_ || token == _DOLLAR_ || token == _BUILTIN_FUNC_NAME_ || token == _EXTENSION_) {
			// allow concatination here only when certain tokens follow
			return new ConcatExpression_AST(te, CONCAT_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices));
		} else if (additional_type_functions && (
					token == KEYWORDS.get("_INTEGER")
					|| token == KEYWORDS.get("_DOUBLE")
					|| token == KEYWORDS.get("_STRING")))
		{
			// allow concatination here only when certain tokens follow
			return new ConcatExpression_AST(te, CONCAT_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices));
		} else {
			return te;
		}
	}

	AST TERTIARY_EXPRESSION(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		AST le1 = LE1_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
		if (token == _QUESTION_MARK_) {
			lexer();
			AST true_block = TERTIARY_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
			lexer(_COLON_);
			AST false_block = TERTIARY_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
			return new TertiaryExpression_AST(le1, true_block, false_block);
		} else {
			return le1;
		}
	}

	// LE_1 = LE_2 [ || LE_1 ]
	AST LE1_EXPRESSION(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		AST le2 = LE2_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
		int op = 0;
		String txt = null;
		AST le1 = null;
		if (token == _OR_) {
			op = token;
			txt = text.toString();
			lexer();
			le1 = LE1_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
			return new LogicalExpression_AST(le2, op, txt, le1);
		}
		return le2;
	}

	// LE_2 = COMPARISON_EXPRESSION [ && LE_2 ]
	AST LE2_EXPRESSION(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		AST comparison_expression = COMPARISON_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
		int op = 0;
		String txt = null;
		AST le2 = null;
		if (token == _AND_) {
			op = token;
			txt = text.toString();
			lexer();
			le2 = LE2_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
			return new LogicalExpression_AST(comparison_expression, op, txt, le2);
		}
		return comparison_expression;
	}

	// COMPARISON_EXPRESSION = EXPRESSION [ (==,>,>=,<,<=,!=,~,!~) COMPARISON_EXPRESSION ]
	// allow_comparators is set false when within a print/printf statement;
	// all other times it is set true
	AST COMPARISON_EXPRESSION(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		AST expression = EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
		int op = 0;
		String txt = null;
		AST comparison_expression = null;
		if (allow_comparators
				&& (token == _EQ_ || token == _GT_ || token == _GE_ || token == _LT_ || token == _LE_ || token == _NE_ || token == _MATCHES_ || token == _NOT_MATCHES_))
		{
			op = token;
			txt = text.toString();
			lexer();
			comparison_expression = COMPARISON_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
			return new ComparisonExpression_AST(expression, op, txt, comparison_expression);
		}
		return expression;
	}

	// EXPRESSION : TERM [ (+|-|,) EXPRESSION ]
	AST EXPRESSION(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		AST term = TERM(allow_comparators, allow_in_keyword, allow_multidim_indices);
		int op = 0;
		String txt = null;
		AST expression = null;
		if (token == _PLUS_ || token == _MINUS_) {
			//|| token == _COMMA_
			op = token;
			txt = text.toString();
			lexer();
			expression = EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);

			BinaryExpression_AST retval = new BinaryExpression_AST(term, op, txt, expression);
			if (expression instanceof BinaryExpression_AST) {
				BinaryExpression_AST be2 = (BinaryExpression_AST) expression;
				if (be2.op == _PLUS_ || be2.op == _MINUS_) {
					// convert right-associativity to left-assiciativity
					return rearrange(retval, be2);
				}
			}
			return retval;
		}
		return term;
	}

	// Before:
	//
	//     |
	//     |
	//    b1
	//   /  \
	//  /    \
	// a     b2
	//      /  \
	//     /    \
	//    b      c
	//
	// After:
	//
	//         |
	//         |
	//        b2
	//       /  \
	//      /    \
	//    b1      c
	//   /  \
	//  /    \
	// a      b
	private static BinaryExpression_AST rearrange(BinaryExpression_AST b1, BinaryExpression_AST b2) {

		assert b1.ast2 == b2;

		AST a = b1.ast1;
		AST b = b2.ast1;
		AST c = b2.ast2;

		b1.ast1 = a;
		b1.ast2 = b;
		b2.ast2 = c;

		b2.ast1 = b1;

		return b2;
	}

	// TERM : FACTOR_FOR_GETLINE [ (*|/|%|^) TERM ]
	AST TERM(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		AST factor_for_getline = FACTOR_FOR_GETLINE(allow_comparators, allow_in_keyword, allow_multidim_indices);
		int op = 0;
		String txt = null;
		AST term = null;
		if (token == _MULT_ || token == _DIVIDE_ || token == _MOD_ || token == _POW_) {
			op = token;
			txt = text.toString();
			lexer();
			term = TERM(allow_comparators, allow_in_keyword, allow_multidim_indices);

			BinaryExpression_AST retval = new BinaryExpression_AST(factor_for_getline, op, txt, term);
			if (term instanceof BinaryExpression_AST) {
				BinaryExpression_AST be2 = (BinaryExpression_AST) term;
				if (be2.op == _MULT_ || be2.op == _DIVIDE_ || be2.op == _MOD_ || be2.op == _POW_) {
					// convert right-associativity to left-assiciativity
					return rearrange(retval, be2);
				}
			}
			return retval;
		}
		return factor_for_getline;
	}

	// FACTOR_FOR_GETLINE : FACTOR_FOR_IN [ | getline_expr ]
	// allow_comparators is set false when within a print/printf statement;
	// all other times it is set true
	AST FACTOR_FOR_GETLINE(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		AST factor_for_in = FACTOR_FOR_IN(allow_comparators, allow_in_keyword, allow_multidim_indices);
		if (allow_comparators && token == _PIPE_) {
			lexer();
			return GETLINE_EXPRESSION(factor_for_in, allow_comparators, allow_in_keyword);
		}
		return factor_for_in;
	}

	// allow_in_keyword is set false while parsing the first expression within
	// a for() statement (because it could be "for (key in arr)", and this
	// production will consume and the for statement will never have a chance
	// of processing it
	// all other times, it is true
	AST FACTOR_FOR_IN(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		// true = allow post_inc/dec operators
		AST factor_for_incdec = FACTOR_FOR_INCDEC(allow_comparators, allow_in_keyword, allow_multidim_indices);
		if (allow_in_keyword && token == KEYWORDS.get("in")) {
			lexer();
			return new InExpression_AST(factor_for_incdec, FACTOR_FOR_IN(allow_comparators, allow_in_keyword, allow_multidim_indices));
		}
		return factor_for_incdec;
	}

	// according to the spec, pre/post inc can occur
	// only on lvalues, which are NAMES (IDs), array,
	// or field references
	private boolean isLvalue(AST ast) {
		return     (ast instanceof ID_AST)
				|| (ast instanceof ArrayReference_AST)
				|| (ast instanceof DollarExpression_AST);
	}

	AST FACTOR_FOR_INCDEC(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		boolean pre_inc = false;
		boolean pre_dec = false;
		boolean post_inc = false;
		boolean post_dec = false;
		if (token == _INC_) {
			pre_inc = true;
			lexer();
		} else if (token == _DEC_) {
			pre_dec = true;
			lexer();
		}

		AST factor_ast = FACTOR(allow_comparators, allow_in_keyword, allow_multidim_indices);

		if ((pre_inc || pre_dec) && !isLvalue(factor_ast)) {
			throw new ParserException("Cannot pre inc/dec a non-lvalue");
		}

		// only do post ops if:
		// - factor_ast is an lvalue
		// - pre ops were not encountered
		if (isLvalue(factor_ast) && !pre_inc && !pre_dec) {
			if (token == _INC_) {
				post_inc = true;
				lexer();
			} else if (token == _DEC_) {
				post_dec = true;
				lexer();
			}
		}

		if ((pre_inc || pre_dec) && (post_inc || post_dec)) {
			throw new ParserException("Cannot do pre inc/dec AND post inc/dec.");
		}

		if (pre_inc) {
			return new PreInc_AST(factor_ast);
		} else if (pre_dec) {
			return new PreDec_AST(factor_ast);
		} else if (post_inc) {
			return new PostInc_AST(factor_ast);
		} else if (post_dec) {
			return new PostDec_AST(factor_ast);
		} else {
			return factor_ast;
		}
	}

	// FACTOR : '(' ASSIGNMENT_EXPRESSION ')' | ! FACTOR | $ FACTOR | - FACTOR | _INTEGER_ | _DOUBLE_ | _STRING_ | GETLINE [ID-or-array-or-$val] | /[=].../ | [++|--] SYMBOL [++|--]
	//AST FACTOR(boolean allow_comparators, boolean allow_in_keyword, boolean allow_post_incdec_operators)
	AST FACTOR(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		if (token == _OPEN_PAREN_) {
			lexer();
			// true = allow multi-dimensional array indices (i.e., commas for 1,2,3,4)
			AST assignment_expression = ASSIGNMENT_EXPRESSION(allow_comparators, allow_in_keyword, true);
			if (allow_multidim_indices && (assignment_expression instanceof ArrayIndex_AST)) {
				throw new ParserException("Cannot nest multi-dimensional array index expressions.");
			}
			lexer(_CLOSE_PAREN_);
			return assignment_expression;
		} else if (token == _NOT_) {
			lexer();
			return new NotExpression_AST(FACTOR(allow_comparators, allow_in_keyword, allow_multidim_indices));
		} else if (token == _MINUS_) {
			lexer();
			return new NegativeExpression_AST(FACTOR(allow_comparators, allow_in_keyword, allow_multidim_indices));
		}
		else if (token == _INTEGER_) {
			AST integer = symbol_table.addINTEGER(text.toString());
			lexer();
			return integer;
		} else if (token == _DOUBLE_) {
			AST dbl = symbol_table.addDOUBLE(text.toString());
			lexer();
			return dbl;
		} else if (token == _STRING_) {
			AST str = symbol_table.addSTRING(string.toString());
			lexer();
			return str;
		} else if (token == KEYWORDS.get("getline")) {
			return GETLINE_EXPRESSION(null, allow_comparators, allow_in_keyword);
		} else if (token == _DIVIDE_ || token == _DIV_EQ_) {
			// /.../ or /=.../
			regexp.setLength(0);
			if (token == _DIV_EQ_) {
				regexp.append('=');
			}
			readRegexp();
			AST regexp_ast = symbol_table.addREGEXP(regexp.toString());
			lexer();
			return regexp_ast;
		} else if (additional_type_functions && token == KEYWORDS.get("_INTEGER")) {
			return INTEGER_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
		} else if (additional_type_functions && token == KEYWORDS.get("_DOUBLE")) {
			return DOUBLE_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
		} else if (additional_type_functions && token == KEYWORDS.get("_STRING")) {
			return STRING_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices);
		} else {
			AST target_ast;
			if (token == _DOLLAR_) {
				lexer();
				target_ast = new DollarExpression_AST(FACTOR(allow_comparators, allow_in_keyword, allow_multidim_indices));
			} else {
				target_ast = SYMBOL(allow_comparators, allow_in_keyword);
			}

			return target_ast;
		}
	}

	AST INTEGER_EXPRESSION(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		boolean parens = (c == '(');
		expectKeyword("_INTEGER");
		if (token == _SEMICOLON_ || token == _NEWLINE_ || token == _CLOSE_BRACE_) {
			throw new ParserException("expression expected");
		} else {
			// do NOT allow for a blank param list: "()" using the parens boolean below
			// otherwise, the parser will complain because assignment_expression cannot be ()
			if (parens) {
				lexer(_OPEN_PAREN_);
			}
			AST int_expr_ast;
			if (token == _CLOSE_PAREN_) {
				throw new ParserException("expression expected");
			} else {
				int_expr_ast = new IntegerExpression_AST(ASSIGNMENT_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices));
			}
			if (parens) {
				lexer(_CLOSE_PAREN_);
			}
			return int_expr_ast;
		}
	}

	AST DOUBLE_EXPRESSION(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		boolean parens = (c == '(');
		expectKeyword("_DOUBLE");
		if (token == _SEMICOLON_ || token == _NEWLINE_ || token == _CLOSE_BRACE_) {
			throw new ParserException("expression expected");
		} else {
			// do NOT allow for a blank param list: "()" using the parens boolean below
			// otherwise, the parser will complain because assignment_expression cannot be ()
			if (parens) {
				lexer(_OPEN_PAREN_);
			}
			AST double_expr_ast;
			if (token == _CLOSE_PAREN_) {
				throw new ParserException("expression expected");
			} else {
				double_expr_ast = new DoubleExpression_AST(ASSIGNMENT_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices));
			}
			if (parens) {
				lexer(_CLOSE_PAREN_);
			}
			return double_expr_ast;
		}
	}

	AST STRING_EXPRESSION(boolean allow_comparators, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException
	{
		boolean parens = (c == '(');
		expectKeyword("_STRING");
		if (token == _SEMICOLON_ || token == _NEWLINE_ || token == _CLOSE_BRACE_) {
			throw new ParserException("expression expected");
		} else {
			// do NOT allow for a blank param list: "()" using the parens boolean below
			// otherwise, the parser will complain because assignment_expression cannot be ()
			if (parens) {
				lexer(_OPEN_PAREN_);
			}
			AST string_expr_ast;
			if (token == _CLOSE_PAREN_) {
				throw new ParserException("expression expected");
			} else {
				string_expr_ast = new StringExpression_AST(ASSIGNMENT_EXPRESSION(allow_comparators, allow_in_keyword, allow_multidim_indices));
			}
			if (parens) {
				lexer(_CLOSE_PAREN_);
			}
			return string_expr_ast;
		}
	}

	// SYMBOL : _ID_ [ '(' params ')' | '[' ASSIGNMENT_EXPRESSION ']' ]
	AST SYMBOL(boolean allow_comparators, boolean allow_in_keyword)
			throws IOException
	{
		if (token != _ID_ && token != _FUNC_ID_ && token != _BUILTIN_FUNC_NAME_ && token != _EXTENSION_) {
			throw new ParserException("Expecting an ID. Got (" + token + "): " + text);
		}
		int id_token = token;
		String id = text.toString();
		boolean parens = (c == '(');
		lexer();

		if (id_token == _EXTENSION_) {
			String extension_keyword = id;
			//JawkExtension extension = extensions.get(extension_keyword);
			AST params;

			/*
			if (extension.requiresParen()) {
				lexer(_OPEN_PAREN_);
				if (token == _CLOSE_PAREN_)
					params = null;
				else
					params = EXPRESSION_LIST(allow_comparators, allow_in_keyword);
				lexer(_CLOSE_PAREN_);
			} else {
				boolean parens = (c == '(');
				//expectKeyword("delete");
				if (parens) {
					assert token == _OPEN_PAREN_;
					lexer();
				}
				//AST symbol_ast = SYMBOL(true,true);	// allow comparators
				params = EXPRESSION_LIST(allow_comparators, allow_in_keyword);
				if (parens)
					lexer(_CLOSE_PAREN_);
			}
			 */

			//if (extension.requiresParens() || parens)
			if (parens) {
				lexer();
				if (token == _CLOSE_PAREN_) {
					params = null;
				} else { //?//params = EXPRESSION_LIST(false,true);	// NO comparators allowed, allow in expression
					params = EXPRESSION_LIST(allow_comparators, allow_in_keyword);	// NO comparators allowed, allow in expression
				}
				lexer(_CLOSE_PAREN_);
			} else {
				/*
				if (token == _NEWLINE_ || token == _SEMICOLON_ || token == _CLOSE_BRACE_ || token == _CLOSE_PAREN_
						|| (token == _GT_ || token == _APPEND_ || token == _PIPE_) )
					params = null;
				else
					params = EXPRESSION_LIST(false,true);	// NO comparators allowed, allow in expression
				 */
				params = null;
			}

			return new Extension_AST(extension_keyword, params);
		} else if (id_token == _FUNC_ID_ || id_token == _BUILTIN_FUNC_NAME_) {
			AST params;
			// length can take on the special form of no parens
			if (id.equals("length")) {
				if (token == _OPEN_PAREN_) {
					lexer();
					if (token == _CLOSE_PAREN_) {
						params = null;
					} else {
						params = EXPRESSION_LIST(allow_comparators, allow_in_keyword);
					}
					lexer(_CLOSE_PAREN_);
				} else {
					params = null;
				}
			} else {
				lexer(_OPEN_PAREN_);
				if (token == _CLOSE_PAREN_) {
					params = null;
				} else {
					params = EXPRESSION_LIST(allow_comparators, allow_in_keyword);
				}
				lexer(_CLOSE_PAREN_);
			}
			if (id_token == _BUILTIN_FUNC_NAME_) {
				return new BuiltinFunctionCall_AST(id, params);
			} else {
				return symbol_table.addFunctionCall(id, params);
			}
		}
		if (token == _OPEN_BRACKET_) {
			lexer();
			AST idx_ast = ARRAY_INDEX(allow_comparators, allow_in_keyword);
			lexer(_CLOSE_BRACKET_);
			if (token == _OPEN_BRACKET_) {
				throw new ParserException("Use [a,b,c,...] instead of [a][b][c]... for multi-dimensional arrays.");
			}
			return symbol_table.addArrayReference(id, idx_ast);
		}
		return symbol_table.addID(id);
	}

	// ARRAY_INDEX : ASSIGNMENT_EXPRESSION [, ARRAY_INDEX]
	AST ARRAY_INDEX(boolean allow_comparators, boolean allow_in_keyword)
			throws IOException
	{
		AST expr_ast = ASSIGNMENT_EXPRESSION(allow_comparators, allow_in_keyword, false);
		if (token == _COMMA_) {
			opt_newline();
			lexer();
			return new ArrayIndex_AST(expr_ast, ARRAY_INDEX(allow_comparators, allow_in_keyword));
		} else {
			return new ArrayIndex_AST(expr_ast, null);
		}
	}

	// STATEMENT :
	// 	IF_STATEMENT
	// 	| WHILE_STATEMENT
	// 	| FOR_STATEMENT
	// 	| DO_STATEMENT
	// 	| RETURN_STATEMENT
	// 	| ASSIGNMENT_EXPRESSION
	AST STATEMENT()
			throws IOException
	{
		if (token == _OPEN_BRACE_) {
			lexer();
			AST lst = STATEMENT_LIST();
			lexer(_CLOSE_BRACE_);
			return lst;
		}
		AST stmt;
		if (token == KEYWORDS.get("if")) { stmt = IF_STATEMENT();
		} else if (token == KEYWORDS.get("while")) { stmt = WHILE_STATEMENT();
		} else if (token == KEYWORDS.get("for")) { stmt = FOR_STATEMENT();
		} else {
			if (token == KEYWORDS.get("do")) { stmt = DO_STATEMENT();
			} else if (token == KEYWORDS.get("return")) { stmt = RETURN_STATEMENT();
			} else if (token == KEYWORDS.get("exit")) { stmt = EXIT_STATEMENT();
			} else if (token == KEYWORDS.get("delete")) { stmt = DELETE_STATEMENT();
			} else if (token == KEYWORDS.get("print")) { stmt = PRINT_STATEMENT();
			} else if (token == KEYWORDS.get("printf")) { stmt = PRINTF_STATEMENT();
			} else if (token == KEYWORDS.get("next")) { stmt = NEXT_STATEMENT();
			} else if (token == KEYWORDS.get("continue")) { stmt = CONTINUE_STATEMENT();
			} else if (token == KEYWORDS.get("break")) { stmt = BREAK_STATEMENT();
			} else if (additional_functions && token == KEYWORDS.get("_sleep")) { stmt = SLEEP_STATEMENT();
			} else if (additional_functions && token == KEYWORDS.get("_dump")) { stmt = DUMP_STATEMENT();
			} else { stmt = EXPRESSION_STATEMENT(true, false);	// allow in keyword, do NOT allow NonStatement_ASTs
			}
			terminator();
			return stmt;
		}
		// NO TERMINATOR FOR IF, WHILE, AND FOR
		// (leave it for absorption by the callee)
		return stmt;
	}

	AST EXPRESSION_STATEMENT(boolean allow_in_keyword, boolean allow_non_statement_asts)
			throws IOException
	{
		// true = allow comparators
		// false = do NOT allow multi-dimensional array indices
		//return new ExpressionStatement_AST(ASSIGNMENT_EXPRESSION(true, allow_in_keyword, false));

		AST expr_ast = ASSIGNMENT_EXPRESSION(true, allow_in_keyword, false);
		if (!allow_non_statement_asts && expr_ast instanceof NonStatement_AST) {
			throw new ParserException("Not a valid statement.");
		}
		return new ExpressionStatement_AST(expr_ast);
	}

	AST IF_STATEMENT()
			throws IOException
	{
		expectKeyword("if");
		lexer(_OPEN_PAREN_);
		AST expr = ASSIGNMENT_EXPRESSION(true, true, false);	// allow comparators, allow in keyword, do NOT allow multidim indices expressions
		lexer(_CLOSE_PAREN_);

		//// Was:
		//// AST b1 = BLOCK_OR_STMT();
		//// But it didn't handle
		//// if ; else ...
		//// properly
		opt_newline();
		AST b1;
		if (token == _SEMICOLON_) {
			lexer();
			// consume the newline after the semicolon
			opt_newline();
			b1 = null;
		} else {
			b1 = BLOCK_OR_STMT();
		}

		// The OPT_NEWLINE() above causes issues with the following form:
		// if (...) {
		// }
		// else { ... }
		// The \n before the else disassociates subsequent statements
		// if an "else" does not immediately follow.
		// To accommodate, the if_statement will continue to manage
		// statements, causing the original OPT_STATEMENT_LIST to relinquish
		// processing statements to this OPT_STATEMENT_LIST.

		opt_newline();
		if (token == KEYWORDS.get("else")) {
			lexer();
			opt_newline();
			AST b2 = BLOCK_OR_STMT();
			return new IfStatement_AST(expr, b1, b2);
		} else {
			AST if_ast = new IfStatement_AST(expr, b1, null);
			return if_ast;
		}
	}

	AST BREAK_STATEMENT()
			throws IOException
	{
		expectKeyword("break");
		return new BreakStatement_AST();
	}

	AST BLOCK_OR_STMT()
			throws IOException
	{
		// default case, does NOT consume (require) a terminator
		return BLOCK_OR_STMT(false);
	}

	AST BLOCK_OR_STMT(boolean require_terminator)
			throws IOException
	{
		opt_newline();
		AST block;
		// HIJACK BRACES HERE SINCE WE MAY NOT HAVE A TERMINATOR AFTER THE CLOSING BRACE
		if (token == _OPEN_BRACE_) {
			lexer();
			block = STATEMENT_LIST();
			lexer(_CLOSE_BRACE_);
			return block;
		} else if (token == _SEMICOLON_) {
			block = null;
		} else {
			block = STATEMENT();
			// NO TERMINATOR HERE!
		}
		if (require_terminator) {
			terminator();
		}
		return block;
	}

	AST WHILE_STATEMENT()
			throws IOException
	{
		expectKeyword("while");
		lexer(_OPEN_PAREN_);
		AST expr = ASSIGNMENT_EXPRESSION(true, true, false);	// allow comparators, allow IN keyword, do NOT allow multidim indices expressions
		lexer(_CLOSE_PAREN_);
		AST block = BLOCK_OR_STMT();
		return new WhileStatement_AST(expr, block);
	}

	AST FOR_STATEMENT()
			throws IOException
	{
		expectKeyword("for");
		AST expr1 = null;
		AST expr2 = null;
		AST expr3 = null;
		lexer(_OPEN_PAREN_);
		expr1 = OPT_SIMPLE_STATEMENT(false);	// false = "no in keyword allowed"

		// branch here if we expect a for(... in ...) statement
		if (token == KEYWORDS.get("in")) {
			if (expr1.ast1 == null || expr1.ast2 != null) {
				throw new ParserException("Invalid expression prior to 'in' statement. Got : " + expr1);
			}
			expr1 = expr1.ast1;
			// analyze expr1 to make sure it's a singleton ID_AST
			if (expr1 == null || !(expr1 instanceof ID_AST)) {
				throw new ParserException("Expecting an ID for 'in' statement. Got : " + expr1);
			}
			// in
			lexer();
			// id
			if (token != _ID_) {
				throw new ParserException("Expecting an ARRAY ID for 'in' statement. Got (" + token + "): " + text);
			}
			String arr_id = text.toString();

			// not an indexed array reference!
			AST array_id_ast = symbol_table.addArrayID(arr_id);

			lexer();
			// close paren ...
			lexer(_CLOSE_PAREN_);
			AST block = BLOCK_OR_STMT();
			return new ForInStatement_AST(expr1, array_id_ast, block);
		}

		if (token == _SEMICOLON_) {
			lexer();
		} else {
			throw new ParserException("Expecting ;. Got (" + token + "): " + text);
		}
		if (token != _SEMICOLON_) {
			expr2 = ASSIGNMENT_EXPRESSION(true, true, false);	// allow comparators, allow IN keyword, do NOT allow multidim indices expressions
		}
		if (token == _SEMICOLON_) {
			lexer();
		} else {
			throw new ParserException("Expecting ;. Got (" + token + "): " + text);
		}
		if (token != _CLOSE_PAREN_) {
			expr3 = OPT_SIMPLE_STATEMENT(true);	// true = "allow the in keyword"
		}
		lexer(_CLOSE_PAREN_);
		AST block = BLOCK_OR_STMT();
		return new ForStatement_AST(expr1, expr2, expr3, block);
	}

	AST OPT_SIMPLE_STATEMENT(boolean allow_in_keyword)
			throws IOException
	{
		if (token == _SEMICOLON_) {
			return null;
		} else if (token == KEYWORDS.get("delete")) {
			return DELETE_STATEMENT();
		} else if (token == KEYWORDS.get("print")) {
			return PRINT_STATEMENT();
		} else if (token == KEYWORDS.get("printf")) {
			return PRINTF_STATEMENT();
		} else {
			// allow NonStatement_ASTs
			return EXPRESSION_STATEMENT(allow_in_keyword, true);
		}
	}

	AST DELETE_STATEMENT()
			throws IOException
	{
		boolean parens = (c == '(');
		expectKeyword("delete");
		if (parens) {
			assert token == _OPEN_PAREN_;
			lexer();
		}
		AST symbol_ast = SYMBOL(true, true);	// allow comparators
		if (parens) {
			lexer(_CLOSE_PAREN_);
		}

		return new DeleteStatement_AST(symbol_ast);
	}

	private static final class ParsedPrintStatement {

		private AST funcParams;
		private int outputToken;
		private AST outputExpr;

		ParsedPrintStatement(AST funcParams, int outputToken, AST outputExpr) {
			this.funcParams = funcParams;
			this.outputToken = outputToken;
			this.outputExpr = outputExpr;
		}

		public AST getFuncParams() {
			return funcParams;
		}

		public int getOutputToken() {
			return outputToken;
		}

		public AST getOutputExpr() {
			return outputExpr;
		}
	}

	private ParsedPrintStatement parsePrintStatement(boolean parens)
			throws IOException
	{
		AST func_params;
		int output_token;
		AST output_expr;
		if (parens) {
			lexer();
			if (token == _CLOSE_PAREN_) {
				func_params = null;
			} else {
				func_params = EXPRESSION_LIST(false, true);	// NO comparators allowed, allow in expression
			}
			lexer(_CLOSE_PAREN_);
		} else {
			if (token == _NEWLINE_ || token == _SEMICOLON_ || token == _CLOSE_BRACE_ || token == _CLOSE_PAREN_
					|| (token == _GT_ || token == _APPEND_ || token == _PIPE_))
			{
				func_params = null;
			} else {
				func_params = EXPRESSION_LIST(false, true);	// NO comparators allowed, allow in expression
			}
		}
		if (token == _GT_ || token == _APPEND_ || token == _PIPE_) {
			output_token = token;
			lexer();
			output_expr = ASSIGNMENT_EXPRESSION(true, true, false);	// true = allow comparators, allow IN keyword, do NOT allow multidim indices expressions
		} else {
			output_token = -1;
			output_expr = null;
		}

		return new ParsedPrintStatement(func_params, output_token, output_expr);
	}

	AST PRINT_STATEMENT()
			throws IOException
	{
		boolean parens = (c == '(');
		expectKeyword("print");
		ParsedPrintStatement parsedPrintStatement = parsePrintStatement(parens);

		return new Print_AST(
				parsedPrintStatement.getFuncParams(),
				parsedPrintStatement.getOutputToken(),
				parsedPrintStatement.getOutputExpr());
	}

	AST PRINTF_STATEMENT()
			throws IOException
	{
		boolean parens = (c == '(');
		expectKeyword("printf");
		ParsedPrintStatement parsedPrintStatement = parsePrintStatement(parens);

		return new Printf_AST(
				parsedPrintStatement.getFuncParams(),
				parsedPrintStatement.getOutputToken(),
				parsedPrintStatement.getOutputExpr());
	}

	AST GETLINE_EXPRESSION(AST pipe_expr, boolean allow_comparators, boolean allow_in_keyword)
			throws IOException
	{
		expectKeyword("getline");
		AST lvalue = LVALUE(allow_comparators, allow_in_keyword);
		AST expr;
		if (token == _LT_) {
			lexer();
			AST assignment_expr = ASSIGNMENT_EXPRESSION(allow_comparators, allow_in_keyword, false);	// do NOT allow multidim indices expressions
			if (pipe_expr != null) {
				throw new ParserException("Cannot have both pipe expression and redirect into a getline.");
			}
			return new Getline_AST(pipe_expr, lvalue, assignment_expr);
		} else {
			return new Getline_AST(pipe_expr, lvalue, null);
		}
	}

	AST LVALUE(boolean allow_comparators, boolean allow_in_keyword)
			throws IOException
	{
		// false = do NOT allow multi dimension indices expressions
		if (token == _DOLLAR_) {
			return FACTOR(allow_comparators, allow_in_keyword, false);
		}
		if (token == _ID_) {
			return FACTOR(allow_comparators, allow_in_keyword, false);
		}
		return null;
	}

	AST DO_STATEMENT()
			throws IOException
	{
		expectKeyword("do");
		opt_newline();
		AST block = BLOCK_OR_STMT();
		if (token == _SEMICOLON_) {
			lexer();
		}
		opt_newline();
		expectKeyword("while");
		lexer(_OPEN_PAREN_);
		AST expr = ASSIGNMENT_EXPRESSION(true, true, false);	// true = allow comparators, allow IN keyword, do NOT allow multidim indices expressions
		lexer(_CLOSE_PAREN_);
		return new DoStatement_AST(block, expr);
	}

	AST RETURN_STATEMENT()
			throws IOException
	{
		expectKeyword("return");
		if (token == _SEMICOLON_ || token == _NEWLINE_ || token == _CLOSE_BRACE_) {
			return new ReturnStatement_AST(null);
		} else {
			return new ReturnStatement_AST(ASSIGNMENT_EXPRESSION(true, true, false));	// true = allow comparators, allow IN keyword, do NOT allow multidim indices expressions
		}
	}

	AST EXIT_STATEMENT()
			throws IOException
	{
		expectKeyword("exit");
		if (token == _SEMICOLON_ || token == _NEWLINE_ || token == _CLOSE_BRACE_) {
			return new ExitStatement_AST(null);
		} else {
			return new ExitStatement_AST(ASSIGNMENT_EXPRESSION(true, true, false));	// true = allow comparators, allow IN keyword, do NOT allow multidim indices expressions
		}
	}

	AST SLEEP_STATEMENT()
			throws IOException
	{
		boolean parens = (c == '(');
		expectKeyword("_sleep");
		if (token == _SEMICOLON_ || token == _NEWLINE_ || token == _CLOSE_BRACE_) {
			return new SleepStatement_AST(null);
		} else {
			// allow for a blank param list: "()" using the parens boolean below
			// otherwise, the parser will complain because assignment_expression cannot be ()
			if (parens) {
				lexer();
			}
			AST sleep_ast;
			if (token == _CLOSE_PAREN_) {
				sleep_ast = new SleepStatement_AST(null);
			} else {
				sleep_ast = new SleepStatement_AST(ASSIGNMENT_EXPRESSION(true, true, false));	// true = allow comparators, allow IN keyword, do NOT allow multidim indices expressions
			}
			if (parens) {
				lexer(_CLOSE_PAREN_);
			}
			return sleep_ast;
		}
	}

	AST DUMP_STATEMENT()
			throws IOException
	{
		boolean parens = (c == '(');
		expectKeyword("_dump");
		if (token == _SEMICOLON_ || token == _NEWLINE_ || token == _CLOSE_BRACE_) {
			return new DumpStatement_AST(null);
		} else {
			if (parens) {
				lexer();
			}
			AST dump_ast;
			if (token == _CLOSE_PAREN_) {
				dump_ast = new DumpStatement_AST(null);
			} else {
				dump_ast = new DumpStatement_AST(EXPRESSION_LIST(true, true));	// true = allow comparators, allow IN keyword
			}
			if (parens) {
				lexer(_CLOSE_PAREN_);
			}
			return dump_ast;
		}
	}

	AST NEXT_STATEMENT()
			throws IOException
	{
		expectKeyword("next");
		return new NextStatement_AST();
	}

	AST CONTINUE_STATEMENT()
			throws IOException
	{
		expectKeyword("continue");
		return new ContinueStatement_AST();
	}

	private void expectKeyword(String keyword)
			throws IOException
	{
		if (token == KEYWORDS.get(keyword)) {
			lexer();
		} else {
			throw new ParserException("Expecting " + keyword + ". Got (" + token + "): " + text);
		}
	}

	// parser
	// ===============================================================================
	// AST class defs
	private abstract class AST implements AwkSyntaxTree {

		private final String sourceDescription = scriptSources.get(scriptSourcesCurrentIndex).getDescription();
		private final int lineNo = reader.getLineNumber() + 1;
		protected AST parent;
		protected AST ast1, ast2, ast3, ast4;

		protected final AST searchFor(Class cls) {
			AST ptr = this;
			while (ptr != null) {
				if (cls.isInstance(ptr)) {
					return ptr;
				}
				ptr = ptr.parent;
			}
			return null;
		}

		protected AST() {}

		protected AST(AST ast1) {
			this.ast1 = ast1;

			if (ast1 != null) {
				ast1.parent = this;
			}
		}

		protected AST(AST ast1, AST ast2) {
			this.ast1 = ast1;
			this.ast2 = ast2;

			if (ast1 != null) {
				ast1.parent = this;
			}
			if (ast2 != null) {
				ast2.parent = this;
			}
		}

		protected AST(AST ast1, AST ast2, AST ast3) {
			this.ast1 = ast1;
			this.ast2 = ast2;
			this.ast3 = ast3;

			if (ast1 != null) {
				ast1.parent = this;
			}
			if (ast2 != null) {
				ast2.parent = this;
			}
			if (ast3 != null) {
				ast3.parent = this;
			}
		}

		protected AST(AST ast1, AST ast2, AST ast3, AST ast4) {
			this.ast1 = ast1;
			this.ast2 = ast2;
			this.ast3 = ast3;
			this.ast4 = ast4;

			if (ast1 != null) {
				ast1.parent = this;
			}
			if (ast2 != null) {
				ast2.parent = this;
			}
			if (ast3 != null) {
				ast3.parent = this;
			}
			if (ast4 != null) {
				ast4.parent = this;
			}
		}

		/**
		 * Dump a meaningful text representation of this
		 * abstract syntax tree node to the output (print)
		 * stream. Either it is called directly by the
		 * application program, or it is called by the
		 * parent node of this tree node.
		 *
		 * @param ps The print stream to dump the text
		 *   representation.
		 */
		@Override
		public void dump(PrintStream ps) {
			dump(ps, 0);
		}

		private void dump(PrintStream ps, int lvl) {
			StringBuffer spaces = new StringBuffer();
			for (int i = 0; i < lvl; i++) {
				spaces.append(' ');
			}
			ps.println(spaces + toString());
			if (ast1 != null) {
				ast1.dump(ps, lvl + 1);
			}
			if (ast2 != null) {
				ast2.dump(ps, lvl + 1);
			}
			if (ast3 != null) {
				ast3.dump(ps, lvl + 1);
			}
			if (ast4 != null) {
				ast4.dump(ps, lvl + 1);
			}
		}

		/**
		 * Apply semantic checks to this node. The default
		 * implementation is to simply call semanticAnalysis()
		 * on all the children of this abstract syntax tree node.
		 * Therefore, this method must be overridden to provide
		 * meaningful semantic analysis / checks.
		 *
		 * @throws SemanticException upon a semantic error.
		 */
		@Override
		public void semanticAnalysis() {
			if (ast1 != null) {
				ast1.semanticAnalysis();
			}
			if (ast2 != null) {
				ast2.semanticAnalysis();
			}
			if (ast3 != null) {
				ast3.semanticAnalysis();
			}
			if (ast4 != null) {
				ast4.semanticAnalysis();
			}
		}

		/**
		 * Appends tuples to the AwkTuples list
		 * for this abstract syntax tree node. Subclasses
		 * must implement this method.
		 * <p>
		 * This is called either by the main program to generate a full
		 * list of tuples for the abstract syntax tree, or it is called
		 * by other abstract syntax tree nodes in response to their
		 * attempt at populating tuples.
		 * </p>
		 *
		 * @param tuples The tuples to populate.
		 *
		 * @return The number of items left on the stack after
		 *	these tuples have executed.
		 */
		@Override
		public abstract int populateTuples(AwkTuples tuples);

		protected final void pushSourceLineNumber(AwkTuples tuples) {
			tuples.pushSourceLineNumber(lineNo);
		}

		protected final void popSourceLineNumber(AwkTuples tuples) {
			tuples.popSourceLineNumber(lineNo);
		}

		protected boolean is_begin = isBegin();
		private boolean isBegin() {
			boolean result = is_begin;
			if (!result && ast1 != null) {
				result = ast1.isBegin();
			}
			if (!result && ast2 != null) {
				result = ast2.isBegin();
			}
			if (!result && ast3 != null) {
				result = ast3.isBegin();
			}
			if (!result && ast4 != null) {
				result = ast4.isBegin();
			}
			return result;
		}

		protected boolean is_end = isEnd();
		private boolean isEnd() {
			boolean result = is_end;
			if (!result && ast1 != null) {
				result = ast1.isEnd();
			}
			if (!result && ast2 != null) {
				result = ast2.isEnd();
			}
			if (!result && ast3 != null) {
				result = ast3.isEnd();
			}
			if (!result && ast4 != null) {
				result = ast4.isEnd();
			}
			return result;
		}

		protected boolean is_function = isFunction();
		private boolean isFunction() {
			boolean result = is_function;
			if (!result && ast1 != null) {
				result = ast1.isFunction();
			}
			if (!result && ast2 != null) {
				result = ast2.isFunction();
			}
			if (!result && ast3 != null) {
				result = ast3.isFunction();
			}
			if (!result && ast4 != null) {
				result = ast4.isFunction();
			}
			return result;
		}

		public boolean isArray() {
			return false;
		}

		public boolean isScalar() {
			return false;
		}

		/**
		 * Made protected so that subclasses can access it.
		 * Package-level access was not necessary.
		 */
		protected class SemanticException extends RuntimeException {

			SemanticException(String msg) {
				super(msg + " (" + sourceDescription + ":" + lineNo + ")");
			}
		}

		protected final void throwSemanticException(String msg) {
			throw new SemanticException(msg);
		}

		@Override
		public String toString() {
			return getClass().getName().replaceFirst(".*[$.]", "");
		}
	}

	private abstract class ScalarExpression_AST extends AST {

		protected ScalarExpression_AST() {
			super();
		}

		protected ScalarExpression_AST(AST a1) {
			super(a1);
		}

		protected ScalarExpression_AST(AST a1, AST a2) {
			super(a1, a2);
		}

		protected ScalarExpression_AST(AST a1, AST a2, AST a3) {
			super(a1, a2, a3);
		}

		@Override
		public final boolean isArray() {
			return false;
		}

		@Override
		public final boolean isScalar() {
			return true;
		}
	}

	private static boolean isRule(AST ast) {
		return ast != null && !ast.isBegin() && !ast.isEnd() && !ast.isFunction();
	}

	/**
	 * Inspects the action rule condition whether it contains
	 * extensions. It does a superficial check of
	 * the abstract syntax tree of the action rule.
	 * In other words, it will not examine whether user-defined
	 * functions within the action rule contain extensions.
	 *
	 * @param ast The action rule expression to examine.
	 *
	 * @return true if the action rule condition contains
	 * 	an extension; false otherwise.
	 */
	private static boolean isExtensionConditionRule(AST ast) {
		if (!isRule(ast)) {
			return false;
		}
		if (ast.ast1 == null) {
			return false;
		}

		if (!containsASTType(ast.ast1, Extension_AST.class)) {
			return false;
		}

		if (containsASTType(ast.ast1, new Class[] {FunctionCall_AST.class, DollarExpression_AST.class})) {
			return false;
		}

		return true;

		//return containsExtension(ast.ast1);

		/*
		// extension { ... }
		if (ast.ast1 instanceof Extension_AST)
			return true;
		// ! extension { ... }
		if (ast.ast1 instanceof NotExpression_AST && ast.ast1.ast1 instanceof Extension_AST)
			return true;

		// otherwise, it is not an extension condition rule
		return false;
		*/
	}

	/*
	private static boolean containsExtension(AST ast) {
		if (ast == null)
			return false;
		if (ast instanceof Extension_AST)
			return true;
		if (containsExtension(ast.ast1)) return true;
		if (containsExtension(ast.ast2)) return true;
		if (containsExtension(ast.ast3)) return true;
		if (containsExtension(ast.ast4)) return true;
		return false;
	}
	*/
	private static boolean containsASTType(AST ast, Class cls) {
		return containsASTType(ast, new Class[] {cls});
	}

	private static boolean containsASTType(AST ast, Class[] cls_array) {
		if (ast == null) {
			return false;
		}
		for (Class cls : cls_array) {
			if (cls.isInstance(ast)) {
				return true;
			}
		}
		return     containsASTType(ast.ast1, cls_array)
				|| containsASTType(ast.ast2, cls_array)
				|| containsASTType(ast.ast3, cls_array)
				|| containsASTType(ast.ast4, cls_array);
	}

	private Address next_address;

	private final class RuleList_AST extends AST {

		private RuleList_AST(AST rule, AST rest) {
			super(rule, rest);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			next_address = tuples.createAddress("next_address");

			Address exit_addr = tuples.createAddress("end blocks start address");

			// goto start address
			Address start_address = tuples.createAddress("start address");

			tuples.setExitAddress(exit_addr);

			tuples.gotoAddress(start_address);

			AST ptr;

			// compile functions

			ptr = this;
			while (ptr != null) {
				if (ptr.ast1 != null && ptr.ast1.isFunction()) {
					assert ptr.ast1 != null;
					int ast1_count = ptr.ast1.populateTuples(tuples);
					assert ast1_count == 0;
				}

				ptr = ptr.ast2;
			}

			// START OF MAIN BLOCK

			tuples.address(start_address);

			// initialze special variables
			ID_AST nr_ast = symbol_table.getID("NR");
			ID_AST fnr_ast = symbol_table.getID("FNR");
			ID_AST nf_ast = symbol_table.getID("NF");
			ID_AST fs_ast = symbol_table.getID("FS");
			ID_AST rs_ast = symbol_table.getID("RS");
			ID_AST ofs_ast = symbol_table.getID("OFS");
			ID_AST rstart_ast = symbol_table.getID("RSTART");
			ID_AST rlength_ast = symbol_table.getID("RLENGTH");
			ID_AST filename_ast = symbol_table.getID("FILENAME");
			ID_AST subsep_ast = symbol_table.getID("SUBSEP");
			ID_AST convfmt_ast = symbol_table.getID("CONVFMT");
			ID_AST ofmt_ast = symbol_table.getID("OFMT");
			ID_AST environ_ast = symbol_table.getID("ENVIRON");
			ID_AST argc_ast = symbol_table.getID("ARGC");
			ID_AST argv_ast = symbol_table.getID("ARGV");
			boolean b
					= nr_ast.is_scalar
					= fnr_ast.is_scalar
					= nf_ast.is_scalar
					= fs_ast.is_scalar
					= rs_ast.is_scalar
					= ofs_ast.is_scalar
					= rstart_ast.is_scalar
					= rlength_ast.is_scalar
					= filename_ast.is_scalar
					= subsep_ast.is_scalar
					= convfmt_ast.is_scalar
					= ofmt_ast.is_scalar
					= environ_ast.is_array	// note!
					= argc_ast.is_scalar
					= argv_ast.is_array	// note!
					= true;

			// MUST BE DONE AFTER FUNCTIONS ARE COMPILED,
			// and after special variables are made known to the symbol table
			// (see above)!
			tuples.setNumGlobals(symbol_table.numGlobals());

			tuples.nfOffset(nf_ast.offset);
			tuples.nrOffset(nr_ast.offset);
			tuples.fnrOffset(fnr_ast.offset);
			tuples.fsOffset(fs_ast.offset);
			tuples.rsOffset(rs_ast.offset);
			tuples.ofsOffset(ofs_ast.offset);
			tuples.rstartOffset(rstart_ast.offset);
			tuples.rlengthOffset(rlength_ast.offset);
			tuples.filenameOffset(filename_ast.offset);
			tuples.subsepOffset(subsep_ast.offset);
			tuples.convfmtOffset(convfmt_ast.offset);
			tuples.ofmtOffset(ofmt_ast.offset);
			tuples.environOffset(environ_ast.offset);
			tuples.argcOffset(argc_ast.offset);
			tuples.argvOffset(argv_ast.offset);

			// grab all BEGINs

			ptr = this;
			// ptr.ast1 == blank rule condition (i.e.: { print })
			while (ptr != null) {
				if (ptr.ast1 != null && ptr.ast1.isBegin()) {
					assert ptr.ast1 != null;
					int ast1_count = ptr.ast1.populateTuples(tuples);
					assert ast1_count == 0;
				}

				ptr = ptr.ast2;
			}

			boolean req_loop = false;
			boolean req_input = false;

			ptr = this;
			while (!req_input && (ptr != null)) {
				if (isRule(ptr.ast1)) {
					req_loop = true;
					if (!no_input) {
						req_input = true;
					}
				}
				ptr = ptr.ast2;
			}

			if (req_loop) {
				Address input_loop_address = null;
				Address no_more_input = null;

				input_loop_address = tuples.createAddress("input_loop_address");
				tuples.address(input_loop_address);

				ptr = this;

				if (req_input) {
					no_more_input = tuples.createAddress("no_more_input");
					tuples.consumeInput(no_more_input);
				}

				// grab all INPUT RULES

				while (ptr != null) {
					// the first one of these is an input rule
					if (isRule(ptr.ast1)) {
						assert ptr.ast1 != null;
						int ast1_count = ptr.ast1.populateTuples(tuples);
						assert ast1_count == 0;
					}

					ptr = ptr.ast2;
				}
				tuples.address(next_address);

				tuples.gotoAddress(input_loop_address);

				if (req_input) {
					tuples.address(no_more_input);
					// compiler has issue with missing nop here
					tuples.nop();
				}
			}

			// indicate where the first end block resides
			// in the event of an exit statement

			tuples.address(exit_addr);
			tuples.setWithinEndBlocks(true);

			// grab all ENDs

			ptr = this;
			while (ptr != null) {
				if (ptr.ast1 != null && ptr.ast1.isEnd()) {
					assert ptr.ast1 != null;
					int ast1_count = ptr.ast1.populateTuples(tuples);
					assert ast1_count == 0;
				}

				ptr = ptr.ast2;
			}

			// force a nop here to resolve any addresses that haven't been resolved yet
			// (i.e., no_more_input wouldn't be resolved if there are no END{} blocks)
			tuples.nop();

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	// made non-static to access the "next_address" field of the frontend
	private final class Rule_AST extends AST implements Nextable {

		private Rule_AST(AST opt_expression, AST opt_rule) {
			super(opt_expression, opt_rule);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (ast1 == null) {
				// just indicate to execute the rule
				tuples.push(1);	// 1 == true
			} else {
				int result = ast1.populateTuples(tuples);
				assert result == 1;
			}
			// result of whether to execute or not is on the stack
			Address bypass_rule = tuples.createAddress("bypass_rule");
			tuples.ifFalse(bypass_rule);
			// execute the opt_rule here!
			if (ast2 == null) {
				if (no_input) {
					// with -ni, no default blank rule of print $0
				} else if (ast1 == null || !ast1.isBegin() && !ast1.isEnd()) {
					// display $0
					tuples.print(0);
				}
				// else, don't populate it with anything
				// (i.e., blank BEGIN/END rule)
			} else {
				// execute it, and leave nothing on the stack
				int ast2_count = ast2.populateTuples(tuples);
				assert ast2_count == 0;
			}
			tuples.address(bypass_rule).nop();
			popSourceLineNumber(tuples);
			return 0;
		}

		@Override
		public Address nextAddress() {
			if (!isRule(this)) {
				throw new SemanticException("Must call next within an input rule.");
			}
			if (next_address == null) {
				throw new SemanticException("Cannot call next here.");
			}
			return next_address;
		}
	}

	private final class IfStatement_AST extends AST {

		private IfStatement_AST(AST expr, AST b1, AST b2) {
			super(expr, b1, b2);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;

			Address elseblock = tuples.createAddress("elseblock");

			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.ifFalse(elseblock);
			if (ast2 != null) {
				int ast2_result = ast2.populateTuples(tuples);
				assert ast2_result == 0;
			}
			if (ast3 == null) {
				tuples.address(elseblock);
			} else {
				Address end = tuples.createAddress("end");
				tuples.gotoAddress(end);
				tuples.address(elseblock);
				int ast3_result = ast3.populateTuples(tuples);
				assert ast3_result == 0;
				tuples.address(end);
			}
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class TertiaryExpression_AST extends ScalarExpression_AST {

		private TertiaryExpression_AST(AST a1, AST a2, AST a3) {
			super(a1, a2, a3);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			assert ast2 != null;
			assert ast3 != null;

			Address elseexpr = tuples.createAddress("elseexpr");
			Address end_tertiary = tuples.createAddress("end_tertiary");

			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.ifFalse(elseexpr);
			int ast2_result = ast2.populateTuples(tuples);
			assert ast2_result == 1;
			tuples.gotoAddress(end_tertiary);

			tuples.address(elseexpr);
			int ast3_result = ast3.populateTuples(tuples);
			assert ast3_result == 1;

			tuples.address(end_tertiary);

			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class WhileStatement_AST extends AST implements Breakable, Continueable {

		private Address break_address;
		private Address continue_address;

		private WhileStatement_AST(AST expr, AST block) {
			super(expr, block);
		}

		@Override
		public Address breakAddress() {
			assert break_address != null;
			return break_address;
		}

		@Override
		public Address continueAddress() {
			assert continue_address != null;
			return continue_address;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			break_address = tuples.createAddress("break_address");

			// LOOP
			Address loop = tuples.createAddress("loop");
			tuples.address(loop);

			// for while statements, the start-of-loop is the continue jump address
			continue_address = loop;

			// condition
			assert (ast1 != null);
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.ifFalse(break_address);

			if (ast2 != null) {
				int ast2_result = ast2.populateTuples(tuples);
				assert ast2_result == 0;
			}

			tuples.gotoAddress(loop);

			tuples.address(break_address);

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class DoStatement_AST extends AST implements Breakable, Continueable {

		private Address break_address;
		private Address continue_address;

		private DoStatement_AST(AST block, AST expr) {
			super(block, expr);
		}

		@Override
		public Address breakAddress() {
			assert break_address != null;
			return break_address;
		}

		@Override
		public Address continueAddress() {
			assert continue_address != null;
			return continue_address;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			break_address = tuples.createAddress("break_address");
			continue_address = tuples.createAddress("continue_address");

			// LOOP
			Address loop = tuples.createAddress("loop");
			tuples.address(loop);

			if (ast1 != null) {
				int ast1_result = ast1.populateTuples(tuples);
				assert ast1_result == 0;
			}

			// for do-while statements, the continue jump address is the loop condition
			tuples.address(continue_address);

			// condition
			assert (ast2 != null);
			int ast2_result = ast2.populateTuples(tuples);
			assert ast2_result == 1;
			tuples.ifTrue(loop);

			//tuples.gotoAddress(loop);

			tuples.address(break_address);

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class ForStatement_AST extends AST implements Breakable, Continueable {

		private Address break_address;
		private Address continue_address;

		private ForStatement_AST(AST expr1, AST expr2, AST expr3, AST block) {
			super(expr1, expr2, expr3, block);
		}

		@Override
		public Address breakAddress() {
			assert break_address != null;
			return break_address;
		}

		@Override
		public Address continueAddress() {
			assert continue_address != null;
			return continue_address;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			break_address = tuples.createAddress("break_address");
			continue_address = tuples.createAddress("continue_address");

			// initial actions
			if (ast1 != null) {
				int ast1_result = ast1.populateTuples(tuples);
				for (int i = 0; i < ast1_result; i++) {
					tuples.pop();
				}
			}
			// LOOP
			Address loop = tuples.createAddress("loop");
			tuples.address(loop);

			if (ast2 != null) {
				// condition
				//assert(ast2 != null);
				int ast2_result = ast2.populateTuples(tuples);
				assert ast2_result == 1;
				tuples.ifFalse(break_address);
			}

			if (ast4 != null) {
				// post loop action
				int ast4_result = ast4.populateTuples(tuples);
				assert ast4_result == 0;
			}

			// for for-loops, the continue jump address is the post-loop-action
			tuples.address(continue_address);

			// post-loop action
			if (ast3 != null) {
				int ast3_result = ast3.populateTuples(tuples);
				for (int i = 0; i < ast3_result; i++) {
					tuples.pop();
				}
			}

			tuples.gotoAddress(loop);

			tuples.address(break_address);

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class ForInStatement_AST extends AST implements Breakable, Continueable {

		private Address break_address;
		private Address continue_address;

		private ForInStatement_AST(AST key_id_ast, AST array_id_ast, AST block) {
			super(key_id_ast, array_id_ast, block);
		}

		@Override
		public Address breakAddress() {
			assert break_address != null;
			return break_address;
		}

		@Override
		public Address continueAddress() {
			assert continue_address != null;
			return continue_address;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			assert ast2 != null;

			ID_AST array_id_ast = (ID_AST) ast2;
			if (array_id_ast.isScalar()) {
				throw new SemanticException(array_id_ast + " is not an array");
			}
			array_id_ast.setArray(true);

			break_address = tuples.createAddress("break_address");

			// push array onto the stack
			int ast2_result = ast2.populateTuples(tuples);
			// pops the array and pushes the keyset
			tuples.keylist();

			// stack now contains:
			//
			// keylist

			// LOOP
			Address loop = tuples.createAddress("loop");
			tuples.address(loop);

			// for for-in loops, the continue jump address is the start-of-loop address
			continue_address = loop;

			assert tuples.checkClass(KeyList.class);

			// condition
			tuples.dup();
			tuples.isEmptyList(break_address);

			assert tuples.checkClass(KeyList.class);

			// take an element off the set
			tuples.dup();
			tuples.getFirstAndRemoveFromList();
			// assign it to the id
			tuples.assign(((ID_AST) ast1).offset, ((ID_AST) ast1).is_global);
			tuples.pop();	// remove the assignment result

			if (ast3 != null) {
				// execute the block
				int ast3_result = ast3.populateTuples(tuples);
				assert ast3_result == 0;
			}
			// otherwise, there is no block to execute

			assert tuples.checkClass(KeyList.class);

			tuples.gotoAddress(loop);

			tuples.address(break_address);
			tuples.pop();	// keylist

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class EmptyStatement_AST extends AST {

		private EmptyStatement_AST() {
			super();
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			// nothing to populate!
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	/**
	 * The AST for an expression used as a statement.
	 * If the expression returns a value, the value is popped
	 * off the stack and discarded.
	 */
	private final class ExpressionStatement_AST extends AST {

		private ExpressionStatement_AST(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			int expr_count = ast1.populateTuples(tuples);
			if        (expr_count == 0) {
			} else if (expr_count == 1) {
				tuples.pop();
			} else {
				assert false : "expr_count = " + expr_count;
			}
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class AssignmentExpression_AST extends ScalarExpression_AST {

		/** operand / operator */
		private int op;
		private String text;

		private AssignmentExpression_AST(AST lhs, int op, String text, AST rhs) {
			super(lhs, rhs);
			this.op = op;
			this.text = text;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + op + "/" + text + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast2 != null;
			int ast2_count = ast2.populateTuples(tuples);
			assert ast2_count == 1;
			// here, stack contains one value
			if (ast1 instanceof ID_AST) {
				ID_AST id_ast = (ID_AST) ast1;
				if (id_ast.isArray()) {
					throw new SemanticException("Cannot use " + id_ast + " as a scalar. It is an array.");
				}
				id_ast.setScalar(true);
				if        (op == _EQUALS_) {
					// Expected side effect:
					// Upon assignment, if the var is RS, reapply RS to input streams.
					tuples.assign(id_ast.offset, id_ast.is_global);
				} else if (op == _PLUS_EQ_) {
					tuples.plusEq(id_ast.offset, id_ast.is_global);
				} else if (op == _MINUS_EQ_) {
					tuples.minusEq(id_ast.offset, id_ast.is_global);
				} else if (op == _MULT_EQ_) {
					tuples.multEq(id_ast.offset, id_ast.is_global);
				} else if (op == _DIV_EQ_) {
					tuples.divEq(id_ast.offset, id_ast.is_global);
				} else if (op == _MOD_EQ_) {
					tuples.modEq(id_ast.offset, id_ast.is_global);
				} else if (op == _POW_EQ_) {
					tuples.powEq(id_ast.offset, id_ast.is_global);
				} else {
					throw new Error("Unhandled op: "+op+" / "+text);
				}
				if (id_ast.id.equals("RS")) {
					tuples.applyRS();
				}
			} else if (ast1 instanceof ArrayReference_AST) {
				ArrayReference_AST arr = (ArrayReference_AST) ast1;
				// push the index
				assert arr.ast2 != null;
				int arr_ast2_result = arr.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				// push the array ref itself
				ID_AST id_ast = (ID_AST) arr.ast1;
				if (id_ast.isScalar()) {
					throw new SemanticException("Cannot use " + id_ast + " as an array. It is a scalar.");
				}
				id_ast.setArray(true);
				if        (op == _EQUALS_) {
					tuples.assignArray(id_ast.offset, id_ast.is_global);
				} else if (op == _PLUS_EQ_) {
					tuples.plusEqArray(id_ast.offset, id_ast.is_global);
				} else if (op == _MINUS_EQ_) {
					tuples.minusEqArray(id_ast.offset, id_ast.is_global);
				} else if (op == _MULT_EQ_) {
					tuples.multEqArray(id_ast.offset, id_ast.is_global);
				} else if (op == _DIV_EQ_) {
					tuples.divEqArray(id_ast.offset, id_ast.is_global);
				} else if (op == _MOD_EQ_) {
					tuples.modEqArray(id_ast.offset, id_ast.is_global);
				} else if (op == _POW_EQ_) {
					tuples.powEqArray(id_ast.offset, id_ast.is_global);
				} else {
					throw new NotImplementedError("Unhandled op: "+op+" / "+text+" for arrays.");
				}
			} else if (ast1 instanceof DollarExpression_AST) {
				DollarExpression_AST dollar_expr = (DollarExpression_AST) ast1;
				assert dollar_expr.ast1 != null;
				int ast1_result = dollar_expr.ast1.populateTuples(tuples);
				assert ast1_result == 1;
				// stack contains eval of dollar arg

				if        (op == _EQUALS_) {
					tuples.assignAsInputField();
				} else if (op == _PLUS_EQ_) {
					tuples.plusEqInputField();
				} else if (op == _MINUS_EQ_) {
					tuples.minusEqInputField();
				} else if (op == _MULT_EQ_) {
					tuples.multEqInputField();
				} else if (op == _DIV_EQ_) {
					tuples.divEqInputField();
				} else if (op == _MOD_EQ_) {
					tuples.modEqInputField();
				} else if (op == _POW_EQ_) {
					tuples.powEqInputField();
				} else {
					throw new NotImplementedError("Unhandled op: "+op+" / "+text+" for dollar expressions.");
				}
			} else {
				throw new SemanticException("Cannot perform an assignment on: "+ast1);
			}
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class InExpression_AST extends ScalarExpression_AST {

		private InExpression_AST(AST arg, AST arr) {
			super(arg, arr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			assert ast2 != null;
			if (!(ast2 instanceof ID_AST)) {
				throw new SemanticException("Expecting an array for rhs of IN. Got an expression.");
			}
			ID_AST arr_ast = (ID_AST) ast2;
			if (arr_ast.isScalar()) {
				throw new SemanticException("Expecting an array for rhs of IN. Got a scalar.");
			}
			arr_ast.setArray(true);

			int ast2_result = arr_ast.populateTuples(tuples);
			assert ast2_result == 1;

			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;

			tuples.isIn();

			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class ComparisonExpression_AST extends ScalarExpression_AST {

		/**
		 * operand / operator
		 */
		private int op;
		private String text;

		private ComparisonExpression_AST(AST lhs, int op, String text, AST rhs) {
			super(lhs, rhs);
			this.op = op;
			this.text = text;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + op + "/" + text + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			assert ast2 != null;

			int ast2_result = ast2.populateTuples(tuples);
			assert ast2_result == 1;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;

			// 2 values on the stack

			if (op == _EQ_) {
				tuples.cmpEq();
			} else if (op == _NE_) {
				tuples.cmpEq();
				tuples.not();
			} else if (op == _LT_) {
				tuples.cmpLt();
			} else if (op == _GT_) {
				tuples.cmpGt();
			} else if (op == _LE_) {
				tuples.cmpGt();
				tuples.not();
			} else if (op == _GE_) {
				tuples.cmpLt();
				tuples.not();
			} else if (op == _MATCHES_) {
				tuples.matches();
			} else if (op == _NOT_MATCHES_) {
				tuples.matches();
				tuples.not();
			} else {
				throw new Error("Unhandled op: "+op+" / "+text);
			}

			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class LogicalExpression_AST extends ScalarExpression_AST {

		/**
		 * operand / operator
		 */
		private int op;
		private String text;

		private LogicalExpression_AST(AST lhs, int op, String text, AST rhs) {
			super(lhs, rhs);
			this.op = op;
			this.text = text;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + op + "/" + text + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			// exhibit short-circuit behavior
			Address end = tuples.createAddress("end");
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.dup();
			if (op == _OR_) {
				// short_circuit when op is OR and 1st arg is true
				tuples.ifTrue(end);
			} else if (op == _AND_) {
				tuples.ifFalse(end);
			} else {
				assert false : "Invalid op: " + op + " / " + text;
			}
			tuples.pop();
			int ast2_result = ast2.populateTuples(tuples);
			assert ast2_result == 1;

			tuples.address(end);

			// turn the result into boolean one or zero
			tuples.toNumber();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class BinaryExpression_AST extends ScalarExpression_AST {

		/**
		 * operand / operator
		 */
		private int op;
		private String text;

		private BinaryExpression_AST(AST lhs, int op, String text, AST rhs) {
			super(lhs, rhs);
			this.op = op;
			this.text = text;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + op + "/" + text + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			int ast2_result = ast2.populateTuples(tuples);
			assert ast2_result == 1;
			tuples.swap();
			if        (op == _PLUS_) {
				tuples.add();
			} else if (op == _MINUS_) {
				tuples.subtract();
			} else if (op == _MULT_) {
				tuples.multiply();
			} else if (op == _DIVIDE_) {
				tuples.divide();
			} else if (op == _MOD_) {
				tuples.mod();
			} else if (op == _POW_) {
				tuples.pow();
			} else {
				throw new Error("Unhandled op: " + op + " / " + this);
			}
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class ConcatExpression_AST extends ScalarExpression_AST {

		private ConcatExpression_AST(AST lhs, AST rhs) {
			super(lhs, rhs);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast2 != null;
			int rhs_count = ast2.populateTuples(tuples);
			assert rhs_count == 1;
			assert ast1 != null;
			int lhs_count = ast1.populateTuples(tuples);
			assert lhs_count == 1;
			tuples.concat();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class NegativeExpression_AST extends ScalarExpression_AST {

		private NegativeExpression_AST(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.negate();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class NotExpression_AST extends ScalarExpression_AST {

		private NotExpression_AST(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.not();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class DollarExpression_AST extends ScalarExpression_AST {

		private DollarExpression_AST(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.getInputField();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class ArrayIndex_AST extends ScalarExpression_AST {

		private ArrayIndex_AST(AST expr_ast, AST next) {
			super(expr_ast, next);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			AST ptr = this;
			int cnt = 0;
			while (ptr != null) {
				assert ptr.ast1 != null;
				int ptr_ast1_result = ptr.ast1.populateTuples(tuples);
				assert ptr_ast1_result == 1;
				++cnt;
				ptr = ptr.ast2;
			}
			assert cnt >= 1;
			if (cnt > 1) {
				tuples.applySubsep(cnt);
			}
			popSourceLineNumber(tuples);
			return 1;
		}
	}


	// made classname all capitals to stand out in a syntax tree dump
	private final class STATEMENTLIST_AST extends AST {

		private STATEMENTLIST_AST(AST statement_ast, AST rest) {
			super(statement_ast, rest);
		}

		/**
		 * Recursively process statements within this statement list.
		 * <p>
		 * It originally was done linearly. However, quirks in the grammar required
		 * a more general, recursive approach to processing this "list".
		 * </p>
		 * <p>
		 * Note: this should be reevaluated periodically in case the grammar
		 * becomes linear again.
		 * </p>
		 */
		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			// typical recursive processing of a list
			assert ast1 != null;
			int ast1_count = ast1.populateTuples(tuples);
			assert ast1_count == 0;
			if (ast2 != null) {
				int ast2_count = ast2.populateTuples(tuples);
				assert ast2_count == 0;
			}
			popSourceLineNumber(tuples);
			return 0;
		}

		@Override
		public String toString() {
			return super.toString() + " <" + ast1 + ">";
		}
	}

	private interface Returnable {

		Address returnAddress();
	}

	// made non-static to access the symbol table
	private final class FunctionDef_AST extends AST implements Returnable {

		private String id;
		private Address function_address;
		private Address return_address;
		// to satisfy the Returnable interface

		@Override
		public Address returnAddress() {
			assert return_address != null;
			return return_address;
		}

		private FunctionDef_AST(String id, AST params, AST func_body) {
			super(params, func_body);
			this.id = id;
			is_function = true;
		}

		public Address getAddress() {
			assert function_address != null;
			return function_address;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			function_address = tuples.createAddress("function: " + id);
			return_address = tuples.createAddress("return_address for " + id);

			// annotate the tuple list
			// (useful for compilation,
			// not necessary for interpretation)
			tuples.function(id, paramCount());

			// function_address refers to first function body statement
			// rather than to function def opcode because during
			// interpretation, the function definition is a nop,
			// and for compilation, the next match of the function
			// name can be used
			tuples.address(function_address);

			// the stack contains the parameters to the function call (in rev order, which is good)

			// execute the body
			// (function body could be empty [no statements])
			if (ast2 != null) {
				int ast2_result = ast2.populateTuples(tuples);
				assert ast2_result == 0 || ast2_result == 1;
			}

			tuples.address(return_address);

			tuples.returnFromFunction();

			/////////////////////////////////////////////

			popSourceLineNumber(tuples);
			return 0;
		}

		int paramCount() {
			AST ptr = ast1;
			int count = 0;
			while (ptr != null) {
				++count;
				ptr = ptr.ast1;
			}
			return count;
		}

		void checkActualToFormalParameters(AST actual_param_list) {
			AST a_ptr = actual_param_list;
			FunctionDefParamList_AST f_ptr = (FunctionDefParamList_AST) ast1;
			while (a_ptr != null) {
				// actual parameter
				AST aparam = a_ptr.ast1;
				// formal function parameter
				AST fparam = symbol_table.getFunctionParameterIDAST(id, f_ptr.id);


				if (aparam.isArray() && fparam.isScalar()) {
					aparam.throwSemanticException(id + ": Actual parameter (" + aparam + ") is an array, but formal parameter is used like a scalar.");
				}
				if (aparam.isScalar() && fparam.isArray()) {
					aparam.throwSemanticException(id + ": Actual parameter (" + aparam + ") is a scalar, but formal parameter is used like an array.");
				}
				// condition parameters appropriately
				// (based on function parameter semantics)
				if (aparam instanceof ID_AST) {
					ID_AST aparam_id_ast = (ID_AST) aparam;
					if (fparam.isScalar()) {
						aparam_id_ast.setScalar(true);
					}
					if (fparam.isArray()) {
						aparam_id_ast.setArray(true);
					}
				}
				// next
				a_ptr = a_ptr.ast2;
				f_ptr = (FunctionDefParamList_AST) f_ptr.ast1;
			}
		}
	}

	private final class FunctionCall_AST extends ScalarExpression_AST {

		private FunctionProxy function_proxy;

		private FunctionCall_AST(FunctionProxy function_proxy, AST params) {
			super(params);
			this.function_proxy = function_proxy;
		}

		/**
		 * Applies several semantic checks with respect
		 * to user-defined-function calls.
		 * <p>
		 * The checks performed are:
		 * <ul>
		 * <li>Make sure the function is defined.</li>
		 * <li>The number of actual parameters does not</li>
		 *   exceed the number of formal parameters.
		 * <li>Matches actual parameters to formal parameter
		 *   usage with respect to whether they are
		 *   scalars, arrays, or either.
		 *   (This determination is based on how
		 *   the formal parameters are used within
		 *   the function block.)</li>
		 * </ul>
		 * A failure of any one of these checks
		 * results in a SemanticException.
		 * </p>
		 *
		 * @throws SemanticException upon a failure of
		 *   any of the semantic checks specified above.
		 */
		@Override
		public void semanticAnalysis()
				throws SemanticException
		{
			if (!function_proxy.isDefined()) {
				throw new SemanticException("function " + function_proxy + " not defined");
			}
			int actual_param_count;
			if (ast1 == null) {
				actual_param_count = 0;
			} else {
				actual_param_count = actualParamCount();
			}
			int formal_param_count = function_proxy.getFunctionParamCount();
			if (formal_param_count < actual_param_count) {
				throw new SemanticException("the " + function_proxy.getFunctionName() + " function"
						+ " only accepts at most " + formal_param_count + " parameter(s), not " + actual_param_count);
			}
			if (ast1 != null) {
				function_proxy.checkActualToFormalParameters(ast1);
			}
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (!function_proxy.isDefined()) {
				throw new SemanticException("function " + function_proxy + " not defined");
			}
			tuples.scriptThis();
			int actual_param_count;
			if (ast1 == null) {
				actual_param_count = 0;
			} else {
				actual_param_count = ast1.populateTuples(tuples);
			}
			int formal_param_count = function_proxy.getFunctionParamCount();
			if (formal_param_count < actual_param_count) {
				throw new SemanticException("the " + function_proxy.getFunctionName() + " function"
						+ " only accepts at most " + formal_param_count + " parameter(s), not " + actual_param_count);
			}

			function_proxy.checkActualToFormalParameters(ast1);
			tuples.callFunction(function_proxy, function_proxy.getFunctionName(), formal_param_count, actual_param_count);
			popSourceLineNumber(tuples);
			return 1;
		}

		private int actualParamCount() {
			int cnt = 0;
			AST ptr = ast1;
			while (ptr != null) {
				assert ptr.ast1 != null;
				++cnt;
				ptr = ptr.ast2;
			}
			return cnt;
		}
	}

	private final class BuiltinFunctionCall_AST extends ScalarExpression_AST {

		private String id;
		private int f_idx;

		private BuiltinFunctionCall_AST(String id, AST params) {
			super(params);
			this.id = id;
			assert BUILTIN_FUNC_NAMES.get(id) != null;
			this.f_idx = BUILTIN_FUNC_NAMES.get(id);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (f_idx == BUILTIN_FUNC_NAMES.get("sprintf")) {
				if (ast1 == null) {
					throw new SemanticException("sprintf requires at least 1 argument");
				}
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result == 0) {
					throw new SemanticException("sprintf requires at minimum 1 argument");
				}
				tuples.sprintf(ast1_result);
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("close")) {
				if (ast1 == null) {
					throw new SemanticException("close requires 1 argument");
				}
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("close requires only 1 argument");
				}
				tuples.close();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("length")) {
				if (ast1 == null) {
					tuples.length(0);
				} else {
					int ast1_result = ast1.populateTuples(tuples);
					if (ast1_result != 1) {
						throw new SemanticException("length requires at least one argument");
					}
					tuples.length(1);
				}
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("srand")) {
				if (ast1 == null) {
					tuples.srand(0);
				} else {
					int ast1_result = ast1.populateTuples(tuples);
					if (ast1_result != 1) {
						throw new SemanticException("srand takes either 0 or one argument, not " + ast1_result);
					}
					tuples.srand(1);
				}
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("rand")) {
				if (ast1 != null) {
					throw new SemanticException("rand does not take arguments");
				}
				tuples.rand();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("sqrt")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("sqrt requires only 1 argument");
				}
				tuples.sqrt();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("int")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("int requires only 1 argument");
				}
				tuples.intFunc();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("log")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("int requires only 1 argument");
				}
				tuples.log();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("exp")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("exp requires only 1 argument");
				}
				tuples.exp();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("sin")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("sin requires only 1 argument");
				}
				tuples.sin();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("cos")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("cos requires only 1 argument");
				}
				tuples.cos();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("atan2")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 2) {
					throw new SemanticException("atan2 requires 2 arguments");
				}
				tuples.atan2();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("match")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 2) {
					throw new SemanticException("match requires 2 arguments");
				}
				tuples.match();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("index")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 2) {
					throw new SemanticException("index requires 2 arguments");
				}
				tuples.index();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("sub") || f_idx == BUILTIN_FUNC_NAMES.get("gsub")) {
				if (ast1 == null || ast1.ast2 == null || ast1.ast2.ast1 == null) {
					throw new SemanticException("sub needs at least 2 arguments");
				}
				boolean is_gsub = f_idx == BUILTIN_FUNC_NAMES.get("gsub");

				int numargs = ast1.populateTuples(tuples);

				// stack contains arg1,arg2[,arg3] - in that pop() order

				if (numargs == 2) {
					tuples.subForDollar0(is_gsub);
				} else if (numargs == 3) {
					AST ptr = ast1.ast2.ast2.ast1;
					if (ptr instanceof ID_AST) {
						ID_AST id_ast = (ID_AST) ptr;
						if (id_ast.isArray()) {
							throw new SemanticException("sub cannot accept an unindexed array as its 3rd argument");
						}
						id_ast.setScalar(true);
						tuples.subForVariable(id_ast.offset, id_ast.is_global, is_gsub);
					} else if (ptr instanceof ArrayReference_AST) {
						ArrayReference_AST arr_ast = (ArrayReference_AST) ptr;
						// push the index
						int ast2_result = arr_ast.ast2.populateTuples(tuples);
						assert ast2_result == 1;
						ID_AST id_ast = (ID_AST) arr_ast.ast1;
						if (id_ast.isScalar()) {
							throw new SemanticException("Cannot use " + id_ast + " as an array.");
						}
						tuples.subForArrayReference(id_ast.offset, id_ast.is_global, is_gsub);
					} else if (ptr instanceof DollarExpression_AST) {
						// push the field ref
						DollarExpression_AST dollar_expr = (DollarExpression_AST) ptr;
						assert dollar_expr.ast1 != null;
						int ast1_result = dollar_expr.ast1.populateTuples(tuples);
						assert ast1_result == 1;
						tuples.subForDollarReference(is_gsub);
					} else {
						throw new SemanticException("sub's 3rd argument must be either an id, an array reference, or an input field reference");
					}
				}
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("split")) {
				// split can take 2 or 3 args:
				// split (string, array [,fs])
				// the 2nd argument is pass by reference, which is ok (?)

				// funccallparamlist.funccallparamlist.id_ast
				if (ast1 == null || ast1.ast2 == null || ast1.ast2.ast1 == null) {
					throw new SemanticException("split needs at least 2 arguments");
				}
				AST ptr = ast1.ast2.ast1;
				if (!(ptr instanceof ID_AST)) {
					throw new SemanticException("split needs an array name as its 2nd argument");
				}
				ID_AST arr_ast = (ID_AST) ptr;
				if (arr_ast.isScalar()) {
					throw new SemanticException("split's 2nd arg cannot be a scalar");
				}
				arr_ast.setArray(true);

				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 2 && ast1_result != 3) {
					throw new SemanticException("split requires 2 or 3 arguments, not " + ast1_result);
				}
				tuples.split(ast1_result);
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("substr")) {
				if (ast1 == null) {
					throw new SemanticException("substr requires at least 2 arguments");
				}
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 2 && ast1_result != 3) {
					throw new SemanticException("substr requires 2 or 3 arguments, not " + ast1_result);
				}
				tuples.substr(ast1_result);
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("tolower")) {
				if (ast1 == null) {
					throw new SemanticException("tolower requires 1 argument");
				}
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("tolower requires only 1 argument");
				}
				tuples.tolower();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("toupper")) {
				if (ast1 == null) {
					throw new SemanticException("toupper requires 1 argument");
				}
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("toupper requires only 1 argument");
				}
				tuples.toupper();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("system")) {
				if (ast1 == null) {
					throw new SemanticException("system requires 1 argument");
				}
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("system requires only 1 argument");
				}
				tuples.system();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTIN_FUNC_NAMES.get("exec")) {
				if (ast1 == null) {
					throw new SemanticException("exec requires 1 argument");
				}
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("exec requires only 1 argument");
				}
				tuples.exec();
				popSourceLineNumber(tuples);
				return 1;
			} else {
				throw new NotImplementedError("builtin: " + id);
			}
		}
	}

	private final class FunctionCallParamList_AST extends AST {

		private FunctionCallParamList_AST(AST expr, AST rest) {
			super(expr, rest);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int retval;
			if (ast2 == null) {
				retval = ast1.populateTuples(tuples);
			} else {
				retval = ast2.populateTuples(tuples) + ast1.populateTuples(tuples);
			}
			popSourceLineNumber(tuples);
			return retval;
		}
	}

	private final class FunctionDefParamList_AST extends AST {

		private String id;
		private int offset;

		private FunctionDefParamList_AST(String id, int offset, AST rest) {
			super(rest);
			this.id = id;
			this.offset = offset;
		}

		public final int populateTuples(AwkTuples tuples) {
			throw new Error("Cannot 'execute' function definition parameter list (formal parameters) in this manner.");
		}

		/**
		 * According to the spec
		 * (http://www.opengroup.org/onlinepubs/007908799/xcu/awk.html)
		 * formal function parameters cannot be special variables,
		 * such as NF, NR, etc).
		 *
		 * @throws SemanticException upon a semantic error.
		 */
		@Override
		public void semanticAnalysis()
				throws SemanticException
		{

			// could do it recursively, but not necessary
			// since all ast1's are FunctionDefParamList's
			// and, thus, terminals (no need to do further
			// semantic analysis)

			FunctionDefParamList_AST ptr = this;
			while (ptr != null) {
				if (SPECIAL_VAR_NAMES.get(ptr.id) != null) {
					throw new SemanticException("Special variable " + ptr.id + " cannot be used as a formal parameter");
				}
				ptr = (FunctionDefParamList_AST) ptr.ast1;
			}
		}
	}

	/**
	 * A tag interface for non-statement expressions.
	 * Unknown for certain, but I think this is done
	 * to avoid partial variable assignment mistakes.
	 * For example, instead of a=3, the programmer
	 * inadvertently places the a on the line. If ID_ASTs
	 * were not tagged with NonStatement_AST, then the
	 * incomplete assignment would parse properly, and
	 * the developer might remain unaware of this issue.
	 */
	private interface NonStatement_AST {}

	private final class ID_AST extends AST implements NonStatement_AST {

		private String id;
		private int offset = AVM.NULL_OFFSET;
		private boolean is_global;

		private ID_AST(String id, boolean is_global) {
			this.id = id;
			this.is_global = is_global;
		}
		private boolean is_array = false;
		private boolean is_scalar = false;
		private Set<ID_AST> formal_parameters = new HashSet<ID_AST>();

		@Override
		public String toString() {
			return super.toString() + " (" + id + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert offset != AVM.NULL_OFFSET : "offset = " + offset + " for " + this;
			tuples.dereference(offset, isArray(), is_global);
			popSourceLineNumber(tuples);
			return 1;
		}

		@Override
		public final boolean isArray() {
			return is_array;
		}

		@Override
		public final boolean isScalar() {
			return is_scalar;
		}

		private void setArray(boolean b) {
			is_array = b;
		}

		private void setScalar(boolean b) {
			is_scalar = b;
		}
	}

	private final class ArrayReference_AST extends ScalarExpression_AST {

		private ArrayReference_AST(AST id_ast, AST idx_ast) {
			super(id_ast, idx_ast);
		}

		@Override
		public String toString() {
			return super.toString() + " (" + ast1 + " [...])";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			assert ast2 != null;
			// get the index
			int ast2_result = ast2.populateTuples(tuples);
			assert ast2_result == 1;
			// get the array var
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.dereferenceArray();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class Integer_AST extends ScalarExpression_AST implements NonStatement_AST {

		private Integer I;

		private Integer_AST(Integer I) {
			this.I = I;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + I + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.push(I);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	/**
	 * Can either assume the role of a double or an integer
	 * by aggressively normalizing the value to an int if possible.
	 */
	private final class Double_AST extends ScalarExpression_AST implements NonStatement_AST {

		private Object D;

		private Double_AST(Double D) {
			double d = D.doubleValue();
			if (d == (int) d) {
				this.D = (int) d;
			} else {
				this.D = d;
			}
		}

		@Override
		public String toString() {
			return super.toString() + " (" + D + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.push(D);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	/**
	 * A string is a string; Awk doesn't attempt to normalize
	 * it until it is used in an arithmetic operation!
	 */
	private final class String_AST extends ScalarExpression_AST implements NonStatement_AST {

		private String S;

		private String_AST(String str) {
			assert str != null;
			this.S = str;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + S + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.push(S);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class Regexp_AST extends ScalarExpression_AST {

		private String regexp_str;

		private Regexp_AST(String regexp_str) {
			assert regexp_str != null;
			this.regexp_str = regexp_str;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + regexp_str + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.regexp(regexp_str);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class RegexpPair_AST extends ScalarExpression_AST {

		private RegexpPair_AST(AST regexp_ast_1, AST regexp_ast_2) {
			super(regexp_ast_1, regexp_ast_2);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast2 != null;
			int ast2_result = ast2.populateTuples(tuples);
			assert ast2_result == 1;
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.regexpPair();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class IntegerExpression_AST extends ScalarExpression_AST {

		private IntegerExpression_AST(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.castInt();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class DoubleExpression_AST extends ScalarExpression_AST {

		private DoubleExpression_AST(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.castDouble();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class StringExpression_AST extends ScalarExpression_AST {

		private StringExpression_AST(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.castString();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class Begin_AST extends AST {

		private Begin_AST() {
			super();
			is_begin = true;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.push(1);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class End_AST extends AST {

		private End_AST() {
			super();
			is_end = true;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.push(1);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class PreInc_AST extends ScalarExpression_AST {

		private PreInc_AST(AST symbol_ast) {
			super(symbol_ast);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			if (ast1 instanceof ID_AST) {
				ID_AST id_ast = (ID_AST) ast1;
				tuples.inc(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof ArrayReference_AST) {
				ArrayReference_AST arr_ast = (ArrayReference_AST) ast1;
				ID_AST id_ast = (ID_AST) arr_ast.ast1;
				assert id_ast != null;
				assert arr_ast.ast2 != null;
				int arr_ast2_result = arr_ast.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				tuples.incArrayRef(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof DollarExpression_AST) {
				DollarExpression_AST dollar_expr = (DollarExpression_AST) ast1;
				assert dollar_expr.ast1 != null;
				int ast1_result = dollar_expr.ast1.populateTuples(tuples);
				assert ast1_result == 1;
				// OPTIMIATION: duplicate the x in $x here
				// so that it is not evaluated again
				tuples.dup();
				// stack contains eval of dollar arg
				//tuples.assignAsInputField();
				tuples.incDollarRef();
				// OPTIMIATION continued: now evaluate
				// the dollar expression with x (for $x)
				// instead of evaluating the expression again
				tuples.getInputField();
				popSourceLineNumber(tuples);
				return 1;			// NOTE, short-circuit return here!
			} else {
				throw new NotImplementedError("unhandled preinc for " + ast1);
			}
			//else
			//	assert false : "cannot refer for pre_inc to "+ast1;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class PreDec_AST extends ScalarExpression_AST {

		private PreDec_AST(AST symbol_ast) {
			super(symbol_ast);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			if (ast1 instanceof ID_AST) {
				ID_AST id_ast = (ID_AST) ast1;
				tuples.dec(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof ArrayReference_AST) {
				ArrayReference_AST arr_ast = (ArrayReference_AST) ast1;
				ID_AST id_ast = (ID_AST) arr_ast.ast1;
				assert id_ast != null;
				assert arr_ast.ast2 != null;
				int arr_ast2_result = arr_ast.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				tuples.decArrayRef(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof DollarExpression_AST) {
				DollarExpression_AST dollar_expr = (DollarExpression_AST) ast1;
				assert dollar_expr.ast1 != null;
				int ast1_result = dollar_expr.ast1.populateTuples(tuples);
				assert ast1_result == 1;
				// OPTIMIATION: duplicate the x in $x here
				// so that it is not evaluated again
				tuples.dup();
				// stack contains eval of dollar arg
				//tuples.assignAsInputField();
				tuples.decDollarRef();
				// OPTIMIATION continued: now evaluate
				// the dollar expression with x (for $x)
				// instead of evaluating the expression again
				tuples.getInputField();
				popSourceLineNumber(tuples);
				return 1;			// NOTE, short-circuit return here!
			} else {
				throw new NotImplementedError("unhandled predec for " + ast1);
			}
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class PostInc_AST extends ScalarExpression_AST {

		private PostInc_AST(AST symbol_ast) {
			super(symbol_ast);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			if (ast1 instanceof ID_AST) {
				ID_AST id_ast = (ID_AST) ast1;
				tuples.inc(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof ArrayReference_AST) {
				ArrayReference_AST arr_ast = (ArrayReference_AST) ast1;
				ID_AST id_ast = (ID_AST) arr_ast.ast1;
				assert id_ast != null;
				assert arr_ast.ast2 != null;
				int arr_ast2_result = arr_ast.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				tuples.incArrayRef(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof DollarExpression_AST) {
				DollarExpression_AST dollar_expr = (DollarExpression_AST) ast1;
				assert dollar_expr.ast1 != null;
				int dollarast_ast1_result = dollar_expr.ast1.populateTuples(tuples);
				assert dollarast_ast1_result == 1;
				tuples.incDollarRef();
			} else {
				throw new NotImplementedError("unhandled postinc for " + ast1);
			}
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class PostDec_AST extends ScalarExpression_AST {

		private PostDec_AST(AST symbol_ast) {
			super(symbol_ast);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			if (ast1 instanceof ID_AST) {
				ID_AST id_ast = (ID_AST) ast1;
				tuples.dec(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof ArrayReference_AST) {
				ArrayReference_AST arr_ast = (ArrayReference_AST) ast1;
				ID_AST id_ast = (ID_AST) arr_ast.ast1;
				assert id_ast != null;
				assert arr_ast.ast2 != null;
				int arr_ast2_result = arr_ast.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				tuples.decArrayRef(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof DollarExpression_AST) {
				DollarExpression_AST dollar_expr = (DollarExpression_AST) ast1;
				assert dollar_expr.ast1 != null;
				int dollarast_ast1_result = dollar_expr.ast1.populateTuples(tuples);
				assert dollarast_ast1_result == 1;
				tuples.decDollarRef();
			} else {
				throw new NotImplementedError("unhandled postinc for " + ast1);
			}
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class Print_AST extends ScalarExpression_AST {

		private int output_token;

		private Print_AST(AST expr_list, int output_token, AST output_expr) {
			super(expr_list, output_expr);
			this.output_token = output_token;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			int param_count;
			if (ast1 == null) {
				param_count = 0;
			} else {
				param_count = ast1.populateTuples(tuples);
				assert param_count >= 0;
				if (param_count == 0) {
					throw new SemanticException("Cannot print the result. The expression doesn't return anything.");
				}
			}

			if (ast2 != null) {
				int ast2_result = ast2.populateTuples(tuples);
				assert ast2_result == 1;
			}

			if (output_token == _GT_) {
				tuples.printToFile(param_count, false);	// false = no append
			} else if (output_token == _APPEND_) {
				tuples.printToFile(param_count, true);	// false = no append
			} else if (output_token == _PIPE_) {
				tuples.printToPipe(param_count);
			} else {
				tuples.print(param_count);
			}

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	// we don't know if it is a scalar
	private final class Extension_AST extends AST {

		private String extension_keyword;

		private Extension_AST(String extension_keyword, AST param_ast) {
			super(param_ast);
			this.extension_keyword = extension_keyword;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			int param_count;
			if (ast1 == null) {
				param_count = 0;
			} else {
				/// Query for the extension.
				JawkExtension extension = extensions.get(extension_keyword);
				int arg_count = countParams((FunctionCallParamList_AST) ast1);
				/// Get all required assoc array parameters:
				int[] req_array_idxs = extension.getAssocArrayParameterPositions(extension_keyword, arg_count);
				assert req_array_idxs != null;

				for (int idx : req_array_idxs) {
					AST param_ast = getParamAst((FunctionCallParamList_AST) ast1, idx);
					assert ast1 instanceof FunctionCallParamList_AST;
					// if the parameter is an ID_AST...
					if (param_ast.ast1 instanceof ID_AST) {
						// then force it to be an array,
						// or complain if it is already tagged as a scalar
						ID_AST id_ast = (ID_AST) param_ast.ast1;
						if (id_ast.isScalar()) {
							throw new SemanticException("Extension '" + extension_keyword + "' requires parameter position " + idx + " be an associative array, not a scalar.");
						}
						id_ast.setArray(true);
					}
				}

				param_count = ast1.populateTuples(tuples);
				assert param_count >= 0;
			}
			// is_initial == true ::
			// retval of this extension is not a function parameter
			// of another extension
			// true iff Extension | FunctionCallParam | FunctionCallParam | etc.
			boolean is_initial;
			if (parent instanceof FunctionCallParamList_AST) {
				AST ptr = parent;
				while (ptr instanceof FunctionCallParamList_AST) {
					ptr = ptr.parent;
				}
				is_initial = !(ptr instanceof Extension_AST);
			} else {
				is_initial = true;
			}
			tuples.extension(extension_keyword, param_count, is_initial);
			popSourceLineNumber(tuples);
			// an extension always returns a value, even if it is blank/null
			return 1;
		}

		private AST getParamAst(FunctionCallParamList_AST p_ast, int pos) {
			for (int i = 0; i < pos; ++i) {
				p_ast = (FunctionCallParamList_AST) p_ast.ast2;
				if (p_ast == null) {
					throw new SemanticException("More arguments required for assoc array parameter position specification.");
				}
			}
			return p_ast;
		}

		private int countParams(FunctionCallParamList_AST p_ast) {
			int cnt = 0;
			while (p_ast != null) {
				p_ast = (FunctionCallParamList_AST) p_ast.ast2;
				++cnt;
			}
			return cnt;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + extension_keyword + ")";
		}
	}

	private final class Printf_AST extends ScalarExpression_AST {

		private int output_token;

		private Printf_AST(AST expr_list, int output_token, AST output_expr) {
			super(expr_list, output_expr);
			this.output_token = output_token;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			int param_count;
			if (ast1 == null) {
				param_count = 0;
			} else {
				param_count = ast1.populateTuples(tuples);
				assert param_count >= 0;
				if (param_count == 0) {
					throw new SemanticException("Cannot printf the result. The expression doesn't return anything.");
				}
			}

			if (ast2 != null) {
				int ast2_result = ast2.populateTuples(tuples);
				assert ast2_result == 1;
			}

			if (output_token == _GT_) {
				tuples.printfToFile(param_count, false);	// false = no append
			} else if (output_token == _APPEND_) {
				tuples.printfToFile(param_count, true);	// false = no append
			} else if (output_token == _PIPE_) {
				tuples.printfToPipe(param_count);
			} else {
				tuples.printf(param_count);
			}

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class Getline_AST extends ScalarExpression_AST {

		private Getline_AST(AST pipe_expr, AST lvalue_ast, AST in_redirect) {
			super(pipe_expr, lvalue_ast, in_redirect);
			if (no_input && pipe_expr == null && in_redirect == null) {
				throw new SemanticException("getline via stdin/ARGV disabled by the -ni option");
			}
			// cannot have both pipe_expr and in_redirect NOT null!
			assert pipe_expr == null || in_redirect == null;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (ast1 != null) {
				int ast1_result = ast1.populateTuples(tuples);
				assert ast1_result == 1;
				// stack has ast1 (i.e., "command")
				tuples.useAsCommandInput();
			} else if (ast3 != null) {
				// getline ... < ast3
				int ast3_result = ast3.populateTuples(tuples);
				assert ast3_result == 1;
				// stack has ast3 (i.e., "filename")
				tuples.useAsFileInput();
			} else {
				tuples.getlineInput();
			}
			// 2 resultant values on the stack!
			// 2nd - -1/0/1 for io-err,eof,success
			// 1st(top) - the input
			if (ast2 == null) {
				tuples.assignAsInput();
				// stack still has the input, to be popped below...
				// (all assignment results are placed on the stack)
			} else if (ast2 instanceof ID_AST) {
				ID_AST id_ast = (ID_AST) ast2;
				tuples.assign(id_ast.offset, id_ast.is_global);
				if (id_ast.id.equals("RS")) {
					tuples.applyRS();
				}
			} else if (ast2 instanceof ArrayReference_AST) {
				ArrayReference_AST arr = (ArrayReference_AST) ast2;
				// push the index
				assert arr.ast2 != null;
				int arr_ast2_result = arr.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				// push the array ref itself
				ID_AST id_ast = (ID_AST) arr.ast1;
				tuples.assignArray(id_ast.offset, id_ast.is_global);
			} else if (ast2 instanceof DollarExpression_AST) {
				DollarExpression_AST dollar_expr = (DollarExpression_AST) ast2;
				assert dollar_expr.ast2 != null;
				int ast2_result = dollar_expr.ast2.populateTuples(tuples);
				assert ast2_result == 1;
				// stack contains eval of dollar arg
				tuples.assignAsInputField();
			} else {
				throw new SemanticException("Cannot getline into a " + ast2);
			}
			// get rid of value left by the assignment
			tuples.pop();
			// one value is left on the stack
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class ReturnStatement_AST extends AST {

		private ReturnStatement_AST(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			Returnable returnable = (Returnable) searchFor(Returnable.class);
			if (returnable == null) {
				throw new SemanticException("Cannot use return here.");
			}
			if (ast1 != null) {
				int ast1_result = ast1.populateTuples(tuples);
				assert ast1_result == 1;
				tuples.setReturnResult();
			}
			tuples.gotoAddress(returnable.returnAddress());
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class ExitStatement_AST extends AST {

		private ExitStatement_AST(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (ast1 != null) {
				int ast1_result = ast1.populateTuples(tuples);
				assert ast1_result == 1;
			} else {
				tuples.push(0);
			}
			tuples.exitWithCode();
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class DeleteStatement_AST extends AST {

		private DeleteStatement_AST(AST symbol_ast) {
			super(symbol_ast);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;

			if (ast1 instanceof ArrayReference_AST) {
				assert ast1.ast1 != null;	// a in a[b]
				assert ast1.ast2 != null;	// b in a[b]
				ID_AST id_ast = (ID_AST) ast1.ast1;
				if (id_ast.isScalar()) {
					throw new SemanticException("delete: Cannot use a scalar as an array.");
				}
				id_ast.setArray(true);
				int idx_result = ast1.ast2.populateTuples(tuples);
				assert idx_result == 1;
				// idx on the stack
				tuples.deleteArrayElement(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof ID_AST) {
				ID_AST id_ast = (ID_AST) ast1;
				if (id_ast.isScalar()) {
					throw new SemanticException("delete: Cannot delete a scalar.");
				}
				id_ast.setArray(true);
				tuples.deleteArray(id_ast.offset, id_ast.is_global);
			} else {
				throw new Error("Should never reach here : delete for " + ast1);
			}

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private class BreakStatement_AST extends AST {

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			Breakable breakable = (Breakable) searchFor(Breakable.class);
			if (breakable == null) {
				throw new SemanticException("cannot break; not within a loop");
			}
			assert breakable != null;
			tuples.gotoAddress(breakable.breakAddress());
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class SleepStatement_AST extends AST {

		private SleepStatement_AST(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (ast1 == null) {
				tuples.sleep(0);
			} else {
				int ast1_result = ast1.populateTuples(tuples);
				assert ast1_result == 1;
				tuples.sleep(ast1_result);
			}
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class DumpStatement_AST extends AST {

		private DumpStatement_AST(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (ast1 == null) {
				tuples.dump(0);
			} else {
				assert ast1 instanceof FunctionCallParamList_AST;
				AST ptr = ast1;
				while (ptr != null) {
					if (!(ptr.ast1 instanceof ID_AST)) {
						throw new SemanticException("ID required for argument(s) to _dump");
					}
					ID_AST id_ast = (ID_AST) ptr.ast1;
					if (id_ast.isScalar()) {
						throw new SemanticException("_dump: Cannot use a scalar as an argument.");
					}
					id_ast.setArray(true);
					ptr = ptr.ast2;
				}
				int ast1_result = ast1.populateTuples(tuples);
				tuples.dump(ast1_result);
			}
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private class NextStatement_AST extends AST {

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			Nextable nextable = (Nextable) searchFor(Nextable.class);
			if (nextable == null) {
				throw new SemanticException("cannot next; not within any input rules");
			}
			assert nextable != null;
			tuples.gotoAddress(nextable.nextAddress());
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class ContinueStatement_AST extends AST {

		private ContinueStatement_AST() {
			super();
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			Continueable continueable = (Continueable) searchFor(Continueable.class);
			if (continueable == null) {
				throw new SemanticException("cannot issue a continue; not within any loops");
			}
			assert continueable != null;
			tuples.gotoAddress(continueable.continueAddress());
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	// this was static...
	// made non-static to throw a meaningful ParserException when necessary
	private final class FunctionProxy implements HasFunctionAddress {

		private FunctionDef_AST function_def_ast;
		private String id;

		private FunctionProxy(String id) {
			this.id = id;
		}

		private void setFunctionDefinition(FunctionDef_AST function_def) {
			if (function_def_ast != null) {
				throw new ParserException("function " + function_def + " already defined");
			} else {
				function_def_ast = function_def;
			}
		}

		private boolean isDefined() {
			return function_def_ast != null;
		}

		@Override
		public Address getFunctionAddress() {
			return function_def_ast.getAddress();
		}

		private String getFunctionName() {
			return id;
		}

		private int getFunctionParamCount() {
			return function_def_ast.paramCount();
		}

		@Override
		public String toString() {
			return super.toString() + " (" + id + ")";
		}

		private void checkActualToFormalParameters(AST actual_params) {
			function_def_ast.checkActualToFormalParameters(actual_params);
		}
	}

	/**
	 * Adds {var_name -&gt; offset} mappings to the tuples so that global variables
	 * can be set by the interpreter while processing filename and name=value
	 * entries from the command-line.
	 * Also sends function names to the tuples, to provide the back end
	 * with names to invalidate if name=value assignments are passed
	 * in via the -v or ARGV arguments.
	 *
	 * @param tuples The tuples to add the mapping to.
	 */
	public void populateGlobalVariableNameToOffsetMappings(AwkTuples tuples) {
		for (String varname : symbol_table.global_ids.keySet()) {
			ID_AST id_ast = symbol_table.global_ids.get(varname);
			// The last arg originally was ", id_ast.is_scalar", but this is not set true
			// if the variable use is ambiguous. Therefore, assume it is a scalar
			// if it's NOT used as an array.
			tuples.addGlobalVariableNameToOffsetMapping(varname, id_ast.offset, id_ast.is_array);
		}
		tuples.setFunctionNameSet(symbol_table.function_proxies.keySet());
	}

	private class AwkSymbolTableImpl {

		int numGlobals() {
			return global_ids.size();
		}

		// "constants"
		private Begin_AST begin_ast = null;
		private End_AST end_ast = null;

		// functions (proxies)
		private Map<String, FunctionProxy> function_proxies = new HashMap<String, FunctionProxy>();

		// variable management
		private Map<String, ID_AST> global_ids = new HashMap<String, ID_AST>();
		private Map<String, Map<String, ID_AST>> local_ids = new HashMap<String, Map<String, ID_AST>>();
		private Map<String, Set<String>> function_parameters = new HashMap<String, Set<String>>();
		private Set<String> ids = new HashSet<String>();

		// current function definition for symbols
		private String func_name = null;

		// using set/clear rather than push/pop, it is impossible to define functions within functions
		void setFunctionName(String func_name) {
			assert this.func_name == null;
			this.func_name = func_name;
		}

		void clearFunctionName(String func_name) {
			assert this.func_name != null && this.func_name.length() > 0;
			assert this.func_name.equals(func_name);
			this.func_name = null;
		}

		AST addBEGIN() {
			if (begin_ast == null) {
				begin_ast = new Begin_AST();
			}
			return begin_ast;
		}

		AST addEND() {
			if (end_ast == null) {
				end_ast = new End_AST();
			}
			return end_ast;
		}

		private ID_AST getID(String id) {
			if (function_proxies.get(id) != null) {
				throw new ParserException("cannot use " + id + " as a variable; it is a function");
			}

			// put in the pool of ids to guard against using it as a function name
			ids.add(id);

			Map<String, ID_AST> map;
			if (func_name == null) {
				map = global_ids;
			} else {
				Set<String> set = function_parameters.get(func_name);
				// we need "set != null && ..." here because if function
				// is defined with no args (i.e., function f() ...),
				// then set is null
				if (set != null && set.contains(id)) {
					map = local_ids.get(func_name);
					if (map == null) {
						local_ids.put(func_name, map = new HashMap<String, ID_AST>());
					}
				} else {
					map = global_ids;
				}
			}
			assert map != null;
			ID_AST id_ast = map.get(id);
			if (id_ast == null) {
				id_ast = new ID_AST(id, map == global_ids);
				id_ast.offset = map.size();
				assert id_ast.offset != AVM.NULL_OFFSET;
				map.put(id, id_ast);
			}
			return id_ast;
		}

		AST addID(String id)
				throws ParserException
		{
			ID_AST ret_val = getID(id);
			/// ***
			/// We really don't know if the evaluation is for an array or for a scalar
			/// here, because we can use an array as a function parameter (passed by reference).
			/// ***
			//if (ret_val.is_array)
			//	throw new ParserException("Cannot use "+ret_val+" as a scalar.");
			//ret_val.is_scalar = true;
			return ret_val;
		}

		int addFunctionParameter(String func_name, String id) {
			Set<String> set = function_parameters.get(func_name);
			if (set == null) {
				function_parameters.put(func_name, set = new HashSet<String>());
			}
			if (set.contains(id)) {
				throw new ParserException("multiply defined parameter " + id + " in function " + func_name);
			}
			int retval = set.size();
			set.add(id);

			Map<String, ID_AST> map = local_ids.get(func_name);
			if (map == null) {
				local_ids.put(func_name, map = new HashMap<String, ID_AST>());
			}
			assert map != null;
			ID_AST id_ast = map.get(id);
			if (id_ast == null) {
				id_ast = new ID_AST(id, map == global_ids);
				id_ast.offset = map.size();
				assert id_ast.offset != AVM.NULL_OFFSET;
				map.put(id, id_ast);
			}

			return retval;
		}

		ID_AST getFunctionParameterIDAST(String func_name, String f_id_string) {
			return local_ids.get(func_name).get(f_id_string);
		}

		AST addArrayID(String id)
				throws ParserException
		{
			ID_AST ret_val = getID(id);
			if (ret_val.isScalar()) {
				throw new ParserException("Cannot use " + ret_val + " as an array.");
			}
			ret_val.setArray(true);
			return ret_val;
		}

		AST addFunctionDef(String func_name, AST param_list, AST block) {
			if (ids.contains(func_name)) {
				throw new ParserException("cannot use " + func_name + " as a function; it is a variable");
			}
			FunctionProxy function_proxy = function_proxies.get(func_name);
			if (function_proxy == null) {
				function_proxies.put(func_name, function_proxy = new FunctionProxy(func_name));
			}
			FunctionDef_AST function_def = new FunctionDef_AST(func_name, param_list, block);
			function_proxy.setFunctionDefinition(function_def);
			return function_def;
		}

		AST addFunctionCall(String id, AST param_list) {
			FunctionProxy function_proxy = function_proxies.get(id);
			if (function_proxy == null) {
				function_proxies.put(id, function_proxy = new FunctionProxy(id));
			}
			return new FunctionCall_AST(function_proxy, param_list);
		}

		AST addArrayReference(String id, AST idx_ast)
				throws ParserException
		{
			return new ArrayReference_AST(addArrayID(id), idx_ast);
		}
		// constants are no longer cached/hashed so that individual ASTs
		// can report accurate line numbers upon errors

		AST addINTEGER(String integer) {
			return new Integer_AST(Integer.parseInt(integer));
		}

		AST addDOUBLE(String dbl) {
			return new Double_AST(Double.valueOf(dbl));
		}

		AST addSTRING(String str) {
			return new String_AST(str);
		}

		AST addREGEXP(String regexp) {
			return new Regexp_AST(regexp);
		}
	}

	private class ParserException extends RuntimeException {

		ParserException(String msg) {
			super(msg + " ("
					+ scriptSources.get(scriptSourcesCurrentIndex).getDescription()
					+ ":" + reader.getLineNumber() + ")");
		}
	}
}
