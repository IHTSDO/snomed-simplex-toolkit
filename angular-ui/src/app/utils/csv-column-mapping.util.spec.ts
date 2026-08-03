import * as XLSX from 'xlsx';
import {
	countConceptIdsInFile,
	detectCsvColumnMapping,
	detectImportColumnMapping,
	detectCsvDelimiter,
	isSpreadsheetFile,
	parseCsvLine,
	parseFirstCsvRow,
	parseSnomedConceptId,
	readCsvHeaders,
	readImportHeaders,
	readSpreadsheetHeaders
} from './csv-column-mapping.util';

describe('csv-column-mapping.util', () => {
	it('parseCsvLine handles quoted commas', () => {
		expect(parseCsvLine('a,b,c')).toEqual(['a', 'b', 'c']);
		expect(parseCsvLine('"a,b",c')).toEqual(['a,b', 'c']);
	});

	it('detectCsvDelimiter recognises common separators', () => {
		expect(detectCsvDelimiter('context,target')).toBe(',');
		expect(detectCsvDelimiter('context\ttarget')).toBe('\t');
		expect(detectCsvDelimiter('context;target')).toBe(';');
		expect(detectCsvDelimiter('context|target')).toBe('|');
	});

	it('parseCsvLine handles tab and semicolon delimiters', () => {
		expect(parseCsvLine('context\ttarget', '\t')).toEqual(['context', 'target']);
		expect(parseCsvLine('context;target', ';')).toEqual(['context', 'target']);
	});

	it('parseFirstCsvRow handles newline inside quoted field', () => {
		expect(parseFirstCsvRow('Concept Code,Other Terms\n100,"line1\nline2"')).toEqual([
			'Concept Code',
			'Other Terms'
		]);
	});

	it('detectCsvColumnMapping defaults for Translation Studio export headers', () => {
		expect(
			detectCsvColumnMapping([
				'Concept Code',
				'English Term',
				'Spanish Preferred Term',
				'Other Spanish Terms',
				'Status',
				'URL'
			])
		).toEqual({
			conceptColumn: 'Concept Code',
			termColumns: ['Spanish Preferred Term', 'Other Spanish Terms']
		});
	});

	it('detectCsvColumnMapping defaults for legacy context,target headers', () => {
		expect(detectCsvColumnMapping(['context', 'target'])).toEqual({
			conceptColumn: 'context',
			termColumns: ['target']
		});
	});

	it('detectCsvColumnMapping falls back to first two columns', () => {
		expect(detectCsvColumnMapping(['id', 'term', 'notes'])).toEqual({
			conceptColumn: 'id',
			termColumns: ['term']
		});
	});

	it('detectImportColumnMapping maps Portugal-style synonym columns', () => {
		expect(
			detectImportColumnMapping([
				'Concept Code',
				'PT',
				'Synonym 1',
				'Synonym 2',
				'Synonym 3',
				'Synonym 4',
				'Synonym 5'
			])
		).toEqual({
			conceptColumn: 'Concept Code',
			termColumns: ['PT', 'Synonym 1', 'Synonym 2', 'Synonym 3', 'Synonym 4', 'Synonym 5']
		});
	});

	it('isSpreadsheetFile detects xlsx uploads', () => {
		expect(isSpreadsheetFile(new File([], 'import.xlsx'))).toBe(true);
		expect(isSpreadsheetFile(new File([], 'import.csv'))).toBe(false);
	});

	it('readImportHeaders reads spreadsheet headers', async () => {
		const workbook = XLSX.utils.book_new();
		const sheet = XLSX.utils.aoa_to_sheet([
			['Concept Code', 'PT', 'Synonym 1'],
			['100', 'asma', 'asma brônquica']
		]);
		XLSX.utils.book_append_sheet(workbook, sheet, 'Sheet1');
		const buffer = XLSX.write(workbook, { type: 'array', bookType: 'xlsx' });
		const file = new File([buffer], 'import.xlsx', {
			type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
		});

		await expect(readImportHeaders(file)).resolves.toEqual(['Concept Code', 'PT', 'Synonym 1']);
	});

	it('readSpreadsheetHeaders reads the first sheet row', async () => {
		const workbook = XLSX.utils.book_new();
		const sheet = XLSX.utils.aoa_to_sheet([['context', 'target']]);
		XLSX.utils.book_append_sheet(workbook, sheet, 'Sheet1');
		const buffer = XLSX.write(workbook, { type: 'array', bookType: 'xlsx' });
		const file = new File([buffer], 'import.xlsx', {
			type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
		});

		await expect(readSpreadsheetHeaders(file)).resolves.toEqual(['context', 'target']);
	});

	it('readCsvHeaders reads the first row from a file', async () => {
		const file = new File(['context,target\n100,asma\n'], 'import.csv', { type: 'text/csv' });
		await expect(readCsvHeaders(file)).resolves.toEqual(['context', 'target']);
	});

	it('readCsvHeaders detects tab-separated headers', async () => {
		const file = new File(['context\ttarget\n100\tasma\n'], 'import.tsv', { type: 'text/csv' });
		await expect(readCsvHeaders(file)).resolves.toEqual(['context', 'target']);
	});

	it('readCsvHeaders detects semicolon-separated headers', async () => {
		const file = new File(['Concept Code;English Term;Spanish Preferred Term\n'], 'import.csv', {
			type: 'text/csv'
		});
		await expect(readCsvHeaders(file)).resolves.toEqual([
			'Concept Code',
			'English Term',
			'Spanish Preferred Term'
		]);
	});

	it('parseSnomedConceptId normalises pipe-delimited concept cells', () => {
		expect(parseSnomedConceptId('123456789 |Term|')).toBe('123456789');
		expect(parseSnomedConceptId('bad')).toBeNull();
	});

	it('countConceptIdsInFile counts unique concept IDs from CSV', async () => {
		const file = new File(['Concept Code,Term\n123456789,a\n123456789,b\n987654321,c\n'], 'set.csv', {
			type: 'text/csv'
		});
		await expect(countConceptIdsInFile(file, 'Concept Code')).resolves.toEqual({
			conceptCount: 2,
			invalidRows: 0,
			duplicateRows: 1
		});
	});
});
