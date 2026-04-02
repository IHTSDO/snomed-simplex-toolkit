import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { SimplexService } from '../../services/simplex/simplex.service';
import { ToastrService } from "ngx-toastr";

export interface ExportTaskDialogData {
    edition: string;
    refsetId: string;
    labelSetName: string;
    label: string;
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
        MatProgressSpinnerModule,
        MatIconModule,
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

    constructor(
        public dialogRef: MatDialogRef<ExportTaskDialogComponent>,
        @Inject(MAT_DIALOG_DATA) public data: ExportTaskDialogData,
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

    onCancel(): void {
        this.dialogRef.close();
    }

    onExport(): void {
        if (this.currentApTask || this.exportForm.valid) {
            this.loading = true;

            let taskData = {};

            if (!this.currentApTask) {
                taskData = {
                    taskTitle: this.exportForm.value.taskName,
                };
            }

            this.simplexService.pullFromSnolate(
                this.data.edition,
                this.data.refsetId,
                this.data.label,
                taskData
            ).subscribe({
                next: pullData => {
                    const responseCheck = setInterval(() => {
                        this.simplexService.pollForExport(this.data.edition, pullData.id).subscribe({
                            next: data => {
                                if (data.status === 'COMPLETE') {
                                    clearInterval(responseCheck);
                                    this.toastr.success('EXPORT COMPLETE');
                                    this.loading = false;
                                }

                                if (data.status === 'SYSTEM_ERROR') {
                                    clearInterval(responseCheck);
                                    this.toastr.error('EXPORT FAILED');
                                    this.loading = false;
                                    this.multipleTasksDetected = true;
                                }
                            }
                        })
                    }, 15000);
                },
                error: (error) => {
                    console.error('Error exporting to authoring platform:', error);
                    this.loading = false;

                    let errorMessage = 'Failed to export to Authoring Platform';
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
