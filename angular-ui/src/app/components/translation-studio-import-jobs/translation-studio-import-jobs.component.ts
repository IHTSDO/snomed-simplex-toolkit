import { ChangeDetectorRef, Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, of, Subscription } from 'rxjs';
import { SimplexService } from '../../services/simplex/simplex.service';
import { formatImportResultMessage } from 'src/app/utils/import-result.util';

export interface TranslationStudioImportLanguageOption {
	refsetId: string;
	languageDialectName: string;
}

@Component({
	selector: 'app-translation-studio-import-jobs',
	templateUrl: './translation-studio-import-jobs.component.html',
	styleUrl: './translation-studio-import-jobs.component.scss'
})
export class TranslationStudioImportJobsComponent implements OnChanges, OnDestroy {
	@Input() edition = '';
	@Input() languageOptions: TranslationStudioImportLanguageOption[] = [];

	jobs: any[] = [];
	loading = false;
	languageFilter: string | null = null;
	readonly displayedColumns = ['created', 'user', 'display', 'status', 'result'];
	readonly skippedPreviewLimit = 20;

	private subscription?: Subscription;
	private intervalId?: ReturnType<typeof setInterval>;
	private notifiedCompletedJobIds = new Set<string>();
	private downloadingJobIds = new Set<string>();

	constructor(
		private simplexService: SimplexService,
		private snackBar: MatSnackBar,
		private changeDetectorRef: ChangeDetectorRef
	) {}

	ngOnChanges(changes: SimpleChanges): void {
		if (changes['edition'] || changes['languageOptions']) {
			this.loadJobs(true);
		}
	}

	ngOnDestroy(): void {
		this.subscription?.unsubscribe();
		if (this.intervalId) {
			clearInterval(this.intervalId);
		}
	}

	onLanguageFilterChange(refsetId: string | null): void {
		this.languageFilter = refsetId;
		this.loadJobs(true);
	}

	refresh(): void {
		this.loadJobs(false);
	}

	resultSummary(job: any): string {
		if (job?.status !== 'COMPLETE') {
			return '';
		}
		return formatImportResultMessage(job.changeSummary);
	}

	skippedNotFoundPreview(job: any): string[] {
		const codes = job?.changeSummary?.skippedNotFoundCodes ?? [];
		return codes.slice(0, this.skippedPreviewLimit);
	}

	additionalSkippedCount(job: any): number {
		const total = job?.changeSummary?.skippedNotFound ?? 0;
		const previewCount = this.skippedNotFoundPreview(job).length;
		return Math.max(0, total - previewCount);
	}

	hasSkippedNotFoundCodes(job: any): boolean {
		return (job?.changeSummary?.skippedNotFoundCodes?.length ?? 0) > 0;
	}

	isDownloadingSkippedCsv(job: any): boolean {
		return this.downloadingJobIds.has(job?.id);
	}

	downloadSkippedNotFoundCsv(job: any): void {
		if (!this.edition || !job?.id || this.isDownloadingSkippedCsv(job)) {
			return;
		}
		this.downloadingJobIds.add(job.id);
		this.simplexService.downloadTranslationStudioImportSkippedCsv(this.edition, job.id).subscribe({
			next: (blob) => {
				this.simplexService.triggerDownload(blob, `skipped-not-found-${job.id}.csv`);
				this.downloadingJobIds.delete(job.id);
				this.changeDetectorRef.detectChanges();
			},
			error: (error) => {
				console.error('Failed to download skipped concept codes:', error);
				this.downloadingJobIds.delete(job.id);
				this.snackBar.open('Failed to download skipped concept codes', 'Dismiss', { duration: 5000 });
				this.changeDetectorRef.detectChanges();
			}
		});
	}

	languageLabel(refsetId: string | undefined): string {
		if (!refsetId) {
			return '';
		}
		return this.languageOptions.find(option => option.refsetId === refsetId)?.languageDialectName ?? refsetId;
	}

	loadJobs(clear: boolean): void {
		if (!this.edition) {
			return;
		}

		if (clear) {
			this.jobs = [];
			this.loading = true;
		}

		this.subscription?.unsubscribe();
		this.subscription = this.simplexService
			.getTranslationStudioImportJobs(this.edition, this.languageFilter ?? undefined)
			.pipe(catchError((error) => {
				console.error('Failed to load Translation Studio import jobs:', error);
				this.loading = false;
				return of([]);
			}))
			.subscribe((data) => {
				const previousInProgress = this.jobs.filter(job => this.isInProgress(job));
				this.jobs = (data ?? []).slice(0, 50);
				this.loading = false;
				this.managePolling();
				this.notifyCompletedJobs(previousInProgress);
				this.changeDetectorRef.detectChanges();
			});
	}

	private notifyCompletedJobs(previousInProgress: any[]): void {
		for (const previousJob of previousInProgress) {
			const updatedJob = this.jobs.find(job => job.id === previousJob.id);
			if (!updatedJob || updatedJob.status !== 'COMPLETE' || this.notifiedCompletedJobIds.has(updatedJob.id)) {
				continue;
			}
			this.notifiedCompletedJobIds.add(updatedJob.id);
			const summary = formatImportResultMessage(updatedJob.changeSummary);
			const hasSkips = (updatedJob.changeSummary?.skippedNotFound ?? 0) > 0
				|| (updatedJob.changeSummary?.skippedOutsideSet ?? 0) > 0;
			if (hasSkips) {
				this.snackBar.open(summary, 'Close', { duration: 12000 });
			}
		}
	}

	private isInProgress(job: any): boolean {
		return job?.status === 'IN_PROGRESS' || job?.status === 'QUEUED';
	}

	private managePolling(): void {
		const hasInProgressJob = this.jobs.some(job => this.isInProgress(job));
		if (this.intervalId) {
			clearInterval(this.intervalId);
			this.intervalId = undefined;
		}
		this.intervalId = setInterval(() => this.loadJobs(false), hasInProgressJob ? 2000 : 10000);
	}
}
