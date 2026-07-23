/** Whether a translation set lifecycle status allows editing members and content. */
export function isTranslationSetEditable(status: string | null | undefined): boolean {
	return status === 'READY';
}

/** Whether a translation set is queued or actively being processed. */
export function isTranslationSetBusy(status: string | null | undefined): boolean {
	return status === 'INITIALISING'
		|| status === 'PROCESSING'
		|| status === 'QUEUED_FOR_UPGRADE'
		|| status === 'UPGRADING';
}

/** Whether a translation set shows in-progress progress UI. */
export function isTranslationSetInProgress(status: string | null | undefined): boolean {
	return status === 'PROCESSING' || status === 'UPGRADING';
}

/** Human-readable label for translation set lifecycle status values. */
export function translationSetLifecycleStatusLabel(status: string | null | undefined): string {
	if (status == null || status === '') {
		return '';
	}
	switch (status) {
		case 'INITIALISING':
			return 'Initialising';
		case 'PROCESSING':
			return 'Processing';
		case 'QUEUED_FOR_UPGRADE':
			return 'Queued for upgrade';
		case 'UPGRADING':
			return 'Upgrading';
		case 'READY':
			return 'Ready';
		case 'FAILED':
			return 'Failed';
		case 'DELETING':
			return 'Deleting';
		default:
			return status
				.replace(/_/g, ' ')
				.toLowerCase()
				.replace(/\b\w/g, (c) => c.toUpperCase());
	}
}
