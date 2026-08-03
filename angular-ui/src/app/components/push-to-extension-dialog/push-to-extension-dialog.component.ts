import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FormsModule } from '@angular/forms';
import { SimplexService } from '../../services/simplex/simplex.service';
import { translationStatusRadioLabel } from 'src/app/utils/translation-status-label';

export interface PushToExtensionDialogData {
	edition: string;
	refsetId: string;
	label: string;
	setName: string;
	statusCounts?: Record<string, number>;
}

@Component({
	selector: 'app-push-to-extension-dialog',
	standalone: true,
	imports: [
		CommonModule,
		MatDialogModule,
		MatButtonModule,
		MatCheckboxModule,
		MatProgressSpinnerModule,
		MatIconModule,
		FormsModule
	],
	templateUrl: './push-to-extension-dialog.component.html',
	styleUrl: './push-to-extension-dialog.component.scss'
})
export class PushToExtensionDialogComponent {
	loading = false;
	includeReadyForReview = false;

	readonly readyForReviewLabel = translationStatusRadioLabel('FOR_REVIEW');
	readonly readyToPushLabel = translationStatusRadioLabel('APPROVED');

	constructor(
		public dialogRef: MatDialogRef<PushToExtensionDialogComponent>,
		@Inject(MAT_DIALOG_DATA) public data: PushToExtensionDialogData,
		private snackBar: MatSnackBar,
		private simplexService: SimplexService
	) {}

	readyForReviewCount(): number {
		return this.data.statusCounts?.['FOR_REVIEW'] ?? 0;
	}

	readyToPushCount(): number {
		return this.data.statusCounts?.['APPROVED'] ?? 0;
	}

	onCancel(): void {
		this.dialogRef.close();
	}

	onPush(): void {
		this.loading = true;
		this.simplexService.pushToSnowstorm(
			this.data.edition,
			this.data.refsetId,
			this.data.label,
			{ includeReadyForReview: this.includeReadyForReview }
		).subscribe({
			next: () => {
				this.loading = false;
				this.dialogRef.close({ action: 'push_started' });
			},
			error: (error) => {
				console.error('Error pushing to extension:', error);
				this.loading = false;

				let errorMessage = 'Failed to push into extension';
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
