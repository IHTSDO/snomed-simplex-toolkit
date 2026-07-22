export interface LanguagePolicyExampleTable {
	headers: string[];
	rows: string[][];
}

export interface LanguagePolicyOption {
	value: string;
	label: string;
	prompt?: string;
	promptTemplate?: string;
	allowFreeText?: boolean;
	freeTextKey?: string;
}

export interface LanguagePolicyQuestion {
	id: string;
	type: 'choice' | 'text' | 'boolean' | 'keyValueTable';
	required: boolean;
	title: string;
	hint?: string;
	exampleTable?: LanguagePolicyExampleTable;
	options?: LanguagePolicyOption[];
	rowKeys?: string[];
	promptTemplate?: string;
}

export interface LanguagePolicySection {
	id: string;
	label: string;
	questions: LanguagePolicyQuestion[];
}

export interface LanguagePolicyQuestionnaire {
	version: string;
	title: string;
	sections: LanguagePolicySection[];
}

export interface LanguageTranslationPolicy {
	id?: string;
	codesystem?: string;
	refset?: string;
	languageCode?: string;
	languageDialectName?: string;
	questionnaireVersion: string;
	policyItems: Record<string, string>;
	selectedRules?: string[];
	created?: string;
	lastModified?: string;
}

export interface LanguagePolicyRow {
	refsetId: string;
	languageDialectName: string;
	languageCode?: string;
	configured: boolean;
	policy?: LanguageTranslationPolicy;
	lastModified?: string;
}

export function emptyLanguageTranslationPolicy(version: string): LanguageTranslationPolicy {
	return {
		questionnaireVersion: version,
		policyItems: {},
		selectedRules: []
	};
}

export function policyFromApi(data: LanguageTranslationPolicy): LanguageTranslationPolicy {
	return {
		languageDialectName: data.languageDialectName || '',
		questionnaireVersion: data.questionnaireVersion || '',
		policyItems: { ...(data.policyItems || {}) },
		selectedRules: data.selectedRules ? [...data.selectedRules] : []
	};
}

export function policyToRequest(policy: LanguageTranslationPolicy): {
	questionnaireVersion: string;
	languageDialectName: string;
	policyItems: Record<string, string>;
	selectedRules: string[];
} {
	return {
		questionnaireVersion: policy.questionnaireVersion,
		languageDialectName: policy.languageDialectName?.trim() || '',
		policyItems: policy.policyItems || {},
		selectedRules: policy.selectedRules || []
	};
}

export function isLanguageDialectNameValid(languageDialectName?: string): boolean {
	return !!languageDialectName?.trim();
}

/**
 * Plain-text language/dialect label; strips trailing SNOMED-style refset name suffixes.
 * Matches backend {@code SnolateTranslationToolService.displayLanguageDialect}.
 */
export function displayLanguageDialect(raw: string | null | undefined): string {
	if (raw == null) {
		return '';
	}
	let s = raw.trim();
	if (!s) {
		return '';
	}
	const lower = s.toLowerCase();
	for (const suffix of ['language reference set', 'language refset']) {
		if (lower.endsWith(suffix)) {
			s = s.slice(0, s.length - suffix.length).trim();
			break;
		}
	}
	return s;
}

export function defaultLanguageDialectName(
	savedLanguageDialectName?: string,
	dialectHint?: string,
	refsetFallback?: string
): string {
	return displayLanguageDialect(savedLanguageDialectName)
		|| displayLanguageDialect(dialectHint)
		|| displayLanguageDialect(refsetFallback)
		|| refsetFallback?.trim()
		|| '';
}

export function deriveSelectedRulesFromPolicyItems(
	questionnaire: LanguagePolicyQuestionnaire,
	policyItems: Record<string, string>
): string[] {
	const selected: string[] = [];
	for (const section of questionnaire.sections) {
		for (const question of section.questions) {
			if (isQuestionAnswered(question, policyItems)) {
				selected.push(question.id);
			}
		}
	}
	return selected;
}

export function parseKeyValueTable(json: string | undefined): Record<string, string> {
	if (!json) {
		return {};
	}
	try {
		return JSON.parse(json);
	} catch {
		return {};
	}
}

export function serializeKeyValueTable(table: Record<string, string>): string {
	return JSON.stringify(table);
}

export function isQuestionAnswered(question: LanguagePolicyQuestion, policyItems: Record<string, string>): boolean {
	const answer = policyItems[question.id];
	if (question.type === 'keyValueTable' || question.type === 'text') {
		return !!answer?.trim();
	}
	if (!answer) {
		return false;
	}
	if (question.type === 'choice') {
		const option = question.options?.find(o => o.value === answer);
		if (option?.allowFreeText && option.freeTextKey) {
			return !!policyItems[option.freeTextKey]?.trim();
		}
	}
	return true;
}

export function isPolicyValid(
	questionnaire: LanguagePolicyQuestionnaire,
	policyItems: Record<string, string>,
	selectedRules: string[],
	languageDialectName?: string
): boolean {
	if (!isLanguageDialectNameValid(languageDialectName)) {
		return false;
	}
	if (!selectedRules.length) {
		return true;
	}
	const selectedSet = new Set(selectedRules);
	for (const section of questionnaire.sections) {
		for (const question of section.questions) {
			if (selectedSet.has(question.id) && !isQuestionAnswered(question, policyItems)) {
				return false;
			}
		}
	}
	return true;
}

export function initializeKeyValueTables(questionnaire: LanguagePolicyQuestionnaire, policyItems: Record<string, string>): void {
	for (const section of questionnaire.sections) {
		for (const question of section.questions) {
			if (question.type === 'keyValueTable' && question.rowKeys && !policyItems[question.id]) {
				const table: Record<string, string> = {};
				question.rowKeys.forEach(key => table[key] = '');
				policyItems[question.id] = serializeKeyValueTable(table);
			}
		}
	}
}
