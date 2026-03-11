import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { SimplexService } from '../../services/simplex/simplex.service';
import { ToastrService } from "ngx-toastr";

export interface WeblateUser {
    id: number;
    username: string;
    full_name: string;
    email: string;
}

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
        MatSelectModule,
        MatProgressSpinnerModule,
        MatIconModule,
        ReactiveFormsModule
    ],
    templateUrl: './export-task-dialog.component.html',
    styleUrl: './export-task-dialog.component.scss'
})
export class ExportTaskDialogComponent implements OnInit {
    loading = false;
    loadingUsers = false;
    checkingForTask = true;
    exportForm: FormGroup;
    users: WeblateUser[] = [];

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
            assignee: ['', Validators.required]
        });
    }

    ngOnInit(): void {
        this.loadUsers();

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

    loadUsers(): void {
        this.loadingUsers = true;
        this.simplexService.getWeblateUsersForRefset(this.data.edition, this.data.refsetId).subscribe({
            next: (users: WeblateUser[]) => {
                this.users = users.sort((a, b) => a.full_name.localeCompare(b.full_name));
                this.loadingUsers = false;

                // If there's only one user, select it by default (optional, but convenient)
                if (this.users.length === 1) {
                    this.exportForm.patchValue({ assignee: this.users[0].username });
                }
            },
            error: (error) => {
                console.error('Error loading users:', error);
                this.snackBar.open('Error loading users. Please try again.', 'Close', {
                    duration: 3000
                });
                this.loadingUsers = false;
            }
        });
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
                    assigneeUsername: this.exportForm.value.assignee
                };
            }

            this.simplexService.pullFromWeblate(
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
