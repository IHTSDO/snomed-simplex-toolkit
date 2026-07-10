import { ParamMap } from '@angular/router';
import { TRANSLATION_STATUS_VALUES } from 'src/app/utils/translation-status-label';

const STATUS_QUERY_PARAM = 'status';
const ENGLISH_QUERY_PARAM = 'english';
const TARGET_QUERY_PARAM = 'target';

const VALID_STATUS_SET = new Set<string>(TRANSLATION_STATUS_VALUES);

export function parseTranslationStatusFilter(queryParamMap: ParamMap | null | undefined): string | null {
	const raw = queryParamMap?.get(STATUS_QUERY_PARAM)?.trim();
	if (!raw || !VALID_STATUS_SET.has(raw)) {
		return null;
	}
	return raw;
}

export function parseTranslationEnglishSearch(queryParamMap: ParamMap | null | undefined): string | null {
	return parseOptionalSearchQueryParam(queryParamMap?.get(ENGLISH_QUERY_PARAM));
}

export function parseTranslationTargetSearch(queryParamMap: ParamMap | null | undefined): string | null {
	return parseOptionalSearchQueryParam(queryParamMap?.get(TARGET_QUERY_PARAM));
}

function parseOptionalSearchQueryParam(raw: string | null | undefined): string | null {
	const trimmed = raw?.trim();
	return trimmed ? trimmed : null;
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

export function translationTermSearchQueryParams(
	english: string | null | undefined,
	target: string | null | undefined
): Record<string, string> {
	const params: Record<string, string> = {};
	const englishTrimmed = english?.trim();
	const targetTrimmed = target?.trim();
	if (englishTrimmed) {
		params[ENGLISH_QUERY_PARAM] = englishTrimmed;
	}
	if (targetTrimmed) {
		params[TARGET_QUERY_PARAM] = targetTrimmed;
	}
	return params;
}

export function mergeTranslationStudioQueryParams(
	base: Record<string, string | number | null | undefined>,
	status?: string | null,
	english?: string | null,
	target?: string | null
): Record<string, string | number> {
	const merged: Record<string, string | number> = {};
	for (const [key, value] of Object.entries(base)) {
		if (value != null && value !== '') {
			merged[key] = value;
		}
	}
	Object.assign(merged, translationStatusFilterQueryParams(status));
	Object.assign(merged, translationTermSearchQueryParams(english, target));
	return merged;
}
