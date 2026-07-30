import { formatImportResultMessage } from './import-result.util';

describe('formatImportResultMessage', () => {
	it('formats updated count only', () => {
		expect(formatImportResultMessage({ updated: 3 })).toBe('Updated 3 concepts.');
	});

	it('includes skip breakdown', () => {
		expect(formatImportResultMessage({
			updated: 2,
			skippedNotFound: 5,
			skippedOutsideSet: 2
		})).toBe('Updated 2 concepts. Skipped 7 rows: 5 not found in Translation Studio, 2 outside set.');
	});
});
