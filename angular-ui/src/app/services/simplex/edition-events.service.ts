import { Injectable, NgZone } from '@angular/core';
import { Observable } from 'rxjs';

export interface TranslationSyncEvent {
    trigger: 'TASK_PROMOTED' | 'PROJECT_REBASED';
    status: string;
    jobId: string;
    errorMessage?: string;
    /** Language refsets pulled from Snowstorm. */
    refsetsSynchronised: number;
    /** Units moved to Complete by a task promotion; absent for a project rebase. */
    unitsCompleted?: number;
}

export interface TranslationTaskRevertedEvent {
    taskBranch: string;
    unitsReverted: number;
}

export type TranslationEvent =
    | { type: 'translation-sync'; data: TranslationSyncEvent }
    | { type: 'translation-task-reverted'; data: TranslationTaskRevertedEvent };

/**
 * Server-sent events for an edition. The browser's EventSource reconnects automatically
 * and sends the session cookie, so no extra authentication handling is needed.
 */
@Injectable({ providedIn: 'root' })
export class EditionEventsService {

    constructor(private zone: NgZone) {}

    /**
     * Emits translation events for the edition over a single connection: content synchronised from Snowstorm
     * (project rebase or task promotion) and a task deleted in the Authoring Platform (units reverted).
     */
    translationEvents(edition: string): Observable<TranslationEvent> {
        return new Observable<TranslationEvent>(subscriber => {
            const source = new EventSource(`api/${encodeURIComponent(edition)}/events`);
            const listen = (type: TranslationEvent['type']) => {
                source.addEventListener(type, (event: Event) => {
                    try {
                        const data = JSON.parse((event as MessageEvent).data);
                        this.zone.run(() => subscriber.next({ type, data } as TranslationEvent));
                    } catch (e) {
                        console.error(`Could not parse ${type} event`, e);
                    }
                });
            };
            listen('translation-sync');
            listen('translation-task-reverted');
            return () => source.close();
        });
    }
}
