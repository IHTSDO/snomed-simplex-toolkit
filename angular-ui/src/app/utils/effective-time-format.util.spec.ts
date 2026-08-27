import { formatEffectiveTimeLabel, formatEffectiveTimeParts } from './effective-time-format.util';

describe('formatEffectiveTimeLabel', () => {
	it('formats a valid SNOMED effective time', () => {
		expect(formatEffectiveTimeLabel(20260801)).toBe('August 2026 (20260801)');
		expect(formatEffectiveTimeLabel('20260801')).toBe('August 2026 (20260801)');
	});

	it('returns null for missing or invalid values', () => {
		expect(formatEffectiveTimeLabel(null)).toBeNull();
		expect(formatEffectiveTimeLabel(undefined)).toBeNull();
		expect(formatEffectiveTimeLabel(20261301)).toBeNull();
		expect(formatEffectiveTimeLabel(20260230)).toBeNull();
	});
});

describe('formatEffectiveTimeParts', () => {
	it('returns month/year and effective time separately', () => {
		expect(formatEffectiveTimeParts('20260801')).toEqual({
			monthYear: 'August 2026',
			effectiveTime: '20260801'
		});
	});
});
