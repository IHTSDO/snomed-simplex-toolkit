export interface CsvColumnMapping {
	conceptColumn: string;
	termColumns: string[];
}

export const COMMON_CSV_DELIMITERS = [',', '\t', ';', '|'] as const;

export type CsvDelimiter = (typeof COMMON_CSV_DELIMITERS)[number];

export function detectCsvDelimiter(headerLine: string): CsvDelimiter {
	const line = stripUtf8Bom(headerLine);
	if (!line.trim()) {
		return ',';
	}
	let bestDelimiter: CsvDelimiter = ',';
	let bestFieldCount = 0;
	let bestOccurrenceCount = -1;
	for (const delimiter of COMMON_CSV_DELIMITERS) {
		const fieldCount = parseCsvLine(line, delimiter).length;
		const occurrenceCount = countDelimiterOccurrencesOutsideQuotes(line, delimiter);
		if (
			fieldCount > bestFieldCount ||
			(fieldCount === bestFieldCount &&
				fieldCount >= 2 &&
				isPreferredDelimiter(delimiter, occurrenceCount, bestDelimiter, bestOccurrenceCount))
		) {
			bestFieldCount = fieldCount;
			bestDelimiter = delimiter;
			bestOccurrenceCount = occurrenceCount;
		}
	}
	return bestFieldCount >= 2 ? bestDelimiter : ',';
}

function stripUtf8Bom(line: string): string {
	if (line.charCodeAt(0) === 0xfeff) {
		return line.slice(1);
	}
	return line;
}

function delimiterPreference(delimiter: CsvDelimiter): number {
	switch (delimiter) {
		case ',':
			return 0;
		case '\t':
			return 1;
		case ';':
			return 2;
		case '|':
			return 3;
	}
}

function isPreferredDelimiter(
	candidate: CsvDelimiter,
	candidateOccurrences: number,
	currentBest: CsvDelimiter,
	currentBestOccurrences: number
): boolean {
	if (candidateOccurrences !== currentBestOccurrences) {
		return candidateOccurrences > currentBestOccurrences;
	}
	return delimiterPreference(candidate) < delimiterPreference(currentBest);
}

function countDelimiterOccurrencesOutsideQuotes(line: string, delimiter: string): number {
	let count = 0;
	let inQuotes = false;
	for (let i = 0; i < line.length; i++) {
		const c = line.charAt(i);
		if (inQuotes) {
			if (c === '"') {
				if (i + 1 < line.length && line.charAt(i + 1) === '"') {
					i++;
				} else {
					inQuotes = false;
				}
			}
		} else if (c === '"') {
			inQuotes = true;
		} else if (c === delimiter) {
			count++;
		}
	}
	return count;
}

export function parseCsvLine(line: string, delimiter: string = ','): string[] {
	const fields: string[] = [];
	if (!line) {
		return fields;
	}
	let field = '';
	let inQuotes = false;
	for (let i = 0; i < line.length; i++) {
		const c = line.charAt(i);
		if (inQuotes) {
			if (c === '"') {
				if (i + 1 < line.length && line.charAt(i + 1) === '"') {
					field += '"';
					i++;
				} else {
					inQuotes = false;
				}
			} else {
				field += c;
			}
		} else if (c === '"') {
			inQuotes = true;
		} else if (c === delimiter) {
			fields.push(field);
			field = '';
		} else {
			field += c;
		}
	}
	fields.push(field);
	return fields;
}

export function parseFirstCsvRow(text: string, delimiter: string = ','): string[] {
	const fields: string[] = [];
	let field = '';
	let inQuotes = false;
	for (let i = 0; i < text.length; i++) {
		const c = text.charAt(i);
		if (inQuotes) {
			if (c === '"') {
				if (i + 1 < text.length && text.charAt(i + 1) === '"') {
					field += '"';
					i++;
				} else {
					inQuotes = false;
				}
			} else {
				field += c;
			}
		} else if (c === '"') {
			inQuotes = true;
		} else if (c === delimiter) {
			fields.push(field);
			field = '';
		} else if (c === '\r') {
			if (i + 1 < text.length && text.charAt(i + 1) === '\n') {
				i++;
			}
			fields.push(field);
			return fields;
		} else if (c === '\n') {
			fields.push(field);
			return fields;
		} else {
			field += c;
		}
	}
	if (field.length > 0 || fields.length > 0) {
		fields.push(field);
	}
	return fields;
}

function readFirstLine(text: string): string {
	for (let i = 0; i < text.length; i++) {
		const c = text.charAt(i);
		if (c === '\r') {
			if (i + 1 < text.length && text.charAt(i + 1) === '\n') {
				return text.slice(0, i);
			}
			return text.slice(0, i);
		}
		if (c === '\n') {
			return text.slice(0, i);
		}
	}
	return text;
}

export async function readCsvHeaders(file: File): Promise<string[]> {
	const chunk = await file.slice(0, 65536).text();
	const headerLine = readFirstLine(chunk);
	const delimiter = detectCsvDelimiter(headerLine);
	const headers = parseFirstCsvRow(headerLine, delimiter).map((header) => header.trim());
	return headers.filter((header) => header.length > 0);
}

export function detectCsvColumnMapping(headers: string[]): CsvColumnMapping {
	if (headers.includes('Concept Code')) {
		const preferred = headers.find((header) => header.endsWith(' Preferred Term'));
		const other = headers.find((header) => header.startsWith('Other ') && header.endsWith(' Terms'));
		const termColumns = [preferred, other].filter((header): header is string => !!header);
		return {
			conceptColumn: 'Concept Code',
			termColumns
		};
	}
	if (headers.includes('context')) {
		return {
			conceptColumn: 'context',
			termColumns: headers.includes('target') ? ['target'] : []
		};
	}
	if (headers.length >= 2) {
		return {
			conceptColumn: headers[0],
			termColumns: [headers[1]]
		};
	}
	return {
		conceptColumn: headers[0] ?? '',
		termColumns: []
	};
}
