import * as XLSX from 'xlsx';

export interface CsvColumnMapping {
	conceptColumn: string;
	termColumns: string[];
}

const KNOWN_CONCEPT_HEADERS = ['concept code', 'context', 'sctid', 'concept id'] as const;

const METADATA_HEADERS = new Set(['english term', 'status', 'url', 'notes', 'comments', 'developer comments']);

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

export function isSpreadsheetFile(file: File): boolean {
	const name = file.name.toLowerCase();
	return name.endsWith('.xlsx');
}

export async function readCsvHeaders(file: File): Promise<string[]> {
	const chunk = await file.slice(0, 65536).text();
	const headerLine = readFirstLine(chunk);
	const delimiter = detectCsvDelimiter(headerLine);
	const headers = parseFirstCsvRow(headerLine, delimiter).map((header) => header.trim());
	return headers.filter((header) => header.length > 0);
}

export async function readSpreadsheetHeaders(file: File): Promise<string[]> {
	const buffer = await file.arrayBuffer();
	const workbook = XLSX.read(buffer, { type: 'array' });
	const sheetName = workbook.SheetNames[0];
	if (!sheetName) {
		return [];
	}
	const sheet = workbook.Sheets[sheetName];
	const rows = XLSX.utils.sheet_to_json<string[]>(sheet, { header: 1, defval: '' });
	const headerRow = rows[0];
	if (!headerRow || !Array.isArray(headerRow)) {
		return [];
	}
	return headerRow
		.map((header) => String(header ?? '').trim())
		.filter((header) => header.length > 0);
}

export async function readImportHeaders(file: File): Promise<string[]> {
	if (isSpreadsheetFile(file)) {
		return readSpreadsheetHeaders(file);
	}
	return readCsvHeaders(file);
}

function isMetadataColumn(header: string): boolean {
	return METADATA_HEADERS.has(header.toLowerCase().trim());
}

function isSynonymColumn(header: string): boolean {
	const lower = header.toLowerCase().trim();
	return /^synonym\s*\d*$/.test(lower)
		|| /^other\s.*terms$/.test(lower)
		|| /^sin[oó]nimo\s*\d*$/.test(lower);
}

function isPreferredTermColumn(header: string): boolean {
	const lower = header.toLowerCase().trim();
	return lower.endsWith(' preferred term') || lower === 'target' || lower === 'pt';
}

function findConceptColumn(headers: string[]): string {
	const known = headers.find((header) =>
		KNOWN_CONCEPT_HEADERS.includes(header.toLowerCase().trim() as typeof KNOWN_CONCEPT_HEADERS[number])
	);
	if (known) {
		return known;
	}
	return headers[0] ?? '';
}

function uniqueTermColumns(columns: (string | undefined)[]): string[] {
	const seen = new Set<string>();
	const result: string[] = [];
	for (const column of columns) {
		if (!column || seen.has(column)) {
			continue;
		}
		seen.add(column);
		result.push(column);
	}
	return result;
}

export function detectImportColumnMapping(headers: string[]): CsvColumnMapping {
	if (headers.includes('Concept Code')) {
		const termCandidates = headers.filter((header) => header !== 'Concept Code' && !isMetadataColumn(header));
		const preferred = termCandidates.find((header) =>
			header.endsWith(' Preferred Term') || isPreferredTermColumn(header)
		);
		const other = termCandidates.find((header) => header.startsWith('Other ') && header.endsWith(' Terms'));
		const synonymColumns = termCandidates.filter((header) => isSynonymColumn(header));
		const additionalTermColumns = termCandidates.filter((header) =>
			header !== preferred && header !== other && !isSynonymColumn(header)
		);
		const termColumns = uniqueTermColumns([preferred, other, ...additionalTermColumns, ...synonymColumns]);
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

	const conceptColumn = findConceptColumn(headers);
	const termCandidates = headers.filter((header) => header !== conceptColumn && !isMetadataColumn(header));
	const preferred = termCandidates.find((header) => isPreferredTermColumn(header));
	const synonymColumns = termCandidates.filter((header) => isSynonymColumn(header));

	if (preferred || synonymColumns.length > 0) {
		return {
			conceptColumn,
			termColumns: uniqueTermColumns([
				preferred,
				...synonymColumns.filter((header) => header !== preferred)
			])
		};
	}

	if (termCandidates.length > 0) {
		return {
			conceptColumn,
			termColumns: termCandidates
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

/** @deprecated Use detectImportColumnMapping */
export const detectCsvColumnMapping = detectImportColumnMapping;
