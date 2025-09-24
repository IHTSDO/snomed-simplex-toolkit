import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

export interface AiBatchTranslationDialogData {
	edition: string;
	refsetId: string;
	labelSetName: string;
	label: string;
	selectedLabelSet?: any;
}

@Component({
	selector: 'app-ai-batch-translation-dialog',
	standalone: true,
	imports: [
		CommonModule,
		MatDialogModule,
		MatButtonModule,
		MatFormFieldModule,
		MatSelectModule,
		MatProgressSpinnerModule,
		MatIconModule,
		ReactiveFormsModule
	],
	template: `
		<h2 mat-dialog-title>Run AI Translation Batch</h2>
		<mat-dialog-content>
			<div class="dialog-content">
				<p>You are about to run an AI translation batch for: <strong>{{ data.labelSetName }}</strong></p>
				
				<div class="warning-section">
					<mat-icon class="warning-icon">warning</mat-icon>
					<div class="warning-text">
						<p><strong>Important:</strong> This will generate AI translations for the selected number of concepts.</p>
						<p>Please ensure you have completed the AI setup before running the batch translation.</p>
					</div>
				</div>
				
				<form [formGroup]="batchForm" class="batch-form">
					<mat-form-field appearance="outline" class="full-width">
						<mat-label>Batch Size</mat-label>
						<mat-select formControlName="batchSize" required>
							<mat-option value="10">10 concepts</mat-option>
							<mat-option value="20">20 concepts</mat-option>
							<mat-option value="50">50 concepts</mat-option>
							<mat-option value="100">100 concepts</mat-option>
							<mat-option value="200">200 concepts</mat-option>
							<mat-option value="500">500 concepts</mat-option>
							<mat-option value="1000">1000 concepts</mat-option>
						</mat-select>
						<mat-hint>Select the number of concepts to translate in this batch</mat-hint>
					</mat-form-field>
				</form>
				
				<div class="info-section">
					<mat-icon class="info-icon">info</mat-icon>
					<div class="info-text">
						<p>The AI will process concepts that haven't been translated yet.</p>
						<p>Processing time will depend on the batch size and complexity of the concepts.</p>
					</div>
				</div>
			</div>
		</mat-dialog-content>
		
		<mat-dialog-actions align="end">
			<button mat-button (click)="onCancel()">Cancel</button>
			<button 
				mat-raised-button 
				color="primary" 
				[disabled]="loading || batchForm.invalid"
				(click)="onConfirm()">
				<span *ngIf="loading" class="loading-content">
					<mat-spinner diameter="20"></mat-spinner>
					Starting batch...
				</span>
				<span *ngIf="!loading">Start AI Translation Batch</span>
			</button>
		</mat-dialog-actions>
	`,
	styles: [`
		.dialog-content {
			min-width: 400px;
			max-width: 500px;
		}
		
		.batch-form {
			margin: 24px 0;
		}
		
		.full-width {
			width: 100%;
		}
		
		.warning-section {
			display: flex;
			align-items: flex-start;
			gap: 12px;
			padding: 16px;
			background-color: #fff3cd;
			border: 1px solid #ffeaa7;
			border-radius: 4px;
			margin: 16px 0;
		}
		
		.warning-icon {
			color: #856404;
			font-size: 24px;
			width: 24px;
			height: 24px;
		}
		
		.warning-text {
			flex: 1;
		}
		
		.warning-text p {
			margin: 0 0 8px 0;
			color: #856404;
			font-size: 0.9em;
			line-height: 1.4;
		}
		
		.warning-text p:last-child {
			margin-bottom: 0;
		}
		
		.info-section {
			display: flex;
			align-items: flex-start;
			gap: 12px;
			padding: 16px;
			background-color: #e3f2fd;
			border: 1px solid #bbdefb;
			border-radius: 4px;
			margin: 16px 0;
		}
		
		.info-icon {
			color: #1976d2;
			font-size: 24px;
			width: 24px;
			height: 24px;
		}
		
		.info-text {
			flex: 1;
		}
		
		.info-text p {
			margin: 0 0 8px 0;
			color: #1976d2;
			font-size: 0.9em;
			line-height: 1.4;
		}
		
		.info-text p:last-child {
			margin-bottom: 0;
		}
		
		.loading-content {
			display: flex;
			align-items: center;
			gap: 8px;
		}
		
		mat-dialog-content {
			max-height: 600px;
			overflow-y: auto;
		}
	`]
})
export class AiBatchTranslationDialogComponent {
	loading = false;
	batchForm: FormGroup;

	constructor(
		public dialogRef: MatDialogRef<AiBatchTranslationDialogComponent>,
		@Inject(MAT_DIALOG_DATA) public data: AiBatchTranslationDialogData,
		private snackBar: MatSnackBar,
		private fb: FormBuilder,
		private http: HttpClient
	) {
		this.batchForm = this.fb.group({
			batchSize: ['10', Validators.required]
		});
	}

	onCancel(): void {
		this.dialogRef.close();
	}

	onConfirm(): void {
		if (this.batchForm.valid) {
			this.loading = true;
			const batchSize = parseInt(this.batchForm.value.batchSize);
			
			// Build the API URL
			const apiUrl = `/api/${this.data.edition}/translations/${this.data.refsetId}/weblate-set/${this.data.label}/run-ai-batch`;
			
			// Prepare the request body
			const requestBody = {
				size: batchSize
			};

			this.http.post(apiUrl, requestBody).subscribe({
				next: () => {
					this.loading = false;
					this.snackBar.open(`AI translation batch started for ${batchSize} concepts`, 'Close', {
						duration: 5000
					});
					this.dialogRef.close({
						action: 'batch_started',
						batchSize: batchSize
					});
				},
				error: (error) => {
					console.error('Error starting AI batch translation:', error);
					this.loading = false;
					
					// Extract error message from API response
					let errorMessage = 'Failed to start AI translation batch';
					if (error.error && error.error.message) {
						errorMessage = `${errorMessage}: ${error.error.message}`;
					}
					
					this.snackBar.open(errorMessage, 'Close', {
						duration: 8000
					});
				}
			});
		}
	}
}
