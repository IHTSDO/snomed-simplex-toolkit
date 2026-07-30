import { SimplexService } from '../services/simplex/simplex.service';

export interface ImportChangeSummary {
	updated?: number;
	skippedNotFound?: number;
	skippedOutsideSet?: number;
}

export function formatImportResultMessage(summary: ImportChangeSummary | null | undefined): string {
	const updated = summary?.updated ?? 0;
	const skippedNotFound = summary?.skippedNotFound ?? 0;
	const skippedOutsideSet = summary?.skippedOutsideSet ?? 0;
	const parts = [`Updated ${updated} concept${updated === 1 ? '' : 's'}.`];

	if (skippedNotFound > 0 || skippedOutsideSet > 0) {
		const skipParts: string[] = [];
		if (skippedNotFound > 0) {
			skipParts.push(`${skippedNotFound} not found in Translation Studio`);
		}
		if (skippedOutsideSet > 0) {
			skipParts.push(`${skippedOutsideSet} outside set`);
		}
		const totalSkipped = skippedNotFound + skippedOutsideSet;
		parts.push(
			`Skipped ${totalSkipped} row${totalSkipped === 1 ? '' : 's'}: ${skipParts.join(', ')}.`
		);
	}

	return parts.join(' ');
}

export function pollImportJob(
	simplexService: SimplexService,
	edition: string,
	jobId: string,
	maxAttempts = 120
): Promise<any> {
	return new Promise((resolve, reject) => {
		let attempts = 0;

		const poll = (): void => {
			simplexService.pollForExport(edition, jobId).subscribe({
				next: (job) => {
					attempts++;
					if (job.status === 'COMPLETE') {
						resolve(job);
						return;
					}
					if (job.status === 'FAILED' || job.status === 'CANCELLED' || job.status?.endsWith('_ERROR')) {
						reject(new Error(job.errorMessage || 'Import job failed'));
						return;
					}
					if (attempts >= maxAttempts) {
						reject(new Error('Import job timed out'));
						return;
					}
					setTimeout(poll, 2000);
				},
				error: (error) => reject(error)
			});
		};

		poll();
	});
}
