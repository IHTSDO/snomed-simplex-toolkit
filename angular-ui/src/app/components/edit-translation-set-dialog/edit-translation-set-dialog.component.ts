import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from '../../services/simplex/simplex.service';

export interface EditTranslationSetDialogData {
    edition: string;
    refsetId: string;
    label: string;
    name: string;
    description?: string;
}

@Component({
    selector: 'app-edit-translation-set-dialog',
    standalone: true,
    imports: [
        CommonModule,
        MatDialogModule,
        MatButtonModule,
        MatFormFieldModule,
        MatInputModule,
        MatProgressSpinnerModule,
        ReactiveFormsModule
    ],
    templateUrl: './edit-translation-set-dialog.component.html',
    styleUrl: './edit-translation-set-dialog.component.scss'
})
export class EditTranslationSetDialogComponent {
    loading = false;
    editForm: FormGroup;

    constructor(
        public dialogRef: MatDialogRef<EditTranslationSetDialogComponent>,
        @Inject(MAT_DIALOG_DATA) public data: EditTranslationSetDialogData,
        private snackBar: MatSnackBar,
        private fb: FormBuilder,
        private simplexService: SimplexService
    ) {
        this.editForm = this.fb.group({
            name: [data.name, Validators.required],
            description: [data.description || '']
        });
    }

    onCancel(): void {
        this.dialogRef.close();
    }

    onSave(): void {
        if (!this.editForm.valid) {
            return;
        }

        this.loading = true;
        const formValue = this.editForm.value;
        const payload = {
            name: formValue.name.trim(),
            description: formValue.description?.trim() || undefined
        };

        this.simplexService.updateTranslationSet(
            this.data.edition,
            this.data.refsetId,
            this.data.label,
            payload
        ).subscribe({
            next: (updatedSet) => {
                this.loading = false;
                this.dialogRef.close({ action: 'updated', set: updatedSet });
            },
            error: (error) => {
                console.error('Error updating translation set:', error);
                this.loading = false;

                let errorMessage = 'Failed to update translation set';
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
