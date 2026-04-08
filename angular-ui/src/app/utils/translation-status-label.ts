/** Display label for Snolate {@code TranslationStatus} API values. */
export function translationStatusLabel(status: string | null | undefined): string {
	if (status == null || status === '') {
		return 'Not started';
	}
	switch (status) {
		case 'NEEDS_EDIT':
			return 'Needs edit';
		case 'FOR_REVIEW':
			return 'For review';
		case 'APPROVED':
			return 'Approved';
		case 'NOT_STARTED':
			return 'Not started';
		default:
			return status
				.replace(/_/g, ' ')
				.toLowerCase()
				.replace(/\b\w/g, (c) => c.toUpperCase());
	}
}

export const TRANSLATION_STATUS_VALUES = [
	'NEEDS_EDIT',
	'FOR_REVIEW',
	'APPROVED',
	'NOT_STARTED'
] as const;

/**
 * Status options shown in the translation edit radio group.
 * {@code NOT_STARTED} is not shown; it applies automatically when there is no translation text.
 */
export const TRANSLATION_STATUS_RADIO_ORDER: readonly (typeof TRANSLATION_STATUS_VALUES)[number][] = [
	'NEEDS_EDIT',
	'FOR_REVIEW',
	'APPROVED'
];

export function translationStatusRadioLabel(status: string): string {
	switch (status) {
		case 'NEEDS_EDIT':
			return 'Needs editing';
		case 'FOR_REVIEW':
			return 'Waiting for review';
		case 'APPROVED':
			return 'Approved';
		case 'NOT_STARTED':
			return 'Not started';
		default:
			return translationStatusLabel(status);
	}
}
