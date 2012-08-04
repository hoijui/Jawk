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
 * The current implementation matches setRecordSeparator against
 * contents of an input buffer (the underlying input
 * stream filling the input buffer). Records are
 * split against the matched regular expression
 * input, treating the regular expression as a
 * record separator.
 * </p>
 * <p>
 * By default, greedy regular expression matching
 * for setRecordSeparator is turned off. It is assumed
 * the user will employ a non-ambiguous regex for setRecordSeparator.
 * For example, ab*c is a non-ambiguous regex,
 * but ab?c?b is an ambiguous regex because
 * it can match ab or abc, and the reader may
 * accept either one, depending on input buffer boundaries.
 * The implemented way to employ greedy regex matching
 * is to consume subsequent input until the match
 * does not occur at the end of the input buffer,
 * or no input is available. However, this behavior
 * is not desirable in all cases (i.e., interactive
 * input against some sort of ambiguous newline
 * regex). To enable greedy setRecordSeparator regex consumption,
 * use <code>-Djawk.forceGreedyRS=true</code>.
 * </p>
 */
public class PartitioningReader extends FilterReader {

	private static final boolean FORCE_GREEDY_RS;

	static {
		String grs = System.getProperty("jawk.forceGreedyRS", "0").trim();
		FORCE_GREEDY_RS = grs.equals("1") || grs.equalsIgnoreCase("yes") || grs.equalsIgnoreCase("true");
	}
	private Pattern rs;
	private Matcher matcher;
	private boolean fromFileNameList;

	/**
	 * Construct the partitioning reader.
	 *
	 * @param reader The reader containing the input data stream.
	 * @param recordSeparator The record separator, as a regular expression.
	 */
	public PartitioningReader(Reader reader, String recordSeparator) {
		this(reader, recordSeparator, false);
	}

	/**
	 * Construct the partitioning reader.
	 *
	 * @param r The reader containing the input data stream.
	 * @param recordSeparator The record separator, as a regular expression.
	 * @param fromFileNameList Whether the underlying input reader
	 *   is a file from the filename list (the parameters passed
	 *   into AWK after the script argument).
	 */
	public PartitioningReader(Reader r, String recordSeparator, boolean fromFileNameList) {
		super(r);
		this.fromFileNameList = fromFileNameList;
		setRecordSeparator(recordSeparator);
	}
	private String priorRecordSeparator = null;
	private boolean consumeAll = false;

	/**
	 * Assign a new record separator for this partitioning reader.
	 *
	 * @param recordSeparator The new record separator, as a regular expression.
	 */
	public final void setRecordSeparator(String recordSeparator) {
		//assert !recordSeparator.equals("") : "recordSeparator cannot be BLANK";
		if (!recordSeparator.equals(priorRecordSeparator)) {
			if (recordSeparator.equals("")) {
				consumeAll = true;
				rs = Pattern.compile("\\z", Pattern.DOTALL | Pattern.MULTILINE);
			} else {
				consumeAll = false;
				rs = Pattern.compile(recordSeparator, Pattern.DOTALL | Pattern.MULTILINE);
			}
			priorRecordSeparator = recordSeparator;
		}
	}

	/**
	 * @return true whether the underlying input reader is from a
	 *	filename list argument; false otherwise
	 */
	public boolean fromFilenameList() {
		return fromFileNameList;
	}

	private StringBuffer remaining = new StringBuffer();
	private char[] readBuffer = new char[4096];

	@Override
	public int read(char[] b, int start, int len) throws IOException {
		int retVal = super.read(b, start, len);
		if (retVal >= 0) {
			remaining.append(b, start, retVal);
		}
		return retVal;
	}

	public boolean willBlock() {
		if (matcher == null) {
			matcher = rs.matcher(remaining);
		} else {
			matcher.reset(remaining);
		}

		return (consumeAll || eof || remaining.length() == 0 || !matcher.find());
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

		while (consumeAll || eof || remaining.length() == 0 || !matcher.find()) {
			int len = read(readBuffer, 0, readBuffer.length);
			if (eof || (len < 0)) {
				eof = true;
				String retVal = remaining.toString();
				remaining.setLength(0);
				if (retVal.length() == 0) {
					return null;
				} else {
					return retVal;
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
				if (read(readBuffer, 0, 1) >= 0) {
					matcher = rs.matcher(remaining);
				} else {
					break;
				}
			}
		}

		// we have a record separator!

		String[] splitString = rs.split(remaining, 2);

		String retVal = splitString[0];
		remaining.setLength(0);
		// append to remaining only if the split
		// resulted in multiple parts
		if (splitString.length > 1) {
			remaining.append(splitString[1]);
		}
		return retVal;
	}
}
