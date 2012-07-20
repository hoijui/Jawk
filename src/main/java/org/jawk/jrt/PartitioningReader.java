package org.jawk.jrt;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A reader which consumes one record at a time from
 * an underlying input reader.
 * <p>
 * <h3>Greedy Regex Matching</h3>
 * The current implementation matches RS against
 * contents of an input buffer (the underlying input
 * stream filling the input buffer).  Records are
 * split against the matched regular expression
 * input, treating the regular expression as a
 * record separator.
 * <p>
 * By default, greedy regular expression matching
 * for RS is turned off.  It is assumed
 * the user will employ a non-ambiguous regex for RS.
 * For example, ab*c is a non-ambiguous regex,
 * but ab?c?b is an ambiguous regex because
 * it can match ab or abc, and the reader may
 * accept either one, depending on input buffer boundaries.
 * The implemented way to employ greedy regex matching
 * is to consume subsequent input until the match
 * does not occur at the end of the input buffer,
 * or no input is available.  However, this behavior
 * is not desirable in all cases (i.e., interactive
 * input against some sort of ambiguous newline
 * regex).  To enable greedy RS regex consumption,
 * use <code>-Djawk.forceGreedyRS=true</code>.
 */
public class PartitioningReader extends FilterReader {

	private static final boolean FORCE_GREEDY_RS;

	static {
		String grs = System.getProperty("jawk.forceGreedyRS", "0").trim();
		FORCE_GREEDY_RS = grs.equals("1") || grs.equalsIgnoreCase("yes") || grs.equalsIgnoreCase("true");
	}
	private Pattern rs;
	private Matcher matcher;
	private boolean from_filename_list;

	/**
	 * Construct the partitioning reader.
	 *
	 * @param r The reader containing the input data stream.
	 * @param rs_string The record separator, as a regular expression.
	 */
	public PartitioningReader(Reader r, String rs_string) {
		this(r, rs_string, false);
	}

	/**
	 * Construct the partitioning reader.
	 *
	 * @param r The reader containing the input data stream.
	 * @param rs_string The record separator, as a regular expression.
	 * @param from_filename_list Whether the underlying input reader
	 *   is a file from the filename list (the parameters passed
	 *   into AWK after the script argument).
	 */
	public PartitioningReader(Reader r, String rs_string, boolean from_filename_list) {
		super(r);
		this.from_filename_list = from_filename_list;
		RS(rs_string);
	}
	private String prior_rs_string = null;
	private boolean consume_all = false;

	/**
	 * Assign a new record separator for this partitioning reader.
	 *
	 * @param rs_string The new record separator, as a regular expression.
	 */
	public void RS(String rs_string) {
		//assert !rs_string.equals("") : "rs_string cannot be BLANK";
		if (!rs_string.equals(prior_rs_string)) {
			if (rs_string.equals("")) {
				consume_all = true;
				rs = Pattern.compile("\\z", Pattern.DOTALL | Pattern.MULTILINE);
			} else {
				consume_all = false;
				rs = Pattern.compile(rs_string, Pattern.DOTALL | Pattern.MULTILINE);
			}
			prior_rs_string = rs_string;
		}
	}

	/**
	 * @return true whether the underlying input reader is from a
	 *	filename list argument; false otherwise
	 */
	public boolean fromFilenameList() {
		return from_filename_list;
	}

	private StringBuffer remaining = new StringBuffer();
	private char[] read_buf = new char[4096];

	@Override
	public int read(char[] b, int start, int len) throws IOException {
		int ret_val = super.read(b, start, len);
		if (ret_val >= 0) {
			remaining.append(b, start, ret_val);
		}
		return ret_val;
	}

	public boolean willBlock() {
		if (matcher == null) {
			matcher = rs.matcher(remaining);
		} else {
			matcher.reset(remaining);
		}

		return (consume_all || eof || remaining.length() == 0 || !matcher.find());
	}
	private boolean eof = false;

	/**
	 * Consume one record from the reader.
	 * It uses the record separator regular
	 * expression to mark start/end of records.
	 *
	 * @return the next record, null if no more records exist
	 *
	 * @throws IOException upon an IO error
	 */
	public String readRecord() throws IOException {

		if (matcher == null) {
			matcher = rs.matcher(remaining);
		} else {
			matcher.reset(remaining);
		}

		while (consume_all || eof || remaining.length() == 0 || !matcher.find()) {
			int len;
			if (eof || (len = read(read_buf, 0, read_buf.length)) < 0) {
				eof = true;
				String ret_val = remaining.toString();
				remaining.setLength(0);
				if (ret_val.length() == 0) {
					return null;
				} else {
					return ret_val;
				}
			} else if (len == 0) {
				throw new RuntimeException("len == 0 ?!");
			}
			matcher = rs.matcher(remaining);
		}

		matcher.reset();

		// if force greedy regex consumption:
		if (FORCE_GREEDY_RS) {
			// attempt to move last match away from the end of the input
			// so that buffer bounderies landing in the middle of
			// regexp matches that *could* match the regexp if more chars
			// were read
			// (one char at a time!)
			while (matcher.find() && matcher.end() == remaining.length() && matcher.requireEnd()) {
				if (read(read_buf, 0, 1) >= 0) {
					matcher = rs.matcher(remaining);
				} else {
					break;
				}
			}
		}

		// we have a record separator!

		String[] split_string = rs.split(remaining, 2);

		String ret_val = split_string[0];
		remaining.setLength(0);
		// append to remaining only if the split
		// resulted in multiple parts
		if (split_string.length > 1) {
			remaining.append(split_string[1]);
		}
		return ret_val;
	}
} // public class PartitioningReader {FilterReader}
