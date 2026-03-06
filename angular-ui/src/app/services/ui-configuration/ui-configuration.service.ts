import {Injectable} from '@angular/core';
import {BehaviorSubject, Observable, Subject} from 'rxjs';
import {catchError} from "rxjs/operators";
import {HttpClient} from "@angular/common/http";

@Injectable({
    providedIn: 'root'
})
export class UiConfigurationService {

    private configuration: any;
    private authoringPlatformMode = new Subject<boolean>();
    private editionSubject = new BehaviorSubject<any>(null); // Initialized with a default value

    constructor(private http: HttpClient) {
    }

    setConfiguration(configuration: any) {
        this.configuration = configuration;
    }

    getConfiguration(): any {
        return this.configuration;
    }

    setAuthoringPlatformMode(authoringPlatformMode: boolean) {
        this.authoringPlatformMode.next(authoringPlatformMode); // Emitting the new value
    }

    getAuthoringPlatformMode(): Observable<boolean> {
        return this.authoringPlatformMode.asObservable(); // Returning the observable
    }

    httpGetUIConfiguration(): Observable<any> {
        return this.http.get('api/ui-configuration');
    }

    setSelectedEdition(edition: any) {
        this.editionSubject.next(edition); // Emitting the new value
    }

    getSelectedEdition(): Observable<any> {
        return this.editionSubject.asObservable(); // Returning the observable
    }
}
