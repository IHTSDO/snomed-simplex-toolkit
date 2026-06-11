import { ParamMap } from '@angular/router';
import { TRANSLATION_STATUS_VALUES } from 'src/app/utils/translation-status-label';

const STATUS_QUERY_PARAM = 'status';

const VALID_STATUS_SET = new Set<string>(TRANSLATION_STATUS_VALUES);

export function parseTranslationStatusFilter(queryParamMap: ParamMap | null | undefined): string | null {
	const raw = queryParamMap?.get(STATUS_QUERY_PARAM)?.trim();
	if (!raw || !VALID_STATUS_SET.has(raw)) {
		return null;
	}
	return raw;
}

/** Router query fragment for an active status filter, or empty object when unset. */
export function translationStatusFilterQueryParams(
	status: string | null | undefined
): Record<string, string> {
	if (status == null || status === '' || !VALID_STATUS_SET.has(status)) {
		return {};
	}
	return { [STATUS_QUERY_PARAM]: status };
}

export function mergeTranslationStudioQueryParams(
	base: Record<string, string | number | null | undefined>,
	status?: string | null
): Record<string, string | number> {
	const merged: Record<string, string | number> = {};
	for (const [key, value] of Object.entries(base)) {
		if (value != null && value !== '') {
			merged[key] = value;
		}
	}
	Object.assign(merged, translationStatusFilterQueryParams(status));
	return merged;
}
