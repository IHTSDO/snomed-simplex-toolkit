import { Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatRadioModule } from '@angular/material/radio';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { FormsModule } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from '../../services/simplex/simplex.service';
import {
	TRANSLATION_STATUS_RADIO_ORDER,
	translationStatusRadioLabel
} from 'src/app/utils/translation-status-label';
import {
	countConceptIdsInFile,
	detectImportColumnMapping,
	readImportHeaders
} from 'src/app/utils/csv-column-mapping.util';

export interface UpdateConceptListDialogData {
	edition: string;
	refsetId: string;
	label: string;
	setName: string;
}

type ImportStatus = (typeof TRANSLATION_STATUS_RADIO_ORDER)[number];

@Component({
	selector: 'app-update-concept-list-dialog',
	standalone: true,
	imports: [
		CommonModule,
		MatDialogModule,
		MatButtonModule,
		MatRadioModule,
		MatProgressSpinnerModule,
		MatSelectModule,
		MatFormFieldModule,
		FormsModule
	],
	templateUrl: './update-concept-list-dialog.component.html',
	styleUrl: './update-concept-list-dialog.component.scss'
})
export class UpdateConceptListDialogComponent {
	@ViewChild('fileInput') fileInput?: ElementRef<HTMLInputElement>;

	loading = false;
	parsingHeaders = false;
	previewingConcepts = false;
	selectedFile: File | null = null;
	selectedFileName = '';
	headers: string[] = [];
	conceptColumn = '';
	termColumns: string[] = [];
	selectedStatus: ImportStatus = 'FOR_REVIEW';
	conceptPreviewCount = 0;
	conceptPreviewInvalidRows = 0;
	conceptPreviewDuplicateRows = 0;

	readonly statusOptions = TRANSLATION_STATUS_RADIO_ORDER.map((status) => ({
		value: status,
		label: translationStatusRadioLabel(status)
	}));

	constructor(
		public dialogRef: MatDialogRef<UpdateConceptListDialogComponent>,
		@Inject(MAT_DIALOG_DATA) public data: UpdateConceptListDialogData,
		private snackBar: MatSnackBar,
		private simplexService: SimplexService
	) {}

	get canSubmit(): boolean {
		return !!this.selectedFile
			&& !!this.conceptColumn
			&& this.conceptPreviewCount > 0
			&& (this.termColumns.length === 0 || !!this.selectedStatus)
			&& !this.loading
			&& !this.parsingHeaders
			&& !this.previewingConcepts;
	}

	get termColumnCountLabel(): string {
		const count = this.termColumns.length;
		if (count === 0) {
			return '';
		}
		return count === 1 ? '1 column selected' : `${count} columns selected`;
	}

	chooseFile(): void {
		this.fileInput?.nativeElement.click();
	}

	async onFileSelected(event: Event): Promise<void> {
		const input = event.target as HTMLInputElement;
		const file = input.files?.[0];
		if (!file) {
			return;
		}

		this.selectedFile = file;
		this.selectedFileName = file.name;
		this.parsingHeaders = true;
		this.headers = [];
		this.conceptColumn = '';
		this.termColumns = [];
		this.resetConceptPreview();

		try {
			this.headers = await readImportHeaders(file);
			if (this.headers.length === 0) {
				throw new Error('No header row found');
			}
			const mapping = detectImportColumnMapping(this.headers);
			this.conceptColumn = mapping.conceptColumn;
			this.termColumns = [...mapping.termColumns];
			await this.refreshConceptPreview();
		} catch (error) {
			console.error('Failed to read file headers:', error);
			this.selectedFile = null;
			this.selectedFileName = '';
			this.snackBar.open('Failed to read file headers. Please choose a valid CSV or Excel spreadsheet.', 'Close', {
				duration: 8000
			});
		} finally {
			this.parsingHeaders = false;
			input.value = '';
		}
	}

	async onConceptColumnChange(): Promise<void> {
		await this.refreshConceptPreview();
	}

	async refreshConceptPreview(): Promise<void> {
		if (!this.selectedFile || !this.conceptColumn) {
			this.resetConceptPreview();
			return;
		}
		this.previewingConcepts = true;
		try {
			const preview = await countConceptIdsInFile(this.selectedFile, this.conceptColumn);
			this.conceptPreviewCount = preview.conceptCount;
			this.conceptPreviewInvalidRows = preview.invalidRows;
			this.conceptPreviewDuplicateRows = preview.duplicateRows;
		} catch (error) {
			console.error('Failed to preview concept count:', error);
			this.resetConceptPreview();
		} finally {
			this.previewingConcepts = false;
		}
	}

	onCancel(): void {
		this.dialogRef.close();
	}

	onSubmit(): void {
		if (!this.canSubmit || !this.selectedFile) {
			return;
		}

		this.loading = true;
		const termColumns = this.termColumns.length > 0 ? this.termColumns : undefined;
		const status = termColumns ? this.selectedStatus : undefined;

		this.simplexService.updateTranslationSetConceptList(
			this.data.edition,
			this.data.refsetId,
			this.data.label,
			this.selectedFile,
			this.conceptColumn,
			termColumns,
			status
		).subscribe({
			next: (job) => {
				this.loading = false;
				this.dialogRef.close({
					action: 'update_started',
					jobId: job?.id,
					refsetId: this.data.refsetId
				});
			},
			error: (error) => {
				console.error('Error updating concept list:', error);
				this.loading = false;

				let errorMessage = 'Failed to update concept list from file';
				if (error.error?.message) {
					errorMessage = `${errorMessage}: ${error.error.message}`;
				}

				this.snackBar.open(errorMessage, 'Close', {
					duration: 8000
				});
			}
		});
	}

	private resetConceptPreview(): void {
		this.conceptPreviewCount = 0;
		this.conceptPreviewInvalidRows = 0;
		this.conceptPreviewDuplicateRows = 0;
	}
}
