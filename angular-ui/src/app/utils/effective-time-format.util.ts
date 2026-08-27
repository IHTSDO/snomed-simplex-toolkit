export interface EffectiveTimeParts {
	monthYear: string;
	effectiveTime: string;
}

function parseEffectiveTime(effectiveTime: number | string | null | undefined): EffectiveTimeParts | null {
	if (effectiveTime == null || effectiveTime === '') {
		return null;
	}

	const value = Math.trunc(Number(effectiveTime));
	if (!Number.isFinite(value)) {
		return null;
	}

	const str = String(value);
	if (!/^\d{8}$/.test(str)) {
		return null;
	}

	const year = Number(str.substring(0, 4));
	const month = Number(str.substring(4, 6));
	const day = Number(str.substring(6, 8));
	if (month < 1 || month > 12 || day < 1 || day > 31) {
		return null;
	}

	const date = new Date(Date.UTC(year, month - 1, day));
	if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
		return null;
	}

	const monthYear = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(date);
	return { monthYear, effectiveTime: str };
}

export function formatEffectiveTimeParts(effectiveTime: number | string | null | undefined): EffectiveTimeParts | null {
	return parseEffectiveTime(effectiveTime);
}

export function formatEffectiveTimeLabel(effectiveTime: number | string | null | undefined): string | null {
	const parts = parseEffectiveTime(effectiveTime);
	if (!parts) {
		return null;
	}
	return `${parts.monthYear} (${parts.effectiveTime})`;
}
