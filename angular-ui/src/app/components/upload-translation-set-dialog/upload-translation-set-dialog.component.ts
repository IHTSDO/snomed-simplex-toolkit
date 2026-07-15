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

export interface UploadTranslationSetDialogData {
	edition: string;
	refsetId: string;
	label: string;
	setName: string;
	languageDialect: string;
}

type ImportStatus = (typeof TRANSLATION_STATUS_RADIO_ORDER)[number];

@Component({
	selector: 'app-upload-translation-set-dialog',
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
	templateUrl: './upload-translation-set-dialog.component.html',
	styleUrl: './upload-translation-set-dialog.component.scss'
})
export class UploadTranslationSetDialogComponent {
	@ViewChild('fileInput') fileInput?: ElementRef<HTMLInputElement>;

	loading = false;
	parsingHeaders = false;
	selectedFile: File | null = null;
	selectedFileName = '';
	headers: string[] = [];
	conceptColumn = '';
	termColumns: string[] = [];
	selectedStatus: ImportStatus = 'FOR_REVIEW';

	readonly statusOptions = TRANSLATION_STATUS_RADIO_ORDER.map((status) => ({
		value: status,
		label: translationStatusRadioLabel(status)
	}));

	constructor(
		public dialogRef: MatDialogRef<UploadTranslationSetDialogComponent>,
		@Inject(MAT_DIALOG_DATA) public data: UploadTranslationSetDialogData,
		private snackBar: MatSnackBar,
		private simplexService: SimplexService
	) {}

	get canImport(): boolean {
		return !!this.selectedFile
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
		this.simplexService.uploadTranslationSetCsv(
			this.data.edition,
			this.data.refsetId,
			this.data.label,
			this.selectedFile,
			this.conceptColumn,
			this.termColumns,
			this.selectedStatus
		).subscribe({
			next: () => {
				this.loading = false;
				this.dialogRef.close({ action: 'import_started' });
			},
			error: (error) => {
				console.error('Error importing translation set CSV:', error);
				this.loading = false;

				let errorMessage = 'Failed to import translation set CSV';
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
