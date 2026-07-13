import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatRadioModule } from '@angular/material/radio';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FormsModule } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from '../../services/simplex/simplex.service';
import { translationStatusRadioLabel } from 'src/app/utils/translation-status-label';

export type TranslationSetDownloadStatusFilter =
	| 'FOR_REVIEW'
	| 'NOT_STARTED'
	| 'NEEDS_EDIT'
	| 'APPROVED'
	| 'ALL';

export interface DownloadTranslationSetDialogData {
	edition: string;
	refsetId: string;
	label: string;
	setName: string;
	languageDialect: string;
	statusCounts?: Record<string, number>;
	totalSize?: number;
}

interface DownloadFilterOption {
	value: TranslationSetDownloadStatusFilter;
	label: string;
	statusParam: string | null;
}

@Component({
	selector: 'app-download-translation-set-dialog',
	standalone: true,
	imports: [
		CommonModule,
		MatDialogModule,
		MatButtonModule,
		MatRadioModule,
		MatProgressSpinnerModule,
		FormsModule
	],
	templateUrl: './download-translation-set-dialog.component.html',
	styleUrl: './download-translation-set-dialog.component.scss'
})
export class DownloadTranslationSetDialogComponent {
	loading = false;
	selectedFilter: TranslationSetDownloadStatusFilter = 'FOR_REVIEW';

	readonly filterOptions: DownloadFilterOption[] = [
		{ value: 'FOR_REVIEW', label: translationStatusRadioLabel('FOR_REVIEW'), statusParam: 'FOR_REVIEW' },
		{ value: 'NOT_STARTED', label: translationStatusRadioLabel('NOT_STARTED'), statusParam: 'NOT_STARTED' },
		{ value: 'NEEDS_EDIT', label: translationStatusRadioLabel('NEEDS_EDIT'), statusParam: 'NEEDS_EDIT' },
		{ value: 'APPROVED', label: translationStatusRadioLabel('APPROVED'), statusParam: 'APPROVED' },
		{ value: 'ALL', label: 'All concepts', statusParam: null }
	];

	constructor(
		public dialogRef: MatDialogRef<DownloadTranslationSetDialogComponent>,
		@Inject(MAT_DIALOG_DATA) public data: DownloadTranslationSetDialogData,
		private snackBar: MatSnackBar,
		private simplexService: SimplexService
	) {}

	conceptCount(option: DownloadFilterOption): number {
		if (option.value === 'ALL') {
			return this.data.totalSize ?? 0;
		}
		return this.data.statusCounts?.[option.statusParam!] ?? 0;
	}

	onCancel(): void {
		this.dialogRef.close();
	}

	onDownload(): void {
		const option = this.filterOptions.find((o) => o.value === this.selectedFilter);
		if (!option) {
			return;
		}

		this.loading = true;
		this.simplexService
			.downloadTranslationSetCsv(
				this.data.edition,
				this.data.refsetId,
				this.data.label,
				option.statusParam
			)
			.subscribe({
				next: (blob) => {
					const filename = this.buildFilename(option);
					this.simplexService.triggerDownload(blob, filename);
					this.loading = false;
					this.dialogRef.close({ action: 'downloaded' });
				},
				error: (error) => {
					console.error('Error downloading translation set CSV:', error);
					this.loading = false;

					let errorMessage = 'Failed to download translation set CSV';
					if (error.error?.message) {
						errorMessage = `${errorMessage}: ${error.error.message}`;
					}

					this.snackBar.open(errorMessage, 'Close', {
						duration: 8000
					});
				}
			});
	}

	private buildFilename(option: DownloadFilterOption): string {
		const setSlug = (this.data.setName || this.data.label)
			.trim()
			.replace(/\s+/g, '_')
			.replace(/[^\w.-]+/g, '_');
		const filterSlug = this.filterFilenameSlug(option);
		return `${setSlug}-${filterSlug}.csv`;
	}

	private filterFilenameSlug(option: DownloadFilterOption): string {
		switch (option.value) {
			case 'ALL':
				return 'all-concepts';
			case 'NOT_STARTED':
				return 'not-started';
			case 'NEEDS_EDIT':
				return 'needs-editing';
			case 'FOR_REVIEW':
				return 'ready-for-review';
			case 'APPROVED':
				return 'ready-to-push';
		}
	}
}
