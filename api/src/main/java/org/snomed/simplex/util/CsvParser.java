package org.snomed.simplex.util;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CsvParser {

	public static final char DELIMITER_COMMA = ',';
	public static final char DELIMITER_TAB = '\t';
	public static final char DELIMITER_SEMICOLON = ';';
	public static final char DELIMITER_PIPE = '|';

	private static final char[] COMMON_DELIMITERS = {
			DELIMITER_COMMA, DELIMITER_TAB, DELIMITER_SEMICOLON, DELIMITER_PIPE
	};

	private CsvParser() {}

	public static char detectDelimiter(String headerLine) {
		String line = FileUtils.removeUTF8BOM(headerLine);
		if (line == null || line.isBlank()) {
			return DELIMITER_COMMA;
		}
		char bestDelimiter = DELIMITER_COMMA;
		int bestFieldCount = 0;
		int bestOccurrenceCount = -1;
		for (char delimiter : COMMON_DELIMITERS) {
			int fieldCount = parseLine(line, delimiter).size();
			int occurrenceCount = countDelimiterOccurrencesOutsideQuotes(line, delimiter);
			if (fieldCount > bestFieldCount
					|| (fieldCount == bestFieldCount && fieldCount >= 2 && isPreferredDelimiter(
							delimiter, occurrenceCount, bestDelimiter, bestOccurrenceCount))) {
				bestFieldCount = fieldCount;
				bestDelimiter = delimiter;
				bestOccurrenceCount = occurrenceCount;
			}
		}
		return bestFieldCount >= 2 ? bestDelimiter : DELIMITER_COMMA;
	}

	private static boolean isPreferredDelimiter(char candidate, int candidateOccurrences,
			char currentBest, int currentBestOccurrences) {
		if (candidateOccurrences != currentBestOccurrences) {
			return candidateOccurrences > currentBestOccurrences;
		}
		return delimiterPreference(candidate) < delimiterPreference(currentBest);
	}

	private static int delimiterPreference(char delimiter) {
		if (delimiter == DELIMITER_COMMA) {
			return 0;
		}
		if (delimiter == DELIMITER_TAB) {
			return 1;
		}
		if (delimiter == DELIMITER_SEMICOLON) {
			return 2;
		}
		if (delimiter == DELIMITER_PIPE) {
			return 3;
		}
		return 4;
	}

	static int countDelimiterOccurrencesOutsideQuotes(String line, char delimiter) {
		int count = 0;
		boolean inQuotes = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (inQuotes) {
				if (c == '"') {
					if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
						i++;
					} else {
						inQuotes = false;
					}
				}
			} else if (c == '"') {
				inQuotes = true;
			} else if (c == delimiter) {
				count++;
			}
		}
		return count;
	}

	public static List<String> parseLine(String line, char delimiter) {
		List<String> fields = new ArrayList<>();
		if (line == null) {
			return fields;
		}
		StringBuilder field = new StringBuilder();
		boolean inQuotes = false;
		int index = 0;
		while (index < line.length()) {
			char c = line.charAt(index);
			if (inQuotes) {
				if (c == '"') {
					if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
						field.append('"');
						index += 2;
						continue;
					}
					inQuotes = false;
				} else {
					field.append(c);
				}
			} else if (c == '"') {
				inQuotes = true;
			} else if (c == delimiter) {
				fields.add(field.toString());
				field.setLength(0);
			} else {
				field.append(c);
			}
			index++;
		}
		fields.add(field.toString());
		return fields;
	}

	public static List<String> readRow(Reader reader, char delimiter) throws IOException {
		List<String> fields = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean inQuotes = false;
		int ch;
		while ((ch = reader.read()) != -1) {
			char c = (char) ch;
			if (inQuotes) {
				if (c == '"') {
					reader.mark(1);
					int next = reader.read();
					if (next == '"') {
						field.append('"');
					} else {
						inQuotes = false;
						if (next != -1) {
							reader.reset();
						}
					}
				} else {
					field.append(c);
				}
			} else if (c == '"') {
				inQuotes = true;
			} else if (c == delimiter) {
				fields.add(field.toString());
				field.setLength(0);
			} else if (c == '\r') {
				reader.mark(1);
				int next = reader.read();
				if (next != '\n' && next != -1) {
					reader.reset();
				}
				fields.add(field.toString());
				return fields;
			} else if (c == '\n') {
				fields.add(field.toString());
				return fields;
			} else {
				field.append(c);
			}
		}
		if (!field.isEmpty() || !fields.isEmpty()) {
			fields.add(field.toString());
			return fields;
		}
		return Collections.emptyList();
	}
}
