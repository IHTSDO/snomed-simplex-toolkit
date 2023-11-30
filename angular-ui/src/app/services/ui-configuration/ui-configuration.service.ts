import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class UiConfigurationService {

  private configuration: any;
  private editionSubject = new BehaviorSubject<any>(null); // Initialized with a default value

  constructor() { }

  setConfiguration(configuration: any) {
    this.configuration = configuration;
  }

  getConfiguration(): Observable<any> {
    return this.configuration;
  }

  setSelectedEdition(edition: any) {
    this.editionSubject.next(edition); // Emitting the new value
  }

  getSelectedEdition(): Observable<any> {
    return this.editionSubject.asObservable(); // Returning the observable
  }
}
