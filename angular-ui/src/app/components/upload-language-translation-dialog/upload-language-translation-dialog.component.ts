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
import { detectCsvColumnMapping, readCsvHeaders } from 'src/app/utils/csv-column-mapping.util';

export interface LanguageImportOption {
	refsetId: string;
	languageDialect: string;
}

export interface UploadLanguageTranslationDialogData {
	edition: string;
	languages: LanguageImportOption[];
}

type ImportStatus = (typeof TRANSLATION_STATUS_RADIO_ORDER)[number];

@Component({
	selector: 'app-upload-language-translation-dialog',
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
	templateUrl: './upload-language-translation-dialog.component.html',
	styleUrl: './upload-language-translation-dialog.component.scss'
})
export class UploadLanguageTranslationDialogComponent {
	@ViewChild('fileInput') fileInput?: ElementRef<HTMLInputElement>;

	loading = false;
	parsingHeaders = false;
	selectedFile: File | null = null;
	selectedFileName = '';
	headers: string[] = [];
	conceptColumn = '';
	termColumns: string[] = [];
	selectedStatus: ImportStatus = 'FOR_REVIEW';
	selectedRefsetId = '';

	readonly statusOptions = TRANSLATION_STATUS_RADIO_ORDER.map((status) => ({
		value: status,
		label: translationStatusRadioLabel(status)
	}));

	constructor(
		public dialogRef: MatDialogRef<UploadLanguageTranslationDialogComponent>,
		@Inject(MAT_DIALOG_DATA) public data: UploadLanguageTranslationDialogData,
		private snackBar: MatSnackBar,
		private simplexService: SimplexService
	) {
		if (data.languages.length === 1) {
			this.selectedRefsetId = data.languages[0].refsetId;
		}
	}

	get canImport(): boolean {
		return !!this.selectedFile
			&& !!this.selectedRefsetId
			&& !!this.conceptColumn
			&& this.termColumns.length > 0
			&& !!this.selectedStatus
			&& !this.loading
			&& !this.parsingHeaders;
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

		try {
			this.headers = await readCsvHeaders(file);
			if (this.headers.length === 0) {
				throw new Error('No CSV header row found');
			}
			const mapping = detectCsvColumnMapping(this.headers);
			this.conceptColumn = mapping.conceptColumn;
			this.termColumns = [...mapping.termColumns];
		} catch (error) {
			console.error('Failed to read CSV headers:', error);
			this.selectedFile = null;
			this.selectedFileName = '';
			this.snackBar.open('Failed to read CSV headers. Please choose a valid CSV file.', 'Close', {
				duration: 8000
			});
		} finally {
			this.parsingHeaders = false;
			input.value = '';
		}
	}

	onCancel(): void {
		this.dialogRef.close();
	}

	onImport(): void {
		if (!this.canImport || !this.selectedFile) {
			return;
		}

		this.loading = true;
		this.simplexService.uploadLanguageTranslationCsv(
			this.data.edition,
			this.selectedRefsetId,
			this.selectedFile,
			this.conceptColumn,
			this.termColumns,
			this.selectedStatus
		).subscribe({
			next: (job) => {
				this.loading = false;
				this.dialogRef.close({ action: 'import_started', jobId: job?.id, refsetId: this.selectedRefsetId });
			},
			error: (error) => {
				console.error('Error importing language translation CSV:', error);
				this.loading = false;

				let errorMessage = 'Failed to import translation CSV';
				if (error.error?.message) {
					errorMessage = `${errorMessage}: ${error.error.message}`;
				}

				this.snackBar.open(errorMessage, 'Close', {
					duration: 8000
				});
			}
		});
	}
}
