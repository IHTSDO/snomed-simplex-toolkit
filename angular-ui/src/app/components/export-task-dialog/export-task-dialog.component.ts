import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialog, MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FormBuilder, FormGroup, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { SimplexService } from '../../services/simplex/simplex.service';
import { translationStatusRadioLabel } from 'src/app/utils/translation-status-label';
import { ToastrService } from "ngx-toastr";
import { PromoteProgressDialogComponent, PromoteProgressResult } from '../promote-progress-dialog/promote-progress-dialog.component';

export interface ExportTaskDialogData {
    edition: string;
    refsetId: string;
    labelSetName: string;
    label: string;
    statusCounts?: Record<string, number>;
}

@Component({
    selector: 'app-export-task-dialog',
    standalone: true,
    imports: [
        CommonModule,
        MatDialogModule,
        MatButtonModule,
        MatFormFieldModule,
        MatInputModule,
        MatCheckboxModule,
        MatProgressSpinnerModule,
        MatIconModule,
        FormsModule,
        ReactiveFormsModule
    ],
    templateUrl: './export-task-dialog.component.html',
    styleUrl: './export-task-dialog.component.scss'
})
export class ExportTaskDialogComponent implements OnInit {
    loading = false;
    checkingForTask = true;
    exportForm: FormGroup;

    currentApTask: any;
    multipleTasksDetected: boolean = false;

    /** Statuses that can be promoted, with Approved ticked by default. */
    readonly statusOptions = ['APPROVED', 'FOR_REVIEW'].map(status => ({
        status,
        label: translationStatusRadioLabel(status),
        count: this.data.statusCounts?.[status] ?? 0,
        selected: status === 'APPROVED'
    }));

    constructor(
        public dialogRef: MatDialogRef<ExportTaskDialogComponent>,
        @Inject(MAT_DIALOG_DATA) public data: ExportTaskDialogData,
        private dialog: MatDialog,
        private snackBar: MatSnackBar,
        private fb: FormBuilder,
        private simplexService: SimplexService,
        private toastr: ToastrService
    ) {
        this.exportForm = this.fb.group({
            taskName: [`Translation of ${data.labelSetName}`, Validators.required],
        });
    }

    ngOnInit(): void {
        this.simplexService.getCurrentAPTask(this.data.edition, this.data.refsetId, this.data.label).subscribe({
            next: data => {
                this.checkingForTask = false;
                if (data?.key) {
                    this.currentApTask = data;
                }
            },
            error: err => {
                this.checkingForTask = false;
                if (err.status === 409) {
                    this.multipleTasksDetected = true;
                }
            }
        })
    }

    selectedStatuses(): string[] {
        return this.statusOptions.filter(o => o.selected).map(o => o.status);
    }

    selectedUnitCount(): number {
        return this.statusOptions.filter(o => o.selected).reduce((total, o) => total + o.count, 0);
    }

    canPromote(): boolean {
        return !this.loading && this.selectedUnitCount() > 0
            && (!!this.currentApTask || this.exportForm.valid);
    }

    onCancel(): void {
        this.dialogRef.close();
    }

    onPromote(): void {
        if (this.canPromote()) {
            this.loading = true;

            const taskData: any = { statuses: this.selectedStatuses() };

            if (!this.currentApTask) {
                taskData.taskTitle = this.exportForm.value.taskName;
            }

            this.simplexService.pullFromTranslationStudio(
                this.data.edition,
                this.data.refsetId,
                this.data.label,
                taskData
            ).subscribe({
                next: pullData => this.showProgress(pullData.id),
                error: (error) => {
                    console.error('Error promoting to task in the Authoring Platform:', error);
                    this.loading = false;

                    let errorMessage = 'Failed to promote to task';
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

    private showProgress(jobId: string): void {
        const progressRef = this.dialog.open<PromoteProgressDialogComponent, any, PromoteProgressResult>(
            PromoteProgressDialogComponent, {
                data: {
                    edition: this.data.edition,
                    jobId,
                    labelSetName: this.data.labelSetName
                },
                width: '100vw',
                height: '100vh',
                maxWidth: '100vw',
                panelClass: 'promote-progress-panel',
                disableClose: true
            });

        progressRef.afterClosed().subscribe(result => {
            this.loading = false;
            if (result?.status === 'COMPLETE') {
                this.toastr.success('Promoted to task');
                this.dialogRef.close({ action: 'export_started', status: result.status });
            } else if (result) {
                this.dialogRef.close({ action: 'export_failed', status: result.status });
            }
        });
    }
}
