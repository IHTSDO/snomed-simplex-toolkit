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
	displayName?: string;
	questionnaireVersion: string;
	policyItems: Record<string, string>;
	created?: string;
	lastModified?: string;
}

export interface LanguagePolicyRow {
	refsetId: string;
	displayName: string;
	languageCode?: string;
	configured: boolean;
	policy?: LanguageTranslationPolicy;
	lastModified?: string;
}

export function emptyLanguageTranslationPolicy(version: string): LanguageTranslationPolicy {
	return {
		questionnaireVersion: version,
		policyItems: {}
	};
}

export function policyFromApi(data: LanguageTranslationPolicy): LanguageTranslationPolicy {
	return {
		questionnaireVersion: data.questionnaireVersion || '',
		policyItems: { ...(data.policyItems || {}) }
	};
}

export function policyToRequest(policy: LanguageTranslationPolicy): { questionnaireVersion: string; policyItems: Record<string, string> } {
	return {
		questionnaireVersion: policy.questionnaireVersion,
		policyItems: policy.policyItems || {}
	};
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

export function isPolicyComplete(questionnaire: LanguagePolicyQuestionnaire, policyItems: Record<string, string>): boolean {
	for (const section of questionnaire.sections) {
		for (const question of section.questions) {
			if (question.required && !isQuestionAnswered(question, policyItems)) {
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
