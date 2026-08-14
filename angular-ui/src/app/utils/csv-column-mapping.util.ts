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

export const DEFAULT_SPREADSHEET_SAMPLE_ROWS = 10;

export interface SpreadsheetSheetSample {
	name: string;
	rows: string[][];
	headerRowIndex: number;
	headers: string[];
}

export interface SpreadsheetFileSample {
	sheets: SpreadsheetSheetSample[];
}

export interface SpreadsheetCountOptions {
	sheetName?: string;
	headerRowIndex?: number;
	sample?: SpreadsheetFileSample;
}

export function countFilledCells(row: string[]): number {
	return row.filter((cell) => String(cell ?? '').trim().length > 0).length;
}

const HEADER_WIDTH_TOLERANCE = 1;
const GENERIC_HEADER_TOKEN = /\b(code|id|term|type|field|name|status|source|notes)\b/i;
const URL_PATTERN = /^https?:\/\//i;

export interface HeaderRowOption {
	index: number;
	label: string;
}

export function rowWidthMatches(headerCount: number, dataCount: number, tolerance: number = HEADER_WIDTH_TOLERANCE): boolean {
	return dataCount >= headerCount - tolerance && dataCount <= headerCount + tolerance;
}

export function countSnomedIdCells(row: string[]): number {
	return row.filter((cell) => parseSnomedConceptId(String(cell ?? '')) !== null).length;
}

export function countUrlCells(row: string[]): number {
	return row.filter((cell) => URL_PATTERN.test(String(cell ?? '').trim())).length;
}

function isLabelLikeCell(value: string): boolean {
	const trimmed = value.trim();
	if (!trimmed) {
		return false;
	}
	if (parseSnomedConceptId(trimmed)) {
		return false;
	}
	if (URL_PATTERN.test(trimmed)) {
		return false;
	}
	if (/^\d+(\.\d+)?([eE][+-]?\d+)?$/.test(trimmed)) {
		return false;
	}
	const alphaMatches = trimmed.match(/[A-Za-z\u00C0-\u024F]/g);
	const alphaCount = alphaMatches?.length ?? 0;
	return alphaCount >= Math.max(2, trimmed.length * 0.3);
}

export function countLabelLikeCells(row: string[]): number {
	return row.filter((cell) => isLabelLikeCell(String(cell ?? ''))).length;
}

export function countGenericHeaderTokens(row: string[]): number {
	return row.filter((cell) => GENERIC_HEADER_TOKEN.test(String(cell ?? ''))).length;
}

function knownHeaderBonus(row: string[]): number {
	const headers = extractHeaders(row);
	let score = 0;
	for (const header of headers) {
		const lower = header.toLowerCase().trim();
		if (KNOWN_CONCEPT_HEADERS.includes(lower as typeof KNOWN_CONCEPT_HEADERS[number])) {
			score += 10;
		}
		if (lower === 'target' || lower === 'pt') {
			score += 5;
		}
	}
	return score;
}

function scoreHeaderCandidate(row: string[], filledCount: number): number {
	let score = 0;
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

export function detectHeaderRowIndex(rows: string[][]): number {
	if (rows.length === 0) {
		return 0;
	}

	const filledCounts = rows.map(countFilledCells);
	const candidates: number[] = [];

	for (let rowIndex = 0; rowIndex < rows.length - 1; rowIndex++) {
		const filledCount = filledCounts[rowIndex];
		if (filledCount < 2) {
			continue;
		}
		let matchingDataRows = 0;
		for (let dataRowIndex = rowIndex + 1; dataRowIndex < rows.length; dataRowIndex++) {
			if (rowWidthMatches(filledCount, filledCounts[dataRowIndex])) {
				matchingDataRows++;
			}
		}
		if (matchingDataRows >= 1) {
			candidates.push(rowIndex);
		}
	}

	if (candidates.length > 0) {
		let bestRowIndex = candidates[0];
		let bestScore = scoreHeaderCandidate(rows[bestRowIndex] ?? [], filledCounts[bestRowIndex]);
		for (let candidateIndex = 1; candidateIndex < candidates.length; candidateIndex++) {
			const candidateRowIndex = candidates[candidateIndex];
			const score = scoreHeaderCandidate(rows[candidateRowIndex] ?? [], filledCounts[candidateRowIndex]);
			if (score > bestScore) {
				bestRowIndex = candidateRowIndex;
				bestScore = score;
			}
		}
		return bestRowIndex;
	}

	const fallbackRowIndex = filledCounts.findIndex((filledCount) => filledCount >= 2);
	return fallbackRowIndex >= 0 ? fallbackRowIndex : 0;
}

export function buildHeaderRowOptions(
	rows: string[][],
	maxRows: number = DEFAULT_SPREADSHEET_SAMPLE_ROWS
): HeaderRowOption[] {
	const options: HeaderRowOption[] = [];
	const limit = Math.min(rows.length, maxRows);
	for (let rowIndex = 0; rowIndex < limit; rowIndex++) {
		const previewCells = extractHeaders(rows[rowIndex] ?? []).slice(0, 3);
		let preview = previewCells.length > 0 ? `: ${previewCells.join(', ')}` : '';
		if (preview.length > 80) {
			preview = `${preview.slice(0, 77)}…`;
		}
		options.push({
			index: rowIndex,
			label: `Row ${rowIndex + 1}${preview}`
		});
	}
	return options;
}

export function applyHeaderRowIndex(
	sample: SpreadsheetFileSample,
	sheetName: string,
	headerRowIndex: number
): SpreadsheetSheetSample {
	const sheet = getSheetSample(sample, sheetName);
	if (!sheet) {
		throw new Error(`Sheet not found: ${sheetName}`);
	}
	const headers = extractHeaders(sheet.rows[headerRowIndex] ?? []);
	return {
		...sheet,
		headerRowIndex,
		headers
	};
}

export function extractHeaders(row: string[]): string[] {
	if (!row || row.length === 0) {
		return [];
	}
	let end = row.length;
	while (end > 0 && !String(row[end - 1] ?? '').trim()) {
		end--;
	}
	return row.slice(0, end)
		.map((header) => String(header ?? '').trim())
		.filter((header) => header.length > 0);
}

function normalizeSpreadsheetRow(row: unknown): string[] {
	if (!Array.isArray(row)) {
		return [];
	}
	return row.map((cell) => String(cell ?? '').trim());
}

function readSheetSampleRows(sheet: XLSX.WorkSheet, maxRows: number): string[][] {
	const rows = XLSX.utils.sheet_to_json<string[]>(sheet, { header: 1, defval: '' });
	return rows.slice(0, maxRows).map((row) => normalizeSpreadsheetRow(row));
}

function buildSheetSample(name: string, rows: string[][]): SpreadsheetSheetSample {
	const headerRowIndex = detectHeaderRowIndex(rows);
	const headers = extractHeaders(rows[headerRowIndex] ?? []);
	return { name, rows, headerRowIndex, headers };
}

export async function readSpreadsheetSample(
	file: File,
	maxRows: number = DEFAULT_SPREADSHEET_SAMPLE_ROWS
): Promise<SpreadsheetFileSample> {
	const buffer = await file.arrayBuffer();
	const workbook = XLSX.read(buffer, { type: 'array', sheetRows: maxRows });
	const sheets = workbook.SheetNames.map((name) => {
		const sheet = workbook.Sheets[name];
		const rows = readSheetSampleRows(sheet, maxRows);
		return buildSheetSample(name, rows);
	});
	return { sheets };
}

export function getSheetSample(sample: SpreadsheetFileSample, sheetName: string): SpreadsheetSheetSample | undefined {
	return sample.sheets.find((sheet) => sheet.name === sheetName);
}

export function applySheetSelection(sample: SpreadsheetFileSample, sheetName: string): SpreadsheetSheetSample {
	const sheet = getSheetSample(sample, sheetName);
	if (!sheet) {
		throw new Error(`Sheet not found: ${sheetName}`);
	}
	return buildSheetSample(sheet.name, sheet.rows);
}

export async function readCsvHeaders(file: File): Promise<string[]> {
	const chunk = await file.slice(0, 65536).text();
	const headerLine = readFirstLine(chunk);
	const delimiter = detectCsvDelimiter(headerLine);
	const headers = parseFirstCsvRow(headerLine, delimiter).map((header) => header.trim());
	return headers.filter((header) => header.length > 0);
}

export async function readSpreadsheetHeaders(file: File): Promise<string[]> {
	const sample = await readSpreadsheetSample(file);
	return sample.sheets[0]?.headers ?? [];
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

const SCTID_PATTERN = /^\d{6,18}$/;

export function parseSnomedConceptId(cellValue: string): string | null {
	if (!cellValue || !cellValue.trim()) {
		return null;
	}
	let value = cellValue.trim();
	if (value.includes('|')) {
		value = value.substring(0, value.indexOf('|')).trim();
	}
	if (!SCTID_PATTERN.test(value)) {
		return null;
	}
	return value;
}

export interface ConceptIdPreviewResult {
	conceptCount: number;
	invalidRows: number;
	duplicateRows: number;
}

export async function countConceptIdsInFile(
	file: File,
	conceptColumn: string,
	spreadsheetOptions?: SpreadsheetCountOptions
): Promise<ConceptIdPreviewResult> {
	if (isSpreadsheetFile(file)) {
		return countConceptIdsInSpreadsheet(file, conceptColumn, spreadsheetOptions);
	}
	return countConceptIdsInCsv(file, conceptColumn);
}

async function countConceptIdsInCsv(file: File, conceptColumn: string): Promise<ConceptIdPreviewResult> {
	const text = await file.text();
	const headerLine = readFirstLine(text);
	const delimiter = detectCsvDelimiter(headerLine);
	const headers = parseFirstCsvRow(headerLine, delimiter).map((header) => header.trim());
	const conceptIndex = headers.indexOf(conceptColumn);
	if (conceptIndex < 0) {
		return { conceptCount: 0, invalidRows: 0, duplicateRows: 0 };
	}

	const seen = new Set<string>();
	let conceptCount = 0;
	let invalidRows = 0;
	let duplicateRows = 0;
	let bodyStart = headerLine.length;
	if (text.charAt(bodyStart) === '\r') {
		bodyStart++;
	}
	if (text.charAt(bodyStart) === '\n') {
		bodyStart++;
	}
	const body = text.slice(bodyStart);
	let lineStart = 0;
	for (let i = 0; i <= body.length; i++) {
		const endOfLine = i === body.length || body.charAt(i) === '\n' || body.charAt(i) === '\r';
		if (!endOfLine) {
			continue;
		}
		const line = body.slice(lineStart, i).trim();
		lineStart = i + 1;
		if (body.charAt(i) === '\r' && body.charAt(i + 1) === '\n') {
			lineStart++;
			i++;
		}
		if (!line) {
			continue;
		}
		const fields = parseCsvLine(line, delimiter);
		const conceptId = parseSnomedConceptId(fields[conceptIndex] ?? '');
		if (!conceptId) {
			const raw = (fields[conceptIndex] ?? '').trim();
			if (raw) {
				invalidRows++;
			}
			continue;
		}
		if (seen.has(conceptId)) {
			duplicateRows++;
			continue;
		}
		seen.add(conceptId);
		conceptCount++;
	}
	return { conceptCount, invalidRows, duplicateRows };
}

function countConceptIdsFromSampleRows(
	rows: string[][],
	headerRowIndex: number,
	conceptColumn: string
): ConceptIdPreviewResult {
	const headerRow = rows[headerRowIndex] ?? [];
	const headers = headerRow.map((header) => String(header ?? '').trim());
	const conceptIndex = headers.indexOf(conceptColumn);
	if (conceptIndex < 0) {
		return { conceptCount: 0, invalidRows: 0, duplicateRows: 0 };
	}

	const seen = new Set<string>();
	let conceptCount = 0;
	let invalidRows = 0;
	let duplicateRows = 0;
	for (let rowIndex = headerRowIndex + 1; rowIndex < rows.length; rowIndex++) {
		const row = rows[rowIndex] ?? [];
		const raw = String(row[conceptIndex] ?? '').trim();
		if (!raw) {
			continue;
		}
		const conceptId = parseSnomedConceptId(raw);
		if (!conceptId) {
			invalidRows++;
			continue;
		}
		if (seen.has(conceptId)) {
			duplicateRows++;
			continue;
		}
		seen.add(conceptId);
		conceptCount++;
	}
	return { conceptCount, invalidRows, duplicateRows };
}

async function countConceptIdsInSpreadsheet(
	file: File,
	conceptColumn: string,
	spreadsheetOptions?: SpreadsheetCountOptions
): Promise<ConceptIdPreviewResult> {
	const sample = spreadsheetOptions?.sample ?? await readSpreadsheetSample(file);
	const sheetName = spreadsheetOptions?.sheetName ?? sample.sheets[0]?.name;
	if (!sheetName) {
		return { conceptCount: 0, invalidRows: 0, duplicateRows: 0 };
	}
	const sheet = getSheetSample(sample, sheetName);
	if (!sheet) {
		return { conceptCount: 0, invalidRows: 0, duplicateRows: 0 };
	}
	const headerRowIndex = spreadsheetOptions?.headerRowIndex ?? sheet.headerRowIndex;
	return countConceptIdsFromSampleRows(sheet.rows, headerRowIndex, conceptColumn);
}
