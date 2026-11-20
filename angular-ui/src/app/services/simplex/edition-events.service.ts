import { Injectable, NgZone } from '@angular/core';
import { Observable } from 'rxjs';

export interface TranslationSyncEvent {
    trigger: string;
    status: string;
    jobId: string;
    errorMessage?: string;
}

/**
 * Server-sent events for an edition. The browser's EventSource reconnects automatically
 * and sends the session cookie, so no extra authentication handling is needed.
 */
@Injectable({ providedIn: 'root' })
export class EditionEventsService {

    constructor(private zone: NgZone) {}

    /** Emits each time translation content is synchronised from Snowstorm (project rebase or task promotion). */
    translationSync(edition: string): Observable<TranslationSyncEvent> {
        return new Observable<TranslationSyncEvent>(subscriber => {
            const source = new EventSource(`api/${encodeURIComponent(edition)}/events`);
            source.addEventListener('translation-sync', (event: Event) => {
                try {
                    const data = JSON.parse((event as MessageEvent).data) as TranslationSyncEvent;
                    this.zone.run(() => subscriber.next(data));
                } catch (e) {
                    console.error('Could not parse translation-sync event', e);
                }
            });
            return () => source.close();
        });
    }
}
