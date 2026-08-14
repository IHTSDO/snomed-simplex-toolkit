import * as XLSX from 'xlsx';
import {
	applySheetSelection,
	buildHeaderRowOptions,
	countConceptIdsInFile,
	countFilledCells,
	detectCsvColumnMapping,
	detectHeaderRowIndex,
	detectImportColumnMapping,
	detectCsvDelimiter,
	isSpreadsheetFile,
	parseCsvLine,
	parseFirstCsvRow,
	parseSnomedConceptId,
	readCsvHeaders,
	readImportHeaders,
	readSpreadsheetHeaders,
	readSpreadsheetSample,
	rowWidthMatches
} from './csv-column-mapping.util';

function createSpreadsheetFile(
	sheetData: unknown[][],
	sheetName = 'Sheet1',
	filename = 'import.xlsx'
): File {
	const workbook = XLSX.utils.book_new();
	const sheet = XLSX.utils.aoa_to_sheet(sheetData);
	XLSX.utils.book_append_sheet(workbook, sheet, sheetName);
	const buffer = XLSX.write(workbook, { type: 'array', bookType: 'xlsx' });
	return new File([buffer], filename, {
		type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
	});
}

function createMultiSheetSpreadsheetFile(
	sheets: Array<{ name: string; data: unknown[][] }>,
	filename = 'import.xlsx'
): File {
	const workbook = XLSX.utils.book_new();
	for (const { name, data } of sheets) {
		const sheet = XLSX.utils.aoa_to_sheet(data);
		XLSX.utils.book_append_sheet(workbook, sheet, name);
	}
	const buffer = XLSX.write(workbook, { type: 'array', bookType: 'xlsx' });
	return new File([buffer], filename, {
		type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
	});
}

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

	it('detectHeaderRowIndex skips title rows before column headers', () => {
		const rows = [
			['Translation set export'],
			[''],
			['Description of the set'],
			[''],
			['Concept Code', 'PT', 'Synonym 1'],
			['123456789', 'asma', 'asma brônquica'],
			['987654321', 'febre', '']
		];
		expect(detectHeaderRowIndex(rows)).toBe(4);
	});

	it('detectHeaderRowIndex falls back to row 0 for simple headers', () => {
		const rows = [
			['context', 'target'],
			['123456789', 'asma']
		];
		expect(detectHeaderRowIndex(rows)).toBe(0);
	});

	it('detectHeaderRowIndex handles header wider than sparse data rows', () => {
		const rows = [
			['Finding location / Atradnes lokācija'],
			['Lokālais value set ID: COLONO-LOC-001'],
			[
				'Value set ID',
				'Field / lauks',
				'SNOMED CT code',
				'Preferred term (EN)',
				'Concept type / Jēdziena tips',
				'Interface term (EN)',
				'Latviskais termins',
				'Statuss',
				'Piezīmes',
				'Avots'
			],
			[
				'COLONO-LOC-001',
				'Finding location / Atradnes lokācija',
				'32713005',
				'Cecum structure',
				'body structure',
				'Cecum',
				'Aklā zarna',
				'Iekļauts',
				'',
				'https://browser.ihtsdotools.org/'
			],
			[
				'COLONO-LOC-001',
				'Finding location / Atradnes lokācija',
				'9040008',
				'Ascending colon structure',
				'body structure',
				'Ascending colon',
				'Augšupejošā resnā zarna',
				'Iekļauts',
				'',
				'https://browser.ihtsdotools.org/'
			]
		];
		expect(detectHeaderRowIndex(rows)).toBe(2);
	});

	it('rowWidthMatches allows one fewer filled cell in data rows', () => {
		expect(rowWidthMatches(10, 9)).toBe(true);
		expect(rowWidthMatches(10, 8)).toBe(false);
	});

	it('detectHeaderRowIndex prefers header row over first data row with SCTID', () => {
		const rows = [
			['Title'],
			['Concept Code', 'English Term', 'PT'],
			['123456789', 'Asthma', 'Asma']
		];
		expect(detectHeaderRowIndex(rows)).toBe(1);
	});

	it('buildHeaderRowOptions includes row preview text', () => {
		const options = buildHeaderRowOptions([
			['Title'],
			['Concept Code', 'PT'],
			['123456789', 'asma']
		]);
		expect(options[1].label).toContain('Row 2: Concept Code, PT');
	});

	it('countFilledCells counts non-empty cells', () => {
		expect(countFilledCells(['a', '', 'b'])).toBe(2);
		expect(countFilledCells(['title only'])).toBe(1);
	});

	it('readSpreadsheetSample reads first rows from every sheet', async () => {
		const file = createMultiSheetSpreadsheetFile([
			{
				name: 'Concepts',
				data: [
					['Concept Code', 'PT'],
					['123456789', 'asma']
				]
			},
			{
				name: 'Notes',
				data: [['Notes sheet']]
			}
		]);

		const sample = await readSpreadsheetSample(file);
		expect(sample.sheets).toHaveLength(2);
		expect(sample.sheets[0].name).toBe('Concepts');
		expect(sample.sheets[0].headers).toEqual(['Concept Code', 'PT']);
		expect(sample.sheets[1].name).toBe('Notes');
	});

	it('readSpreadsheetSample detects header after preamble rows', async () => {
		const rows: unknown[][] = [];
		for (let i = 0; i < 7; i++) {
			rows.push([`Title line ${i + 1}`]);
		}
		rows.push(['Concept Code', 'PT']);
		rows.push(['123456789', 'asma']);

		const file = createSpreadsheetFile(rows);
		const sample = await readSpreadsheetSample(file);
		expect(sample.sheets[0].headerRowIndex).toBe(7);
		expect(sample.sheets[0].headers).toEqual(['Concept Code', 'PT']);
	});

	it('applySheetSelection recomputes headers for chosen sheet', async () => {
		const file = createMultiSheetSpreadsheetFile([
			{ name: 'Wrong', data: [['Notes only']] },
			{
				name: 'Data',
				data: [
					['Concept Code', 'PT'],
					['123456789', 'asma']
				]
			}
		]);
		const sample = await readSpreadsheetSample(file);
		const selected = applySheetSelection(sample, 'Data');
		expect(selected.headers).toEqual(['Concept Code', 'PT']);
	});

	it('countConceptIdsInFile counts concepts from spreadsheet sample rows only', async () => {
		const file = createSpreadsheetFile([
			['Title'],
			['Concept Code', 'PT'],
			['123456789', 'asma'],
			['123456789', 'dup'],
			['987654321', 'febre']
		]);
		const sample = await readSpreadsheetSample(file);
		await expect(countConceptIdsInFile(file, 'Concept Code', {
			sample,
			sheetName: 'Sheet1',
			headerRowIndex: 1
		})).resolves.toEqual({
			conceptCount: 2,
			invalidRows: 0,
			duplicateRows: 1
		});
	});
});
