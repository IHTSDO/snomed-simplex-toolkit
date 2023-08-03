import {Injectable} from '@angular/core';
import {Observable, Subject} from "rxjs";
import {HttpClient} from "@angular/common/http";

@Injectable({
    providedIn: 'root'
})
export class SnowstormService {

    private codesystems = new Subject();
    private activeCodesystem = new Subject();


    constructor(private http: HttpClient) {
    }

    setCodesystems(codesystems) {
        this.codesystems.next(codesystems);
    }

    getCodesystems() {
        return this.codesystems.asObservable();
    }

    httpGetCodesystems(): Observable<any> {
        return this.http.get('/snowstorm/snomed-ct/codesystems');
    }

    setActiveCodesystem(codesystem) {
        this.activeCodesystem.next(codesystem);
    }

    getActiveCodesystem() {
        return this.activeCodesystem.asObservable();
    }
}
