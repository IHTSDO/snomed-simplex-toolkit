import {
	detectCsvColumnMapping,
	parseCsvLine,
	parseFirstCsvRow,
	readCsvHeaders
} from './csv-column-mapping.util';

describe('csv-column-mapping.util', () => {
	it('parseCsvLine handles quoted commas', () => {
		expect(parseCsvLine('a,b,c')).toEqual(['a', 'b', 'c']);
		expect(parseCsvLine('"a,b",c')).toEqual(['a,b', 'c']);
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

	it('readCsvHeaders reads the first row from a file', async () => {
		const file = new File(['context,target\n100,asma\n'], 'import.csv', { type: 'text/csv' });
		await expect(readCsvHeaders(file)).resolves.toEqual(['context', 'target']);
	});
});
