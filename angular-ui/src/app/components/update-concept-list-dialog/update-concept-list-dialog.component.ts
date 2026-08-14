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
	applySheetSelection,
	applyHeaderRowIndex,
	buildHeaderRowOptions,
	countConceptIdsInFile,
	detectImportColumnMapping,
	isSpreadsheetFile,
	readCsvHeaders,
	readSpreadsheetSample,
	HeaderRowOption,
	SpreadsheetFileSample
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
	spreadsheetSample: SpreadsheetFileSample | null = null;
	selectedSheetName = '';
	headerRowIndex = 0;
	headerRowOptions: HeaderRowOption[] = [];

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

	get isSpreadsheet(): boolean {
		return !!this.selectedFile && isSpreadsheetFile(this.selectedFile);
	}

	get showSpreadsheetSheetPicker(): boolean {
		return (this.spreadsheetSample?.sheets.length ?? 0) > 1;
	}

	get canSubmit(): boolean {
		const hasConceptPreview = this.isSpreadsheet || this.conceptPreviewCount > 0;
		return !!this.selectedFile
			&& !!this.conceptColumn
			&& hasConceptPreview
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
		this.spreadsheetSample = null;
		this.selectedSheetName = '';
		this.headerRowIndex = 0;
		this.resetConceptPreview();

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

			this.parsingHeaders = false;
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

	async onSheetChange(): Promise<void> {
		if (!this.spreadsheetSample || !this.selectedSheetName) {
			return;
		}
		try {
			this.applySpreadsheetSheetSelection(this.selectedSheetName);
			await this.refreshConceptPreview();
		} catch (error) {
			console.error('Failed to read spreadsheet sheet:', error);
			this.snackBar.open('Failed to read the selected sheet.', 'Close', {
				duration: 8000
			});
		}
	}

	async onHeaderRowChange(): Promise<void> {
		if (!this.spreadsheetSample || !this.selectedSheetName) {
			return;
		}
		try {
			this.applySpreadsheetHeaderMapping();
			await this.refreshConceptPreview();
		} catch (error) {
			console.error('Failed to apply header row:', error);
			this.snackBar.open('Failed to apply the selected header row.', 'Close', {
				duration: 8000
			});
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
			const spreadsheetOptions = this.isSpreadsheet && this.spreadsheetSample
				? {
					sample: this.spreadsheetSample,
					sheetName: this.selectedSheetName,
					headerRowIndex: this.headerRowIndex
				}
				: undefined;
			const preview = await countConceptIdsInFile(this.selectedFile, this.conceptColumn, spreadsheetOptions);
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
			status,
			this.isSpreadsheet ? this.selectedSheetName : undefined,
			this.isSpreadsheet ? this.headerRowIndex : undefined
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

	private resetConceptPreview(): void {
		this.conceptPreviewCount = 0;
		this.conceptPreviewInvalidRows = 0;
		this.conceptPreviewDuplicateRows = 0;
	}
}
