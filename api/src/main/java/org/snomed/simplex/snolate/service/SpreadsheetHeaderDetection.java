package org.snomed.simplex.snolate.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class SpreadsheetHeaderDetection {

	private static final int HEADER_WIDTH_TOLERANCE = 1;

	private static final Set<String> KNOWN_CONCEPT_HEADERS = Set.of(
			"concept code", "context", "sctid", "concept id");

	private static final Pattern GENERIC_HEADER_TOKEN = Pattern.compile(
			"\\b(code|id|term|type|field|name|status|source|notes)\\b", Pattern.CASE_INSENSITIVE);

	private static final Pattern URL_PATTERN = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);

	private static final Pattern NUMERIC_PATTERN = Pattern.compile("^\\d+(\\.\\d+)?([eE][+-]?\\d+)?$");

	private static final Pattern SCTID_PATTERN = Pattern.compile("\\d{6,18}");

	private SpreadsheetHeaderDetection() {
	}

	static int detectHeaderRowIndex(List<List<String>> rows) {
		if (rows.isEmpty()) {
			return 0;
		}

		int[] filledCounts = rows.stream().mapToInt(SpreadsheetHeaderDetection::countFilledCells).toArray();
		List<Integer> candidates = new ArrayList<>();
		for (int rowIndex = 0; rowIndex < rows.size() - 1; rowIndex++) {
			if (filledCounts[rowIndex] < 2) {
				continue;
			}
			int matchingDataRows = 0;
			for (int dataRowIndex = rowIndex + 1; dataRowIndex < rows.size(); dataRowIndex++) {
				if (rowWidthMatches(filledCounts[rowIndex], filledCounts[dataRowIndex])) {
					matchingDataRows++;
				}
			}
			if (matchingDataRows >= 1) {
				candidates.add(rowIndex);
			}
		}

		if (!candidates.isEmpty()) {
			int bestRowIndex = candidates.get(0);
			int bestScore = scoreHeaderCandidate(rows.get(bestRowIndex), filledCounts[bestRowIndex]);
			for (int candidateIndex = 1; candidateIndex < candidates.size(); candidateIndex++) {
				int candidateRowIndex = candidates.get(candidateIndex);
				int score = scoreHeaderCandidate(rows.get(candidateRowIndex), filledCounts[candidateRowIndex]);
				if (score > bestScore) {
					bestRowIndex = candidateRowIndex;
					bestScore = score;
				}
			}
			return bestRowIndex;
		}

		for (int rowIndex = 0; rowIndex < filledCounts.length; rowIndex++) {
			if (filledCounts[rowIndex] >= 2) {
				return rowIndex;
			}
		}
		return 0;
	}

	private static boolean rowWidthMatches(int headerCount, int dataCount) {
		return dataCount >= headerCount - HEADER_WIDTH_TOLERANCE
				&& dataCount <= headerCount + HEADER_WIDTH_TOLERANCE;
	}

	private static int countFilledCells(List<String> row) {
		return (int) row.stream().filter(value -> value != null && !value.isBlank()).count();
	}

	private static int scoreHeaderCandidate(List<String> row, int filledCount) {
		int score = 0;
		score += countLabelLikeCells(row) * 10;
		score += countGenericHeaderTokens(row) * 3;
		score += knownHeaderBonus(row);
		score -= countSnomedIdCells(row) * 15;
		score -= countUrlCells(row) * 5;
		if (filledCount >= 3) {
			score += 2;
		}
		return score;
	}

	private static int knownHeaderBonus(List<String> row) {
		int score = 0;
		for (String header : row) {
			if (header == null || header.isBlank()) {
				continue;
			}
			String lower = header.toLowerCase().trim();
			if (KNOWN_CONCEPT_HEADERS.contains(lower)) {
				score += 10;
			}
			if ("target".equals(lower) || "pt".equals(lower)) {
				score += 5;
			}
		}
		return score;
	}

	private static int countSnomedIdCells(List<String> row) {
		int count = 0;
		for (String cell : row) {
			if (cell != null && isSnomedConceptId(cell.trim())) {
				count++;
			}
		}
		return count;
	}

	private static int countUrlCells(List<String> row) {
		int count = 0;
		for (String cell : row) {
			if (cell != null && URL_PATTERN.matcher(cell.trim()).find()) {
				count++;
			}
		}
		return count;
	}

	private static int countLabelLikeCells(List<String> row) {
		int count = 0;
		for (String cell : row) {
			if (cell != null && isLabelLikeCell(cell)) {
				count++;
			}
		}
		return count;
	}

	private static int countGenericHeaderTokens(List<String> row) {
		int count = 0;
		for (String cell : row) {
			if (cell != null && GENERIC_HEADER_TOKEN.matcher(cell).find()) {
				count++;
			}
		}
		return count;
	}

	private static boolean isLabelLikeCell(String value) {
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return false;
		}
		if (isSnomedConceptId(trimmed)) {
			return false;
		}
		if (URL_PATTERN.matcher(trimmed).find()) {
			return false;
		}
		if (NUMERIC_PATTERN.matcher(trimmed).matches()) {
			return false;
		}
		long alphaCount = trimmed.chars()
				.filter(ch -> Character.isLetter(ch))
				.count();
		return alphaCount >= Math.max(2, Math.round(trimmed.length() * 0.3));
	}

	private static boolean isSnomedConceptId(String value) {
		if (value.contains("|")) {
			value = value.substring(0, value.indexOf('|')).trim();
		}
		return SCTID_PATTERN.matcher(value).matches();
	}
}
