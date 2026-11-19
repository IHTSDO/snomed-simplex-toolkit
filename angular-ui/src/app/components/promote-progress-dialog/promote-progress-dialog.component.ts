import { Component, Inject, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { of, Subject, timer } from 'rxjs';
import { catchError, switchMap, takeUntil } from 'rxjs/operators';
import { SimplexService } from '../../services/simplex/simplex.service';

export interface PromoteProgressDialogData {
    edition: string;
    jobId: string;
    labelSetName: string;
}

export interface PromoteProgressResult {
    status: string;
    errorMessage?: string;
}

type Phase = 'working' | 'success' | 'error';

const POLL_INTERVAL_MS = 5000;
const MAX_CONSECUTIVE_POLL_FAILURES = 3;
const AUTO_CLOSE_MS = 5000;

/**
 * Full screen progress display for the long-running "Promote to Task" job,
 * styled after the rebase / merge screens in the Authoring Platform.
 */
@Component({
    selector: 'app-promote-progress-dialog',
    standalone: true,
    imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule],
    templateUrl: './promote-progress-dialog.component.html',
    styleUrl: './promote-progress-dialog.component.scss',
    encapsulation: ViewEncapsulation.None
})
export class PromoteProgressDialogComponent implements OnInit, OnDestroy {
    phase: Phase = 'working';
    heading = 'Promoting translations to task...';
    detail = 'Waiting for the promotion to start.';
    result?: PromoteProgressResult;

    private readonly destroy$ = new Subject<void>();
    private consecutiveFailures = 0;
    private autoCloseHandle?: ReturnType<typeof setTimeout>;

    constructor(
        private dialogRef: MatDialogRef<PromoteProgressDialogComponent, PromoteProgressResult>,
        @Inject(MAT_DIALOG_DATA) public data: PromoteProgressDialogData,
        private simplexService: SimplexService
    ) {
        this.dialogRef.disableClose = true;
    }

    ngOnInit(): void {
        timer(0, POLL_INTERVAL_MS).pipe(
            switchMap(() => this.simplexService.pollForExport(this.data.edition, this.data.jobId).pipe(
                catchError(() => of(null))
            )),
            takeUntil(this.destroy$)
        ).subscribe(job => {
            if (job) {
                this.consecutiveFailures = 0;
                this.handleStatus(job);
            } else {
                this.handlePollFailure();
            }
        });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
        if (this.autoCloseHandle) {
            clearTimeout(this.autoCloseHandle);
        }
    }

    close(): void {
        this.dialogRef.close(this.result);
    }

    private handleStatus(job: any): void {
        switch (job?.status) {
            case 'QUEUED':
                this.detail = 'Waiting for the promotion to start.';
                break;
            case 'IN_PROGRESS':
                this.detail = 'Pulling translations from Translation Studio into the task. This may take a few minutes.';
                break;
            case 'COMPLETE':
                this.finish('success', job, 'Success!', 'Translations have been promoted to the task.');
                this.autoCloseHandle = setTimeout(() => this.close(), AUTO_CLOSE_MS);
                break;
            case 'USER_CONTENT_ERROR':
                this.finish('error', job, 'Promotion failed',
                    job.errorMessage || 'Some translation content could not be promoted. Please review the job details.');
                break;
            case 'TECHNICAL_CONTENT_ISSUE':
            case 'SYSTEM_ERROR':
                this.finish('error', job, 'Promotion failed',
                    job.errorMessage || 'An unexpected error occurred. Please contact the administrator.');
                break;
            default:
                break;
        }
    }

    private handlePollFailure(): void {
        this.consecutiveFailures++;
        if (this.consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
            this.finish('error', { status: 'UNKNOWN' }, 'Lost contact with the server',
                'Could not check on the promotion. It may still be running; check the jobs list before trying again.');
        } else {
            this.detail = 'Having trouble reaching the server, retrying...';
        }
    }

    private finish(phase: Phase, job: any, heading: string, detail: string): void {
        this.destroy$.next();
        this.phase = phase;
        this.heading = heading;
        this.detail = detail;
        this.result = { status: job?.status, errorMessage: job?.errorMessage };
        this.dialogRef.disableClose = false;
    }
}
