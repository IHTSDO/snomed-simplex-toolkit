export function isTranslationSetEditable(status: string | null | undefined): boolean {
	return status === 'READY';
}

export function isTranslationSetBusy(status: string | null | undefined): boolean {
	return status === 'INITIALISING'
		|| status === 'PROCESSING'
		|| status === 'QUEUED_FOR_UPGRADE'
		|| status === 'UPGRADING';
}

export function isTranslationSetPolling(status: string | null | undefined): boolean {
	return isTranslationSetBusy(status);
}

export function translationSetLifecycleStatusLabel(status: string | null | undefined): string {
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
			if (status == null || status === '') {
				return 'Unknown';
			}
			return status
				.replace(/_/g, ' ')
				.toLowerCase()
				.replace(/\b\w/g, (c) => c.toUpperCase());
	}
}

export function isTranslationSetInProgress(status: string | null | undefined): boolean {
	return status === 'PROCESSING' || status === 'UPGRADING';
}
