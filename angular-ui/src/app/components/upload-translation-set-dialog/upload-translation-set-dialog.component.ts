import { Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
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
	applySheetSelection,
	applyHeaderRowIndex,
	buildHeaderRowOptions,
	detectImportColumnMapping,
	isSpreadsheetFile,
	readCsvHeaders,
	readSpreadsheetSample,
	HeaderRowOption,
	SpreadsheetFileSample
} from 'src/app/utils/csv-column-mapping.util';

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
		MatCheckboxModule,
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
	skipRowsOutsideSet = true;
	spreadsheetSample: SpreadsheetFileSample | null = null;
	selectedSheetName = '';
	headerRowIndex = 0;
	headerRowOptions: HeaderRowOption[] = [];

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

	get isSpreadsheet(): boolean {
		return !!this.selectedFile && isSpreadsheetFile(this.selectedFile);
	}

	get showSpreadsheetSheetPicker(): boolean {
		return (this.spreadsheetSample?.sheets.length ?? 0) > 1;
	}

	get canImport(): boolean {
		return !!this.selectedFile
			&& !!this.conceptColumn
			&& this.termColumns.length > 0
			&& !!this.selectedStatus
			&& !this.loading
			&& !this.parsingHeaders;
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
		this.spreadsheetSample = null;
		this.selectedSheetName = '';
		this.headerRowIndex = 0;

		try {
			if (isSpreadsheetFile(file)) {
				this.spreadsheetSample = await readSpreadsheetSample(file);
				const initialSheet = this.spreadsheetSample.sheets[0];
				if (!initialSheet || initialSheet.headers.length === 0) {
					throw new Error('No header row found');
				}
				this.applySpreadsheetSheetSelection(initialSheet.name);
			} else {
				this.headers = await readCsvHeaders(file);
				if (this.headers.length === 0) {
					throw new Error('No header row found');
				}
				const mapping = detectImportColumnMapping(this.headers);
				this.conceptColumn = mapping.conceptColumn;
				this.termColumns = [...mapping.termColumns];
			}
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

	onSheetChange(): void {
		if (!this.spreadsheetSample || !this.selectedSheetName) {
			return;
		}
		try {
			this.applySpreadsheetSheetSelection(this.selectedSheetName);
		} catch (error) {
			console.error('Failed to read spreadsheet sheet:', error);
			this.snackBar.open('Failed to read the selected sheet.', 'Close', {
				duration: 8000
			});
		}
	}

	onHeaderRowChange(): void {
		if (!this.spreadsheetSample || !this.selectedSheetName) {
			return;
		}
		try {
			this.applySpreadsheetHeaderMapping();
		} catch (error) {
			console.error('Failed to apply header row:', error);
			this.snackBar.open('Failed to apply the selected header row.', 'Close', {
				duration: 8000
			});
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
			this.selectedStatus,
			this.skipRowsOutsideSet ? 'SKIP' : 'UPDATE',
			this.isSpreadsheet ? this.selectedSheetName : undefined,
			this.isSpreadsheet ? this.headerRowIndex : undefined
		).subscribe({
			next: (job) => {
				this.loading = false;
				this.dialogRef.close({
					action: 'import_started',
					jobId: job?.id,
					refsetId: this.data.refsetId
				});
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

	private applySpreadsheetSheetSelection(sheetName: string): void {
		if (!this.spreadsheetSample) {
			return;
		}
		const sheet = applySheetSelection(this.spreadsheetSample, sheetName);
		this.selectedSheetName = sheet.name;
		this.headerRowIndex = sheet.headerRowIndex;
		this.headerRowOptions = buildHeaderRowOptions(sheet.rows);
		this.applySpreadsheetHeaderMapping();
	}

	private applySpreadsheetHeaderMapping(): void {
		if (!this.spreadsheetSample || !this.selectedSheetName) {
			return;
		}
		const sheet = applyHeaderRowIndex(this.spreadsheetSample, this.selectedSheetName, this.headerRowIndex);
		this.headers = [...sheet.headers];
		const mapping = detectImportColumnMapping(this.headers);
		this.conceptColumn = mapping.conceptColumn;
		this.termColumns = [...mapping.termColumns];
	}
}
