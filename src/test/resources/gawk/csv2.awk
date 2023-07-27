BEGIN {
	text[1] = "a";			expected[1] = 1
	text[2] = "  a";		expected[2] = 1
	text[3] = ",a";			expected[3] = 2
	text[4] = " , a";		expected[4] = 2
	text[5] = "a,b";		expected[5] = 2
	text[6] = "a,b,c";		expected[6] = 3
	text[7] = "\"\"";		expected[7] = 1
	text[8] = "\"abc\"";		expected[8] = 1
	text[9] = "\"a\"\"b\"";		expected[9] = 1
	text[10] = "\"a\",\"b\"";	expected[10] = 2
	text[11] = "a\"\"b";		expected[11] = 1
	text[12] = "\"a,b\"";		expected[12] = 1
	text[13] = "\"\"\"\"";		expected[13] = 1
	text[14] = "\"\"\"\"\"\"";	expected[14] = 1
	text[15] = "\"\"\"x\"\"\"";	expected[15] = 1
	text[16] = ",,\"\"";		expected[16] = 3
	text[17] = "a\"\"b";		expected[17] = 1
	text[18] = "a\"b";		expected[18] = 1
	text[19] = "a''b";		expected[19] = 1
	text[20] = "\"abc";		expected[20] = 1
	text[21] = ",,";		expected[21] = 3
	text[22] = "a,";		expected[22] = 2
	text[23] = "\"\",";		expected[23] = 2
	text[24] = ",";			expected[24] = 2
	text[25] = "\"abc\",def";	expected[25] = 2

	for (i = 1; i <= length(text); i++) {
		n = split(text[i], array)
		if (n != expected[i])
			printf("text[%d] = <%s>, expected %d, got %d\n",
			       i, text[i], expected[i], n)
	}
}
